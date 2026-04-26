package com.kakeibo.android.feature.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kakeibo.android.core.data.auth.AuthRepository
import com.kakeibo.android.core.data.auth.AuthResult
import com.kakeibo.android.core.data.auth.SessionManager
import com.kakeibo.android.core.data.auth.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the app-wide session bootstrap:
 *  - On first launch, decides whether we land on Login, Setup, Locked, or main.
 *  - After biometric unlock, validates the session against /me (which transparently
 *    refreshes via the OkHttp Authenticator) and flips state accordingly.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = sessionManager.state

    init {
        bootstrap()
    }

    private fun bootstrap() {
        viewModelScope.launch {
            sessionManager.setLoading()
            if (sessionManager.hasRefreshToken()) {
                sessionManager.setLocked()
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
                    sessionManager.setLocked()
                }
                is AuthResult.NetworkError, is AuthResult.Unknown -> {
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
