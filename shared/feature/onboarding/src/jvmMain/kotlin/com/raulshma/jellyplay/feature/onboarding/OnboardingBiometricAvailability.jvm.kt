package com.raulshma.jellyplay.feature.onboarding

import androidx.compose.runtime.Composable

// Desktop has no biometric prompt (same degradation the settings module's
// BiometricGate accepts): the SecurityStep toggle takes its existing
// unavailable path — enabled = false keeps the row inert, the subtitle
// switches to the setup hint.
@Composable
internal actual fun rememberBiometricAvailable(): Boolean = false
