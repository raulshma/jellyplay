package com.raulshma.jellyplay.core.model

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

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
            assertTrue(
isSideLoadableEmbeddedSubtitle(codec),
"Expected '$codec' to be side-loadable",
)
        }
    }

    @Test
    fun `text codecs are case-insensitive`() {
        listOf("SRT", "ASS", "VTT", "WebVTT", "TTML").forEach { codec ->
            assertTrue(
isSideLoadableEmbeddedSubtitle(codec),
"Expected '$codec' to be side-loadable",
)
        }
    }

    @Test
    fun `image codecs are not side-loadable`() {
        IMAGE_SUBTITLE_CODECS.forEach { codec ->
            assertFalse(
isSideLoadableEmbeddedSubtitle(codec),
"Expected '$codec' to NOT be side-loadable",
)
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
            assertTrue(
isImageSubtitleCodec(codec),
"Expected '$codec' to be an image codec",
)
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
