package com.kakeibo.android.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

enum class BiometricAvailability {
    Available,
    NotEnrolled,
    NoHardware,
    Unavailable,
}

sealed interface BiometricResult {
    data object Success : BiometricResult
    data object UserCanceled : BiometricResult
    data class Error(val code: Int, val message: String) : BiometricResult
}

object BiometricAuthenticator {

    fun availability(context: Context): BiometricAvailability {
        val manager = BiometricManager.from(context)
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return when (manager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NotEnrolled
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.NoHardware
            else -> BiometricAvailability.Unavailable
        }
    }

    suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String? = null,
        cancelLabel: String,
    ): BiometricResult = suspendCancellableCoroutine { cont ->
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (cont.isActive) cont.resume(BiometricResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val outcome = when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> BiometricResult.UserCanceled
                    else -> BiometricResult.Error(errorCode, errString.toString())
                }
                if (cont.isActive) cont.resume(outcome)
            }

            override fun onAuthenticationFailed() {
                // Soft fail (wrong fingerprint). Do not resume; let the user retry.
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { subtitle?.let(::setSubtitle) }
            .setNegativeButtonText(cancelLabel)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info)
        cont.invokeOnCancellation { prompt.cancelAuthentication() }
    }
}
