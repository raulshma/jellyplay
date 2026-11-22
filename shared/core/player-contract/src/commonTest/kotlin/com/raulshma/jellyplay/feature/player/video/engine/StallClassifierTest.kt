package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure matrix tests for [StallClassifier] (ADR-0006: commonTest, zero mocks).
 */
class StallClassifierTest {

    @Test
    fun `decoder stuck classifies as DECODER_OVERLOAD`() {
        val cause = StallClassifier.classify(
            bufferedDurationMs = 5_000L,
            rebufferThresholdMs = 3_000L,
            throughputRatio = 1.5f,
            isTranscoding = false,
            transcodeSpeedRatio = null,
            droppedFrameRatio = 0.0f,
            isDecoderStuck = true,
        )
        assertEquals(StallCause.DECODER_OVERLOAD, cause)
    }

    @Test
    fun `high dropped frame ratio classifies as DECODER_OVERLOAD`() {
        val cause = StallClassifier.classify(
            bufferedDurationMs = 5_000L,
            rebufferThresholdMs = 3_000L,
            throughputRatio = 1.5f,
            isTranscoding = false,
            transcodeSpeedRatio = null,
            droppedFrameRatio = 0.25f,
            isDecoderStuck = false,
        )
        assertEquals(StallCause.DECODER_OVERLOAD, cause)
    }

    @Test
    fun `server transcode speed below real-time classifies as SERVER_TRANSCODE_BOTTLENECK`() {
        val cause = StallClassifier.classify(
            bufferedDurationMs = 200L,
            rebufferThresholdMs = 3_000L,
            throughputRatio = 2.0f,
            isTranscoding = true,
            transcodeSpeedRatio = 0.72,
            droppedFrameRatio = 0.0f,
            isDecoderStuck = false,
        )
        assertEquals(StallCause.SERVER_TRANSCODE_BOTTLENECK, cause)
    }

    @Test
    fun `transcode bottleneck does not trigger for direct play`() {
        val cause = StallClassifier.classify(
            bufferedDurationMs = 200L,
            rebufferThresholdMs = 3_000L,
            throughputRatio = 2.0f,
            isTranscoding = false,
            transcodeSpeedRatio = 0.72,
            droppedFrameRatio = 0.0f,
            isDecoderStuck = false,
        )
        // Direct play with a short buffer but adequate throughput is a plain
        // underrun (normal priming), not starvation.
        assertEquals(StallCause.BUFFER_UNDERRUN, cause)
    }

    @Test
    fun `low throughput ratio classifies as NETWORK_STARVATION`() {
        val cause = StallClassifier.classify(
            bufferedDurationMs = 800L,
            rebufferThresholdMs = 3_000L,
            throughputRatio = 0.65f,
            isTranscoding = false,
            transcodeSpeedRatio = null,
            droppedFrameRatio = 0.0f,
            isDecoderStuck = false,
        )
        assertEquals(StallCause.NETWORK_STARVATION, cause)
    }

    @Test
    fun `critically low buffer with adequate throughput classifies as BUFFER_UNDERRUN`() {
        val cause = StallClassifier.classify(
            bufferedDurationMs = 300L,
            rebufferThresholdMs = 3_000L,
            throughputRatio = 1.0f,
            isTranscoding = false,
            transcodeSpeedRatio = null,
            droppedFrameRatio = 0.0f,
            isDecoderStuck = false,
        )
        assertEquals(StallCause.BUFFER_UNDERRUN, cause)
    }

    @Test
    fun `adequate throughput below rebuffer threshold classifies as BUFFER_UNDERRUN`() {
        val cause = StallClassifier.classify(
            bufferedDurationMs = 1_500L,
            rebufferThresholdMs = 3_000L,
            throughputRatio = 1.2f,
            isTranscoding = false,
            transcodeSpeedRatio = null,
            droppedFrameRatio = 0.0f,
            isDecoderStuck = false,
        )
        assertEquals(StallCause.BUFFER_UNDERRUN, cause)
    }

    @Test
    fun `healthy buffer above threshold classifies as UNKNOWN`() {
        val cause = StallClassifier.classify(
            bufferedDurationMs = 4_000L,
            rebufferThresholdMs = 3_000L,
            throughputRatio = 1.5f,
            isTranscoding = false,
            transcodeSpeedRatio = null,
            droppedFrameRatio = 0.0f,
            isDecoderStuck = false,
        )
        assertEquals(StallCause.UNKNOWN, cause)
    }
}
