package com.raulshma.jellyplay.feature.player.video

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleSyncCalculatorTest {

    @Test
    fun `text late produces negative delta to move subs earlier`() {
        // Voice heard at 1000ms; subtitle text seen at 1500ms (text is 500ms late).
        val delta = SubtitleSyncCalculator.computeDelayDelta(voiceHeardMs = 1000, textSeenMs = 1500)
        assertEquals(-500L, delta)
    }

    @Test
    fun `text early produces positive delta to move subs later`() {
        val delta = SubtitleSyncCalculator.computeDelayDelta(voiceHeardMs = 1500, textSeenMs = 1000)
        assertEquals(500L, delta)
    }

    @Test
    fun `perfectly aligned produces zero delta`() {
        assertEquals(0L, SubtitleSyncCalculator.computeDelayDelta(1000, 1000))
    }

    @Test
    fun `applyDelta clamps to max bound`() {
        // Current 29s + 5s delta would exceed 30s → clamped.
        val result = SubtitleSyncCalculator.applyDelta(currentDelayMs = 29_000, deltaMs = 5_000)
        assertEquals(30_000L, result)
    }

    @Test
    fun `applyDelta clamps to min bound`() {
        val result = SubtitleSyncCalculator.applyDelta(currentDelayMs = -29_000, deltaMs = -5_000)
        assertEquals(-30_000L, result)
    }

    @Test
    fun `applyDelta sums within bounds`() {
        assertEquals(200L, SubtitleSyncCalculator.applyDelta(currentDelayMs = 500, deltaMs = -300))
    }
}
