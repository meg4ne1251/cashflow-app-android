package com.kakeibo.android.feature.root

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kakeibo.android.R
import com.kakeibo.android.core.data.auth.SessionState
import com.kakeibo.android.core.security.BiometricAuthenticator
import com.kakeibo.android.core.security.BiometricAvailability
import com.kakeibo.android.core.security.BiometricResult
import com.kakeibo.android.feature.auth.AuthGraph
import com.kakeibo.android.feature.auth.AuthRoute
import com.kakeibo.android.feature.auth.LockedScreen
import com.kakeibo.android.ui.shell.AppShell
import kotlinx.coroutines.launch

@Composable
fun RootApp(viewModel: RootViewModel = hiltViewModel()) {
    val state by viewModel.sessionState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val coroutineScope = rememberCoroutineScope()

    val unlockTitle = stringResource(R.string.auth_biometric_title)
    val unlockSubtitle = stringResource(R.string.auth_biometric_subtitle)

    val triggerUnlock: () -> Unit = remember(activity) {
        {
            val act = activity
            if (act == null) {
                viewModel.onBiometricUnlocked()
            } else {
                coroutineScope.launch {
                    val result = BiometricAuthenticator.authenticate(
                        activity = act,
                        title = unlockTitle,
                        subtitle = unlockSubtitle,
                    )
                    when (result) {
                        BiometricResult.Success -> viewModel.onBiometricUnlocked()
                        BiometricResult.UserCanceled -> viewModel.onBiometricCancelled()
                        is BiometricResult.Error -> viewModel.onBiometricCancelled()
                    }
                }
            }
        }
    }

    // Auto-trigger only on the events emitted by the ViewModel (i.e. once per fresh
    // Locked transition). Recompositions of the Locked state itself do not re-prompt.
    LaunchedEffect(viewModel) {
        viewModel.unlockPromptEvents.collect {
            val canUseBiometric =
                BiometricAuthenticator.availability(context) == BiometricAvailability.Available
            if (!canUseBiometric) {
                // No biometric hardware/enrolment and no device credential → resume via /me.
                viewModel.onBiometricUnlocked()
            } else {
                triggerUnlock()
            }
        }
    }

    when (state) {
        SessionState.Loading -> SplashContent()
        SessionState.NeedsSetup -> AuthGraph(initialRoute = AuthRoute.Setup)
        SessionState.LoggedOut -> AuthGraph(initialRoute = AuthRoute.Login)
        SessionState.Locked -> LockedScreen(
            onUnlockClick = triggerUnlock,
            onSignOutClick = viewModel::signOut,
        )
        is SessionState.Authenticated -> AppShell()
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

@Composable
private fun SplashContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
