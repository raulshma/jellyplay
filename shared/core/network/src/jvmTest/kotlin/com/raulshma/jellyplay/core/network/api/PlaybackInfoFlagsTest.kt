package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.PlaybackMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the pure [resolvePlaybackFlags] mapper. These pin the
 * `enable*` / `allow*` / bitrate / device-profile flag table that the
 * Jellyfin `PlaybackInfo` request is built from, for both the VOD
 * ([PlaybackMode]) and live ([LiveStreamOption]) paths.
 */
class PlaybackInfoFlagsTest {

    private val bitrate = 8_000_000L

    // ----- Live TV (LiveStreamOption) -----

    @Test
    fun `live AUTO enables all play methods and forwards bitrate`() {
        val flags = resolvePlaybackFlags(
            PlaybackMode.AUTO,
            LiveStreamOption.AUTO,
            bitrate,
        )
        assertTrue(flags.enableDirectPlay)
        assertTrue(flags.enableDirectStream)
        assertTrue(flags.enableTranscoding)
        assertTrue(flags.allowStreamCopy)
        assertEquals(bitrate, flags.sendBitrate)
        assertFalse(flags.useDirectPlayAllProfile)
    }

    @Test
    fun `live DIRECT_STREAM disables direct play and transcoding`() {
        // static=true breaks non-seekable live tuners → direct play off.
        // Transcoding off so a server that can't direct-stream fails loudly
        // instead of silently transcoding.
        val flags = resolvePlaybackFlags(
            PlaybackMode.AUTO,
            LiveStreamOption.DIRECT_STREAM,
            bitrate,
        )
        assertFalse(flags.enableDirectPlay)
        assertTrue(flags.enableDirectStream)
        assertFalse(flags.enableTranscoding)
        assertTrue(flags.allowStreamCopy)
        assertNull(flags.sendBitrate)
        assertFalse(flags.useDirectPlayAllProfile)
    }

    @Test
    fun `live TRANSCODE disables direct play and direct stream`() {
        val flags = resolvePlaybackFlags(
            PlaybackMode.AUTO,
            LiveStreamOption.TRANSCODE,
            bitrate,
        )
        assertFalse(flags.enableDirectPlay)
        assertFalse(flags.enableDirectStream)
        assertTrue(flags.enableTranscoding)
        assertFalse(flags.allowStreamCopy)
        assertEquals(bitrate, flags.sendBitrate)
        assertFalse(flags.useDirectPlayAllProfile)
    }

    @Test
    fun `live option takes precedence over VOD mode`() {
        // mode = FORCE_TRANSCODE would disable everything, but the live option
        // wins and enables direct play/stream.
        val flags = resolvePlaybackFlags(
            PlaybackMode.FORCE_TRANSCODE,
            LiveStreamOption.AUTO,
            bitrate,
        )
        assertTrue(flags.enableDirectPlay)
        assertTrue(flags.enableDirectStream)
    }

    // ----- VOD (PlaybackMode, liveStreamOption = null) -----

    @Test
    fun `VOD AUTO enables all play methods`() {
        val flags = resolvePlaybackFlags(PlaybackMode.AUTO, null, bitrate)
        assertTrue(flags.enableDirectPlay)
        assertTrue(flags.enableDirectStream)
        assertTrue(flags.enableTranscoding)
        assertTrue(flags.allowStreamCopy)
        assertEquals(bitrate, flags.sendBitrate)
        assertFalse(flags.useDirectPlayAllProfile)
    }

    @Test
    fun `VOD FORCE_DIRECT_PLAY disables stream copy and transcoding and uses direct-play-all profile`() {
        val flags = resolvePlaybackFlags(PlaybackMode.FORCE_DIRECT_PLAY, null, bitrate)
        assertTrue(flags.enableDirectPlay)
        assertFalse(flags.enableDirectStream)
        assertFalse(flags.enableTranscoding)
        assertFalse(flags.allowStreamCopy)
        // No cap — the file is served verbatim.
        assertNull(flags.sendBitrate)
        assertTrue(flags.useDirectPlayAllProfile)
    }

    @Test
    fun `VOD FORCE_TRANSCODE disables direct play and direct stream but keeps bitrate cap`() {
        val flags = resolvePlaybackFlags(PlaybackMode.FORCE_TRANSCODE, null, bitrate)
        assertFalse(flags.enableDirectPlay)
        assertFalse(flags.enableDirectStream)
        assertTrue(flags.enableTranscoding)
        assertFalse(flags.allowStreamCopy)
        // So a forced transcode still targets the chosen resolution.
        assertEquals(bitrate, flags.sendBitrate)
        assertFalse(flags.useDirectPlayAllProfile)
    }

    @Test
    fun `null bitrate is preserved through AUTO paths`() {
        val liveAuto = resolvePlaybackFlags(PlaybackMode.AUTO, LiveStreamOption.AUTO, null)
        assertNull(liveAuto.sendBitrate)
        val vodAuto = resolvePlaybackFlags(PlaybackMode.AUTO, null, null)
        assertNull(vodAuto.sendBitrate)
    }
}
