package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Network policy governing proactive audio-cache prefetching. Passive
 * cache-on-play (side-caching of bytes as ExoPlayer reads them) is always
 * allowed regardless of this setting — only ahead-of-playhead warming is gated.
 */
@Immutable
@Serializable
enum class AudioCacheNetworkPolicy(val displayName: String) {
    OFF("Off — cache on play only"),
    WIFI_ONLY("Wi-Fi only"),
    ANY_NETWORK("Any network (subject to monthly cap)"),
    ;

    companion object {
        val DEFAULT: AudioCacheNetworkPolicy = WIFI_ONLY
    }
}
