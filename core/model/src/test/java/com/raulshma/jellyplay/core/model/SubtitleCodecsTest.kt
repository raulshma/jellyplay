package com.raulshma.jellyplay.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the subtitle-codec taxonomy in [SubtitleCodecs.kt].
 *
 * [isSideLoadableEmbeddedSubtitle] decides whether an embedded subtitle stream
 * can be side-loaded via the Jellyfin subtitle endpoint (text) vs. needing
 * burn-in / container demux (image). [isImageSubtitleCodec] is the inverse
 * check used to refuse image codecs at URL-build time.
 */
class SubtitleCodecsTest {

    // ── isSideLoadableEmbeddedSubtitle ───────────────────────────────

    @Test
    fun `text codecs are side-loadable`() {
        TEXT_SUBTITLE_CODECS.forEach { codec ->
            assertTrue("Expected '$codec' to be side-loadable", isSideLoadableEmbeddedSubtitle(codec))
        }
    }

    @Test
    fun `text codecs are case-insensitive`() {
        listOf("SRT", "ASS", "VTT", "WebVTT", "TTML").forEach { codec ->
            assertTrue("Expected '$codec' to be side-loadable", isSideLoadableEmbeddedSubtitle(codec))
        }
    }

    @Test
    fun `image codecs are not side-loadable`() {
        IMAGE_SUBTITLE_CODECS.forEach { codec ->
            assertFalse("Expected '$codec' to NOT be side-loadable", isSideLoadableEmbeddedSubtitle(codec))
        }
    }

    @Test
    fun `image codecs are case-insensitive`() {
        assertFalse(isSideLoadableEmbeddedSubtitle("PGS"))
        assertFalse(isSideLoadableEmbeddedSubtitle("HDMV_PGS_SUBTITLE"))
        assertFalse(isSideLoadableEmbeddedSubtitle("DVD_SUBTITLE"))
    }

    @Test
    fun `null codec is side-loadable`() {
        assertTrue(isSideLoadableEmbeddedSubtitle(null))
    }

    @Test
    fun `blank codec is side-loadable`() {
        assertTrue(isSideLoadableEmbeddedSubtitle(""))
        assertTrue(isSideLoadableEmbeddedSubtitle("   "))
    }

    @Test
    fun `unknown codec is permissively side-loadable`() {
        assertTrue(isSideLoadableEmbeddedSubtitle("future_codec"))
    }

    // ── isImageSubtitleCodec ─────────────────────────────────────────

    @Test
    fun `isImageSubtitleCodec true for image family`() {
        IMAGE_SUBTITLE_CODECS.forEach { codec ->
            assertTrue("Expected '$codec' to be an image codec", isImageSubtitleCodec(codec))
        }
    }

    @Test
    fun `isImageSubtitleCodec false for text and null`() {
        assertFalse(isImageSubtitleCodec(null))
        assertFalse(isImageSubtitleCodec("srt"))
        assertFalse(isImageSubtitleCodec("ASS"))
        assertFalse(isImageSubtitleCodec("future_codec"))
    }
}
