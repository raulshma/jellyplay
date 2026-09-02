package com.raulshma.jellyplay.core.ui.animation

import kotlin.test.assertEquals
import kotlin.test.Test

class PressScaleValueTest {

    @Test
    fun notPressed_returnsOne() {
        // pressScaleValue(isPressed=false) == 1f regardless of default.
        assertEquals(1f, pressScaleValueForLogic(isPressed = false, reducedMotion = false), 0.001f)
    }

    @Test
    fun pressed_returnsCardScale() {
        assertEquals(
            AnimationTokens.CardPressScale,
            pressScaleValueForLogic(isPressed = true, reducedMotion = false),
            0.001f,
        )
    }

    @Test
    fun reducedMotion_alwaysOne() {
        assertEquals(
            1f,
            pressScaleValueForLogic(isPressed = true, reducedMotion = true),
            0.001f,
        )
    }
}
