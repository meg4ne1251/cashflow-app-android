package com.kakeibo.android.core.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the high-water mark (last successful pull's `sync_timestamp`) so subsequent syncs
 * are incremental. Behind an interface to keep [SyncEngine] testable without DataStore.
 */
interface SyncWatermarkStore {
    suspend fun get(): String?
    suspend fun set(value: String)
    suspend fun clear()
}

private val Context.syncDataStore by preferencesDataStore(name = "sync_state")

@Singleton
class DataStoreSyncWatermarkStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncWatermarkStore {

    private val key = stringPreferencesKey("last_sync_timestamp")

    override suspend fun get(): String? =
        context.syncDataStore.data.map { it[key] }.first()

    override suspend fun set(value: String) {
        context.syncDataStore.edit { it[key] = value }
    }

    override suspend fun clear() {
        context.syncDataStore.edit { it.remove(key) }
    }
}
