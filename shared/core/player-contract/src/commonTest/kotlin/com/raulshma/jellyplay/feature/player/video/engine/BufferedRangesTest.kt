package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Matrix tests for the pure buffered-range algebra — the clamp /
 * prune / sort / merge ladder every engine publishes through, plus the three
 * per-engine derivations (contiguous window, fraction-ahead approximation,
 * mpv demuxer-cache-state node).
 */
class BufferedRangesTest {

    private val duration = 600_000L // 10 min

    // ── normalize ───────────────────────────────────────────────────────────

    @Test
    fun `normalize clamps ranges to the item bounds`() {
        assertEquals(
            listOf(0L..5_000L, 10_000L..duration),
            BufferedRanges.normalize(listOf((-2_000L)..5_000L, 10_000L..900_000L), duration),
        )
    }

    @Test
    fun `normalize drops degenerate and empty ranges`() {
        assertEquals(
            emptyList(),
            BufferedRanges.normalize(listOf(1_000L..1_000L, 5_000L..1_000L), duration),
        )
        assertEquals(emptyList(), BufferedRanges.normalize(emptyList(), duration))
    }

    @Test
    fun `normalize sorts unsorted input`() {
        assertEquals(
            listOf(0L..1_000L, 5_000L..8_000L),
            BufferedRanges.normalize(listOf(5_000L..8_000L, 0L..1_000L), duration),
        )
    }

    @Test
    fun `normalize merges overlapping ranges`() {
        // The middle range overlaps the first, the third touches the merged
        // end — one chain-merged window.
        assertEquals(
            listOf(0L..10_000L),
            BufferedRanges.normalize(listOf(0L..4_000L, 3_000L..7_000L, 7_000L..10_000L), duration),
        )
    }

    @Test
    fun `normalize merges adjacent touching ranges`() {
        assertEquals(
            listOf(0L..8_000L),
            BufferedRanges.normalize(listOf(0L..4_000L, 4_000L..8_000L), duration),
        )
    }

    @Test
    fun `normalize keeps a gap between disjoint ranges`() {
        assertEquals(
            listOf(0L..4_000L, 8_000L..12_000L),
            BufferedRanges.normalize(listOf(8_000L..12_000L, 0L..4_000L), duration),
        )
    }

    @Test
    fun `normalize extends a merged range to the longest tail`() {
        assertEquals(
            listOf(0L..12_000L),
            BufferedRanges.normalize(listOf(0L..12_000L, 2_000L..5_000L), duration),
        )
    }

    @Test
    fun `normalize without a known duration clamps only the lower bound`() {
        // Live stream: no finite duration, negative mpv timeline start.
        assertEquals(
            listOf(0L..30_000L),
            BufferedRanges.normalize(listOf((-5_000L)..30_000L), 0L),
        )
    }

    @Test
    fun `normalize drops ranges entirely past the duration`() {
        // Both ends beyond the clamp collapse onto the duration and become
        // degenerate — a stale window past EOF publishes nothing.
        assertEquals(
            emptyList(),
            BufferedRanges.normalize(listOf(700_000L..900_000L), duration),
        )
    }

    // ── contiguous window ───────────────────────────────────────────────────

    @Test
    fun `contiguous returns the clamped single window`() {
        assertEquals(
            listOf(0L..5_000L),
            BufferedRanges.contiguous(startMs = -1_000L, endMs = 5_000L, durationMs = duration),
        )
    }

    @Test
    fun `contiguous clamps the end to the duration`() {
        assertEquals(
            listOf(10_000L..duration),
            BufferedRanges.contiguous(10_000L, 900_000L, duration),
        )
    }

    @Test
    fun `contiguous collapses degenerate windows to no ranges`() {
        assertEquals(
            emptyList(),
            BufferedRanges.contiguous(5_000L, 5_000L, duration),
        )
        assertEquals(
            emptyList(),
            BufferedRanges.contiguous(5_000L, 1_000L, duration),
        )
    }

    // ── fraction-ahead approximation (libVLC) ──────────────────────────────

    @Test
    fun `aheadOfPosition spans position to position plus fraction of duration`() {
        assertEquals(
            listOf(60_000L..90_000L),
            BufferedRanges.aheadOfPosition(60_000L, 0.05f, duration),
        )
    }

    @Test
    fun `aheadOfPosition clamps the band to the duration`() {
        assertEquals(
            listOf(590_000L..duration),
            BufferedRanges.aheadOfPosition(590_000L, 0.5f, duration),
        )
    }

    @Test
    fun `aheadOfPosition rejects zero fractions and unknown durations`() {
        assertEquals(emptyList(), BufferedRanges.aheadOfPosition(60_000L, 0f, duration))
        assertEquals(emptyList(), BufferedRanges.aheadOfPosition(60_000L, 0.5f, 0L))
    }

    // ── mpv demuxer-cache-state derivation ─────────────────────────────────

    @Test
    fun `fromDemuxerCacheState derives the contiguous window in ms`() {
        assertEquals(
            listOf(2_000L..180_000L),
            BufferedRanges.fromDemuxerCacheState(
                demuxerStartTimeSec = 2.0,
                cacheEndSec = 180.0,
                seekableRangesSec = null,
                durationMs = duration,
            ),
        )
    }

    @Test
    fun `fromDemuxerCacheState clamps the window to the item bounds`() {
        assertEquals(
            listOf(0L..duration),
            BufferedRanges.fromDemuxerCacheState(-30.0, 900.0, null, duration),
        )
    }

    @Test
    fun `fromDemuxerCacheState prefers per-range detail when exposed`() {
        assertEquals(
            listOf(0L..4_000L, 10_000L..20_000L),
            BufferedRanges.fromDemuxerCacheState(
                demuxerStartTimeSec = 0.0,
                cacheEndSec = 20.0,
                seekableRangesSec = listOf(0.0 to 4.0, 10.0 to 20.0),
                durationMs = duration,
            ),
        )
    }

    @Test
    fun `fromDemuxerCacheState ignores empty per-range detail`() {
        assertEquals(
            listOf(0L..10_000L),
            BufferedRanges.fromDemuxerCacheState(0.0, 10.0, emptyList(), duration),
        )
    }

    @Test
    fun `fromDemuxerCacheState treats missing values as no ranges`() {
        assertEquals(
            emptyList(),
            BufferedRanges.fromDemuxerCacheState(null, null, null, duration),
        )
        assertEquals(
            emptyList(),
            BufferedRanges.fromDemuxerCacheState(5.0, Double.NaN, null, duration),
        )
        // A negative/no-timestamp cache-end (mpv's "nothing cached" form) must
        // not fabricate a band.
        assertEquals(
            emptyList(),
            BufferedRanges.fromDemuxerCacheState(5.0, -1.0, null, duration),
        )
    }

    @Test
    fun `fromDemuxerCacheState without a duration clamps only the start`() {
        assertEquals(
            listOf(2_000L..180_000L),
            BufferedRanges.fromDemuxerCacheState(2.0, 180.0, null, 0L),
        )
    }

    // ── shared EMPTY default ────────────────────────────────────────────────

    @Test
    fun `EMPTY is an always-empty flow`() {
        assertTrue(BufferedRanges.EMPTY.value.isEmpty())
    }
}
