package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable

/**
 * Platform seam for the biometric unlock toggle on the security screen
 * (SettingsMessenger pattern). The common screen only needs two operations:
 * "can I show the biometric row" and "launch the crypto-bound system prompt
 * with these resolved strings and callbacks" — exactly the shape of the legacy
 * BiometricAuthHelper call sites. A null gate (desktop, or no FragmentActivity
 * host on Android) means the row takes its existing unavailable path and stays
 * hidden.
 */
internal interface BiometricGate {
    fun isAvailable(): Boolean

    fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit,
    )
}

@Composable
internal expect fun rememberBiometricGate(): BiometricGate?
