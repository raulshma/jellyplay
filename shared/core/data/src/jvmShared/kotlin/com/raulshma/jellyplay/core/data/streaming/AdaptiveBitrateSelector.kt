package com.raulshma.jellyplay.core.data.streaming

import com.raulshma.jellyplay.core.model.AudioBitrateTier
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.network.interceptor.BandwidthInterceptor
import kotlinx.coroutines.flow.StateFlow

class AdaptiveBitrateSelector(
    private val bandwidthMonitor: BandwidthMonitor,
    private val bandwidthInterceptor: BandwidthInterceptor,
) {
    val bandwidthKbps: StateFlow<Double> = bandwidthInterceptor.estimatedBandwidthKbps

    fun selectTier(maxAllowed: AudioBitrateTier = AudioBitrateTier.LOSSLESS): AudioBitrateTier {
        val networkKbps = bandwidthInterceptor.estimatedBandwidthKbps.value
        val localKbps = bandwidthMonitor.estimatedBandwidthKbps.value
        val availableKbps = maxOf(networkKbps, localKbps)
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
