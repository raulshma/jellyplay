package com.raulshma.jellyplay.core.data.streaming

import com.raulshma.jellyplay.core.model.AudioBitrateTier
import com.raulshma.jellyplay.core.model.StreamingQuality
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks an audio bitrate tier based on measured bandwidth and the user's
 * [StreamingQuality] selection. When the user chooses [StreamingQuality.AUTO],
 * the selector picks the best tier that comfortably fits the available
 * bandwidth, leaving a 1.5x headroom for stability.
 *
 * Bitrate tiers are conservative for audio since tracks typically range from
 * 96 kbps (low) to 320 kbps (high); lossless tracks require ~1 Mbps.
 */
@Singleton
class AdaptiveBitrateSelector @Inject constructor(
    private val bandwidthMonitor: BandwidthMonitor,
) {
    val bandwidthKbps: StateFlow<Double> = bandwidthMonitor.estimatedBandwidthKbps

    fun selectTier(maxAllowed: AudioBitrateTier = AudioBitrateTier.LOSSLESS): AudioBitrateTier {
        val availableKbps = bandwidthMonitor.estimatedBandwidthKbps.value
        if (availableKbps <= 0.0) {
            return AudioBitrateTier.LOW
        }
        val targetKbps = availableKbps / 1.5
        val pick = when {
            targetKbps >= AudioBitrateTier.LOSSLESS.targetKbps -> AudioBitrateTier.LOSSLESS
            targetKbps >= AudioBitrateTier.HIGH.targetKbps -> AudioBitrateTier.HIGH
            targetKbps >= AudioBitrateTier.MEDIUM.targetKbps -> AudioBitrateTier.MEDIUM
            else -> AudioBitrateTier.LOW
        }
        return minOf(pick, maxAllowed)
    }

    fun resolveBitrate(
        userChoice: StreamingQuality,
        maxAllowed: AudioBitrateTier = AudioBitrateTier.LOSSLESS,
    ): AudioBitrateTier {
        return when (userChoice) {
            StreamingQuality.AUTO -> selectTier(maxAllowed)
            StreamingQuality.LOW_360P -> AudioBitrateTier.LOW
            StreamingQuality.SD_480P -> AudioBitrateTier.LOW
            StreamingQuality.HD_720P -> AudioBitrateTier.MEDIUM
            StreamingQuality.FHD_1080P -> AudioBitrateTier.HIGH
            StreamingQuality.UHD_4K -> AudioBitrateTier.LOSSLESS
        }.coerceAtMost(maxAllowed)
    }
}
