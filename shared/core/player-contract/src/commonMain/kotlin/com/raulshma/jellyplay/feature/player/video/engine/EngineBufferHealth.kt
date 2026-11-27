package com.raulshma.jellyplay.feature.player.video.engine

import androidx.compose.runtime.Immutable

/**
 * Granular buffer health snapshot emitted by [MediaEngine] and consumed by
 * diagnostic advisors and UI overlays.
 *
 * All durations are in milliseconds.
 */
@Immutable
data class EngineBufferHealth(
    val bufferedDurationMs: Long,
    val rebufferThresholdMs: Long,
    val targetBufferDurationMs: Long,
    val bufferedBytes: Long = -1L,
    val targetBufferBytes: Long = -1L,
    val bufferProgressFraction: Float = if (rebufferThresholdMs > 0) {
        (bufferedDurationMs.toFloat() / rebufferThresholdMs).coerceIn(0f, 1f)
    } else 1f,
)
