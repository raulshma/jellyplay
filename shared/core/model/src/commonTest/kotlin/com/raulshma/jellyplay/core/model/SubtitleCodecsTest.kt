package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    // ── subtitleSidecarExtension ─────────────────────────────────────

    @Test
    fun `text codecs map to their own extension`() {
        assertEquals("srt", subtitleSidecarExtension("srt"))
        assertEquals("srt", subtitleSidecarExtension("subrip"))
        assertEquals("ass", subtitleSidecarExtension("ass"))
        assertEquals("ass", subtitleSidecarExtension("SSA"))
        assertEquals("vtt", subtitleSidecarExtension("webvtt"))
        assertEquals("ttml", subtitleSidecarExtension("mov_text"))
        assertEquals("ttml", subtitleSidecarExtension("dfxp"))
        assertEquals("sub", subtitleSidecarExtension("microdvd"))
    }

    @Test
    fun `image codecs never masquerade as srt`() {
        // The regression: image bytes used to fall through to `.srt`.
        IMAGE_SUBTITLE_CODECS.forEach { codec ->
            val ext = subtitleSidecarExtension(codec)
            assertTrue(
                ext == "sup" || ext == "sub",
                "Expected '$codec' to map to a bitmap extension, got '$ext'",
            )
        }
    }

    @Test
    fun `pgs variants map to sup`() {
        listOf("pgs", "PGSSUB", "hdmv_pgs_subtitle", "dvb_subtitle").forEach { codec ->
            assertEquals("sup", subtitleSidecarExtension(codec))
        }
    }

    @Test
    fun `vobsub family maps to sub`() {
        assertEquals("sub", subtitleSidecarExtension("dvd_subtitle"))
        assertEquals("sub", subtitleSidecarExtension("vobsub"))
    }

    @Test
    fun `null and unknown codec default to srt`() {
        assertEquals("srt", subtitleSidecarExtension(null))
        assertEquals("srt", subtitleSidecarExtension("future_codec"))
    }

    @Test
    fun `every text codec in the taxonomy hits a known arm`() {
        // Guards the when-arms against drifting from TEXT_SUBTITLE_CODECS AND
        // pins the exact extension per codec: a codec added to the set without
        // an arm, or a typo'd arm, fails here instead of bundling under a
        // wrong name.
        val expected = mapOf(
            "srt" to "srt", "subrip" to "srt",
            "ass" to "ass", "ssa" to "ass",
            "vtt" to "vtt", "webvtt" to "vtt",
            "ttml" to "ttml", "dfxp" to "ttml", "tt" to "ttml", "mov_text" to "ttml",
        )
        TEXT_SUBTITLE_CODECS.forEach { codec ->
            assertEquals(expected[codec], subtitleSidecarExtension(codec), "Expected dedicated arm for '$codec'")
        }
    }

    @Test
    fun `vobsub family stays within the image taxonomy`() {
        // VOBSUB_FAMILY_CODECS only fires inside the IMAGE_SUBTITLE_CODECS
        // branch; a VobSub codec added outside the image set would silently
        // fall to a text arm (.srt) instead of .sub.
        val imageOnly = IMAGE_SUBTITLE_CODECS - VOBSUB_FAMILY_CODECS
        VOBSUB_FAMILY_CODECS.forEach { codec ->
            assertTrue(codec in IMAGE_SUBTITLE_CODECS, "'$codec' must be in IMAGE_SUBTITLE_CODECS to map to .sub")
            assertEquals("sub", subtitleSidecarExtension(codec))
        }
        imageOnly.forEach { codec ->
            assertEquals("sup", subtitleSidecarExtension(codec))
        }
    }

    // ── VobSub pair helpers ──────────────────────────────────────────

    @Test
    fun `vobsub family codecs are detected case-insensitively`() {
        assertTrue(isVobsubFamilyCodec("dvd_subtitle"))
        assertTrue(isVobsubFamilyCodec("VOBSUB"))
        assertFalse(isVobsubFamilyCodec("pgs"))
        assertFalse(isVobsubFamilyCodec("dvb_subtitle"))
        assertFalse(isVobsubFamilyCodec(null))
        assertFalse(isVobsubFamilyCodec("srt"))
    }

    @Test
    fun `pair companion names swap idx and sub`() {
        assertEquals("2.sub", subtitleCompanionFileName("2.idx"))
        assertEquals("2.idx", subtitleCompanionFileName("2.sub"))
    }

    @Test
    fun `non-pair names have no companion`() {
        assertNull(subtitleCompanionFileName("0.srt"))
        assertNull(subtitleCompanionFileName("1.sup"))
        assertNull(subtitleCompanionFileName("4.ttml"))
    }
}
