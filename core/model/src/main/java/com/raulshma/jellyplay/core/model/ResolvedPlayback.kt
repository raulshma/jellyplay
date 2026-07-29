package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable

/**
 * The outcome of resolving a media source against the Jellyfin
 * [PlaybackInfo](https://api.jellyfin.org/#tag/PlaybackInfo) endpoint.
 *
 * Holds everything the player engine + progress reporter need to play a
 * single [MediaSource]: the playable [streamUrl], the negotiated
 * [playMethod] (Direct Play / Direct Stream / Transcode), and the server
 * [playSessionId] used to associate progress reports with the (possibly
 * transcoded) stream so the server can reap idle transcode jobs.
 */
@Immutable
data class ResolvedPlayback(
    val mediaSourceId: String,
    val streamUrl: String,
    val playMethod: PlayMethod,
    val playSessionId: String?,
    val maxStreamingBitrate: Long?,
    /**
     * The media-source container as reported by the server (e.g. `"ts"`,
     * `"hls"`, `"mkv"`). The live engine uses it to pick the right ExoPlayer
     * MIME hint: an `hls`-container live source is a real HLS playlist
     * (needs `APPLICATION_M3U8`), while a `ts` source is raw MPEG-TS over
     * HTTP (needs `VIDEO_MP2T`). `null` for VOD (the VOD engine infers MIME
     * from the URL).
     */
    val container: String? = null,
)
