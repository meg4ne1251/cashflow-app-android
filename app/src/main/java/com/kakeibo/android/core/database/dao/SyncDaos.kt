package com.kakeibo.android.core.database.dao

import androidx.paging.PagingSource
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

    /** Single-row upsert for local CRUD (create + edit) writes. */
    @Upsert suspend fun upsert(item: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE deleted_at IS NULL ORDER BY date DESC, created_at DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id") fun observeById(id: String): Flow<TransactionEntity?>

    /** Physically removes a row (used for an undo of a never-synced local create). */
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun hardDelete(id: String)

    /**
     * Rows ready to push (created, edited, or a *committed* soft-delete). A `pending_delete` row is
     * an undo-window soft-delete whose push is deferred until [markDeleteCommitted] or
     * [commitStaleDeletes] promotes it to `pending`, so it is intentionally excluded here.
     */
    @Query("SELECT * FROM transactions WHERE sync_status IN ('pending', 'conflict')")
    suspend fun dirty(): List<TransactionEntity>

    /**
     * Ids of rows carrying an un-pushed local change: an edit/create awaiting push (`pending`) or a
     * soft-delete still inside its undo window (`pending_delete`). The sync pull uses this to avoid
     * overwriting them with the server copy. `conflict` is deliberately excluded — those are resolved
     * server-wins by the very next pull.
     */
    @Query("SELECT id FROM transactions WHERE sync_status IN ('pending', 'pending_delete')")
    suspend fun locallyDirtyIds(): List<String>

    /**
     * Marks a just-pushed row clean and adopts the server [version] — but only if the row has not
     * been locally re-touched since the push snapshot ([expectedLocalUpdatedAt]). A concurrent edit
     * or a hard-delete during the network round-trip changes (or removes) the row, in which case this
     * is a no-op and the fresh local change survives to be pushed on the next run. Returns the number
     * of rows updated (0 when the row changed underneath the push).
     */
    @Query(
        "UPDATE transactions SET version = :version, is_synced = 1, sync_status = 'clean' " +
            "WHERE id = :id AND local_updated_at = :expectedLocalUpdatedAt"
    )
    suspend fun markSynced(id: String, version: Int, expectedLocalUpdatedAt: Long): Int

    /** Flags a pushed row as conflicted, under the same stale-snapshot guard as [markSynced]. */
    @Query(
        "UPDATE transactions SET sync_status = 'conflict' " +
            "WHERE id = :id AND local_updated_at = :expectedLocalUpdatedAt AND sync_status != 'clean'"
    )
    suspend fun markConflicted(id: String, expectedLocalUpdatedAt: Long): Int

    /** Promotes a single undo-window soft-delete to a pushable `pending`. Returns rows updated. */
    @Query("UPDATE transactions SET sync_status = 'pending' WHERE id = :id AND sync_status = 'pending_delete'")
    suspend fun markDeleteCommitted(id: String): Int

    /**
     * Crash-safety net: promotes any undo-window soft-delete older than [before] (`local_updated_at`)
     * to `pending`, so a process death during the undo window cannot strand a locally-hidden row that
     * never reaches the server. The grace window keeps a still-live undo from being committed early.
     */
    @Query(
        "UPDATE transactions SET sync_status = 'pending' " +
            "WHERE sync_status = 'pending_delete' AND local_updated_at < :before"
    )
    suspend fun commitStaleDeletes(before: Long): Int

    /**
     * Paged list for the UI, joined with category/account for display. Filters are skipped when
     * their bind value is null. [sortKey]: 0 = date desc (default), 1 = date asc, 2 = amount desc,
     * 3 = amount asc; `created_at` is the stable tiebreaker. `date` is an ISO-local string, so
     * lexicographic comparison is chronological.
     */
    @Query(
        """
        SELECT t.id AS id, t.name AS name, t.type AS type, t.amount AS amount, t.date AS date,
               t.memo AS memo, t.category_id AS categoryId, c.name AS categoryName,
               c.icon AS categoryIcon, c.color AS categoryColor,
               t.account_id AS accountId, a.name AS accountName, t.sync_status AS syncStatus
        FROM transactions t
        LEFT JOIN categories c ON t.category_id = c.id
        LEFT JOIN accounts a ON t.account_id = a.id
        WHERE t.deleted_at IS NULL
          AND (:keyword IS NULL
               OR t.name LIKE '%' || :keyword || '%' ESCAPE '\'
               OR t.memo LIKE '%' || :keyword || '%' ESCAPE '\')
          AND (:type IS NULL OR t.type = :type)
          AND (:categoryId IS NULL OR t.category_id = :categoryId)
          AND (:accountId IS NULL OR t.account_id = :accountId)
          AND (:dateFrom IS NULL OR t.date >= :dateFrom)
          AND (:dateTo IS NULL OR t.date <= :dateTo)
        ORDER BY
          CASE WHEN :sortKey = 1 THEN t.date END ASC,
          CASE WHEN :sortKey = 2 THEN t.amount END DESC,
          CASE WHEN :sortKey = 3 THEN t.amount END ASC,
          CASE WHEN :sortKey = 0 THEN t.date END DESC,
          t.created_at DESC
        """
    )
    fun pagingSource(
        keyword: String?,
        type: String?,
        categoryId: String?,
        accountId: String?,
        dateFrom: String?,
        dateTo: String?,
        sortKey: Int,
    ): PagingSource<Int, TransactionListItem>

    /** Most-recent transactions (joined) for the dashboard. */
    @Query(
        """
        SELECT t.id AS id, t.name AS name, t.type AS type, t.amount AS amount, t.date AS date,
               t.memo AS memo, t.category_id AS categoryId, c.name AS categoryName,
               c.icon AS categoryIcon, c.color AS categoryColor,
               t.account_id AS accountId, a.name AS accountName, t.sync_status AS syncStatus
        FROM transactions t
        LEFT JOIN categories c ON t.category_id = c.id
        LEFT JOIN accounts a ON t.account_id = a.id
        WHERE t.deleted_at IS NULL
        ORDER BY t.date DESC, t.created_at DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int): Flow<List<TransactionListItem>>

    /**
     * Sum of a type over [start, end) (ISO-local bounds), 0 when empty. Balance adjustments are
     * excluded to match the backend's analytics aggregations (TransactionRepository.getMonthlySummary).
     */
    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM transactions " +
            "WHERE deleted_at IS NULL AND is_balance_adjustment = 0 " +
            "AND type = :type AND date >= :start AND date < :end"
    )
    fun observeSumByType(type: String, start: String, end: String): Flow<Long>

    /** Expense spent per category over [start, end), for budget consumption (excludes adjustments). */
    @Query(
        "SELECT category_id AS categoryId, COALESCE(SUM(amount), 0) AS spent FROM transactions " +
            "WHERE deleted_at IS NULL AND is_balance_adjustment = 0 " +
            "AND type = 'expense' AND category_id IS NOT NULL " +
            "AND date >= :start AND date < :end GROUP BY category_id"
    )
    fun observeCategorySpending(start: String, end: String): Flow<List<CategorySpent>>

    /** Expense rows since [start] (ISO-local), for the 30-day sparkline (excludes adjustments). */
    @Query(
        "SELECT date AS date, amount AS amount FROM transactions " +
            "WHERE deleted_at IS NULL AND is_balance_adjustment = 0 " +
            "AND type = 'expense' AND date >= :start"
    )
    fun observeExpensesSince(start: String): Flow<List<DateAmount>>

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

    @Query("SELECT * FROM templates WHERE id = :id") suspend fun getById(id: String): TemplateEntity?

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
