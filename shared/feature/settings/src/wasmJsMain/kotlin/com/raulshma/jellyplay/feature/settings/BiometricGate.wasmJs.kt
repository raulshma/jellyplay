package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable

/**
 * Web seam truth for [rememberBiometricGate]: no biometric backend, ever —
 * the desktop actual's shape (null gate → the security screen's biometric row
 * takes its existing unavailable path and stays hidden).
 */
@Composable
internal actual fun rememberBiometricGate(): BiometricGate? = null
