package com.kakeibo.android.core.data.auth

import com.kakeibo.android.core.network.auth.PersistentCookieJar
import com.kakeibo.android.core.network.auth.SessionInvalidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for [SessionState]. Lives at the application scope so the root
 * navigator and feature ViewModels observe the same flow.
 *
 * Implements [SessionInvalidator] so the OkHttp Authenticator can flip us back to
 * [SessionState.LoggedOut] when refresh fails.
 */
@Singleton
class SessionManager @Inject constructor(
    private val cookieJar: PersistentCookieJar,
) : SessionInvalidator {

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    fun setLoading() {
        _state.value = SessionState.Loading
    }

    fun setNeedsSetup() {
        _state.value = SessionState.NeedsSetup
    }

    fun setLoggedOut() {
        cookieJar.clear()
        _state.value = SessionState.LoggedOut
    }

    fun setLocked() {
        _state.value = SessionState.Locked
    }

    fun setAuthenticated(username: String) {
        _state.value = SessionState.Authenticated(username)
    }

    fun hasRefreshToken(): Boolean = cookieJar.hasRefreshToken()

    override fun onSessionLost() {
        cookieJar.clear()
        _state.value = SessionState.LoggedOut
    }
}
