package com.kakeibo.android.di

import com.kakeibo.android.core.data.sync.DataStoreSyncWatermarkStore
import com.kakeibo.android.core.data.sync.SyncScheduler
import com.kakeibo.android.core.data.sync.SyncWatermarkStore
import com.kakeibo.android.core.data.sync.WorkManagerSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    @Singleton
    abstract fun bindSyncWatermarkStore(impl: DataStoreSyncWatermarkStore): SyncWatermarkStore

    @Binds
    @Singleton
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler
}
