package com.kakeibo.android.core.data.sync

import com.kakeibo.android.core.database.entity.SyncMeta
import com.kakeibo.android.core.database.entity.SyncStatus
import com.kakeibo.android.core.database.entity.TransactionEntity
import com.kakeibo.android.core.network.dto.AccountDto
import com.kakeibo.android.core.network.dto.InputPatternDto
import com.kakeibo.android.core.network.dto.TransactionDto
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SyncMappersTest {

    @Test
    fun `account dto maps every field and marks row clean`() {
        val dto = AccountDto(
            id = "a1", name = "現金", type = "cash", initial_balance = 1000, currency = "JPY",
            sort_order = 2, payment_day = null, balance = 1500, version = 3,
            created_at = "2026-05-01T00:00:00Z", updated_at = "2026-05-02T00:00:00Z",
            deleted_at = null,
        )

        val entity = dto.toEntity(now = 999L)

        assertEquals("a1", entity.id)
        assertEquals("現金", entity.name)
        assertEquals(1000L, entity.initialBalance)
        assertEquals(1500L, entity.balance)
        assertEquals(3, entity.version)
        assertEquals("2026-05-02T00:00:00Z", entity.updatedAt)
        assertTrue(entity.sync.isSynced)
        assertEquals(SyncStatus.CLEAN, entity.sync.syncStatus)
        assertEquals(
            Instant.parse("2026-05-02T00:00:00Z").toEpochMilli(),
            entity.sync.localUpdatedAt,
        )
    }

    @Test
    fun `soft-deleted transaction keeps deleted_at`() {
        val dto = TransactionDto(
            id = "t1", type = "expense", amount = 500, currency = "JPY", date = "2026-05-01",
            version = 1, created_at = "2026-05-01T00:00:00Z", updated_at = "2026-05-01T00:00:00Z",
            deleted_at = "2026-05-03T00:00:00Z",
        )

        val entity = dto.toEntity(now = 0L)

        assertEquals("2026-05-03T00:00:00Z", entity.deletedAt)
    }

    @Test
    fun `input pattern without timestamp metadata seeds localUpdatedAt from last_used_at`() {
        val dto = InputPatternDto(
            id = "p1", keyword = "ローソン", hit_count = 5, last_used_at = "2026-05-04T10:00:00Z",
        )

        val entity = dto.toEntity(now = 1L)

        assertEquals(5, entity.hitCount)
        assertEquals(
            Instant.parse("2026-05-04T10:00:00Z").toEpochMilli(),
            entity.sync.localUpdatedAt,
        )
    }

    @Test
    fun `invalid timestamp falls back to now`() {
        assertEquals(42L, parseEpochMillis("not-a-date", fallback = 42L))
        assertEquals(42L, parseEpochMillis(null, fallback = 42L))
    }

    @Test
    fun `transaction date is normalized to a fixed second-precision form`() {
        // The backend drops :00 seconds (LocalDateTime.toString); the form always writes seconds.
        // Normalizing on pull keeps the two representations comparable for dashboard date ranges.
        fun dateOf(raw: String) = TransactionDto(
            id = "t1", type = "expense", amount = 1, currency = "JPY", date = raw, version = 1,
            created_at = "2026-05-01T00:00:00Z", updated_at = "2026-05-01T00:00:00Z", deleted_at = null,
        ).toEntity(now = 0L).date

        assertEquals("2026-06-03T14:30:00", dateOf("2026-06-03T14:30"))      // seconds re-added
        assertEquals("2026-06-03T14:30:47", dateOf("2026-06-03T14:30:47.123")) // fraction dropped
        assertEquals("2026-05-01T00:00:00", dateOf("2026-05-01"))            // date-only → midnight
    }

    // ----- toSyncChange (Phase 3 push) -----

    @Test
    fun `never-synced transaction becomes a create with client_version 0`() {
        val change = txEntity(version = 0).toSyncChange()

        assertEquals("transaction", change.entity_type)
        assertEquals("create", change.operation)
        assertEquals(0, change.client_version)
        assertEquals("t1", change.data["id"]?.jsonPrimitive?.content)
        assertEquals("c1", change.data["category_id"]?.jsonPrimitive?.content)
        assertEquals(500L, change.data["amount"]?.jsonPrimitive?.longOrNull)
        assertEquals("2026-06-02T14:30:00", change.data["date"]?.jsonPrimitive?.content)
        assertEquals(false, change.data["is_balance_adjustment"]?.jsonPrimitive?.booleanOrNull)
    }

    @Test
    fun `synced transaction becomes an update carrying its base version`() {
        val change = txEntity(version = 3).toSyncChange()

        assertEquals("update", change.operation)
        assertEquals(3, change.client_version)
    }

    @Test
    fun `soft-deleted transaction becomes a delete with id only`() {
        val change = txEntity(version = 2, deletedAt = "2026-06-03T00:00:00").toSyncChange()

        assertEquals("delete", change.operation)
        assertEquals(2, change.client_version)
        assertEquals("t1", change.data["id"]?.jsonPrimitive?.content)
        assertNull(change.data["amount"])
    }

    private fun txEntity(version: Int, deletedAt: String? = null) = TransactionEntity(
        id = "t1", name = null, type = "expense", amount = 500, currency = "JPY",
        date = "2026-06-02T14:30:00", memo = null, categoryId = "c1", accountId = null,
        isAutoGenerated = false, isBalanceAdjustment = false, recurringTransactionId = null,
        version = version, createdAt = "2026-06-02T14:30:00", updatedAt = "2026-06-02T14:30:00",
        deletedAt = deletedAt, sync = SyncMeta(),
    )
}
