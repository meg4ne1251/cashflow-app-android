package com.kakeibo.android.core.data.sync

import androidx.room.Room
import com.kakeibo.android.core.database.CashflowDatabase
import com.kakeibo.android.core.database.entity.SyncMeta
import com.kakeibo.android.core.database.entity.SyncStatus
import com.kakeibo.android.core.database.entity.TransactionEntity
import com.kakeibo.android.core.network.api.SyncApiService
import com.kakeibo.android.core.network.dto.SyncData
import com.kakeibo.android.core.network.dto.SyncPullResponse
import com.kakeibo.android.core.network.dto.SyncPushRequest
import com.kakeibo.android.core.network.dto.SyncPushResponse
import com.kakeibo.android.core.network.dto.SyncResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncEnginePushTest {

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
    fun tearDown() = db.close()

    @Test
    fun `accepted create adopts server version and clears the pending flag`() = runTest {
        db.transactionDao().upsert(pending(id = "t1", version = 0))
        val api = FakePushApi(push = { SyncPushResponse(listOf(accepted("t1", serverVersion = 1))) })

        val outcome = engine(api).push()

        assertEquals(SyncOutcome.Success(1), outcome)
        val row = db.transactionDao().getById("t1")!!
        assertEquals(1, row.version)
        assertEquals(SyncStatus.CLEAN, row.sync.syncStatus)
        assertTrue(row.sync.isSynced)
        // create is sent with client_version 0 so the server's optimistic-lock numbering lines up.
        assertEquals(0, api.requests.single().changes.single().client_version)
        assertEquals("create", api.requests.single().changes.single().operation)
    }

    @Test
    fun `conflict flags the row so the following pull can win`() = runTest {
        db.transactionDao().upsert(pending(id = "t1", version = 3))
        val api = FakePushApi(push = { SyncPushResponse(listOf(conflict("t1"))) })

        engine(api).push()

        val row = db.transactionDao().getById("t1")!!
        assertEquals(SyncStatus.CONFLICT, row.sync.syncStatus)
        assertEquals("update", api.requests.single().changes.single().operation)
    }

    @Test
    fun `soft-deleted pending row pushes a delete and stays deleted when accepted`() = runTest {
        db.transactionDao().upsert(pending(id = "t1", version = 2, deletedAt = "2026-06-03T00:00:00"))
        val api = FakePushApi(push = { SyncPushResponse(listOf(accepted("t1", serverVersion = 3))) })

        engine(api).push()

        assertEquals("delete", api.requests.single().changes.single().operation)
        val row = db.transactionDao().getById("t1")!!
        assertNotNull(row.deletedAt)
        assertEquals(3, row.version)
        assertEquals(SyncStatus.CLEAN, row.sync.syncStatus)
    }

    @Test
    fun `sync pushes then pulls`() = runTest {
        db.transactionDao().upsert(pending(id = "t1", version = 0))
        val api = FakePushApi(
            push = { SyncPushResponse(listOf(accepted("t1", serverVersion = 1))) },
            pull = {
                SyncPullResponse(
                    data = SyncData(),
                    sync_timestamp = "2026-06-10T00:00:00Z",
                    has_more = false,
                )
            },
        )

        val outcome = engine(api).sync()

        assertEquals(SyncOutcome.Success(0), outcome) // pull persisted 0 rows
        assertEquals(1, api.requests.size) // push happened
        assertEquals(1, api.pulls) // pull happened
        assertEquals("2026-06-10T00:00:00Z", watermark.get())
    }

    @Test
    fun `nothing dirty is a no-op success`() = runTest {
        val api = FakePushApi(push = { error("push should not be called") })

        assertEquals(SyncOutcome.Success(0), engine(api).push())
        assertTrue(api.requests.isEmpty())
    }

    // ----- fakes & fixtures -----

    private fun engine(api: SyncApiService) = SyncEngine(
        api = api, db = db, watermark = watermark,
        accountDao = db.accountDao(), categoryDao = db.categoryDao(), tagDao = db.tagDao(),
        transactionDao = db.transactionDao(), transferDao = db.transferDao(),
        templateDao = db.templateDao(), recurringDao = db.recurringTransactionDao(),
        budgetDao = db.budgetDao(), notificationSettingDao = db.notificationSettingDao(),
        inputPatternDao = db.inputPatternDao(),
    )

    private class FakeWatermarkStore : SyncWatermarkStore {
        private var value: String? = null
        override suspend fun get() = value
        override suspend fun set(value: String) { this.value = value }
        override suspend fun clear() { value = null }
    }

    private class FakePushApi(
        val push: (SyncPushRequest) -> SyncPushResponse,
        val pull: () -> SyncPullResponse = {
            SyncPullResponse(SyncData(), "2026-06-10T00:00:00Z", has_more = false)
        },
    ) : SyncApiService {
        val requests = mutableListOf<SyncPushRequest>()
        var pulls = 0
        override suspend fun pull(since: String): SyncPullResponse {
            pulls++
            return pull.invoke()
        }
        override suspend fun push(request: SyncPushRequest): SyncPushResponse {
            requests += request
            return push.invoke(request)
        }
    }

    private fun accepted(id: String, serverVersion: Int) =
        SyncResult(entity_type = "transaction", id = id, status = "accepted", server_version = serverVersion)

    private fun conflict(id: String) =
        SyncResult(entity_type = "transaction", id = id, status = "conflict")

    private fun pending(id: String, version: Int, deletedAt: String? = null) = TransactionEntity(
        id = id, name = null, type = "expense", amount = 500, currency = "JPY",
        date = "2026-06-02T14:30:00", memo = null, categoryId = "c1", accountId = null,
        isAutoGenerated = false, isBalanceAdjustment = false, recurringTransactionId = null,
        version = version, createdAt = "2026-06-02T14:30:00", updatedAt = "2026-06-02T14:30:00",
        deletedAt = deletedAt,
        sync = SyncMeta(isSynced = false, syncStatus = SyncStatus.PENDING, localUpdatedAt = 1L),
    )
}
