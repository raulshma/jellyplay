package com.raulshma.jellyplay.feature.onboarding

import androidx.compose.runtime.Composable

/**
 * Platform seam for the SecurityStep's biometric-lock toggle: whether a
 * strong biometric authenticator is enrolled and promptable on this device.
 * The Android actual wraps the legacy :core:ui BiometricAuthHelper (the
 * settings module's BiometricGate seam derives its availability the same
 * way); desktop has no biometric prompt, so the actual is false and the
 * toggle renders its existing unavailable path (shown, disabled, setup-hint
 * subtitle — verified reachable and sane on the JVM render).
 */
@Composable
internal expect fun rememberBiometricAvailable(): Boolean
