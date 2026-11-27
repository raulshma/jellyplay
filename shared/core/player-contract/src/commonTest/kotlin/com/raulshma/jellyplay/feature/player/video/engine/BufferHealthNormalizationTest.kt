package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure tests for [EngineBufferHealth] and [NetworkThroughputSnapshot] calculations
 * (ADR-0006: commonTest, zero mocks).
 */
class BufferHealthNormalizationTest {

    @Test
    fun `bufferProgressFraction calculates proportional fraction`() {
        val health = EngineBufferHealth(
            bufferedDurationMs = 1_500L,
            rebufferThresholdMs = 3_000L,
            targetBufferDurationMs = 25_000L,
        )
        assertEquals(0.5f, health.bufferProgressFraction)
    }

    @Test
    fun `bufferProgressFraction clamps to 1 when buffered exceeds threshold`() {
        val health = EngineBufferHealth(
            bufferedDurationMs = 4_500L,
            rebufferThresholdMs = 3_000L,
            targetBufferDurationMs = 25_000L,
        )
        assertEquals(1.0f, health.bufferProgressFraction)
    }

    @Test
    fun `bufferProgressFraction clamps to 0 when buffered duration is negative`() {
        val health = EngineBufferHealth(
            bufferedDurationMs = -500L,
            rebufferThresholdMs = 3_000L,
            targetBufferDurationMs = 25_000L,
        )
        assertEquals(0.0f, health.bufferProgressFraction)
    }

    @Test
    fun `bufferProgressFraction defaults to 1 when threshold is zero or negative`() {
        val zeroThreshold = EngineBufferHealth(
            bufferedDurationMs = 0L,
            rebufferThresholdMs = 0L,
            targetBufferDurationMs = 25_000L,
        )
        assertEquals(1.0f, zeroThreshold.bufferProgressFraction)

        val negativeThreshold = EngineBufferHealth(
            bufferedDurationMs = 1_000L,
            rebufferThresholdMs = -1L,
            targetBufferDurationMs = 25_000L,
        )
        assertEquals(1.0f, negativeThreshold.bufferProgressFraction)
    }

    @Test
    fun `throughputRatio calculates ratio against active bitrate`() {
        val snapshot = NetworkThroughputSnapshot(
            currentThroughputBps = 12_000_000L,
            estimatedBandwidthBps = 15_000_000L,
            activeBitrateBps = 8_000_000,
        )
        assertEquals(1.5f, snapshot.throughputRatio)
    }

    @Test
    fun `throughputRatio defaults to 1 when active bitrate is zero or negative`() {
        val zeroBitrate = NetworkThroughputSnapshot(
            currentThroughputBps = 5_000_000L,
            estimatedBandwidthBps = 5_000_000L,
            activeBitrateBps = 0,
        )
        assertEquals(1.0f, zeroBitrate.throughputRatio)

        val negativeBitrate = NetworkThroughputSnapshot(
            currentThroughputBps = 5_000_000L,
            estimatedBandwidthBps = 5_000_000L,
            activeBitrateBps = -1,
        )
        assertEquals(1.0f, negativeBitrate.throughputRatio)
    }
}
