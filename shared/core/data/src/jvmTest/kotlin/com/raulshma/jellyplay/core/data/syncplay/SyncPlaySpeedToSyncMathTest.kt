package com.raulshma.jellyplay.core.data.syncplay

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Regression tests: [SyncPlayPlaybackCore.calculateSpeedCorrection] must clamp
 * both ends so a large drift (e.g. 1500ms ahead) doesn't produce a speed that exceeds the
 * player's capability (e.g. 2.5×). Before the fix only `coerceAtLeast(MIN_SPEED)` was
 * applied, allowing unbounded speedup.
 */
class SyncPlaySpeedToSyncMathTest {

    @Test
    fun `small positive drift yields slightly-faster-than-one speed`() {
        val diffMs = 100.0
        val time = SyncPlayPlaybackCore.calculateSpeedToSyncTime(diffMs)
        val speed = SyncPlayPlaybackCore.calculateSpeedCorrection(diffMs, time)
        assertTrue(speed in 1.0f..1.2f, "Expected 1.0 < speed <= 1.2, got $speed")
    }

    @Test
    fun `small negative drift yields slightly-slower-than-one speed`() {
        val diffMs = -100.0
        val time = SyncPlayPlaybackCore.calculateSpeedToSyncTime(diffMs)
        val speed = SyncPlayPlaybackCore.calculateSpeedCorrection(diffMs, time)
        assertTrue(speed in 0.8f..1.0f, "Expected 0.8 <= speed < 1.0, got $speed")
    }

    @Test
    fun `1500ms positive drift clamps to MAX_SPEED`() {
        // The pre-fix math produced speed = 1.0 + 1500/1000 = 2.5 here.
        val diffMs = 1500.0
        val time = SyncPlayPlaybackCore.calculateSpeedToSyncTime(diffMs)
        val speed = SyncPlayPlaybackCore.calculateSpeedCorrection(diffMs, time)
        assertEquals(2.0f, speed, 0.001f)
    }

    @Test
    fun `5000ms positive drift still clamps to MAX_SPEED`() {
        val diffMs = 5_000.0
        val time = SyncPlayPlaybackCore.calculateSpeedToSyncTime(diffMs)
        val speed = SyncPlayPlaybackCore.calculateSpeedCorrection(diffMs, time)
        assertEquals(2.0f, speed, 0.001f)
    }

    @Test
    fun `extreme negative drift clamps to MIN_SPEED`() {
        // -10_000 ms drift: speed = 1.0 + (-10000/1000) = -9.0 → must clamp to MIN_SPEED.
        val diffMs = -10_000.0
        val time = SyncPlayPlaybackCore.calculateSpeedToSyncTime(diffMs)
        val speed = SyncPlayPlaybackCore.calculateSpeedCorrection(diffMs, time)
        assertEquals(0.2f, speed, 0.001f)
    }

    @Test
    fun `calculateSpeedToSyncTime extends duration when drift is large negative`() {
        // When diff is very negative, default duration is extended so MIN_SPEED can cover it.
        val diffMs = -10_000.0
        val time = SyncPlayPlaybackCore.calculateSpeedToSyncTime(diffMs)
        assertTrue(
            time > 1_000.0,
            "Expected extended duration > default 1000ms, got $time",
        )
    }

    @Test
    fun `calculateSpeedToSyncTime keeps default duration for moderate drift`() {
        val diffMs = 500.0
        val time = SyncPlayPlaybackCore.calculateSpeedToSyncTime(diffMs)
        assertEquals(1_000.0, time, 0.001)
    }

    @Test
    fun `zero drift yields speed exactly 1`() {
        val speed = SyncPlayPlaybackCore.calculateSpeedCorrection(diffMs = 0.0, speedToSyncTime = 1_000.0)
        assertEquals(1.0f, speed, 0.001f)
    }

    @Test
    fun `speed correction is always within MIN_SPEED to MAX_SPEED range`() {
        // Brute-force: every drift in the actionable range must produce a valid speed.
        longArrayOf(-5000, -2000, -1000, -500, -100, -50, 50, 100, 500, 1000, 2000, 5000).forEach { diff ->
            val time = SyncPlayPlaybackCore.calculateSpeedToSyncTime(diff.toDouble())
            val speed = SyncPlayPlaybackCore.calculateSpeedCorrection(diff.toDouble(), time)
            assertTrue(
                speed in 0.2f..2.0f,
                "Speed $speed for diff ${diff}ms is out of [0.2, 2.0]",
            )
        }
    }
}
