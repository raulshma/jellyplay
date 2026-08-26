package com.raulshma.jellyplay.feature.player.live.engine

import androidx.compose.runtime.Immutable

/**
 * Tunable knobs for the live engine. Buffer values are intentionally tighter
 * than the VOD defaults so the player joins the live edge quickly.
 */
@Immutable
data class LiveEngineConfig(
    val authToken: String? = null,
    val minBufferMs: Int = 10_000,
    val maxBufferMs: Int = 30_000,
    val rebufferMs: Int = 5_000,
)

/**
 * Request handed to [LivePlayerEngine.load]. The URL is the resolved live
 * stream URL from
 * [com.raulshma.jellyplay.core.data.repository.PlaybackRepository.resolvePlayback]
 * — typically `/Videos/{id}/stream?LiveStreamId=…`.
 *
 * Auth is carried by [LiveEngineConfig.authToken] (consumed when the engine
 * builds its HTTP data source factory); the request itself only describes
 * *what* to play and how.
 */
@Immutable
data class LivePlaybackRequest(
    val url: String,
    val title: String,
    val playMethod: LivePlayMethod = LivePlayMethod.DIRECT_STREAM,
    /**
     * The media-source container reported by the server (e.g. `"ts"`, `"hls"`),
     * used to pick the ExoPlayer MIME hint for a direct stream. `null` for a
     * transcode (the URL is always a Jellyfin HLS master playlist).
     */
    val container: String? = null,
)

enum class LivePlayMethod { DIRECT_PLAY, DIRECT_STREAM, TRANSCODE }
