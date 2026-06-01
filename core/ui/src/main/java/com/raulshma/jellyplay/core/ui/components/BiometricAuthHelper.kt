package com.raulshma.jellyplay.core.ui.components

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import android.content.ContextWrapper

private const val TAG = "BiometricAuthHelper"

object BiometricAuthHelper {

    enum class Availability {
        AVAILABLE, NO_HARDWARE, NO_ENROLLED, UNSUPPORTED
    }

    fun checkAvailability(context: Context): Availability {
        val manager = BiometricManager.from(context)
        val result = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        Log.d(TAG, "canAuthenticate result: $result")
        return when (result) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Log.w(TAG, "No biometric hardware detected")
                Availability.NO_HARDWARE
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Log.w(TAG, "Biometric hardware exists but no biometrics enrolled")
                Availability.NO_ENROLLED
            }
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                Log.w(TAG, "Security update required")
                Availability.UNSUPPORTED
            }
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                Log.w(TAG, "Biometric not supported on this device")
                Availability.UNSUPPORTED
            }
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                Log.w(TAG, "Biometric status unknown")
                Availability.UNSUPPORTED
            }
            else -> {
                Log.w(TAG, "Unknown biometric status: $result")
                Availability.UNSUPPORTED
            }
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String = "Use PIN",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    onFailed()
                }
            },
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
                    or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }
}

@Composable
fun rememberBiometricAvailability(): BiometricAuthHelper.Availability {
    val context = LocalContext.current
    val availability = remember { mutableStateOf(BiometricAuthHelper.checkAvailability(context)) }
    LaunchedEffect(Unit) {
        availability.value = BiometricAuthHelper.checkAvailability(context)
    }
    return availability.value
}

tailrec fun Context.findFragmentActivity(): FragmentActivity? {
    if (this is FragmentActivity) return this
    if (this is ContextWrapper) return baseContext.findFragmentActivity()
    return null
}
