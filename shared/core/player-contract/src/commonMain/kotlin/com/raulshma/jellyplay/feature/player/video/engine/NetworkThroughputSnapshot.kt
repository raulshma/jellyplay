package com.raulshma.jellyplay.feature.player.video.engine

import androidx.compose.runtime.Immutable

/**
 * Live network throughput and transfer rate telemetry.
 *
 * Rates are in bits per second (bps).
 */
@Immutable
data class NetworkThroughputSnapshot(
    val currentThroughputBps: Long,        // Instantaneous transfer rate of active chunks
    val estimatedBandwidthBps: Long,       // EWMA bandwidth estimation
    val activeBitrateBps: Int,             // Required stream bitrate (container/manifest)
    val throughputRatio: Float = if (activeBitrateBps > 0) {
        currentThroughputBps.toFloat() / activeBitrateBps
    } else 1f,
)
