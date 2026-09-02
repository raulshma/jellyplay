package com.raulshma.jellyplay.core.network.playback

import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.PlaybackMode

/**
 * Resolved `PlaybackInfoDto` flags for a single request. See
 * [resolveWasmPlaybackFlags].
 */
data class WasmPlaybackFlags(
    val enableDirectPlay: Boolean,
    val enableDirectStream: Boolean,
    val enableTranscoding: Boolean,
    val allowStreamCopy: Boolean,
    val sendBitrate: Long?,
)

/**
 * The wasm port of the jvmShared `resolvePlaybackFlags` (PlaybackInfoFlags.kt)
 * flag table — duplicated by design: the jvmShared original is not compiled
 * for wasmJs, and the table is the cross-platform contract for how
 * [PlaybackMode] / [LiveStreamOption] map onto the `enable*` / `allow*` /
 * bitrate request fields. Any change here belongs in the JVM twin too.
 *
 * VOD table ([mode], when [liveStreamOption] is null):
 * - [PlaybackMode.AUTO] — enable everything; server decides.
 * - [PlaybackMode.FORCE_DIRECT_PLAY] — disable stream copy + transcoding
 *   (the JVM impl also swaps in a "direct play all" device profile there;
 *   wasm v1 sends no profile at all — documented cut, see
 *   [PlaybackInfoRequestDtoWire]).
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
fun resolveWasmPlaybackFlags(
    mode: PlaybackMode,
    liveStreamOption: LiveStreamOption?,
    maxStreamingBitrateBits: Long?,
): WasmPlaybackFlags = when (liveStreamOption) {
    LiveStreamOption.AUTO -> WasmPlaybackFlags(
        enableDirectPlay = true,
        enableDirectStream = true,
        enableTranscoding = true,
        allowStreamCopy = true,
        sendBitrate = maxStreamingBitrateBits,
    )
    LiveStreamOption.DIRECT_STREAM -> WasmPlaybackFlags(
        enableDirectPlay = false,
        enableDirectStream = true,
        enableTranscoding = false,
        allowStreamCopy = true,
        sendBitrate = null,
    )
    LiveStreamOption.TRANSCODE -> WasmPlaybackFlags(
        enableDirectPlay = false,
        enableDirectStream = false,
        enableTranscoding = true,
        allowStreamCopy = false,
        sendBitrate = maxStreamingBitrateBits,
    )
    null -> when (mode) {
        PlaybackMode.AUTO -> WasmPlaybackFlags(
            enableDirectPlay = true,
            enableDirectStream = true,
            enableTranscoding = true,
            allowStreamCopy = true,
            sendBitrate = maxStreamingBitrateBits,
        )
        PlaybackMode.FORCE_DIRECT_PLAY -> WasmPlaybackFlags(
            enableDirectPlay = true,
            enableDirectStream = false,
            enableTranscoding = false,
            allowStreamCopy = false,
            // No cap — the file is served verbatim.
            sendBitrate = null,
        )
        PlaybackMode.FORCE_TRANSCODE -> WasmPlaybackFlags(
            enableDirectPlay = false,
            enableDirectStream = false,
            enableTranscoding = true,
            allowStreamCopy = false,
            // So a forced transcode still targets the chosen resolution.
            sendBitrate = maxStreamingBitrateBits,
        )
    }
}
