package com.raulshma.jellyplay.feature.player.video.state

import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for [DoubleTapHoldSeekPolicy] — the zone split
 * (the same 35%/65% thresholds the inline double-tap handler used) and the
 * accelerating repeat cadence for the double-tap-and-hold continuous seek.
 */
class DoubleTapHoldSeekPolicyTest {

    // ---- seekZone ----

    @Test
    fun `left zone below 35 percent is back`() {
        assertEquals(-1, DoubleTapHoldSeekPolicy.seekZone(x = 0f, widthPx = 1000))
        assertEquals(-1, DoubleTapHoldSeekPolicy.seekZone(x = 349f, widthPx = 1000))
    }

    @Test
    fun `right zone above 65 percent is forward`() {
        assertEquals(1, DoubleTapHoldSeekPolicy.seekZone(x = 651f, widthPx = 1000))
        assertEquals(1, DoubleTapHoldSeekPolicy.seekZone(x = 1000f, widthPx = 1000))
    }

    @Test
    fun `middle band is the center zone`() {
        assertEquals(0, DoubleTapHoldSeekPolicy.seekZone(x = 350f, widthPx = 1000))
        assertEquals(0, DoubleTapHoldSeekPolicy.seekZone(x = 500f, widthPx = 1000))
        assertEquals(0, DoubleTapHoldSeekPolicy.seekZone(x = 650f, widthPx = 1000))
    }

    // ---- repeatIntervalMs ----

    @Test
    fun `first repeats use the normal cadence`() {
        assertEquals(
            DoubleTapHoldSeekPolicy.NORMAL_REPEAT_MS,
            DoubleTapHoldSeekPolicy.repeatIntervalMs(completedRepeats = 0),
        )
        assertEquals(
            DoubleTapHoldSeekPolicy.NORMAL_REPEAT_MS,
            DoubleTapHoldSeekPolicy.repeatIntervalMs(completedRepeats = DoubleTapHoldSeekPolicy.ACCELERATION_AFTER_REPEATS - 1),
        )
    }

    @Test
    fun `cadence accelerates after the acceleration threshold`() {
        assertEquals(
            DoubleTapHoldSeekPolicy.FAST_REPEAT_MS,
            DoubleTapHoldSeekPolicy.repeatIntervalMs(completedRepeats = DoubleTapHoldSeekPolicy.ACCELERATION_AFTER_REPEATS),
        )
        assertEquals(
            DoubleTapHoldSeekPolicy.FAST_REPEAT_MS,
            DoubleTapHoldSeekPolicy.repeatIntervalMs(completedRepeats = 1000),
        )
    }

    @Test
    fun `cadence is monotonically non-increasing and always positive`() {
        var previous = Long.MAX_VALUE
        for (completed in 0..64) {
            val interval = DoubleTapHoldSeekPolicy.repeatIntervalMs(completed)
            assertTrue(interval > 0, "interval at $completed must be positive")
            assertTrue(interval <= previous, "interval at $completed must not speed up past $previous")
            previous = interval
        }
    }

    @Test
    fun `accelerated cadence is faster than the normal cadence`() {
        assertTrue(DoubleTapHoldSeekPolicy.FAST_REPEAT_MS < DoubleTapHoldSeekPolicy.NORMAL_REPEAT_MS)
    }

    // ---- zone constants (pinned: they must stay the double-tap handler's split) ----

    @Test
    fun `zone fractions stay at the shipped double-tap split`() {
        assertEquals(0.35f, DoubleTapHoldSeekPolicy.SEEK_ZONE_LEFT_FRACTION)
        assertEquals(0.65f, DoubleTapHoldSeekPolicy.SEEK_ZONE_RIGHT_FRACTION)
    }
}
