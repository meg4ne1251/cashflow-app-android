package com.kakeibo.android.feature.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kakeibo.android.core.data.auth.AuthRepository
import com.kakeibo.android.core.data.auth.AuthResult
import com.kakeibo.android.core.data.auth.SessionManager
import com.kakeibo.android.core.data.auth.SessionState
import com.kakeibo.android.core.data.sync.SyncManager
import com.kakeibo.android.core.data.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the app-wide session bootstrap:
 *  - On first launch, decides whether we land on Login, Setup, Locked, or main.
 *  - After biometric unlock, validates the session against /me (which transparently
 *    refreshes via the OkHttp Authenticator) and flips state accordingly.
 *
 * Auto-prompts the biometric sheet exactly once per Locked transition via
 * [unlockPromptEvents]. Subsequent attempts (user cancel, network error) require an
 * explicit tap on the LockedScreen unlock button to avoid an infinite re-prompt loop.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    private val syncManager: SyncManager,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = sessionManager.state
    val syncState: StateFlow<SyncState> = syncManager.state

    private val _unlockPromptEvents = Channel<Unit>(capacity = Channel.CONFLATED)
    val unlockPromptEvents: Flow<Unit> = _unlockPromptEvents.receiveAsFlow()

    init {
        bootstrap()
        triggerSyncOnAuthentication()
    }

    /**
     * Kicks off an initial full sync whenever the session becomes authenticated (fresh login,
     * setup, or biometric resume). [SyncManager] coalesces concurrent runs, so re-emissions
     * are harmless.
     */
    private fun triggerSyncOnAuthentication() {
        viewModelScope.launch {
            sessionManager.state.collect { state ->
                if (state is SessionState.Authenticated) syncManager.syncNow()
            }
        }
    }

    private fun bootstrap() {
        viewModelScope.launch {
            sessionManager.setLoading()
            if (sessionManager.hasRefreshToken()) {
                sessionManager.setLocked()
                _unlockPromptEvents.trySend(Unit)
                return@launch
            }
            decideUnauthenticatedState()
        }
    }

    fun onBiometricUnlocked() {
        viewModelScope.launch {
            when (val result = authRepository.resumeSession()) {
                is AuthResult.Success -> Unit // SessionManager already flipped to Authenticated
                is AuthResult.HttpError -> if (result.code == 401) {
                    sessionManager.setLoggedOut()
                    decideUnauthenticatedState()
                } else {
                    // Server-side error after a successful biometric. Stay locked but
                    // do not auto-reprompt — user must tap unlock again.
                    sessionManager.setLocked()
                }
                is AuthResult.NetworkError, is AuthResult.Unknown -> {
                    // Offline / unknown failure. Same policy as 5xx.
                    sessionManager.setLocked()
                }
            }
        }
    }

    fun onBiometricCancelled() {
        // Stay on the locked screen; user can retry or sign out.
        sessionManager.setLocked()
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.logout()
            decideUnauthenticatedState()
        }
    }

    private suspend fun decideUnauthenticatedState() {
        when (val status = authRepository.fetchSetupStatus()) {
            is AuthResult.Success -> if (status.value) {
                sessionManager.setNeedsSetup()
            } else {
                sessionManager.setLoggedOut()
            }
            else -> sessionManager.setLoggedOut()
        }
    }
}
