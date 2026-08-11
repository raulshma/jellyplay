package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.StreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure unit tests for the probe's mapping logic ([mediaStreamFromProbe] and
 * [resolveVideoRange]), isolated from the Android [android.media.MediaFormat]
 * reader so no framework types are required. Drives the type classification,
 * language-sentinel handling, and HDR/Dolby Vision detection that the offline
 * quality/audio badges render.
 */
class LocalStreamProbeMappingTest {

    // ── mediaStreamFromProbe: type classification ────────────────────

    @Test
    fun `video mime maps to a VIDEO stream with codec after slash`() {
        val stream = mediaStreamFromProbe(
            index = 0, mime = "video/hevc",
            height = 1080, width = 1920,
            channels = null, sampleRate = null, language = null,
            videoDoViTitle = null, videoRangeType = "HDR",
        )
        assertEquals(StreamType.VIDEO, stream?.type)
        assertEquals("hevc", stream?.codec)
        assertEquals(1080, stream?.height)
        assertEquals(1920, stream?.width)
        assertEquals("HDR", stream?.videoRangeType)
    }

    @Test
    fun `audio mime maps to an AUDIO stream`() {
        val stream = mediaStreamFromProbe(
            index = 1, mime = "audio/ac3",
            height = null, width = null,
            channels = 6, sampleRate = 48000, language = "eng",
            videoDoViTitle = null, videoRangeType = null,
        )
        assertEquals(StreamType.AUDIO, stream?.type)
        assertEquals("ac3", stream?.codec)
        assertEquals(6, stream?.channels)
        assertEquals(48000, stream?.sampleRate)
        assertEquals("eng", stream?.language)
    }

    @Test
    fun `subtitle or data mimes map to null`() {
        assertNull(mediaStreamFromProbe(0, "text/srt", null, null, null, null, null, null, null))
        assertNull(mediaStreamFromProbe(0, "application/octet-stream", null, null, null, null, null, null, null))
    }

    @Test
    fun `undetermined language sentinel is dropped`() {
        val stream = mediaStreamFromProbe(
            index = 1, mime = "audio/aac",
            height = null, width = null,
            channels = 2, sampleRate = 48000, language = "und",
            videoDoViTitle = null, videoRangeType = null,
        )
        assertNull(stream?.language)
    }

    // ── resolveVideoRange ────────────────────────────────────────────

    @Test
    fun `Dolby Vision codec sets videoDoViTitle and no HDR range`() {
        val (doVi, range) = resolveVideoRange("dolby-vision", hasHdrStaticInfo = true)
        assertEquals("Dolby Vision", doVi)
        assertNull(range) // DoVi takes precedence over the static-info flag.
    }

    @Test
    fun `static HDR metadata marks HDR`() {
        val (doVi, range) = resolveVideoRange("hevc", hasHdrStaticInfo = true)
        assertNull(doVi)
        assertEquals("HDR", range)
    }

    @Test
    fun `no static metadata and non-DoVi codec is SDR`() {
        val (doVi, range) = resolveVideoRange("avc", hasHdrStaticInfo = false)
        assertNull(doVi)
        assertNull(range) // Badge defaults to "SDR".
    }
}
