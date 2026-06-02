package com.kakeibo.android.core.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Observable state of the background sync, surfaced to the UI. */
sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Success(val pulled: Int) : SyncState
    data class Error(val message: String) : SyncState
}

/**
 * Application-scoped coordinator that triggers [SyncEngine] and exposes [state]. Runs on its
 * own supervised scope so an in-flight sync survives ViewModel recreation. Concurrent
 * requests collapse into one run (a sync already in progress is a no-op).
 */
@Singleton
class SyncManager @Inject constructor(
    private val syncEngine: SyncEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Fire-and-forget sync; safe to call on every authentication. */
    fun syncNow() {
        scope.launch { runSync() }
    }

    private suspend fun runSync() {
        if (!mutex.tryLock()) return // a sync is already running; coalesce.
        try {
            _state.value = SyncState.Syncing
            _state.value = when (val outcome = syncEngine.pull()) {
                is SyncOutcome.Success -> SyncState.Success(outcome.pulled)
                is SyncOutcome.Error -> SyncState.Error(outcome.message)
            }
        } finally {
            mutex.unlock()
        }
    }
}
