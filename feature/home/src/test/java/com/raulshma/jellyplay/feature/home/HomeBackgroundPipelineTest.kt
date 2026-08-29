package com.raulshma.jellyplay.feature.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun targetBackgroundColor_darkTheme_blendsOverlayWithBlack() {
        val baseOverlayColor = Color.Red
        val darkTarget = lerp(baseOverlayColor, Color.Black, 0.65f)

        assertNotEquals(baseOverlayColor, darkTarget)
        assertNotEquals(Color.Black, darkTarget)

        val fullStart = lerp(baseOverlayColor, Color.Black, 0f)
        val fullEnd = lerp(baseOverlayColor, Color.Black, 1f)

        assertEquals(baseOverlayColor, fullStart)
        assertEquals(Color.Black, fullEnd)
    }
}
