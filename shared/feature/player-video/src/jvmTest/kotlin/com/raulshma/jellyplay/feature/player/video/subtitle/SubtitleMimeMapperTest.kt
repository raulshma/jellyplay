package com.raulshma.jellyplay.feature.player.video.subtitle

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class SubtitleMimeMapperTest {

    @Test
    fun mapCodecToMime_mapsKnownCodecsAndLabels() {
        assertEquals("application/x-subrip", SubtitleMimeMapper.mapCodecToMime("srt"))
        assertEquals("application/x-subrip", SubtitleMimeMapper.mapCodecToMime("SUBRIP"))
        assertEquals("text/x-ssa", SubtitleMimeMapper.mapCodecToMime("ass"))
        assertEquals("text/x-ssa", SubtitleMimeMapper.mapCodecToMime("ssa"))
        assertEquals("text/vtt", SubtitleMimeMapper.mapCodecToMime("vtt"))
        assertEquals("text/vtt", SubtitleMimeMapper.mapCodecToMime("webvtt"))
        assertEquals("application/ttml+xml", SubtitleMimeMapper.mapCodecToMime("ttml"))
        assertEquals("application/ttml+xml", SubtitleMimeMapper.mapCodecToMime("dfxp"))
        assertEquals("application/pgs", SubtitleMimeMapper.mapCodecToMime("pgs"))
        // Server-reported variants of the same bitmap format: without these,
        // ExoPlayer drops the sidecar silently at mime resolution.
        assertEquals("application/pgs", SubtitleMimeMapper.mapCodecToMime("pgssub"))
        assertEquals("application/pgs", SubtitleMimeMapper.mapCodecToMime("hdmv_pgs_subtitle"))
        assertEquals("application/x-quicktime-tx3g", SubtitleMimeMapper.mapCodecToMime("mov_text"))
    }

    @Test
    fun mapCodecToMime_returnsNullForNullOrUnknownInput() {
        assertNull(SubtitleMimeMapper.mapCodecToMime(null))
        assertNull(SubtitleMimeMapper.mapCodecToMime("unknown_format"))
    }
}
