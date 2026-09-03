package com.raulshma.jellyplay.feature.onboarding

import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Pins the desktop actual of the onboarding appearance seam: the JVM target
 * has no Material You wallpaper source, so [supportsDynamicColor] is false and
 * the AppearanceStep's dynamic-theming row takes its hidden path. The Android
 * actual (Build.VERSION gate) is verified on the device suite, not here.
 */
class OnboardingAppearancePlatformJvmTest {

    @Test
    fun desktop_reports_no_dynamic_color_support() {
        assertFalse(supportsDynamicColor)
    }
}
