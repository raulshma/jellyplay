package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.HandMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the handedness state holder consumed by layout mirroring:
 * [HandModeState.isLeft] is true EXACTLY for [HandMode.LEFT] — every other
 * mode (including future additions) must keep default (right-handed) layout,
 * and the state is a value type so composition locals can diff it.
 */
class HandModeStateTest {

    @Test
    fun leftMode_isLeft() {
        val state = HandModeState(HandMode.LEFT)

        assertTrue(state.isLeft)
        assertEquals(HandMode.LEFT, state.mode)
    }

    @Test
    fun rightMode_isNotLeft() {
        val state = HandModeState(HandMode.RIGHT)

        assertFalse(state.isLeft)
        assertEquals(HandMode.RIGHT, state.mode)
    }

    @Test
    fun stateIsAValueType() {
        assertEquals(HandModeState(HandMode.LEFT), HandModeState(HandMode.LEFT))
    }
}
