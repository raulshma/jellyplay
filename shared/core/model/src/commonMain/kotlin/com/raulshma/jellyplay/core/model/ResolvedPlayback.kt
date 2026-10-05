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

/**
 * Everything the repository's playback-resolution seam needs to resolve a
 * playable stream for one item — the single request shape behind
 * `PlaybackRepository.resolvePlayable` (the former identical 9-parameter
 * `fetchPlaybackInfo`/`resolvePlayback` lists plus the static-fallback
 * inputs the two players each carried at their call sites).
 *
 * [staticFallbackLiveStreamId] feeds ONLY the static-fallback arm (the VOD
 * players' former `?: getStreamUrl(..., source?.liveStreamId)` fold); the
 * live ladder reads the server-issued `MediaSource.liveStreamId` from the
 * `PlaybackInfo` response instead.
 *
 * [forceLiveStreamFallback] skips the server-verdict arm and runs the live
 * ladder (fetchPlaybackInfo → first source → liveStreamId URL) directly.
 * It exists for the live player's DIRECT_STREAM probe-override: the caller
 * owns the policy (a transcode verdict under a Direct Stream request is
 * untrusted), the repository only owns the choreography.
 */
@Immutable
data class PlaybackResolveRequest(
    val itemId: String,
    val mediaSourceId: String,
    val startTimeTicks: Long = 0L,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val maxStreamingBitrateBits: Long? = null,
    val mode: PlaybackMode,
    val playerType: PlayerType,
    val liveStreamOption: LiveStreamOption? = null,
    val staticFallbackLiveStreamId: String? = null,
    val forceLiveStreamFallback: Boolean = false,
)

/**
 * The outcome of the repository's one playback-resolution ladder
 * (`PlaybackRepository.resolvePlayable`):
 *
 *  - [Resolved] — the server `PlaybackInfo` verdict (Direct Play / Direct
 *    Stream / Transcode) or the live fallback ladder produced a playable
 *    [ResolvedPlayback];
 *  - [StaticFallback] — the server offered nothing for a non-live request:
 *    the historical static direct URL with the DIRECT_PLAY default and no
 *    play session (the VOD players' `?: getStreamUrl(...)` fold);
 *  - [Unplayable] — nothing playable emerged (live requests only: a
 *    non-live request always degrades to [StaticFallback]).
 */
@Immutable
sealed interface PlaybackResolution {
    data class Resolved(val playback: ResolvedPlayback) : PlaybackResolution
    data class StaticFallback(val streamUrl: String) : PlaybackResolution
    data object Unplayable : PlaybackResolution
}
