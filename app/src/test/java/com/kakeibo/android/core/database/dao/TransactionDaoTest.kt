package com.kakeibo.android.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Room
import com.kakeibo.android.core.database.CashflowDatabase
import com.kakeibo.android.core.database.entity.AccountEntity
import com.kakeibo.android.core.database.entity.CategoryEntity
import com.kakeibo.android.core.database.entity.SyncMeta
import com.kakeibo.android.core.database.entity.SyncStatus
import com.kakeibo.android.core.database.entity.TransactionEntity
import com.kakeibo.android.core.data.transaction.TransactionSort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TransactionDaoTest {

    private lateinit var db: CashflowDatabase
    private lateinit var dao: TransactionDao

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            CashflowDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.transactionDao()

        db.categoryDao().upsert(
            listOf(
                category("c1", "食費", "expense"),
                category("c2", "給与", "income"),
            )
        )
        db.accountDao().upsert(listOf(account("a1", "現金")))
        dao.upsert(
            listOf(
                tx("t1", "expense", 500, "2026-06-02T10:00:00", "c1", "a1", SyncStatus.CLEAN),
                tx("t2", "income", 1000, "2026-06-03T10:00:00", "c2", null, SyncStatus.CLEAN),
                tx("t3", "expense", 300, "2026-05-15T10:00:00", "c1", null, SyncStatus.CLEAN),
                tx("t4", "expense", 200, "2026-06-01T10:00:00", "c1", null, SyncStatus.PENDING),
            )
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `default sort is date desc and joins category and account`() = runTest {
        val rows = load(filterSort(TransactionSort.DATE_DESC))

        assertEquals(listOf("t2", "t1", "t4", "t3"), rows.map { it.id })
        val t1 = rows.first { it.id == "t1" }
        assertEquals("食費", t1.categoryName)
        assertEquals("現金", t1.accountName)
    }

    @Test
    fun `type filter narrows to income`() = runTest {
        val rows = load(dao.pagingSource(null, "income", null, null, null, null, 0))
        assertEquals(listOf("t2"), rows.map { it.id })
    }

    @Test
    fun `amount desc sort orders by amount`() = runTest {
        val rows = load(filterSort(TransactionSort.AMOUNT_DESC))
        assertEquals(listOf("t2", "t1", "t3", "t4"), rows.map { it.id })
    }

    @Test
    fun `dirty returns only non-clean rows`() = runTest {
        assertEquals(listOf("t4"), dao.dirty().map { it.id })
    }

    @Test
    fun `monthly expense sum excludes other months and income`() = runTest {
        val june = dao.observeSumByType("expense", "2026-06-01T00:00:00", "2026-07-01T00:00:00").first()
        assertEquals(700L, june) // t1 (500) + t4 (200); t3 is May, t2 is income
    }

    @Test
    fun `dashboard aggregations exclude balance adjustments`() = runTest {
        // A balance-adjustment expense in June must not count toward the dashboard totals,
        // matching the backend analytics queries (isBalanceAdjustment eq false).
        dao.upsert(
            tx("adj", "expense", 9999, "2026-06-04T10:00:00", "c1", null, SyncStatus.CLEAN)
                .copy(isBalanceAdjustment = true)
        )

        val june = dao.observeSumByType("expense", "2026-06-01T00:00:00", "2026-07-01T00:00:00").first()
        assertEquals(700L, june) // unchanged: t1 (500) + t4 (200), adjustment excluded

        val spending = dao.observeCategorySpending("2026-06-01T00:00:00", "2026-07-01T00:00:00").first()
        assertEquals(700L, spending.first { it.categoryId == "c1" }.spent)

        val sparkRows = dao.observeExpensesSince("2026-06-01T00:00:00").first()
        assertTrue(sparkRows.none { it.amount == 9999L })
    }

    @Test
    fun `markSynced applies only when local_updated_at matches the snapshot`() = runTest {
        // t4 is the pending row from setup; its localUpdatedAt defaults to 0.
        assertEquals(0, dao.markSynced("t4", version = 9, expectedLocalUpdatedAt = 999L)) // stale → no-op
        assertEquals(SyncStatus.PENDING, dao.getById("t4")!!.sync.syncStatus)

        assertEquals(1, dao.markSynced("t4", version = 9, expectedLocalUpdatedAt = 0L)) // matches → applied
        val row = dao.getById("t4")!!
        assertEquals(9, row.version)
        assertEquals(SyncStatus.CLEAN, row.sync.syncStatus)
        assertTrue(row.sync.isSynced)
    }

    @Test
    fun `dirty excludes undo-window soft-deletes until committed`() = runTest {
        val t4 = dao.getById("t4")!!
        dao.upsert(
            t4.copy(
                deletedAt = "2026-06-05T00:00:00",
                sync = t4.sync.copy(syncStatus = SyncStatus.PENDING_DELETE),
            )
        )
        assertTrue(dao.dirty().none { it.id == "t4" }) // deferred: not yet pushable

        assertEquals(1, dao.markDeleteCommitted("t4"))
        assertEquals(listOf("t4"), dao.dirty().map { it.id }) // now pushable
    }

    @Test
    fun `commitStaleDeletes promotes only soft-deletes older than the cutoff`() = runTest {
        dao.upsert(
            tx("d-old", "expense", 1, "2026-06-01T00:00:00", "c1", null, SyncStatus.PENDING_DELETE)
                .copy(deletedAt = "2026-06-01T00:00:00", sync = SyncMeta(syncStatus = SyncStatus.PENDING_DELETE, localUpdatedAt = 100L))
        )
        dao.upsert(
            tx("d-new", "expense", 1, "2026-06-01T00:00:00", "c1", null, SyncStatus.PENDING_DELETE)
                .copy(deletedAt = "2026-06-01T00:00:00", sync = SyncMeta(syncStatus = SyncStatus.PENDING_DELETE, localUpdatedAt = 1000L))
        )

        assertEquals(1, dao.commitStaleDeletes(before = 500L))
        assertEquals(SyncStatus.PENDING, dao.getById("d-old")!!.sync.syncStatus)
        assertEquals(SyncStatus.PENDING_DELETE, dao.getById("d-new")!!.sync.syncStatus)
    }

    @Test
    fun `keyword like treats percent as a literal via escape`() = runTest {
        dao.upsert(
            listOf(
                tx("p1", "expense", 1, "2026-06-10T00:00:00", "c1", null, SyncStatus.CLEAN).copy(name = "50%OFF"),
                tx("p2", "expense", 1, "2026-06-10T00:00:00", "c1", null, SyncStatus.CLEAN).copy(name = "5000 yen"),
            )
        )
        // Raw "50%" would wildcard-match both; the escaped form matches only the literal "50%".
        val rows = load(dao.pagingSource("50\\%", null, null, null, null, null, 0))
        assertEquals(listOf("p1"), rows.map { it.id })
    }

    // ----- helpers -----

    private fun filterSort(sort: TransactionSort) =
        dao.pagingSource(null, null, null, null, null, null, sort.key)

    private suspend fun load(source: PagingSource<Int, TransactionListItem>): List<TransactionListItem> {
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 100, placeholdersEnabled = false)
        )
        return (result as PagingSource.LoadResult.Page).data
    }

    private fun category(id: String, name: String, type: String) = CategoryEntity(
        id = id, name = name, type = type, icon = null, color = null, sortOrder = 0,
        isDefault = false, version = 1, createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z", deletedAt = null, sync = SyncMeta(),
    )

    private fun account(id: String, name: String) = AccountEntity(
        id = id, name = name, type = "cash", initialBalance = 0, currency = "JPY", sortOrder = 0,
        paymentDay = null, balance = 0, version = 1, createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z", deletedAt = null, sync = SyncMeta(),
    )

    private fun tx(
        id: String,
        type: String,
        amount: Long,
        date: String,
        categoryId: String,
        accountId: String?,
        status: String,
    ) = TransactionEntity(
        id = id, name = null, type = type, amount = amount, currency = "JPY", date = date,
        memo = null, categoryId = categoryId, accountId = accountId, isAutoGenerated = false,
        isBalanceAdjustment = false, recurringTransactionId = null, version = 1,
        createdAt = date + "Z", updatedAt = date + "Z", deletedAt = null,
        sync = SyncMeta(syncStatus = status),
    )
}
