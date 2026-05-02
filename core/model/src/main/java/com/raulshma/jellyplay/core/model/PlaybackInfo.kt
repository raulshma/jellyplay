package com.raulshma.jellyplay.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackInfo(
    val itemId: String,
    val mediaSourceId: String,
    val playMethod: PlayMethod,
    val startPositionTicks: Long = 0,
    val liveStreamId: String? = null,
    val playSessionId: String? = null,
)

@Serializable
enum class PlayMethod {
    DIRECT_PLAY,
    DIRECT_STREAM,
    TRANSCODE,
}

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

@Serializable
data class PlaybackStartInfo(
    val itemId: String,
    val sessionId: String,
    val mediaSourceId: String? = null,
    val playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
)
