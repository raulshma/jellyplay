package com.raulshma.jellyplay.feature.player.video.state

import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * Pure-JVM tests for [GestureSeekMath] — the clamp/accumulator/position math that
 * previously had zero coverage (`VideoPlayerSeekStateTest` re-implements the
 * math inline and never calls production code). Pins the live-stream cap, the
 * VOD clamp, and the volume/brightness clamps.
 */
class GestureSeekMathTest {

    // ---- seekTarget ----

    @Test
    fun `seekTarget VOD clamps to zero`() {
        val target = GestureSeekMath.seekTarget(
            startPositionMs = 10_000L,
            totalDeltaMs = -50_000L,
            durationMs = 120_000L,
            swipeSeekMaxMs = 120_000L,
        )
        assertEquals(0L, target)
    }

    @Test
    fun `seekTarget VOD clamps to duration`() {
        val target = GestureSeekMath.seekTarget(
            startPositionMs = 110_000L,
            totalDeltaMs = 50_000L,
            durationMs = 120_000L,
            swipeSeekMaxMs = 120_000L,
        )
        assertEquals(120_000L, target)
    }

    @Test
    fun `seekTarget VOD applies delta within bounds`() {
        val target = GestureSeekMath.seekTarget(
            startPositionMs = 60_000L,
            totalDeltaMs = 30_000L,
            durationMs = 120_000L,
            swipeSeekMaxMs = 120_000L,
        )
        assertEquals(90_000L, target)
    }

    @Test
    fun `seekTarget live caps per-gesture delta to positive swipeSeekMaxMs`() {
        // durationMs <= 0 means live: a 500s delta must cap at swipeSeekMaxMs (120s).
        val target = GestureSeekMath.seekTarget(
            startPositionMs = 1_000L,
            totalDeltaMs = 500_000L,
            durationMs = 0L,
            swipeSeekMaxMs = 120_000L,
        )
        assertEquals(121_000L, target)
    }

    @Test
    fun `seekTarget live caps per-gesture delta to negative swipeSeekMaxMs`() {
        val target = GestureSeekMath.seekTarget(
            startPositionMs = 100_000L,
            totalDeltaMs = -500_000L,
            durationMs = -1L,
            swipeSeekMaxMs = 120_000L,
        )
        assertEquals(0L, target) // capped to -120s → -20s → floored at 0
    }

    @Test
    fun `seekTarget live floors at zero`() {
        val target = GestureSeekMath.seekTarget(
            startPositionMs = 5_000L,
            totalDeltaMs = -120_000L,
            durationMs = 0L,
            swipeSeekMaxMs = 120_000L,
        )
        assertEquals(0L, target)
    }

    // ---- localVolumeStep ----

    @Test
    fun `localVolumeStep quantizes positive accumulation to whole steps`() {
        // accumulator 0.3 with threshold 0.1 → 3 steps, 0 remainder.
        val (steps, remainder) = GestureSeekMath.localVolumeStep(accumulator = 0.3f, stepThreshold = 0.1f, maxSteps = 15)
        assertEquals(3, steps)
        assertEquals(0f, remainder, 0.0001f)
    }

    @Test
    fun `localVolumeStep quantizes negative accumulation`() {
        val (steps, remainder) = GestureSeekMath.localVolumeStep(accumulator = -0.25f, stepThreshold = 0.1f, maxSteps = 15)
        assertEquals(-2, steps)
        assertEquals(-0.05f, remainder, 0.0001f)
    }

    @Test
    fun `localVolumeStep carries fractional remainder`() {
        // accumulator 0.15 with threshold 0.1 → 1 step, 0.05 remainder.
        val (steps, remainder) = GestureSeekMath.localVolumeStep(accumulator = 0.15f, stepThreshold = 0.1f, maxSteps = 15)
        assertEquals(1, steps)
        assertEquals(0.05f, remainder, 0.0001f)
    }

    @Test
    fun `localVolumeStep zero accumulator yields zero steps`() {
        val (steps, remainder) = GestureSeekMath.localVolumeStep(accumulator = 0f, stepThreshold = 0.1f, maxSteps = 15)
        assertEquals(0, steps)
        assertEquals(0f, remainder, 0.0001f)
    }

    @Test
    fun `localVolumeStep clamps to maxSteps`() {
        // A huge delta can't overshoot the stream range.
        val (steps, _) = GestureSeekMath.localVolumeStep(accumulator = 100f, stepThreshold = 0.1f, maxSteps = 15)
        assertEquals(15, steps)
    }

    @Test
    fun `localVolumeStep zero threshold is a no-op`() {
        val (steps, remainder) = GestureSeekMath.localVolumeStep(accumulator = 0.5f, stepThreshold = 0f, maxSteps = 15)
        assertEquals(0, steps)
        assertEquals(0.5f, remainder, 0.0001f)
    }

    // ---- castVolumeTarget ----

    @Test
    fun `castVolumeTarget clamps above 1`() {
        assertEquals(1f, GestureSeekMath.castVolumeTarget(currentNorm = 0.9f, accumulator = 0.5f), 0.0001f)
    }

    @Test
    fun `castVolumeTarget clamps below 0`() {
        assertEquals(0f, GestureSeekMath.castVolumeTarget(currentNorm = 0.1f, accumulator = -0.5f), 0.0001f)
    }

    @Test
    fun `castVolumeTarget applies delta in range`() {
        assertEquals(0.6f, GestureSeekMath.castVolumeTarget(currentNorm = 0.4f, accumulator = 0.2f), 0.0001f)
    }

    // ---- brightnessTarget ----

    @Test
    fun `brightnessTarget clamps above 1`() {
        assertEquals(1f, GestureSeekMath.brightnessTarget(current = 0.9f, delta = 0.5f), 0.0001f)
    }

    @Test
    fun `brightnessTarget clamps below 0`() {
        assertEquals(0f, GestureSeekMath.brightnessTarget(current = 0.1f, delta = -0.5f), 0.0001f)
    }

    @Test
    fun `brightnessTarget applies delta in range`() {
        assertEquals(0.55f, GestureSeekMath.brightnessTarget(current = 0.3f, delta = 0.25f), 0.0001f)
    }
}
