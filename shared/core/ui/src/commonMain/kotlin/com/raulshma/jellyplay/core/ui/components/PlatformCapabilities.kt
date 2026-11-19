package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable

/**
 * Platform seam for the two device facts feature modules gate visibility on:
 * whether a strong biometric prompt can actually launch, and whether a
 * wallpaper-based (Material You) dynamic color scheme can be derived.
 * Formerly duplicated as module-internal expect/actual twins — the settings
 * module's BiometricGate availability half and the onboarding module's
 * OnboardingBiometricAvailability / OnboardingAppearancePlatform (all four
 * actuals byte-identical) — which consume this seam directly now, the
 * PlatformTime precedent.
 *
 * Both members are public: the settings and onboarding features consume them
 * across module bounds. The Android actuals carry the verified predicates
 * verbatim (the `Build.VERSION_CODES.S` gate; the BiometricAuthHelper
 * strong-authenticator check collapsed to a Boolean); desktop is flatly
 * false on both, pinned by PlatformCapabilitiesJvmTest.
 */

/**
 * Whether this platform can derive a wallpaper-based (Material You) color
 * scheme. Android 12+ says yes; desktop has no dynamic color, so the
 * dynamic-theming rows stay hidden there and brand schemes apply instead
 * (settings module precedent).
 */
expect val supportsDynamicColor: Boolean

/**
 * Whether a strong biometric authenticator is enrolled and promptable on
 * this device: the BiometricAuthHelper `canAuthenticate(BIOMETRIC_STRONG)`
 * check collapsed to the Boolean its consumers derived inline, with the
 * same remember/recomposition behavior. Desktop has no biometric prompt, so
 * the actual is false and consumers take their existing unavailable paths
 * (the settings gate nulls, the wizard's toggle renders disabled).
 */
@Composable
expect fun biometricPromptAvailable(): Boolean
