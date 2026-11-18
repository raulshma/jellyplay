package com.raulshma.jellyplay.core.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Pins the desktop actuals of the PlatformCapabilities seam — the two device
 * facts behind the rows the settings and onboarding surfaces gate on:
 *
 *  - [supportsDynamicColor] is false: the JVM target has no Material You
 *    wallpaper source, so the dynamic-theming rows take their hidden paths
 *    (settings AppearanceScreen + onboarding AppearanceStep) and brand
 *    schemes apply instead.
 *  - [biometricPromptAvailable] is false: desktop has no biometric backend,
 *    so the settings gate nulls (row hidden) and the wizard's SecurityStep
 *    toggle takes its unavailable path. Pinned through the named desktop
 *    actual val, without a composition (the DesktopBiometricGate trick).
 *
 * The Android actuals (the Build.VERSION gate; the BiometricManager
 * strong-authenticator check) are verified on the device suite, not here.
 */
class PlatformCapabilitiesJvmTest {

    @Test
    fun desktop_reports_no_dynamic_color_support() {
        assertFalse(supportsDynamicColor)
    }

    @Test
    fun desktop_reports_no_biometric_prompt() {
        assertFalse(desktopBiometricPromptAvailable)
    }
}
