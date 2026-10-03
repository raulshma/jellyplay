package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The audio-player preference enums: the equalizer state and the shared
 * effect-strength vocabulary (bass boost, virtualizer, dialogue/night mode).
 */

@Immutable
@Serializable
data class EqualizerSettings(
    val bandLevels: List<Int> = List(10) { 0 },
) {
    companion object {
        val BAND_FREQUENCIES = listOf(60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000)
    }
}

@Immutable
@Serializable
enum class EffectStrength(val displayName: String) {
    NONE("Off"),
    LOW("Low"),
    MODERATE("Moderate"),
    HIGH("High"),
}
