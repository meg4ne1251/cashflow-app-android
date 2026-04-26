package com.kakeibo.android.core.data.auth

/**
 * Top-level auth state used to drive root-level navigation.
 *
 * - Loading: launching the app, deciding whether the user has a live session.
 * - NeedsSetup: backend has no users yet — show the initial setup screen.
 * - LoggedOut: no usable refresh_token — show login.
 * - Locked: refresh_token exists but the user must pass biometric unlock first.
 * - Authenticated: session is live, show the main app shell.
 */
sealed interface SessionState {
    data object Loading : SessionState
    data object NeedsSetup : SessionState
    data object LoggedOut : SessionState
    data object Locked : SessionState
    data class Authenticated(val username: String) : SessionState
}
