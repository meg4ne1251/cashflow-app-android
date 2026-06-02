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
            while (true) {
                val response = api.pull(cursor)
                val data = response.data
                persist(data)
                pulled += data.total()

                if (!response.has_more) {
                    watermark.set(response.sync_timestamp)
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
                    Timber.w("Sync pagination stalled (page=%d, cursor=%s); stopping", page, cursor)
                    break
                }
                cursor = next
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
            transactionDao.upsert(data.transactions.map { it.toEntity(now) })
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
    }
}
