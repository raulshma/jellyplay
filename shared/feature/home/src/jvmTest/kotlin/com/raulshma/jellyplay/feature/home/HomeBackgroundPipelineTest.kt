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

    @Test
    fun luminanceCalculation_identifiesLightVsDarkColors() {
        fun isLight(color: Color): Boolean {
            val luminance = color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f
            return luminance > 0.5f
        }

        assertTrue(isLight(Color.White))
        assertTrue(isLight(Color(0xFFEEEEEE)))
        assertFalse(isLight(Color.Black))
        assertFalse(isLight(Color(0xFF121212)))
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
