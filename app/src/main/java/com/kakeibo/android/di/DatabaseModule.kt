package com.kakeibo.android.di

import android.content.Context
import androidx.room.Room
import com.kakeibo.android.core.database.CashflowDatabase
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
        ).build()
}
