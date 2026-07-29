package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.PlaybackMode

/**
 * Resolved `PlaybackInfoDto` flags for a single request. See
 * [resolvePlaybackFlags].
 */
data class PlaybackFlags(
    val enableDirectPlay: Boolean,
    val enableDirectStream: Boolean,
    val enableTranscoding: Boolean,
    val allowStreamCopy: Boolean,
    val sendBitrate: Long?,
    val useDirectPlayAllProfile: Boolean,
)

/**
 * Derives the `enable*` / `allow*` / bitrate / device-profile flags sent to
 * the Jellyfin `PlaybackInfo` endpoint. [liveStreamOption] takes precedence
 * over [mode] when non-null (live TV path); otherwise the VOD [PlaybackMode]
 * table is used.
 *
 * Extracted as a pure top-level function so the flag table is unit-testable
 * without standing up the Jellyfin SDK client.
 *
 * VOD table ([mode], when [liveStreamOption] is null):
 * - [PlaybackMode.AUTO] — enable everything; server decides.
 * - [PlaybackMode.FORCE_DIRECT_PLAY] — disable stream copy + transcoding,
 *   send the "direct play all" device profile so the server hands back a
 *   `?static=true` URL; no bitrate cap (file served verbatim).
 * - [PlaybackMode.FORCE_TRANSCODE] — disable direct play + direct stream;
 *   keep the bitrate cap so a forced transcode still targets the chosen
 *   resolution.
 *
 * Live table ([liveStreamOption]):
 * - [LiveStreamOption.AUTO] — enable everything; server decides.
 * - [LiveStreamOption.DIRECT_STREAM] — read the open tuner verbatim.
 *   `static=true` breaks non-seekable live streams, so direct play stays off;
 *   transcoding stays off so a server that can't direct-stream fails loudly
 *   instead of silently transcoding.
 * - [LiveStreamOption.TRANSCODE] — re-encode on the server (lower bandwidth).
 */
fun resolvePlaybackFlags(
    mode: PlaybackMode,
    liveStreamOption: LiveStreamOption?,
    maxStreamingBitrateBits: Long?,
): PlaybackFlags = when (liveStreamOption) {
    LiveStreamOption.AUTO -> PlaybackFlags(
        enableDirectPlay = true,
        enableDirectStream = true,
        enableTranscoding = true,
        allowStreamCopy = true,
        sendBitrate = maxStreamingBitrateBits,
        useDirectPlayAllProfile = false,
    )
    LiveStreamOption.DIRECT_STREAM -> PlaybackFlags(
        enableDirectPlay = false,
        enableDirectStream = true,
        enableTranscoding = false,
        allowStreamCopy = true,
        sendBitrate = null,
        useDirectPlayAllProfile = false,
    )
    LiveStreamOption.TRANSCODE -> PlaybackFlags(
        enableDirectPlay = false,
        enableDirectStream = false,
        enableTranscoding = true,
        allowStreamCopy = false,
        sendBitrate = maxStreamingBitrateBits,
        useDirectPlayAllProfile = false,
    )
    null -> when (mode) {
        PlaybackMode.AUTO -> PlaybackFlags(
            enableDirectPlay = true,
            enableDirectStream = true,
            enableTranscoding = true,
            allowStreamCopy = true,
            sendBitrate = maxStreamingBitrateBits,
            useDirectPlayAllProfile = false,
        )
        PlaybackMode.FORCE_DIRECT_PLAY -> PlaybackFlags(
            enableDirectPlay = true,
            enableDirectStream = false,
            enableTranscoding = false,
            allowStreamCopy = false,
            // No cap — the file is served verbatim.
            sendBitrate = null,
            useDirectPlayAllProfile = true,
        )
        PlaybackMode.FORCE_TRANSCODE -> PlaybackFlags(
            enableDirectPlay = false,
            enableDirectStream = false,
            enableTranscoding = true,
            allowStreamCopy = false,
            // So a forced transcode still targets the chosen resolution.
            sendBitrate = maxStreamingBitrateBits,
            useDirectPlayAllProfile = false,
        )
    }
}
