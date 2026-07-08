package com.kakeibo.android.core.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
 * Application-scoped coordinator that triggers [SyncEngine] and exposes [state]. Runs on its own
 * supervised scope so an in-flight sync survives ViewModel recreation. Concurrent requests — the
 * in-app auth trigger and the background [SyncWorker] alike — collapse into a single run: a caller
 * that arrives while a sync is active joins the in-flight run instead of queueing a second one.
 */
@Singleton
class SyncManager @Inject constructor(
    private val syncEngine: SyncEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** The run currently in flight, shared by every concurrent caller so they coalesce into one. */
    private var inFlight: Deferred<SyncOutcome>? = null

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Fire-and-forget sync; safe to call on every authentication. */
    fun syncNow() {
        scope.launch { sync() }
    }

    /**
     * Runs a full sync and returns its outcome, coalescing concurrent callers: a sync already in
     * progress (whether started by [syncNow] or the [SyncWorker]) is joined rather than run a second
     * time back-to-back. The work runs on the manager's own scope so it survives the caller being
     * cancelled (e.g. WorkManager stopping the worker).
     */
    suspend fun sync(): SyncOutcome {
        val run = mutex.withLock {
            inFlight ?: scope.async {
                try {
                    runSync()
                } finally {
                    mutex.withLock { inFlight = null }
                }
            }.also { inFlight = it }
        }
        return run.await()
    }

    private suspend fun runSync(): SyncOutcome {
        _state.value = SyncState.Syncing
        val outcome = syncEngine.sync()
        _state.value = when (outcome) {
            is SyncOutcome.Success -> SyncState.Success(outcome.pulled)
            is SyncOutcome.Error -> SyncState.Error(outcome.message)
        }
        return outcome
    }
}
