package com.raulshma.jellyplay.feature.player.video.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pure buffered-range algebra. Engines previously exposed a single
 * scalar ([MediaEngine.bufferedPositionMs]); the seek bar shaded one
 * contiguous `0..bufferedFraction` band from it. Real demuxers keep
 * discontinuous ranges (a network seek drops the old window, the back-buffer
 * survives behind the playhead), so the contract grows
 * [MediaEngine.bufferedRanges] and every clamp/merge decision lands HERE —
 * shared by all four engines (ExoPlayer / mpv Android+desktop / libVLC) and
 * unit-testable without a live player.
 *
 * All magnitudes are milliseconds in the item's absolute timeline, matching
 * [MediaEngine.currentPositionMs] / [MediaEngine.durationMs].
 */
object BufferedRanges {

    /**
     * Shared, never-mutated "no ranges" flow — the default backing for
     * engines/fakes that have no buffer surface ([MediaEngine.bufferedRanges]'
     * default getter). A single instance is safe precisely because nothing
     * ever writes it; engines with real ranges declare their own
     * [MutableStateFlow] and override the property.
     */
    val EMPTY: StateFlow<List<LongRange>> = MutableStateFlow(emptyList())

    /**
     * Clamps, prunes, sorts and merges raw engine ranges into the canonical
     * published form:
     *  - each range is clamped to `0..durationMs` (lower bound only when the
     *    duration is unknown — live streams buffer without a finite end);
     *  - degenerate/empty ranges (`first >= last` after clamping) are dropped;
     *  - the result is sorted by start and overlapping/adjacent ranges are
     *    merged (two touching windows are one continuous buffer).
     */
    fun normalize(ranges: List<LongRange>, durationMs: Long): List<LongRange> {
        if (ranges.isEmpty()) return emptyList()
        val clamped = ArrayList<LongRange>(ranges.size)
        for (range in ranges) {
            var start = range.first
            var end = range.last
            if (start < 0L) start = 0L
            if (durationMs > 0L && end > durationMs) end = durationMs
            if (durationMs > 0L && start > durationMs) start = durationMs
            if (end > start) clamped.add(start..end)
        }
        if (clamped.isEmpty()) return emptyList()
        clamped.sortBy { it.first }
        val merged = ArrayList<LongRange>(clamped.size)
        for (range in clamped) {
            val last = merged.lastOrNull()
            if (last != null && range.first <= last.last) {
                if (range.last > last.last) {
                    merged[merged.size - 1] = last.first..range.last
                }
            } else {
                merged.add(range)
            }
        }
        return merged
    }

    /**
     * The conservative single-window derivation (v1 for every engine): one
     * contiguous `[startMs, endMs]` clamped to the item bounds. Returns an
     * empty list for degenerate windows so a scalar-only engine publishes no
     * shading rather than a full-track band.
     */
    fun contiguous(startMs: Long, endMs: Long, durationMs: Long): List<LongRange> =
        if (endMs <= startMs) emptyList() else normalize(listOf(startMs..endMs), durationMs)

    /**
     * Fraction-ahead approximation (libVLC v1): `[positionMs, positionMs +
     * fraction * durationMs]`. libVLC only reports a 0..100 buffering
     * percentage; treating it as the buffered-ahead fraction of the runtime is
     * the standard approximation.
     */
    fun aheadOfPosition(positionMs: Long, fraction: Float, durationMs: Long): List<LongRange> {
        if (durationMs <= 0L || fraction <= 0f) return emptyList()
        val end = positionMs + (fraction.toDouble() * durationMs).toLong()
        return contiguous(positionMs, end, durationMs)
    }

    /**
     * mpv `demuxer-cache-state` derivation, shared by both mpv engines (the
     * node shape differs per binding — JNA `Map<*, *>` vs the Android
     * `MPVNode` tree — so the engines only extract the three numbers and this
     * function owns the decision):
     *  - when the installed libmpv exposes `seekable-ranges` (per-range
     *    detail, mpv >= 0.35), those ranges are taken directly;
     *  - otherwise the contiguous window `[demuxer-start-time, cache-end]`
     *    (seconds in mpv's timeline) is derived, clamped to `[0, duration]`.
     *
     * Null/NaN/negative-infinite inputs mean "mpv has no value yet" and
     * collapse to no ranges.
     */
    fun fromDemuxerCacheState(
        demuxerStartTimeSec: Double?,
        cacheEndSec: Double?,
        seekableRangesSec: List<Pair<Double, Double>>?,
        durationMs: Long,
    ): List<LongRange> {
        val detailed = seekableRangesSec
        if (!detailed.isNullOrEmpty()) {
            return normalize(detailed.mapNotNull { (startSec, endSec) ->
                secondsToRange(startSec, endSec)
            }, durationMs)
        }
        val startSec = demuxerStartTimeSec?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val endSec = cacheEndSec?.takeIf { it.isFinite() && it > 0.0 } ?: return emptyList()
        return secondsToRange(startSec, endSec)?.let { normalize(listOf(it), durationMs) } ?: emptyList()
    }

    private fun secondsToRange(startSec: Double, endSec: Double): LongRange? {
        if (!startSec.isFinite() || !endSec.isFinite()) return null
        if (endSec <= startSec) return null
        return (startSec * 1000.0).toLong()..(endSec * 1000.0).toLong()
    }
}
