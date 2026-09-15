package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.PlayMethod

/**
 * Which URL builder must produce the stream URL for a
 * [PlaybackMethodSelection] — the decision is split from the building so the
 * ladder stays pure: [PlaybackRepositoryImpl.resolvePlayback] maps each arm
 * onto exactly one choreography,
 *  - [LIVE_STREAM] / [STATIC_STREAM]: the client-constructed
 *    `/Videos/{id}/stream` URL (its `getStreamUrl`), the live one echoing the
 *    server-issued `MediaSource.liveStreamId`;
 *  - [TRANSCODE]: the server-baked `MediaSource.transcodeUrl`, resolved
 *    against the session's server/token (`resolveTranscodeUrl`).
 */
internal enum class PlaybackUrlSource {
    /** Client-built stream URL echoing the liveStreamId so the tuner opens a live session. */
    LIVE_STREAM,

    /** Client-built static stream URL — the plain non-live direct-play form. */
    STATIC_STREAM,

    /** Server-baked transcode path resolved against the session's server/token. */
    TRANSCODE,
}

/**
 * The play-method ladder's outcome: which [PlayMethod] the session reports
 * and which [PlaybackUrlSource] must build its stream URL.
 */
internal data class PlaybackMethodSelection(
    val playMethod: PlayMethod,
    val urlSource: PlaybackUrlSource,
)

/**
 * Pure play-method selection ladder of
 * [PlaybackRepositoryImpl.resolvePlayback], split out so every branch is
 * directly pinnable: given the server's refreshed playability decision for
 * one [MediaSource], decide the transport — live / direct-play /
 * direct-stream / transcode — without touching the network. Behaviour moved
 * verbatim from the facade's former inline when-chain; the repository keeps
 * only the choreography (fetching PlaybackInfo, building the URL the
 * selection points at, the blank-URL guard, assembling ResolvedPlayback).
 *
 * Ladder, in precedence order:
 *  - **Live** (`liveStreamId` present or `requiresOpening`): live sources
 *    never direct play — the stream URL must echo `LiveStreamId` back so the
 *    tuner opens a live session, and static direct-play
 *    (`/Videos/{id}/stream?static=true`) does not work for live sources, so
 *    they route through direct stream. The client-built stream URL is used
 *    whenever direct stream OR direct play is offered; otherwise the
 *    server-baked transcode path. The reported method is decided
 *    independently of that URL branch: direct stream when offered, else
 *    transcode when offered, else direct stream (the historical fallback — a
 *    live source offering nothing still claims DIRECT_STREAM while its URL
 *    comes from the transcode path).
 *  - **Direct play**: the client-built static stream URL (no live id).
 *  - **Direct stream**: the SDK surfaces no DirectStreamUrl, so the direct
 *    stream URL *is* the server-baked transcode path.
 *  - **Transcode**: the server-baked transcode path.
 *  - `null`: the server offered no playable method for this source/mode.
 */
internal fun selectPlaybackMethod(source: MediaSource): PlaybackMethodSelection? {
    // Live TV channels carry a server-issued liveStreamId; the stream URL
    // must echo it back as `LiveStreamId` so the tuner opens a live session.
    val isLiveStream = source.liveStreamId != null || source.requiresOpening
    return when {
        isLiveStream -> {
            val urlSource = if (source.supportsDirectStream || source.supportsDirectPlay) {
                PlaybackUrlSource.LIVE_STREAM
            } else {
                PlaybackUrlSource.TRANSCODE
            }
            val playMethod = if (source.supportsDirectStream) PlayMethod.DIRECT_STREAM
                else if (source.supportsTranscoding) PlayMethod.TRANSCODE
                else PlayMethod.DIRECT_STREAM
            PlaybackMethodSelection(playMethod, urlSource)
        }
        source.supportsDirectPlay ->
            PlaybackMethodSelection(PlayMethod.DIRECT_PLAY, PlaybackUrlSource.STATIC_STREAM)
        source.supportsDirectStream ->
            PlaybackMethodSelection(PlayMethod.DIRECT_STREAM, PlaybackUrlSource.TRANSCODE)
        source.supportsTranscoding ->
            PlaybackMethodSelection(PlayMethod.TRANSCODE, PlaybackUrlSource.TRANSCODE)
        // No playable method offered by the server for this source/mode.
        else -> null
    }
}
