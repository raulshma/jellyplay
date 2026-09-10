package com.raulshma.jellyplay.feature.player.video.subtitle

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class SubtitleFormatCatalogTest {

    @Test
    fun mapCodecToMime_mapsKnownCodecsAndLabels() {
        assertEquals("application/x-subrip", SubtitleFormatCatalog.mapCodecToMime("srt"))
        assertEquals("application/x-subrip", SubtitleFormatCatalog.mapCodecToMime("SUBRIP"))
        assertEquals("text/x-ssa", SubtitleFormatCatalog.mapCodecToMime("ass"))
        assertEquals("text/x-ssa", SubtitleFormatCatalog.mapCodecToMime("ssa"))
        assertEquals("text/vtt", SubtitleFormatCatalog.mapCodecToMime("vtt"))
        assertEquals("text/vtt", SubtitleFormatCatalog.mapCodecToMime("webvtt"))
        assertEquals("application/ttml+xml", SubtitleFormatCatalog.mapCodecToMime("ttml"))
        assertEquals("application/ttml+xml", SubtitleFormatCatalog.mapCodecToMime("dfxp"))
        assertEquals("application/ttml+xml", SubtitleFormatCatalog.mapCodecToMime("tt"))
        assertEquals("application/pgs", SubtitleFormatCatalog.mapCodecToMime("pgs"))
        // Server-reported variants of the same bitmap format: without these,
        // ExoPlayer drops the sidecar silently at mime resolution.
        assertEquals("application/pgs", SubtitleFormatCatalog.mapCodecToMime("pgssub"))
        assertEquals("application/pgs", SubtitleFormatCatalog.mapCodecToMime("hdmv_pgs_subtitle"))
        assertEquals("application/x-quicktime-tx3g", SubtitleFormatCatalog.mapCodecToMime("mov_text"))
    }

    @Test
    fun mapCodecToMime_returnsNullForNullOrUnknownInput() {
        assertNull(SubtitleFormatCatalog.mapCodecToMime(null))
        assertNull(SubtitleFormatCatalog.mapCodecToMime("unknown_format"))
    }

    @Test
    fun codecForExtension_foldsAliasesOntoCanonicalCodecs() {
        assertEquals("srt", SubtitleFormatCatalog.codecForExtension("srt"))
        assertEquals("srt", SubtitleFormatCatalog.codecForExtension("subrip"))
        assertEquals("ass", SubtitleFormatCatalog.codecForExtension("ass"))
        assertEquals("ass", SubtitleFormatCatalog.codecForExtension("SSA"))
        assertEquals("vtt", SubtitleFormatCatalog.codecForExtension("vtt"))
        assertEquals("vtt", SubtitleFormatCatalog.codecForExtension("webvtt"))
        assertEquals("ttml", SubtitleFormatCatalog.codecForExtension("ttml"))
        assertEquals("ttml", SubtitleFormatCatalog.codecForExtension("dfxp"))
        assertEquals("ttml", SubtitleFormatCatalog.codecForExtension("tt"))
        assertNull(SubtitleFormatCatalog.codecForExtension(null))
        assertNull(SubtitleFormatCatalog.codecForExtension("mkv"))
    }

    @Test
    fun codecForExtension_composesWithCodecToMime_forEveryCanonicalCodec() {
        // The two tables must agree: every extension the picker can hand back
        // resolves to a codec the MIME table understands.
        for (ext in listOf("srt", "subrip", "ass", "ssa", "vtt", "webvtt", "ttml", "dfxp", "tt")) {
            val codec = SubtitleFormatCatalog.codecForExtension(ext)
            assertEquals(
                true,
                codec != null && SubtitleFormatCatalog.mapCodecToMime(codec) != null,
                "extension '$ext' must fold to a canonical codec with a known MIME",
            )
        }
    }

    @Test
    fun pickerMimeTypes_coversEveryPickerCodec() {
        // Every extension the picker's MIME list implies must fold to a known
        // codec, so the fallback file-name decode never loads a blind pick.
        val mimes = SubtitleFormatCatalog.pickerMimeTypes.toSet()
        assertEquals(
            mimes,
            setOf(
                "application/x-subrip",
                "text/vtt",
                "text/plain",
                "text/x-ssa",
                "application/ttml+xml",
            ),
        )
        assertEquals("application/x-subrip", SubtitleFormatCatalog.mapCodecToMime("srt"))
        assertEquals("text/x-ssa", SubtitleFormatCatalog.mapCodecToMime("ass"))
        assertEquals("text/vtt", SubtitleFormatCatalog.mapCodecToMime("vtt"))
        assertEquals("application/ttml+xml", SubtitleFormatCatalog.mapCodecToMime("ttml"))
    }
}
