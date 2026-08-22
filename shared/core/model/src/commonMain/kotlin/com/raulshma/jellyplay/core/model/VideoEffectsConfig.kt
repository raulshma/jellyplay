package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Per-session (and optionally per-item persisted) video filter values.
 *
 * Ranges are chosen to mirror the underlying FFmpeg / mpv / VLC filter
 * parameters so the engine layer can apply them with minimal remapping:
 *
 * - brightness: -1..1, 0 = neutral
 * - contrast: 0.5..2, 1 = neutral
 * - saturation: 0..3, 1 = neutral
 * - sharpness: 0..1, 0 = off
 * - hue: 0..360 degrees, 0 = neutral (no rotation on the colour wheel)
 * - rotationDegrees: -180..180, 0 = no rotation
 * - redGain / greenGain / blueGain: 0..2, 1 = neutral
 * - gaussianBlur: 0..10, 0 = off
 */
@Immutable
@Serializable
data class VideoEffectsConfig(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val sharpness: Float = 0f,
    val hue: Float = 0f,
    val rotationDegrees: Float = 0f,
    val redGain: Float = 1f,
    val greenGain: Float = 1f,
    val blueGain: Float = 1f,
    val gaussianBlur: Float = 0f,
) {
    /** True when every field matches its neutral default. */
    val isNeutral: Boolean
        get() = brightness == 0f &&
            contrast == 1f &&
            saturation == 1f &&
            sharpness == 0f &&
            hue == 0f &&
            rotationDegrees == 0f &&
            redGain == 1f &&
            greenGain == 1f &&
            blueGain == 1f &&
            gaussianBlur == 0f
}
