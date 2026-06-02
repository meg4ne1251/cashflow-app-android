package com.kakeibo.android.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kakeibo.android.core.database.entity.AccountEntity
import com.kakeibo.android.core.database.entity.BudgetEntity
import com.kakeibo.android.core.database.entity.CategoryEntity
import com.kakeibo.android.core.database.entity.InputPatternEntity
import com.kakeibo.android.core.database.entity.NotificationSettingEntity
import com.kakeibo.android.core.database.entity.RecurringTransactionEntity
import com.kakeibo.android.core.database.entity.TagEntity
import com.kakeibo.android.core.database.entity.TemplateEntity
import com.kakeibo.android.core.database.entity.TransactionEntity
import com.kakeibo.android.core.database.entity.TransferEntity
import kotlinx.coroutines.flow.Flow

/**
 * Phase 2 DAOs. Each exposes an idempotent [Upsert] used by the sync engine, a `deleted_at`
 * filtered [Flow] for the UI (added in later phases), plus `count`/`clear` for tests and
 * destructive resync. Soft-deleted rows are stored verbatim so conflict detection keeps
 * working; the observe queries hide them.
 */

@Dao
interface AccountDao {
    @Upsert suspend fun upsert(items: List<AccountEntity>)

    @Query("SELECT * FROM accounts WHERE deleted_at IS NULL ORDER BY sort_order ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT COUNT(*) FROM accounts") suspend fun count(): Int
    @Query("DELETE FROM accounts") suspend fun clear()
}

@Dao
interface CategoryDao {
    @Upsert suspend fun upsert(items: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE deleted_at IS NULL ORDER BY sort_order ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories") suspend fun count(): Int
    @Query("DELETE FROM categories") suspend fun clear()
}

@Dao
interface TagDao {
    @Upsert suspend fun upsert(items: List<TagEntity>)

    @Query("SELECT * FROM tags WHERE deleted_at IS NULL ORDER BY name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT COUNT(*) FROM tags") suspend fun count(): Int
    @Query("DELETE FROM tags") suspend fun clear()
}

@Dao
interface TransactionDao {
    @Upsert suspend fun upsert(items: List<TransactionEntity>)

    @Query("SELECT * FROM transactions WHERE deleted_at IS NULL ORDER BY date DESC, created_at DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions") suspend fun count(): Int
    @Query("DELETE FROM transactions") suspend fun clear()
}

@Dao
interface TransferDao {
    @Upsert suspend fun upsert(items: List<TransferEntity>)

    @Query("SELECT * FROM transfers WHERE deleted_at IS NULL ORDER BY date DESC")
    fun observeAll(): Flow<List<TransferEntity>>

    @Query("SELECT COUNT(*) FROM transfers") suspend fun count(): Int
    @Query("DELETE FROM transfers") suspend fun clear()
}

@Dao
interface TemplateDao {
    @Upsert suspend fun upsert(items: List<TemplateEntity>)

    @Query("SELECT * FROM templates WHERE deleted_at IS NULL ORDER BY use_count DESC, last_used_at DESC")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT COUNT(*) FROM templates") suspend fun count(): Int
    @Query("DELETE FROM templates") suspend fun clear()
}

@Dao
interface RecurringTransactionDao {
    @Upsert suspend fun upsert(items: List<RecurringTransactionEntity>)

    @Query("SELECT * FROM recurring_transactions WHERE deleted_at IS NULL ORDER BY next_execution_date ASC")
    fun observeAll(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT COUNT(*) FROM recurring_transactions") suspend fun count(): Int
    @Query("DELETE FROM recurring_transactions") suspend fun clear()
}

@Dao
interface BudgetDao {
    @Upsert suspend fun upsert(items: List<BudgetEntity>)

    @Query("SELECT * FROM budgets WHERE deleted_at IS NULL ORDER BY year_month DESC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT COUNT(*) FROM budgets") suspend fun count(): Int
    @Query("DELETE FROM budgets") suspend fun clear()
}

@Dao
interface NotificationSettingDao {
    @Upsert suspend fun upsert(items: List<NotificationSettingEntity>)

    @Query("SELECT * FROM notification_settings ORDER BY type ASC")
    fun observeAll(): Flow<List<NotificationSettingEntity>>

    @Query("SELECT COUNT(*) FROM notification_settings") suspend fun count(): Int
    @Query("DELETE FROM notification_settings") suspend fun clear()
}

@Dao
interface InputPatternDao {
    @Upsert suspend fun upsert(items: List<InputPatternEntity>)

    @Query("SELECT * FROM input_patterns ORDER BY hit_count DESC")
    fun observeAll(): Flow<List<InputPatternEntity>>

    @Query("SELECT COUNT(*) FROM input_patterns") suspend fun count(): Int
    @Query("DELETE FROM input_patterns") suspend fun clear()
}
