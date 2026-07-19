package com.raulshma.jellyplay.feature.player.live.engine

import androidx.compose.runtime.Immutable
import androidx.media3.common.MimeTypes

/**
 * Tunable knobs for the live engine. Buffer values are intentionally tighter
 * than the VOD defaults so the player joins the live edge quickly.
 */
@Immutable
data class LiveEngineConfig(
    val serverUrl: String? = null,
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
 * [mimeType] defaults to [MimeTypes.APPLICATION_M3U8] because Jellyfin serves
 * live channels as HLS master playlists; the previous VOD path set
 * `VIDEO_MP2T`, which selected the wrong MediaSource and never played.
 */
@Immutable
data class LivePlaybackRequest(
    val url: String,
    val title: String,
    val mimeType: String = MimeTypes.APPLICATION_M3U8,
    val headers: Map<String, String> = emptyMap(),
    val playMethod: LivePlayMethod = LivePlayMethod.DIRECT_STREAM,
    /** When true, the next error triggers a transcode fallback resolve. */
    val allowTranscodeFallback: Boolean = true,
)

enum class LivePlayMethod { DIRECT_PLAY, DIRECT_STREAM, TRANSCODE }
