package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable

/**
 * The desktop seam truth [rememberBiometricGate] exposes: no biometric
 * backend, ever. A named val (not an inline `null`) so the
 * flag ⟺ seam-null-ness equality is pinnable from
 * `DesktopPlatformActualsTest` without a composition.
 */
internal val desktopBiometricGate: BiometricGate? = null

@Composable
internal actual fun rememberBiometricGate(): BiometricGate? = desktopBiometricGate
