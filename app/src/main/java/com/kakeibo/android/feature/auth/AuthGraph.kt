package com.kakeibo.android.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Lightweight in-memory router for the auth flow. We could use NavController here, but
 * the flow only has two screens and the top-level router already swaps the whole subtree
 * based on SessionState, so a simple state machine is enough.
 */
sealed interface AuthRoute {
    data object Login : AuthRoute
    data object Setup : AuthRoute
}

@Composable
fun AuthGraph(initialRoute: AuthRoute) {
    var route by remember { mutableStateOf(initialRoute) }
    when (route) {
        AuthRoute.Login -> LoginScreen(onNavigateToSetup = { route = AuthRoute.Setup })
        AuthRoute.Setup -> {
            // Only intercept back when Setup was pushed on top of Login. If Setup is the
            // initial route (backend has no users), let the system back press fall through.
            if (initialRoute == AuthRoute.Login) {
                BackHandler { route = AuthRoute.Login }
            }
            SetupScreen()
        }
    }
}
