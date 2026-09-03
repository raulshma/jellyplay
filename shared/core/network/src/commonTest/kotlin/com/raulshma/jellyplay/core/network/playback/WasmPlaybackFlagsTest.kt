package com.raulshma.jellyplay.core.network.playback

import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.PlaybackMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Drift-guard for [resolveWasmPlaybackFlags] — the documented wasm duplicate
 * of jvmShared's [com.raulshma.jellyplay.core.network.api.resolvePlaybackFlags]
 * flag table (PlaybackInfoFlags.kt). These tests mirror the JVM
 * PlaybackInfoFlagsTest case-for-case across the VOD ([PlaybackMode]) × live
 * ([LiveStreamOption]) matrix and pin the two deliberate divergences:
 *
 *  1. No `useDirectPlayAllProfile` field — the wasm type carries only the five
 *     request flags (the JVM FORCE_DIRECT_PLAY profile swap is a documented
 *     cut; wasm sends no device profile at all).
 *  2. Every other cell must stay byte-identical to the JVM table — any change
 *     here belongs in the JVM twin too.
 */
class WasmPlaybackFlagsTest {

    private val bitrate = 8_000_000L

    // ----- Live TV (LiveStreamOption) -----

    @Test
    fun `live AUTO enables all play methods and forwards bitrate`() {
        val flags = resolveWasmPlaybackFlags(
            PlaybackMode.AUTO,
            LiveStreamOption.AUTO,
            bitrate,
        )
        assertEquals(
            WasmPlaybackFlags(
                enableDirectPlay = true,
                enableDirectStream = true,
                enableTranscoding = true,
                allowStreamCopy = true,
                sendBitrate = bitrate,
            ),
            flags,
        )
    }

    @Test
    fun `live DIRECT_STREAM disables direct play and transcoding`() {
        // static=true breaks non-seekable live tuners → direct play off.
        // Transcoding off so a server that can't direct-stream fails loudly
        // instead of silently transcoding.
        val flags = resolveWasmPlaybackFlags(
            PlaybackMode.AUTO,
            LiveStreamOption.DIRECT_STREAM,
            bitrate,
        )
        assertEquals(
            WasmPlaybackFlags(
                enableDirectPlay = false,
                enableDirectStream = true,
                enableTranscoding = false,
                allowStreamCopy = true,
                sendBitrate = null,
            ),
            flags,
        )
    }

    @Test
    fun `live TRANSCODE disables direct play and direct stream`() {
        val flags = resolveWasmPlaybackFlags(
            PlaybackMode.AUTO,
            LiveStreamOption.TRANSCODE,
            bitrate,
        )
        assertEquals(
            WasmPlaybackFlags(
                enableDirectPlay = false,
                enableDirectStream = false,
                enableTranscoding = true,
                allowStreamCopy = false,
                sendBitrate = bitrate,
            ),
            flags,
        )
    }

    @Test
    fun `live option takes precedence over VOD mode`() {
        // mode = FORCE_TRANSCODE would disable everything, but the live option
        // wins and enables direct play/stream.
        val flags = resolveWasmPlaybackFlags(
            PlaybackMode.FORCE_TRANSCODE,
            LiveStreamOption.AUTO,
            bitrate,
        )
        assertEquals(true, flags.enableDirectPlay)
        assertEquals(true, flags.enableDirectStream)
    }

    // ----- VOD (PlaybackMode, liveStreamOption = null) -----

    @Test
    fun `VOD AUTO enables all play methods`() {
        val flags = resolveWasmPlaybackFlags(PlaybackMode.AUTO, null, bitrate)
        assertEquals(
            WasmPlaybackFlags(
                enableDirectPlay = true,
                enableDirectStream = true,
                enableTranscoding = true,
                allowStreamCopy = true,
                sendBitrate = bitrate,
            ),
            flags,
        )
    }

    @Test
    fun `VOD FORCE_DIRECT_PLAY disables stream copy and transcoding`() {
        // JVM twin also swaps in the "direct play all" device profile and
        // carries useDirectPlayAllProfile=true — the wasm table documents that
        // cut by simply having no such field.
        val flags = resolveWasmPlaybackFlags(PlaybackMode.FORCE_DIRECT_PLAY, null, bitrate)
        assertEquals(
            WasmPlaybackFlags(
                enableDirectPlay = true,
                enableDirectStream = false,
                enableTranscoding = false,
                allowStreamCopy = false,
                sendBitrate = null,
            ),
            flags,
        )
    }

    @Test
    fun `VOD FORCE_TRANSCODE disables direct play and direct stream but keeps bitrate cap`() {
        val flags = resolveWasmPlaybackFlags(PlaybackMode.FORCE_TRANSCODE, null, bitrate)
        assertEquals(
            WasmPlaybackFlags(
                enableDirectPlay = false,
                enableDirectStream = false,
                enableTranscoding = true,
                allowStreamCopy = false,
                sendBitrate = bitrate,
            ),
            flags,
        )
    }

    @Test
    fun `null bitrate is preserved through AUTO paths`() {
        val liveAuto = resolveWasmPlaybackFlags(PlaybackMode.AUTO, LiveStreamOption.AUTO, null)
        assertNull(liveAuto.sendBitrate)
        val vodAuto = resolveWasmPlaybackFlags(PlaybackMode.AUTO, null, null)
        assertNull(vodAuto.sendBitrate)
    }

    // ----- Full VOD × live matrix (drift guard) -----

    @Test
    fun `every VOD mode resolves to the same flags under a given live option`() {
        // liveStreamOption non-null wins for all three modes: the flags for
        // (mode, live) must equal the flags for (AUTO, live) — this is the
        // precedence table both platforms share.
        val modes = listOf(
            PlaybackMode.AUTO,
            PlaybackMode.FORCE_DIRECT_PLAY,
            PlaybackMode.FORCE_TRANSCODE,
        )
        val liveOptions = listOf(
            LiveStreamOption.AUTO,
            LiveStreamOption.DIRECT_STREAM,
            LiveStreamOption.TRANSCODE,
        )
        for (live in liveOptions) {
            val baseline = resolveWasmPlaybackFlags(PlaybackMode.AUTO, live, bitrate)
            for (mode in modes) {
                assertEquals(
                    baseline,
                    resolveWasmPlaybackFlags(mode, live, bitrate),
                    "mode $mode must not alter the flags under live $live",
                )
            }
        }
    }
}
