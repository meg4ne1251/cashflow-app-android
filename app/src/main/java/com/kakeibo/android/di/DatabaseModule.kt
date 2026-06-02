package com.kakeibo.android.di

import android.content.Context
import androidx.room.Room
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CashflowDatabase =
        Room.databaseBuilder(
            context,
            CashflowDatabase::class.java,
            "cashflow.db"
        )
            // Pre-release: the local replica can always be rebuilt from the server, so drop
            // and recreate on any schema change rather than authoring migrations. Replace with
            // real migrations before shipping.
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideAccountDao(db: CashflowDatabase): AccountDao = db.accountDao()
    @Provides fun provideCategoryDao(db: CashflowDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideTagDao(db: CashflowDatabase): TagDao = db.tagDao()
    @Provides fun provideTransactionDao(db: CashflowDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideTransferDao(db: CashflowDatabase): TransferDao = db.transferDao()
    @Provides fun provideTemplateDao(db: CashflowDatabase): TemplateDao = db.templateDao()
    @Provides fun provideRecurringTransactionDao(db: CashflowDatabase): RecurringTransactionDao =
        db.recurringTransactionDao()
    @Provides fun provideBudgetDao(db: CashflowDatabase): BudgetDao = db.budgetDao()
    @Provides fun provideNotificationSettingDao(db: CashflowDatabase): NotificationSettingDao =
        db.notificationSettingDao()
    @Provides fun provideInputPatternDao(db: CashflowDatabase): InputPatternDao = db.inputPatternDao()
}
