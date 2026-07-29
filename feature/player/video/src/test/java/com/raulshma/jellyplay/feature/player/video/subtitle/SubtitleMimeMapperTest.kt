package com.raulshma.jellyplay.feature.player.video.subtitle

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleMimeMapperTest {

    @Test
    fun mapCodecToMime_mapsKnownCodecsAndLabels() {
        assertEquals(MimeTypes.APPLICATION_SUBRIP, SubtitleMimeMapper.mapCodecToMime("srt"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, SubtitleMimeMapper.mapCodecToMime("SUBRIP"))
        assertEquals(MimeTypes.TEXT_SSA, SubtitleMimeMapper.mapCodecToMime("ass"))
        assertEquals(MimeTypes.TEXT_SSA, SubtitleMimeMapper.mapCodecToMime("ssa"))
        assertEquals(MimeTypes.TEXT_VTT, SubtitleMimeMapper.mapCodecToMime("vtt"))
        assertEquals(MimeTypes.TEXT_VTT, SubtitleMimeMapper.mapCodecToMime("webvtt"))
        assertEquals(MimeTypes.APPLICATION_TTML, SubtitleMimeMapper.mapCodecToMime("ttml"))
        assertEquals(MimeTypes.APPLICATION_TTML, SubtitleMimeMapper.mapCodecToMime("dfxp"))
        assertEquals(MimeTypes.APPLICATION_PGS, SubtitleMimeMapper.mapCodecToMime("pgs"))
        assertEquals(MimeTypes.APPLICATION_TX3G, SubtitleMimeMapper.mapCodecToMime("mov_text"))
    }

    @Test
    fun mapCodecToMime_returnsNullForNullOrUnknownInput() {
        assertNull(SubtitleMimeMapper.mapCodecToMime(null))
        assertNull(SubtitleMimeMapper.mapCodecToMime("unknown_format"))
    }
}
