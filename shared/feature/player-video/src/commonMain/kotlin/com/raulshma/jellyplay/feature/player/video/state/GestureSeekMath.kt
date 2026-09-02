package com.raulshma.jellyplay.feature.player.video.state

/**
 * Pure decision logic for the gesture-seek/volume/brightness overlays, split out
 * of `VideoPlayerScreen` so it can be unit-tested with zero Android deps.
 *
 * This is the logic that previously had **no test coverage** and is the highest
 * regression risk: the live-stream delta cap, the local-volume step quantize,
 * and the commit-vs-cancel asymmetry. See [GestureSeekController] for the
 * stateful wrapper that drives these functions.
 */
internal object GestureSeekMath {

    /**
     * Clamp the live seek delta into a target position.
     *
     * VOD clamps to `[0, durationMs]`. Live streams (`durationMs <= 0`) cap the
     * *per-gesture* delta to `±swipeSeekMaxMs` so a swipe can't jump to a
     * position that doesn't exist, then floor at 0 (no negative position).
     */
    fun seekTarget(
        startPositionMs: Long,
        totalDeltaMs: Long,
        durationMs: Long,
        swipeSeekMaxMs: Long,
    ): Long = if (durationMs <= 0L) {
        val capped = totalDeltaMs.coerceIn(-swipeSeekMaxMs, swipeSeekMaxMs)
        (startPositionMs + capped).coerceAtLeast(0L)
    } else {
        (startPositionMs + totalDeltaMs).coerceIn(0L, durationMs)
    }

    /**
     * Local-volume step: quantize the accumulated delta into discrete hardware
     * steps and return the remainder so fractional motion carries across events.
     *
     * Returns `(stepsToApply, remainingAccumulator)`. `steps` is clamped to
     * `±maxSteps` so a huge single delta can't overshoot the stream range.
     */
    fun localVolumeStep(accumulator: Float, stepThreshold: Float, maxSteps: Int): Pair<Int, Float> {
        if (stepThreshold <= 0f) return 0 to accumulator
        val steps = (accumulator / stepThreshold).toInt().coerceIn(-maxSteps, maxSteps)
        return steps to (accumulator - steps * stepThreshold)
    }

    /** Cast-volume: continuous float, just clamp to `[0,1]`. */
    fun castVolumeTarget(currentNorm: Float, accumulator: Float): Float =
        (currentNorm + accumulator).coerceIn(0f, 1f)

    /** Brightness: add the delta, clamp to `[0,1]`. */
    fun brightnessTarget(current: Float, delta: Float): Float =
        (current + delta).coerceIn(0f, 1f)
}
