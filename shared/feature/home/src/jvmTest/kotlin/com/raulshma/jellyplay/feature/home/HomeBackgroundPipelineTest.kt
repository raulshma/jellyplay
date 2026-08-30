package com.raulshma.jellyplay.feature.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class HomeBackgroundPipelineTest {

    @Test
    fun homeBackgroundState_holdsColorAndThemeFlag() {
        val state = HomeBackgroundState(
            backgroundColor = Color.Black,
            isLightTheme = false,
        )
        assertEquals(Color.Black, state.backgroundColor)
        assertFalse(state.isLightTheme)
    }

    /**
     * Asserts the PRODUCTION [isLightBackdropTheme] — previously this test
     * declared its own local copy of the luminance formula and asserted
     * against that.
     */
    @Test
    fun luminanceCalculation_identifiesLightVsDarkColors() {
        assertTrue(isLightBackdropTheme(Color.White))
        assertTrue(isLightBackdropTheme(Color(0xFFEEEEEE)))
        assertFalse(isLightBackdropTheme(Color.Black))
        assertFalse(isLightBackdropTheme(Color(0xFF121212)))
    }

    /**
     * Asserts the PRODUCTION [resolveHomeTargetBackgroundColor] — previously
     * this test re-implemented the blend locally with `lerp` and asserted
     * lerp's own endpoints.
     */
    @Test
    fun targetBackgroundColor_lightTheme_usesThemeBackgroundUntinted() {
        val overlay = Color.Red
        val themeBackground = Color(0xFFFAFAFA)

        assertEquals(themeBackground, resolveHomeTargetBackgroundColor(overlay, isLightTheme = true, themeBackground = themeBackground))
    }

    @Test
    fun targetBackgroundColor_darkTheme_blendsOverlayTowardBlack() {
        val overlay = Color.Red
        val themeBackground = Color(0xFF121212)

        val target = resolveHomeTargetBackgroundColor(overlay, isLightTheme = false, themeBackground = themeBackground)

        // 65% of the way from the overlay to black: distinct from both ends,
        // and exactly the production blend (the sheet's tinted-scrim rule).
        assertEquals(lerp(overlay, Color.Black, 0.65f), target)
        assertNotEquals(overlay, target)
        assertNotEquals(Color.Black, target)
    }
}
