package com.raulshma.jellyplay.feature.player.video.state

import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for [PlayerWheelPolicy] — the wheel-tier classification
 * (plain wheel = volume, Shift+wheel / horizontal-dominant = seek, mpv-style
 * direction) and the sub-notch accumulator that lets fractional trackpad
 * deltas add up to one seek step.
 */
class PlayerWheelPolicyTest {

    // ---- isSeekScroll ----

    @Test
    fun `shift held makes any scroll a seek`() {
        assertTrue(PlayerWheelPolicy.isSeekScroll(isShiftPressed = true, scrollX = 0f, scrollY = 1f))
        assertTrue(PlayerWheelPolicy.isSeekScroll(isShiftPressed = true, scrollX = 0f, scrollY = -1f))
        assertTrue(PlayerWheelPolicy.isSeekScroll(isShiftPressed = true, scrollX = 0f, scrollY = 0f))
    }

    @Test
    fun `horizontal dominant delta is a seek without shift`() {
        assertTrue(PlayerWheelPolicy.isSeekScroll(isShiftPressed = false, scrollX = 1f, scrollY = 0f))
        assertTrue(PlayerWheelPolicy.isSeekScroll(isShiftPressed = false, scrollX = 2f, scrollY = 1f))
        assertTrue(PlayerWheelPolicy.isSeekScroll(isShiftPressed = false, scrollX = -0.5f, scrollY = 0.4f))
    }

    @Test
    fun `vertical only delta without shift is volume`() {
        assertFalse(PlayerWheelPolicy.isSeekScroll(isShiftPressed = false, scrollX = 0f, scrollY = 1f))
        assertFalse(PlayerWheelPolicy.isSeekScroll(isShiftPressed = false, scrollX = 0f, scrollY = -1f))
        assertFalse(PlayerWheelPolicy.isSeekScroll(isShiftPressed = false, scrollX = 1f, scrollY = 3f))
    }

    // ---- volumeDirection ----

    @Test
    fun `scroll up is volume up`() {
        assertEquals(1, PlayerWheelPolicy.volumeDirection(scrollY = -1f))
        assertEquals(1, PlayerWheelPolicy.volumeDirection(scrollY = -0.2f))
    }

    @Test
    fun `scroll down is volume down`() {
        assertEquals(-1, PlayerWheelPolicy.volumeDirection(scrollY = 1f))
        assertEquals(-1, PlayerWheelPolicy.volumeDirection(scrollY = 0.2f))
    }

    // ---- seekDirection ----

    @Test
    fun `negative axis delta is seek forward`() {
        // Wheel up (y = -1) and tilt right (x = +1)... tilt right is POSITIVE x.
        // mpv: WHEEL_UP seeks forward → the negative vertical axis seeks forward;
        // the horizontal axis maps the same by sign: a negative delta = forward.
        assertEquals(1, PlayerWheelPolicy.seekDirection(seekAxisDelta = -1f))
    }

    @Test
    fun `positive axis delta is seek back`() {
        assertEquals(-1, PlayerWheelPolicy.seekDirection(seekAxisDelta = 1f))
    }

    // ---- seekAxisDelta ----

    @Test
    fun `dominant axis wins`() {
        assertEquals(3f, PlayerWheelPolicy.seekAxisDelta(scrollX = 1f, scrollY = 3f))
        assertEquals(-2f, PlayerWheelPolicy.seekAxisDelta(scrollX = -2f, scrollY = 1f))
    }

    // ---- accumulateSeekNotches ----

    @Test
    fun `whole notches pass through with zero remainder`() {
        val (notches, remainder) = PlayerWheelPolicy.accumulateSeekNotches(previousAccumulator = 0f, rawDelta = 2f)
        assertEquals(2, notches)
        assertEquals(0f, remainder, 0.0001f)
    }

    @Test
    fun `fractional deltas accumulate into one notch`() {
        // Trackpad pans: 0.3 + 0.4 → still nothing; + 0.3 → one notch, 0 remainder.
        val (notches1, rem1) = PlayerWheelPolicy.accumulateSeekNotches(0f, 0.3f)
        assertEquals(0, notches1)
        val (notches2, rem2) = PlayerWheelPolicy.accumulateSeekNotches(rem1, 0.4f)
        assertEquals(0, notches2)
        val (notches3, rem3) = PlayerWheelPolicy.accumulateSeekNotches(rem2, 0.3f)
        assertEquals(1, notches3)
        assertEquals(0f, rem3, 0.0001f)
    }

    @Test
    fun `negative accumulation yields negative notches`() {
        val (notches, remainder) = PlayerWheelPolicy.accumulateSeekNotches(previousAccumulator = 0f, rawDelta = -1.5f)
        assertEquals(-1, notches)
        assertEquals(-0.5f, remainder, 0.0001f)
    }

    @Test
    fun `zero delta is a no-op`() {
        val (notches, remainder) = PlayerWheelPolicy.accumulateSeekNotches(previousAccumulator = 0.5f, rawDelta = 0f)
        assertEquals(0, notches)
        assertEquals(0.5f, remainder, 0.0001f)
    }

    // ---- calibration constants ----

    @Test
    fun `volume notch delta matches the gesture a11y step`() {
        // One wheel notch ≈ one moderate swipe bucket (the calibration the
        // feature spec asks for): same magnitude as GestureOverlay's
        // A11Y_STEP_DELTA.
        assertEquals(0.1f, PlayerWheelPolicy.VOLUME_NOTCH_DELTA)
    }

    @Test
    fun `engine volume notch delta matches the engine seam default`() {
        assertEquals(0.05f, PlayerWheelPolicy.ENGINE_VOLUME_NOTCH_DELTA)
    }
}
