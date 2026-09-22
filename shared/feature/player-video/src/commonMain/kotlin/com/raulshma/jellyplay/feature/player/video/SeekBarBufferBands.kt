package com.raulshma.jellyplay.feature.player.video

/**
 * Pure band-layout math for the seek bar's multi-range buffered shading.
 * Extracted from [components.PlayerControls] so the ladder —
 * duration guard, per-range fraction coercion, degenerate-band pruning — is
 * matrix-testable without a Compose host; the Canvas draw is a dumb loop over
 * the returned bands.
 */
object SeekBarBufferBands {

    /** One shaded band as a fraction pair on the track (`0..1`, end > start). */
    data class Band(val startFraction: Float, val endFraction: Float)

    /**
     * Maps absolute-ms buffered ranges onto track fractions:
     *  - `durationMs <= 0` (live/unknown runtime) shades nothing — there is
     *    no denominator to divide by;
     *  - each range end is coerced into `0..1` (a clamped engine range can
     *    still land marginally out of bounds through float rounding);
     *  - degenerate bands (`end <= start` after coercion) are dropped rather
     *    than drawn as zero-width slivers.
     */
    fun bands(ranges: List<LongRange>, durationMs: Long): List<Band> {
        if (durationMs <= 0L || ranges.isEmpty()) return emptyList()
        val result = ArrayList<Band>(ranges.size)
        for (range in ranges) {
            val start = (range.first.toFloat() / durationMs).coerceIn(0f, 1f)
            val end = (range.last.toFloat() / durationMs).coerceIn(0f, 1f)
            if (end > start) result.add(Band(start, end))
        }
        return result
    }
}
