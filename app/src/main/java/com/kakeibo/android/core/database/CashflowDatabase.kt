package com.kakeibo.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
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

/**
 * Local replica of the backend Postgres schema (database_design.md §5). Holds the entities
 * delivered by the sync pull; the source of truth remains the server, this is the
 * offline-first cache the UI observes.
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        TransactionEntity::class,
        TransferEntity::class,
        TemplateEntity::class,
        RecurringTransactionEntity::class,
        BudgetEntity::class,
        NotificationSettingEntity::class,
        InputPatternEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class CashflowDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun transactionDao(): TransactionDao
    abstract fun transferDao(): TransferDao
    abstract fun templateDao(): TemplateDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun notificationSettingDao(): NotificationSettingDao
    abstract fun inputPatternDao(): InputPatternDao
}
