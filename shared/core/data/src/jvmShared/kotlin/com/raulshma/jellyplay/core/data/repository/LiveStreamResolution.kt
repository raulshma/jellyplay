package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.PlayMethod

/**
 * The live-stream fallback ladder's ONE resolution decision, owned by
 * [PlaybackRepositoryImpl.resolvePlayable] (moved verbatim from the live
 * player's former `LiveStreamResolution` module when the
 * fetchPlaybackInfo → first-source → liveStreamId fallback was absorbed
 * into the repository): when the server-verdict arm offers nothing for a
 * live request, WHICH source capability arm builds the stream URL and what
 * play method the built URL carries — written once, pinned by
 * LiveStreamResolutionTest, with the repository keeping only the fetch and
 * the `getStreamUrl` call (injected as the [resolveLiveStreamResolution]'s
 * builder lambda so this stays pure and jvmTest-pinnable).
 *
 * The ladder, verbatim:
 *  1. direct stream/play flags → [LiveStreamResolution.Via.DIRECT_STREAM];
 *  2. transcode flag + non-blank `transcodeUrl` →
 *     [LiveStreamResolution.Via.TRANSCODE_URL];
 *  3. liveStreamId fallback → [LiveStreamResolution.Via.LIVE_STREAM_ID]:
 *     live tuner sessions opened via autoOpenLiveStream=true can be read by
 *     hitting `/Videos/{id}/stream?LiveStreamId=…` even when the server's
 *     playability decision returned all-false flags (observed with some
 *     M3U/HLS-only tuners under FORCE_DIRECT_PLAY or when the device profile
 *     doesn't claim HLS support). The tuner is already open server-side, so
 *     the URL is valid;
 *  4. none of the above → [LiveStreamResolution.NoPlayableMethod] (the
 *     degenerate no-info case: no capability flags and no liveStreamId).
 *
 * The play-method fold lives here too: the fallback's URL is always a direct
 * `/Videos/{id}/stream` URL (via `getStreamUrl`), never a transcoding
 * master.m3u8 — even when the server's flags say transcoding is the only
 * option — so [LiveStreamResolution.Resolved.playMethod] reflects the URL
 * the ladder built, not the server's verdict, keeping the live player's
 * onPlayerError -> transcode fallback eligible.
 */
internal sealed interface LiveStreamResolution {

    /** Which ladder arm fired — the diagnostics the repository logs. */
    enum class Via {
        DIRECT_STREAM,
        TRANSCODE_URL,
        LIVE_STREAM_ID,
    }

    /**
     * A playable URL: the [url] the [buildStreamUrl] lambda produced for the
     * arm that fired ([via]), the play method that URL carries, and the
     * server-issued [liveStreamId] appended to it (null when the source
     * carried none).
     */
    data class Resolved(
        val url: String,
        val playMethod: PlayMethod,
        val liveStreamId: String?,
        val via: Via,
    ) : LiveStreamResolution

    /** No capability arm matched: no direct/transcode flags, no liveStreamId. */
    data object NoPlayableMethod : LiveStreamResolution
}

/**
 * Runs the capability ladder over [source] and, when an arm fires, builds
 * the URL through [buildStreamUrl] (the repository's `getStreamUrl` call —
 * injected so this stays pure and jvmTest-pinnable). Returns
 * [LiveStreamResolution.NoPlayableMethod] for the degenerate source; never
 * calls [buildStreamUrl] in that case.
 */
internal fun resolveLiveStreamResolution(
    source: MediaSource,
    buildStreamUrl: (mediaSourceId: String, liveStreamId: String?) -> String,
): LiveStreamResolution {
    val liveId = source.liveStreamId
    val via = when {
        source.supportsDirectStream || source.supportsDirectPlay ->
            LiveStreamResolution.Via.DIRECT_STREAM
        source.supportsTranscoding && !source.transcodeUrl.isNullOrBlank() ->
            LiveStreamResolution.Via.TRANSCODE_URL
        !liveId.isNullOrBlank() -> LiveStreamResolution.Via.LIVE_STREAM_ID
        else -> return LiveStreamResolution.NoPlayableMethod
    }
    return LiveStreamResolution.Resolved(
        url = buildStreamUrl(source.id, liveId),
        playMethod = if (source.supportsDirectPlay) {
            PlayMethod.DIRECT_PLAY
        } else {
            PlayMethod.DIRECT_STREAM
        },
        liveStreamId = liveId,
        via = via,
    )
}
