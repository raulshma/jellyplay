package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class PlaybackInfo(
    val itemId: String,
    val mediaSourceId: String,
    val playMethod: PlayMethod,
    val startPositionTicks: Long = 0,
    val liveStreamId: String? = null,
    val playSessionId: String? = null,
)

@Immutable
@Serializable
enum class PlayMethod {
    DIRECT_PLAY,
    DIRECT_STREAM,
    TRANSCODE,
}

@Immutable
@Serializable
data class PlaybackProgress(
    val itemId: String,
    val sessionId: String,
    val positionTicks: Long,
    val isPaused: Boolean = false,
    val isMuted: Boolean = false,
    val volumeLevel: Int? = null,
    val playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
    val mediaSourceId: String? = null,
    val canSeek: Boolean = true,
)

@Immutable
@Serializable
data class PlaybackStartInfo(
    val itemId: String,
    val sessionId: String,
    val mediaSourceId: String? = null,
    val playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
    val startPositionTicks: Long? = null,
)
