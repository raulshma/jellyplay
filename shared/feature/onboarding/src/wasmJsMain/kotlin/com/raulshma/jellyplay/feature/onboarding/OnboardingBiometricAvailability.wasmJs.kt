package com.raulshma.jellyplay.feature.onboarding

import androidx.compose.runtime.Composable

// Web has no biometric prompt (same degradation desktop and the settings
// module's BiometricGate accept): the SecurityStep toggle takes its existing
// unavailable path — enabled = false keeps the row inert, the subtitle
// switches to the setup hint.
@Composable
internal actual fun rememberBiometricAvailable(): Boolean = false
