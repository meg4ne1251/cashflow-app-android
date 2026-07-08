package com.kakeibo.android.core.data.sync

import androidx.room.Room
import com.kakeibo.android.core.database.CashflowDatabase
import com.kakeibo.android.core.database.entity.SyncMeta
import com.kakeibo.android.core.database.entity.SyncStatus
import com.kakeibo.android.core.database.entity.TransactionEntity
import com.kakeibo.android.core.network.api.SyncApiService
import com.kakeibo.android.core.network.dto.AccountDto
import com.kakeibo.android.core.network.dto.CategoryDto
import com.kakeibo.android.core.network.dto.SyncData
import com.kakeibo.android.core.network.dto.SyncPullResponse
import com.kakeibo.android.core.network.dto.SyncPushRequest
import com.kakeibo.android.core.network.dto.SyncPushResponse
import com.kakeibo.android.core.network.dto.TransactionDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncEngineTest {

    private lateinit var db: CashflowDatabase
    private lateinit var watermark: FakeWatermarkStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            CashflowDatabase::class.java,
        ).allowMainThreadQueries().build()
        watermark = FakeWatermarkStore()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun engine(api: SyncApiService) = SyncEngine(
        api = api,
        db = db,
        watermark = watermark,
        accountDao = db.accountDao(),
        categoryDao = db.categoryDao(),
        tagDao = db.tagDao(),
        transactionDao = db.transactionDao(),
        transferDao = db.transferDao(),
        templateDao = db.templateDao(),
        recurringDao = db.recurringTransactionDao(),
        budgetDao = db.budgetDao(),
        notificationSettingDao = db.notificationSettingDao(),
        inputPatternDao = db.inputPatternDao(),
    )

    @Test
    fun `first pull persists rows and stores watermark`() = runTest {
        val api = FakeSyncApi(
            ArrayDeque(
                listOf(
                    SyncPullResponse(
                        data = SyncData(
                            accounts = listOf(account("a1", "2026-05-02T00:00:00Z")),
                            categories = listOf(category("c1", "2026-05-02T00:00:00Z")),
                            transactions = listOf(transaction("t1", deleted = false)),
                        ),
                        sync_timestamp = "2026-05-10T00:00:00Z",
                        has_more = false,
                    ),
                ),
            ),
        )

        val outcome = engine(api).pull()

        assertEquals(SyncOutcome.Success(3), outcome)
        assertEquals(listOf(SyncEngine.EPOCH), api.requestedSince)
        assertEquals(1, db.accountDao().count())
        assertEquals(1, db.transactionDao().observeAll().first().size)
        assertEquals("2026-05-10T00:00:00Z", watermark.get())
    }

    @Test
    fun `soft-deleted rows are stored but hidden from observeAll`() = runTest {
        val api = FakeSyncApi(
            ArrayDeque(
                listOf(
                    SyncPullResponse(
                        data = SyncData(
                            transactions = listOf(
                                transaction("t1", deleted = false),
                                transaction("t2", deleted = true),
                            ),
                        ),
                        sync_timestamp = "2026-05-10T00:00:00Z",
                        has_more = false,
                    ),
                ),
            ),
        )

        engine(api).pull()

        assertEquals(2, db.transactionDao().count())
        assertEquals(1, db.transactionDao().observeAll().first().size)
    }

    @Test
    fun `pagination follows has_more and advances the cursor`() = runTest {
        val api = FakeSyncApi(
            ArrayDeque(
                listOf(
                    SyncPullResponse(
                        data = SyncData(accounts = listOf(account("a1", "2026-05-02T00:00:00Z"))),
                        sync_timestamp = "2026-05-10T00:00:00Z",
                        has_more = true,
                    ),
                    SyncPullResponse(
                        data = SyncData(accounts = listOf(account("a2", "2026-05-05T00:00:00Z"))),
                        sync_timestamp = "2026-05-11T00:00:00Z",
                        has_more = false,
                    ),
                ),
            ),
        )

        val outcome = engine(api).pull()

        assertEquals(SyncOutcome.Success(2), outcome)
        // Second page is fetched with the newest updated_at from the first page as the cursor.
        assertEquals(listOf(SyncEngine.EPOCH, "2026-05-02T00:00:00Z"), api.requestedSince)
        assertEquals(2, db.accountDao().count())
        assertEquals("2026-05-11T00:00:00Z", watermark.get())
    }

    @Test
    fun `cursor resumes from the oldest entity max so a truncated type is not skipped`() = runTest {
        val api = FakeSyncApi(
            ArrayDeque(
                listOf(
                    // Page 1: a heavy type (transactions) capped at an OLD timestamp, alongside a
                    // lightweight type (accounts) carrying a much NEWER row. has_more = true.
                    SyncPullResponse(
                        data = SyncData(
                            transactions = listOf(transaction("t1", "2026-05-01T00:00:00Z")),
                            accounts = listOf(account("a1", "2026-05-09T00:00:00Z")),
                        ),
                        sync_timestamp = "2026-05-20T00:00:00Z",
                        has_more = true,
                    ),
                    SyncPullResponse(
                        data = SyncData(),
                        sync_timestamp = "2026-05-20T00:00:00Z",
                        has_more = false,
                    ),
                ),
            ),
        )

        engine(api).pull()

        // Page 2 must resume from the transaction's older timestamp, not the account's newer
        // one — otherwise un-fetched transactions between the two would be silently dropped.
        assertEquals(listOf(SyncEngine.EPOCH, "2026-05-01T00:00:00Z"), api.requestedSince)
    }

    @Test
    fun `pagination that cannot advance reports an error and leaves the watermark untouched`() = runTest {
        // has_more stays true but every returned row sits exactly at the current cursor (EPOCH),
        // so nextCursor() == cursor and the loop cannot make progress. This must surface as an
        // error rather than a false Success that hides the incomplete pull.
        val api = FakeSyncApi(
            ArrayDeque(
                listOf(
                    SyncPullResponse(
                        data = SyncData(accounts = listOf(account("a1", SyncEngine.EPOCH))),
                        sync_timestamp = "2026-05-10T00:00:00Z",
                        has_more = true,
                    ),
                ),
            ),
        )

        val outcome = engine(api).pull()

        assertTrue(outcome is SyncOutcome.Error)
        assertEquals(1, db.accountDao().count()) // the page that was fetched is still persisted
        assertNull(watermark.get())
    }

    @Test
    fun `network failure surfaces an error and leaves the watermark untouched`() = runTest {
        val api = object : SyncApiService {
            override suspend fun pull(since: String): SyncPullResponse = throw IOException("offline")
            override suspend fun push(request: SyncPushRequest) = SyncPushResponse(emptyList())
        }

        val outcome = engine(api).pull()

        assertTrue(outcome is SyncOutcome.Error)
        assertEquals(0, db.accountDao().count())
        assertNull(watermark.get())
    }

    @Test
    fun `pull does not clobber a locally pending row with the server copy`() = runTest {
        // A local edit awaiting push (e.g. re-edited while an accepted push was in flight): the pull
        // that follows must not overwrite it with the server's older data or clear its pending flag,
        // otherwise the edit is silently lost and never re-pushed.
        db.transactionDao().upsert(localPending("t1", amount = 999, status = SyncStatus.PENDING))
        val api = FakeSyncApi(
            ArrayDeque(
                listOf(
                    SyncPullResponse(
                        data = SyncData(transactions = listOf(serverTx("t1", amount = 100))),
                        sync_timestamp = "2026-05-10T00:00:00Z",
                        has_more = false,
                    ),
                ),
            ),
        )

        engine(api).pull()

        val row = db.transactionDao().getById("t1")!!
        assertEquals(999L, row.amount) // local edit survived
        assertEquals(SyncStatus.PENDING, row.sync.syncStatus) // still dirty → re-pushed next run
    }

    @Test
    fun `pull does not resurrect a row soft-deleted inside the undo window`() = runTest {
        // A pending_delete row is hidden but not yet pushed. The concurrent pull must leave it deleted
        // so an undo still in progress isn't undone from under the user.
        db.transactionDao().upsert(
            localPending("t1", amount = 500, status = SyncStatus.PENDING_DELETE)
                .copy(deletedAt = "2026-05-04T00:00:00")
        )
        val api = FakeSyncApi(
            ArrayDeque(
                listOf(
                    SyncPullResponse(
                        data = SyncData(transactions = listOf(serverTx("t1", amount = 100))),
                        sync_timestamp = "2026-05-10T00:00:00Z",
                        has_more = false,
                    ),
                ),
            ),
        )

        engine(api).pull()

        val row = db.transactionDao().getById("t1")!!
        assertEquals(SyncStatus.PENDING_DELETE, row.sync.syncStatus)
        assertTrue(row.deletedAt != null) // stays hidden
    }

    @Test
    fun `pull overwrites a conflicted row so the server wins`() = runTest {
        // Unlike pending rows, a conflict is resolved server-wins: the pull is expected to replace it.
        db.transactionDao().upsert(localPending("t1", amount = 999, status = SyncStatus.CONFLICT))
        val api = FakeSyncApi(
            ArrayDeque(
                listOf(
                    SyncPullResponse(
                        data = SyncData(transactions = listOf(serverTx("t1", amount = 100))),
                        sync_timestamp = "2026-05-10T00:00:00Z",
                        has_more = false,
                    ),
                ),
            ),
        )

        engine(api).pull()

        val row = db.transactionDao().getById("t1")!!
        assertEquals(100L, row.amount) // server copy won
        assertEquals(SyncStatus.CLEAN, row.sync.syncStatus)
    }

    // ----- fakes & fixtures -----

    private class FakeWatermarkStore : SyncWatermarkStore {
        private var value: String? = null
        override suspend fun get(): String? = value
        override suspend fun set(value: String) { this.value = value }
        override suspend fun clear() { value = null }
    }

    private class FakeSyncApi(private val pages: ArrayDeque<SyncPullResponse>) : SyncApiService {
        val requestedSince = mutableListOf<String>()
        override suspend fun pull(since: String): SyncPullResponse {
            requestedSince += since
            return pages.removeFirst()
        }
        override suspend fun push(request: SyncPushRequest) = SyncPushResponse(emptyList())
    }

    private fun account(id: String, updatedAt: String) = AccountDto(
        id = id, name = id, type = "cash", initial_balance = 0, currency = "JPY", sort_order = 0,
        balance = 0, version = 1, created_at = updatedAt, updated_at = updatedAt,
    )

    private fun category(id: String, updatedAt: String) = CategoryDto(
        id = id, name = id, type = "expense", sort_order = 0, is_default = false, version = 1,
        created_at = updatedAt, updated_at = updatedAt,
    )

    private fun transaction(id: String, deleted: Boolean) = TransactionDto(
        id = id, type = "expense", amount = 100, currency = "JPY", date = "2026-05-01", version = 1,
        created_at = "2026-05-01T00:00:00Z", updated_at = "2026-05-01T00:00:00Z",
        deleted_at = if (deleted) "2026-05-02T00:00:00Z" else null,
    )

    private fun transaction(id: String, updatedAt: String) = TransactionDto(
        id = id, type = "expense", amount = 100, currency = "JPY", date = "2026-05-01", version = 1,
        created_at = updatedAt, updated_at = updatedAt,
    )

    /** A server-side transaction row (already synced), used to check pull overwrite behaviour. */
    private fun serverTx(id: String, amount: Long) = TransactionDto(
        id = id, type = "expense", amount = amount, currency = "JPY", date = "2026-05-01", version = 5,
        created_at = "2026-05-01T00:00:00Z", updated_at = "2026-05-09T00:00:00Z",
    )

    /** A local row holding an un-pushed change, inserted directly to model an offline edit/delete. */
    private fun localPending(id: String, amount: Long, status: String) = TransactionEntity(
        id = id, name = null, type = "expense", amount = amount, currency = "JPY",
        date = "2026-05-01T00:00:00", memo = null, categoryId = "c1", accountId = null,
        isAutoGenerated = false, isBalanceAdjustment = false, recurringTransactionId = null,
        version = 5, createdAt = "2026-05-01T00:00:00", updatedAt = "2026-05-01T00:00:00",
        deletedAt = null,
        sync = SyncMeta(isSynced = false, syncStatus = status, localUpdatedAt = 2L),
    )
}
