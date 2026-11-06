package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.components.PerformanceStatsAdvice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The §5.12 "what to try" fold (issue #186) — the stats overlay's only
 * decision logic, pure and pinned table-style: the priority ladder (dropped
 * frames + direct play → AUTO/LOW; dropped frames + transcode → LOW /
 * switch engine; bandwidth below bitrate → lower quality / check Wi-Fi) and
 * the two "high drop" shapes (rate- vs absolute-gated). The overlay maps the
 * verdicts to localized lines; nothing here touches a store.
 */
class PerformanceStatsAdviceTest {

    // ── the priority ladder ─────────────────────────────────────────────────

    @Test
    fun `high drop rate with direct play advises AUTO or LOW`() {
        val advice = PerformanceStatsAdvice.adviceFor(
            droppedFrames = 30,
            totalVideoFrames = 1000,
            isDirectPlay = true,
            estimatedBandwidthBps = 0,
            streamBitrateBps = 0,
        )
        assertEquals(PerformanceStatsAdvice.Advice.TRY_AUTO_OR_LOW, advice)
    }

    @Test
    fun `high drop rate with transcode advises LOW or engine switch`() {
        val advice = PerformanceStatsAdvice.adviceFor(
            droppedFrames = 30,
            totalVideoFrames = 1000,
            isDirectPlay = false,
            estimatedBandwidthBps = 0,
            streamBitrateBps = 0,
        )
        assertEquals(PerformanceStatsAdvice.Advice.TRY_LOW_OR_SWITCH_ENGINE, advice)
    }

    @Test
    fun `bandwidth below stream bitrate advises lower quality`() {
        val advice = PerformanceStatsAdvice.adviceFor(
            droppedFrames = 0,
            totalVideoFrames = 1000,
            isDirectPlay = true,
            estimatedBandwidthBps = 4_000_000,
            streamBitrateBps = 12_000_000,
        )
        assertEquals(PerformanceStatsAdvice.Advice.LOWER_QUALITY_CHECK_WIFI, advice)
    }

    @Test
    fun `drop advice outranks the bandwidth advice`() {
        // Both causes present: the dropped-frames verdict wins (§5.12's
        // first-listed cause) — the profile lever is the stronger one.
        val advice = PerformanceStatsAdvice.adviceFor(
            droppedFrames = 30,
            totalVideoFrames = 1000,
            isDirectPlay = false,
            estimatedBandwidthBps = 1_000_000,
            streamBitrateBps = 12_000_000,
        )
        assertEquals(PerformanceStatsAdvice.Advice.TRY_LOW_OR_SWITCH_ENGINE, advice)
    }

    @Test
    fun `a healthy session gets no advice`() {
        assertNull(
            PerformanceStatsAdvice.adviceFor(
                droppedFrames = 0,
                totalVideoFrames = 10_000,
                isDirectPlay = true,
                estimatedBandwidthBps = 50_000_000,
                streamBitrateBps = 8_000_000,
            ),
        )
    }

    // ── the "high drop" gates ───────────────────────────────────────────────

    @Test
    fun `drop rate at the threshold counts as high`() {
        // Exactly 2% (20 dropped of 980 rendered — the same dropped/(dropped
        // + total) convention the overlay's Drop Rate row uses): >= holds.
        assertTrue(PerformanceStatsAdvice.isDropRateHigh(droppedFrames = 20, totalVideoFrames = 980))
        assertFalse(PerformanceStatsAdvice.isDropRateHigh(droppedFrames = 19, totalVideoFrames = 980))
    }

    @Test
    fun `no frame totals falls back to the absolute gate`() {
        // An engine without totals cannot rate — the absolute count keeps the
        // advice reachable.
        assertTrue(PerformanceStatsAdvice.isDropRateHigh(droppedFrames = 250, totalVideoFrames = 0))
        assertFalse(PerformanceStatsAdvice.isDropRateHigh(droppedFrames = 249, totalVideoFrames = 0))
    }

    @Test
    fun `bandwidth advice needs both signals present`() {
        // A zero bandwidth estimate (unknown) or zero bitrate must not fire
        // the line on missing data.
        assertNull(
            PerformanceStatsAdvice.adviceFor(
                droppedFrames = 0,
                totalVideoFrames = 10_000,
                isDirectPlay = true,
                estimatedBandwidthBps = 0,
                streamBitrateBps = 8_000_000,
            ),
        )
        assertNull(
            PerformanceStatsAdvice.adviceFor(
                droppedFrames = 0,
                totalVideoFrames = 10_000,
                isDirectPlay = true,
                estimatedBandwidthBps = 4_000_000,
                streamBitrateBps = 0,
            ),
        )
    }
}
