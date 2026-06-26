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
)
