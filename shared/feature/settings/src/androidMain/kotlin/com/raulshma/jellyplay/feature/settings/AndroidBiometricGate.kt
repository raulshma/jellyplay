package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.raulshma.jellyplay.core.ui.components.BiometricAuthHelper
import com.raulshma.jellyplay.core.ui.components.findFragmentActivity
import com.raulshma.jellyplay.core.ui.components.rememberBiometricAvailability

/**
 * Wraps the legacy :core:ui BiometricAuthHelper verbatim: availability comes
 * from rememberBiometricAvailability(), the FragmentActivity from the ambient
 * context exactly like the legacy screen did. Gate is non-null only when a
 * prompt could actually be launched (activity present AND biometric strong
 * available), so the screen can treat null as "unavailable".
 */
@Composable
internal actual fun rememberBiometricGate(): BiometricGate? {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() } ?: return null
    val availability = rememberBiometricAvailability()
    if (availability != BiometricAuthHelper.Availability.AVAILABLE) return null
    return remember(activity) {
        object : BiometricGate {
            override fun isAvailable(): Boolean = true

            override fun authenticate(
                title: String,
                subtitle: String,
                onSuccess: () -> Unit,
                onError: (String) -> Unit,
                onFailed: () -> Unit,
            ) {
                BiometricAuthHelper.authenticate(
                    activity = activity,
                    title = title,
                    subtitle = subtitle,
                    onSuccess = onSuccess,
                    onError = onError,
                    onFailed = onFailed,
                )
            }
        }
    }
}
