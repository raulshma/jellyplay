package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
enum class AudioNormalizationMode(val displayName: String) {
    NONE("None"),
    DYNAMIC("Dynamic Compression"),
    TRACK("Track Normalization"),
    ALBUM("Album Normalization"),
}

@Immutable
@Serializable
enum class ChannelMixMode(val displayName: String) {
    AUTO("Auto"),
    STEREO_DOWNMIX("Stereo Downmix"),
    SURROUND_UPMIX("Surround Upmix"),
    MONO("Mono"),
}
