package com.raulshma.jellyplay.core.ui.animation

import kotlin.test.assertEquals
import kotlin.test.Test

class PressScaleValueTest {

    @Test
    fun notPressed_returnsOne() {
        // pressScaleValueForLogic(isPressed=false) == 1f regardless of default.
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

    @Test
    fun pressed_customScale_isHonored() {
        // The list-row idiom passes 0.97f; Modifier.pressScale forwards its
        // `defaultScale` here verbatim.
        assertEquals(
            0.97f,
            pressScaleValueForLogic(isPressed = true, reducedMotion = false, defaultScale = 0.97f),
            0.001f,
        )
    }

    @Test
    fun notPressed_customScale_stillOne() {
        assertEquals(
            1f,
            pressScaleValueForLogic(isPressed = false, reducedMotion = false, defaultScale = 0.97f),
            0.001f,
        )
    }

    @Test
    fun reducedMotion_customScale_stillOne() {
        // The flatten posture wins over any custom pressed scale.
        assertEquals(
            1f,
            pressScaleValueForLogic(isPressed = true, reducedMotion = true, defaultScale = 0.97f),
            0.001f,
        )
    }
}
