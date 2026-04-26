package com.kakeibo.android.core.network.auth

/**
 * Hook that the network layer calls when the refresh flow gives up. The session manager
 * implements this to flip the auth state back to "needs login" without depending on the
 * network module directly (avoids a Hilt cycle).
 */
interface SessionInvalidator {
    fun onSessionLost()
}
