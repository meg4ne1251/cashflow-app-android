package com.kakeibo.android.core.data.sync

import androidx.room.withTransaction
import com.kakeibo.android.core.database.CashflowDatabase
import com.kakeibo.android.core.database.dao.AccountDao
import com.kakeibo.android.core.database.dao.BudgetDao
import com.kakeibo.android.core.database.dao.CategoryDao
import com.kakeibo.android.core.database.dao.InputPatternDao
import com.kakeibo.android.core.database.dao.NotificationSettingDao
import com.kakeibo.android.core.database.dao.RecurringTransactionDao
import com.kakeibo.android.core.database.dao.TagDao
import com.kakeibo.android.core.database.dao.TemplateDao
import com.kakeibo.android.core.database.dao.TransactionDao
import com.kakeibo.android.core.database.dao.TransferDao
import com.kakeibo.android.core.network.api.SyncApiService
import com.kakeibo.android.core.network.dto.SyncData
import com.kakeibo.android.core.network.dto.SyncPushRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a sync run. */
sealed interface SyncOutcome {
    data class Success(val pulled: Int) : SyncOutcome
    data class Error(val message: String) : SyncOutcome
}

/**
 * Offline-first sync foundation (design §3.1). Phase 2 implements the pull half: it fetches
 * every row updated since the stored watermark and upserts it into Room as the source of
 * truth. The push half (uploading local `pending` rows) lands in Phase 3 with local CRUD.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val api: SyncApiService,
    private val db: CashflowDatabase,
    private val watermark: SyncWatermarkStore,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val tagDao: TagDao,
    private val transactionDao: TransactionDao,
    private val transferDao: TransferDao,
    private val templateDao: TemplateDao,
    private val recurringDao: RecurringTransactionDao,
    private val budgetDao: BudgetDao,
    private val notificationSettingDao: NotificationSettingDao,
    private val inputPatternDao: InputPatternDao,
) {

    /** Serializes full sync runs so the in-app [SyncManager] and the [SyncWorker] never overlap. */
    private val syncMutex = Mutex()

    /**
     * Full offline-first sync: push local changes first, then pull. Push runs first so a row we
     * just uploaded comes back in the same pull and reconciles to the server's authoritative
     * version/timestamp. A push transport failure aborts before the (doomed) pull.
     */
    suspend fun sync(): SyncOutcome = syncMutex.withLock {
        when (val pushResult = push()) {
            is SyncOutcome.Error -> pushResult
            is SyncOutcome.Success -> pull()
        }
    }

    /**
     * Uploads every locally-changed transaction (created/edited/soft-deleted offline) in batches.
     * On `accepted` the row adopts the server version and is marked clean; on `conflict` it is
     * flagged so the following pull overwrites it with the server's newer copy (last-write-wins,
     * server side, per req 2.6.2); on `error` it stays pending for the next attempt.
     */
    suspend fun push(): SyncOutcome {
        return try {
            // Promote any soft-delete whose undo window has long since closed (e.g. the app was
            // killed before commitDelete ran) so it is not stranded as a locally-hidden, never-pushed row.
            transactionDao.commitStaleDeletes(System.currentTimeMillis() - DELETE_COMMIT_GRACE_MS)
            val dirty = transactionDao.dirty()
            if (dirty.isEmpty()) return SyncOutcome.Success(0)
            var pushed = 0
            dirty.chunked(MAX_BATCH).forEach { batch ->
                val byId = batch.associateBy { it.id }
                val response = api.push(SyncPushRequest(changes = batch.map { it.toSyncChange() }))
                db.withTransaction {
                    response.results.forEach { result ->
                        val entity = byId[result.id] ?: return@forEach
                        // Reconcile against the pre-push snapshot's local_updated_at: if the row was
                        // edited or hard-deleted while the request was in flight, the guarded update is
                        // a no-op and the fresh local change is preserved (re-pushed next run) instead
                        // of being silently overwritten with the stale, just-acknowledged data.
                        val stamp = entity.sync.localUpdatedAt
                        when (result.status) {
                            "accepted" -> {
                                val newVersion = result.server_version ?: (entity.version + 1)
                                if (transactionDao.markSynced(entity.id, newVersion, stamp) > 0) pushed++
                            }
                            "conflict" -> transactionDao.markConflicted(entity.id, stamp)
                            else -> Timber.w(
                                "Sync push rejected: %s %s status=%s",
                                result.entity_type, result.id, result.status,
                            )
                        }
                    }
                }
            }
            Timber.i("Sync push complete: %d accepted", pushed)
            SyncOutcome.Success(pushed)
        } catch (e: HttpException) {
            Timber.w(e, "Sync push HTTP error")
            SyncOutcome.Error("HTTP ${e.code()}")
        } catch (e: IOException) {
            Timber.w(e, "Sync push network error")
            SyncOutcome.Error(e.message ?: "network")
        } catch (e: Exception) {
            Timber.e(e, "Sync push failed")
            SyncOutcome.Error(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    /**
     * Pulls all changes since the last watermark, paging until the server reports no more.
     * On success the watermark advances to the final page's `sync_timestamp`; on any failure
     * it is left untouched so the next attempt retries from the same point.
     */
    suspend fun pull(): SyncOutcome {
        return try {
            var cursor = watermark.get() ?: EPOCH
            var pulled = 0
            var page = 0
            var complete = false
            while (true) {
                val response = api.pull(cursor)
                val data = response.data
                persist(data)
                pulled += data.total()

                if (!response.has_more) {
                    watermark.set(response.sync_timestamp)
                    complete = true
                    break
                }
                // Advance to the OLDEST of each entity type's newest row, not the global max.
                // Every type is paginated independently and capped server-side, so a type that
                // was truncated still has un-fetched rows at/after its own max `updated_at`.
                // Jumping to the global max (e.g. a freshly-touched account) would step the
                // cursor past those rows of a heavier type (e.g. transactions) and silently drop
                // them. Already-synced rows of other types may be re-fetched on the next page,
                // but the upsert is idempotent so that is harmless.
                val next = data.nextCursor()
                if (next == null || next == cursor || ++page > MAX_PAGES) {
                    Timber.w("Sync pagination stalled (page=%d, cursor=%s) after %d rows", page, cursor, pulled)
                    break
                }
                cursor = next
            }
            if (!complete) {
                // The server still reported has_more but the cursor could not advance (a
                // page-cap tie with no secondary sort key, or the MAX_PAGES guard). The
                // watermark is deliberately left untouched so the next run retries from the
                // same point; report an error rather than a false Success so the incomplete
                // pull is visible instead of silently re-fetching forever.
                return SyncOutcome.Error("sync incomplete: pagination stalled after $pulled rows")
            }
            Timber.i("Sync pull complete: %d rows", pulled)
            SyncOutcome.Success(pulled)
        } catch (e: HttpException) {
            Timber.w(e, "Sync pull HTTP error")
            SyncOutcome.Error("HTTP ${e.code()}")
        } catch (e: IOException) {
            Timber.w(e, "Sync pull network error")
            SyncOutcome.Error(e.message ?: "network")
        } catch (e: Exception) {
            Timber.e(e, "Sync pull failed")
            SyncOutcome.Error(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    private suspend fun persist(data: SyncData) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            accountDao.upsert(data.accounts.map { it.toEntity(now) })
            categoryDao.upsert(data.categories.map { it.toEntity(now) })
            tagDao.upsert(data.tags.map { it.toEntity(now) })
            // Never overwrite a transaction that still holds an un-pushed local change (`pending`) or
            // a soft-delete inside its undo window (`pending_delete`). Pull runs right after push, so
            // a row edited during the push round-trip — deliberately preserved by markSynced's stale
            // guard — would otherwise be clobbered here and its edit silently lost. `conflict` rows are
            // intentionally *not* protected: server-wins resolution (req 2.6.2) expects pull to replace
            // them. Reading the id set inside this transaction keeps it consistent with the upsert.
            val protectedIds = transactionDao.locallyDirtyIds().toHashSet()
            transactionDao.upsert(
                data.transactions.filter { it.id !in protectedIds }.map { it.toEntity(now) }
            )
            transferDao.upsert(data.transfers.map { it.toEntity(now) })
            templateDao.upsert(data.templates.map { it.toEntity(now) })
            recurringDao.upsert(data.recurring_transactions.map { it.toEntity(now) })
            budgetDao.upsert(data.budgets.map { it.toEntity(now) })
            notificationSettingDao.upsert(data.notification_settings.map { it.toEntity(now) })
            inputPatternDao.upsert(data.input_patterns.map { it.toEntity(now) })
        }
    }

    private fun SyncData.total(): Int =
        accounts.size + categories.size + tags.size + transactions.size + transfers.size +
            templates.size + recurring_transactions.size + budgets.size +
            notification_settings.size + input_patterns.size

    /**
     * Cursor for the next page: the oldest among each non-empty entity type's newest row
     * (per-type max `updated_at`/`last_used_at`, then the minimum across types). Returns null
     * when the page held no rows. Taking the minimum rather than the global maximum is what
     * keeps a type that hit the server page cap from being skipped while another type carried a
     * newer row (see the call site).
     */
    private fun SyncData.nextCursor(): String? = listOfNotNull(
        accounts.maxStamp { it.updated_at },
        categories.maxStamp { it.updated_at },
        tags.maxStamp { it.updated_at },
        transactions.maxStamp { it.updated_at },
        transfers.maxStamp { it.updated_at },
        templates.maxStamp { it.updated_at },
        recurring_transactions.maxStamp { it.updated_at },
        budgets.maxStamp { it.updated_at },
        notification_settings.maxStamp { it.updated_at },
        input_patterns.maxStamp { it.last_used_at },
    ).minByOrNull { parseEpochMillis(it, Long.MAX_VALUE) }

    /** This type's newest timestamp (as the original string), or null when the list is empty. */
    private inline fun <T> List<T>.maxStamp(stamp: (T) -> String): String? =
        maxByOrNull { parseEpochMillis(stamp(it), Long.MIN_VALUE) }?.let(stamp)

    companion object {
        /** Beginning of time for a first full sync. Valid ISO-8601 OffsetDateTime. */
        const val EPOCH = "1970-01-01T00:00:00Z"

        /** Safety cap on pagination iterations to avoid an unbounded loop. */
        private const val MAX_PAGES = 200

        /** Server-side per-request change limit (SyncService.MAX_BATCH_SIZE). */
        private const val MAX_BATCH = 100

        /**
         * Grace period after a soft-delete before a background sync force-commits it. Must comfortably
         * exceed the undo snackbar duration so a live undo is never committed out from under the user.
         */
        private const val DELETE_COMMIT_GRACE_MS = 30_000L
    }
}
