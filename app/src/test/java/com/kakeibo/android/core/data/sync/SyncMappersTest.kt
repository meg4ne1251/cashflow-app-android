package com.kakeibo.android.core.data.sync

import com.kakeibo.android.core.database.entity.SyncStatus
import com.kakeibo.android.core.network.dto.AccountDto
import com.kakeibo.android.core.network.dto.InputPatternDto
import com.kakeibo.android.core.network.dto.TransactionDto
import org.junit.Assert.assertEquals
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
}
