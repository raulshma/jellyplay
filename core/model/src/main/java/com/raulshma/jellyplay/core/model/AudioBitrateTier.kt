package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Audio bitrate tiers used by the adaptive bitrate selector. Each tier
 * corresponds to a target average bitrate in kbps; tracks with a higher
 * encoded bitrate will be transcoded down to this cap on the server.
 */
@Immutable
@Serializable
enum class AudioBitrateTier(val targetKbps: Int, val displayName: String) {
    LOW(96, "Low (96 kbps)"),
    MEDIUM(192, "Medium (192 kbps)"),
    HIGH(320, "High (320 kbps)"),
    LOSSLESS(1411, "Lossless (~1.4 Mbps)"),
    ;

    companion object {
        val DEFAULT: AudioBitrateTier = HIGH
    }
}
