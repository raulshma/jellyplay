package com.raulshma.jellyplay.feature.player.video.components

import com.raulshma.jellyplay.feature.player.video.engine.NetworkThroughputSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure tests for [bufferingPillTelemetry] — the buffering diagnostics pill's
 * details-line fold (commonMain logic, so the common lane per ADR-0006 rule 1).
 * The pinned contract: the EWMA bandwidth estimate rides its OWN row (the
 * composable renders it labeled as an estimate, never as a download rate),
 * and the measured `↓` row appears only when the engine actually measured a
 * throughput.
 */
class BufferingPillTelemetryTest {

    @Test
    fun `a measured throughput keeps the download-rate row`() {
        val telemetry = bufferingPillTelemetry(
            throughput = NetworkThroughputSnapshot(
                currentThroughputBps = 8_000_000L,
                estimatedBandwidthBps = 10_000_000L,
                activeBitrateBps = 6_000_000,
            ),
            transcodeSpeedRatio = null,
            isTranscoding = false,
        )
        assertEquals(8_000_000L, telemetry.measuredThroughputBps)
        assertEquals(10_000_000L, telemetry.estimatedBandwidthBps)
        assertEquals(6_000_000L, telemetry.streamBitrateBps)
        assertNull(telemetry.transcodeSpeedRatio)
    }

    @Test
    fun `no measured throughput drops the download-rate row but keeps the estimate`() {
        // The producer-side contract (DiagnosticsTelemetry): engines expose no
        // measured instantaneous throughput, so currentThroughputBps stays 0
        // while the EWMA estimate lives in estimatedBandwidthBps.
        val telemetry = bufferingPillTelemetry(
            throughput = NetworkThroughputSnapshot(
                currentThroughputBps = 0L,
                estimatedBandwidthBps = 12_500_000L,
                activeBitrateBps = 8_000_000,
            ),
            transcodeSpeedRatio = null,
            isTranscoding = false,
        )
        assertNull(telemetry.measuredThroughputBps)
        assertEquals(12_500_000L, telemetry.estimatedBandwidthBps)
        assertEquals(8_000_000L, telemetry.streamBitrateBps)
    }

    @Test
    fun `a zero estimate renders no estimate row`() {
        val telemetry = bufferingPillTelemetry(
            throughput = NetworkThroughputSnapshot(
                currentThroughputBps = 0L,
                estimatedBandwidthBps = 0L,
                activeBitrateBps = 4_000_000,
            ),
            transcodeSpeedRatio = null,
            isTranscoding = false,
        )
        assertNull(telemetry.measuredThroughputBps)
        assertNull(telemetry.estimatedBandwidthBps)
        assertEquals(4_000_000L, telemetry.streamBitrateBps)
    }

    @Test
    fun `a null snapshot renders no network rows`() {
        val telemetry = bufferingPillTelemetry(
            throughput = null,
            transcodeSpeedRatio = null,
            isTranscoding = false,
        )
        assertNull(telemetry.measuredThroughputBps)
        assertNull(telemetry.estimatedBandwidthBps)
        assertNull(telemetry.streamBitrateBps)
        assertNull(telemetry.transcodeSpeedRatio)
    }

    @Test
    fun `the transcode ratio renders only while transcoding and rounds to one decimal`() {
        val transcribing = bufferingPillTelemetry(
            throughput = null,
            transcodeSpeedRatio = 1.9499,
            isTranscoding = true,
        )
        assertEquals(1.9, transcribing.transcodeSpeedRatio!!, 0.0001)

        val notTranscoding = bufferingPillTelemetry(
            throughput = null,
            transcodeSpeedRatio = 1.9499,
            isTranscoding = false,
        )
        assertNull(notTranscoding.transcodeSpeedRatio)
    }
}
