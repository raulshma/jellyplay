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

/**
 * Parsed response from the Jellyfin `PlaybackInfo` endpoint. The
 * [mediaSources] carry the server's refreshed playability decision
 * (supportsDirectPlay / supportsDirectStream / supportsTranscoding and a
 * ready-to-use [MediaSource.transcodeUrl]) for the requested
 * [PlaybackMode], and [playSessionId] is the server-issued id that must
 * accompany subsequent progress reports so transcode sessions are tracked
 * correctly.
 */
@Immutable
@Serializable
data class PlaybackInfoResult(
    val playSessionId: String?,
    val mediaSources: List<MediaSource>,
)

@Immutable
@Serializable
enum class PlayMethod {
    DIRECT_PLAY,
    DIRECT_STREAM,
    TRANSCODE;

    fun displayName(): String = when (this) {
        DIRECT_PLAY -> "Direct Play"
        DIRECT_STREAM -> "Direct Stream"
        TRANSCODE -> "Transcode"
    }
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
