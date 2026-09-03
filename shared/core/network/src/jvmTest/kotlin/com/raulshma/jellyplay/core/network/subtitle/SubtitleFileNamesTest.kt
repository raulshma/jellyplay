package com.raulshma.jellyplay.core.network.subtitle

import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the fallback file name of [defaultSubtitleFileName]: the release name
 * with filesystem-unsafe characters replaced, else "subtitle", plus the
 * lowercased format extension (default "srt"). The result is written straight
 * to disk as the downloaded subtitle's file name, so unsafe characters must
 * never survive.
 */
class SubtitleFileNamesTest {

    private fun result(releaseName: String?, format: String?): SubtitleSearchResult =
        SubtitleSearchResult(
            provider = SubtitleProviderKind.WYZIE,
            id = "row-1",
            language = "eng",
            displayName = "English",
            releaseName = releaseName,
            format = format,
        )

    @Test
    fun `safe release name passes through unchanged`() {
        // A-Za-z0-9 . _ - are the whitelist; a typical scene-style release
        // already lives inside it.
        assertEquals(
            "Movie.2023.1080p.BluRay.x264-GRP.srt",
            defaultSubtitleFileName(result("Movie.2023.1080p.BluRay.x264-GRP", "srt")),
        )
    }

    @Test
    fun `filesystem-unsafe characters are replaced with underscores`() {
        // / \ : * ? " < > | and space are all outside the whitelist.
        assertEquals(
            "Bad_Name_2023.srt",
            defaultSubtitleFileName(result("Bad/Name:2023", "srt")),
        )
        assertEquals(
            "A_B_C.srt",
            defaultSubtitleFileName(result("A\\B*C", "srt")),
        )
    }

    @Test
    fun `null release name falls back to subtitle`() {
        assertEquals("subtitle.srt", defaultSubtitleFileName(result(releaseName = null, format = "srt")))
    }

    @Test
    fun `blank release name falls back to subtitle`() {
        assertEquals("subtitle.srt", defaultSubtitleFileName(result(releaseName = "   ", format = "srt")))
    }

    @Test
    fun `null format defaults to srt`() {
        assertEquals("Movie.2023.srt", defaultSubtitleFileName(result("Movie.2023", format = null)))
    }

    @Test
    fun `blank format defaults to srt`() {
        assertEquals("Movie.2023.srt", defaultSubtitleFileName(result("Movie.2023", format = "  ")))
    }

    @Test
    fun `format is lowercased`() {
        assertEquals("Movie.2023.ass", defaultSubtitleFileName(result("Movie.2023", format = "ASS")))
    }

    @Test
    fun `custom format is used as the extension`() {
        assertEquals("Movie.2023.vtt", defaultSubtitleFileName(result("Movie.2023", format = "vtt")))
    }
}
