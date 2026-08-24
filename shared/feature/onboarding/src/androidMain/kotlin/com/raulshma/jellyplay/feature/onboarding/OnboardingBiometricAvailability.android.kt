package com.raulshma.jellyplay.feature.onboarding

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.core.ui.components.BiometricAuthHelper
import com.raulshma.jellyplay.core.ui.components.rememberBiometricAvailability

/**
 * Legacy BiometricAuthHelper availability, collapsed to the Boolean the
 * pre-migration screen derived inline
 * (`rememberBiometricAvailability() == Availability.AVAILABLE`). Same
 * remember/recomposition behavior as the legacy helper itself.
 */
@Composable
internal actual fun rememberBiometricAvailable(): Boolean =
    rememberBiometricAvailability() == BiometricAuthHelper.Availability.AVAILABLE
