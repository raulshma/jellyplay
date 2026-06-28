package com.raulshma.jellyplay.feature.player.video.subtitle

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleMimeMapperTest {

    @Test
    fun srt_mapsToSubRip() {
        assertEquals(MimeTypes.APPLICATION_SUBRIP, SubtitleMimeMapper.mapCodecToMime("srt"))
    }

    @Test
    fun subrip_mapsToSubRip() {
        assertEquals(MimeTypes.APPLICATION_SUBRIP, SubtitleMimeMapper.mapCodecToMime("subrip"))
    }

    @Test
    fun ass_mapsToSsa() {
        assertEquals(MimeTypes.TEXT_SSA, SubtitleMimeMapper.mapCodecToMime("ass"))
    }

    @Test
    fun ssa_mapsToSsa() {
        assertEquals(MimeTypes.TEXT_SSA, SubtitleMimeMapper.mapCodecToMime("ssa"))
    }

    @Test
    fun vtt_mapsToVtt() {
        assertEquals(MimeTypes.TEXT_VTT, SubtitleMimeMapper.mapCodecToMime("vtt"))
    }

    @Test
    fun webvtt_mapsToVtt() {
        assertEquals(MimeTypes.TEXT_VTT, SubtitleMimeMapper.mapCodecToMime("webvtt"))
    }

    @Test
    fun ttml_mapsToTtml() {
        assertEquals(MimeTypes.APPLICATION_TTML, SubtitleMimeMapper.mapCodecToMime("ttml"))
    }

    @Test
    fun dfxp_mapsToTtml() {
        assertEquals(MimeTypes.APPLICATION_TTML, SubtitleMimeMapper.mapCodecToMime("dfxp"))
    }

    @Test
    fun pgs_mapsToPgs() {
        assertEquals(MimeTypes.APPLICATION_PGS, SubtitleMimeMapper.mapCodecToMime("pgs"))
    }

    @Test
    fun mov_text_mapsToTx3g() {
        // mov_text is MPEG-4 Part-17 (tx3g / 3GPP Timed Text), NOT TTML.
        // Routing it through TTML mis-renders or drops MP4-embedded subs.
        assertEquals(MimeTypes.APPLICATION_TX3G, SubtitleMimeMapper.mapCodecToMime("mov_text"))
    }

    @Test
    fun nullCodec_mapsToNull() {
        assertNull(SubtitleMimeMapper.mapCodecToMime(null))
    }

    @Test
    fun unknownCodec_mapsToNull() {
        assertNull(SubtitleMimeMapper.mapCodecToMime("unknown_codec"))
    }

    @Test
    fun caseInsensitive() {
        assertEquals(MimeTypes.APPLICATION_SUBRIP, SubtitleMimeMapper.mapCodecToMime("SRT"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, SubtitleMimeMapper.mapCodecToMime("Srt"))
        assertEquals(MimeTypes.TEXT_SSA, SubtitleMimeMapper.mapCodecToMime("ASS"))
        assertEquals(MimeTypes.TEXT_VTT, SubtitleMimeMapper.mapCodecToMime("WEBVTT"))
    }
}
