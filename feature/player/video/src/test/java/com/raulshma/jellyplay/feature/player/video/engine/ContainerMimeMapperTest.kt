package com.raulshma.jellyplay.feature.player.video.engine

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContainerMimeMapperTest {

    @Test
    fun mp4_mapsToApplicationMp4() {
        assertEquals(MimeTypes.APPLICATION_MP4, ContainerMimeMapper.mapToMime("mp4"))
    }

    @Test
    fun m4v_mapsToApplicationMp4() {
        assertEquals(MimeTypes.APPLICATION_MP4, ContainerMimeMapper.mapToMime("m4v"))
    }

    @Test
    fun mov_mapsToApplicationMp4() {
        assertEquals(MimeTypes.APPLICATION_MP4, ContainerMimeMapper.mapToMime("mov"))
    }

    @Test
    fun mkv_mapsToMatroska() {
        assertEquals(MimeTypes.APPLICATION_MATROSKA, ContainerMimeMapper.mapToMime("mkv"))
    }

    @Test
    fun webm_mapsToMatroska() {
        assertEquals(MimeTypes.APPLICATION_MATROSKA, ContainerMimeMapper.mapToMime("webm"))
    }

    @Test
    fun ts_mapsToVideoMp2t() {
        assertEquals(MimeTypes.VIDEO_MP2T, ContainerMimeMapper.mapToMime("ts"))
    }

    @Test
    fun m2ts_mapsToVideoMp2t() {
        assertEquals(MimeTypes.VIDEO_MP2T, ContainerMimeMapper.mapToMime("m2ts"))
    }

    @Test
    fun flac_mapsToAudioFlac() {
        assertEquals(MimeTypes.AUDIO_FLAC, ContainerMimeMapper.mapToMime("flac"))
    }

    @Test
    fun mp3_mapsToAudioMpeg() {
        assertEquals(MimeTypes.AUDIO_MPEG, ContainerMimeMapper.mapToMime("mp3"))
    }

    @Test
    fun flv_mapsToVideoFlv() {
        assertEquals(MimeTypes.VIDEO_FLV, ContainerMimeMapper.mapToMime("flv"))
    }

    @Test
    fun nullContainer_mapsToNull() {
        assertNull(ContainerMimeMapper.mapToMime(null))
    }

    @Test
    fun blankContainer_mapsToNull() {
        assertNull(ContainerMimeMapper.mapToMime("   "))
    }

    @Test
    fun unknownContainer_mapsToNull() {
        assertNull(ContainerMimeMapper.mapToMime("totally_unknown_format"))
    }

    @Test
    fun caseInsensitive() {
        assertEquals(MimeTypes.APPLICATION_MATROSKA, ContainerMimeMapper.mapToMime("MKV"))
        assertEquals(MimeTypes.APPLICATION_MATROSKA, ContainerMimeMapper.mapToMime("Mkv"))
        assertEquals(MimeTypes.APPLICATION_MP4, ContainerMimeMapper.mapToMime("MP4"))
        assertEquals(MimeTypes.VIDEO_MP2T, ContainerMimeMapper.mapToMime("TS"))
    }

    @Test
    fun whitespace_isTrimmed() {
        assertEquals(MimeTypes.APPLICATION_MATROSKA, ContainerMimeMapper.mapToMime("  mkv  "))
    }
}
