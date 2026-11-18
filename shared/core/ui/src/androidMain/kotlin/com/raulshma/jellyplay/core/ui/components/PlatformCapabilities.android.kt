package com.raulshma.jellyplay.core.ui.components

import android.os.Build
import androidx.compose.runtime.Composable

/** The Build.VERSION_CODES.S gate the settings/onboarding twins shipped. */
actual val supportsDynamicColor: Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The legacy BiometricAuthHelper availability, collapsed to the Boolean the
 * pre-migration screens derived inline
 * (`rememberBiometricAvailability() == Availability.AVAILABLE`). Same
 * remember/recomposition behavior as the legacy helper itself.
 */
@Composable
actual fun biometricPromptAvailable(): Boolean =
    rememberBiometricAvailability() == BiometricAuthHelper.Availability.AVAILABLE
