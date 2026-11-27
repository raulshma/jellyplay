package com.raulshma.jellyplay.feature.player.video.engine

/**
 * The classified root cause of a playback stall or buffering freeze.
 */
enum class StallCause {
    UNKNOWN,
    NETWORK_STARVATION,          // Bandwidth < Stream Bitrate
    SERVER_TRANSCODE_BOTTLENECK, // Server transcode speed ratio < 1.0x
    DECODER_OVERLOAD,            // Excessive dropped frames or frozen surface
    BUFFER_UNDERRUN,             // Normal post-seek or initial priming
}

/**
 * Pure classification logic determining why playback entered a stall or
 * buffering state. Zero platform or Media3 dependencies.
 */
object StallClassifier {

    /**
     * Classifies a stall event based on live buffer health, throughput,
     * transcode metrics, and frame presentation.
     *
     * @param bufferedDurationMs current buffered duration ahead in milliseconds.
     * @param rebufferThresholdMs required buffer threshold to resume in milliseconds.
     * @param throughputRatio currentThroughputBps / activeBitrateBps.
     * @param isTranscoding whether the active stream is being transcoded on the server.
     * @param transcodeSpeedRatio transcodeFps / videoFps (null if not available).
     * @param droppedFrameRatio ratio of dropped frames over total frames in [0, 1].
     * @param isDecoderStuck whether position is frozen while playing with zero presentations.
     */
    fun classify(
        bufferedDurationMs: Long,
        rebufferThresholdMs: Long,
        throughputRatio: Float,
        isTranscoding: Boolean,
        transcodeSpeedRatio: Double?,
        droppedFrameRatio: Float,
        isDecoderStuck: Boolean,
    ): StallCause {
        if (isDecoderStuck || droppedFrameRatio > 0.15f) {
            return StallCause.DECODER_OVERLOAD
        }
        if (isTranscoding && transcodeSpeedRatio != null && transcodeSpeedRatio < 0.92) {
            return StallCause.SERVER_TRANSCODE_BOTTLENECK
        }
        // Starvation is a THROUGHPUT verdict (bandwidth below the stream's
        // needs). A short buffer with adequate supply is normal priming /
        // post-seed fill — the threshold comparison below classifies it as
        // BUFFER_UNDERRUN, not starvation.
        if (throughputRatio < 0.85f) {
            return StallCause.NETWORK_STARVATION
        }
        if (bufferedDurationMs < rebufferThresholdMs) {
            return StallCause.BUFFER_UNDERRUN
        }
        return StallCause.UNKNOWN
    }
}
