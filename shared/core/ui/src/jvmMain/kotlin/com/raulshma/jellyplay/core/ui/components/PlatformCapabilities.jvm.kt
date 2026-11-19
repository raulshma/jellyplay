package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable

// No wallpaper color source on desktop — brand schemes apply instead
// (settings module precedent). The dynamic-theming rows stay hidden.
actual val supportsDynamicColor: Boolean = false

// Desktop has no biometric prompt (same degradation the settings module's
// BiometricGate accepts): the settings gate nulls and the wizard's
// SecurityStep toggle takes its existing unavailable path. A named val
// (not an inline `false`) so the fact is pinnable from
// PlatformCapabilitiesJvmTest without a composition.
internal val desktopBiometricPromptAvailable: Boolean = false

@Composable
actual fun biometricPromptAvailable(): Boolean = desktopBiometricPromptAvailable
