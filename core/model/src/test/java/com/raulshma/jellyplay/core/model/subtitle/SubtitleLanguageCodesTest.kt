package com.raulshma.jellyplay.core.model.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SubtitleLanguageCodes] — the cross-dialect normalization that
 * lets a single user-selected ISO 639-3 culture feed Wyzie (639-1),
 * OpenSubtitles (639-2B), and Jellyfin (639-3).
 */
class SubtitleLanguageCodesTest {

    @Test
    fun `toIso3 normalizes 2-letter codes`() {
        assertEquals("eng", SubtitleLanguageCodes.toIso3("en"))
        assertEquals("fra", SubtitleLanguageCodes.toIso3("fr"))
        assertEquals("deu", SubtitleLanguageCodes.toIso3("de"))
        assertEquals("spa", SubtitleLanguageCodes.toIso3("es"))
        assertEquals("jpn", SubtitleLanguageCodes.toIso3("ja"))
    }

    @Test
    fun `toIso3 passes through 3-letter terminologic codes`() {
        assertEquals("eng", SubtitleLanguageCodes.toIso3("eng"))
        // A bibliographic (639-2B) code entering should map to its terminologic (639-3) form.
        assertEquals("deu", SubtitleLanguageCodes.toIso3("ger")) // ger is 639-2B for deu
        assertEquals("fra", SubtitleLanguageCodes.toIso3("fre")) // fre is 639-2B for fra
    }

    @Test
    fun `toIso3 strips region suffixes`() {
        assertEquals("eng", SubtitleLanguageCodes.toIso3("en-US"))
        assertEquals("eng", SubtitleLanguageCodes.toIso3("en_GB"))
        assertEquals("por", SubtitleLanguageCodes.toIso3("pt-BR"))
    }

    @Test
    fun `toIso3 handles null and blank`() {
        assertNull(SubtitleLanguageCodes.toIso3(null))
        assertNull(SubtitleLanguageCodes.toIso3(""))
        assertNull(SubtitleLanguageCodes.toIso3("   "))
    }

    @Test
    fun `toIso1 converts 3-letter back to 2-letter`() {
        assertEquals("en", SubtitleLanguageCodes.toIso1("eng"))
        assertEquals("fr", SubtitleLanguageCodes.toIso1("fra"))
        assertEquals("de", SubtitleLanguageCodes.toIso1("deu"))
    }

    @Test
    fun `toIso1 passes through 2-letter codes`() {
        assertEquals("en", SubtitleLanguageCodes.toIso1("en"))
        assertEquals("pt", SubtitleLanguageCodes.toIso1("pt-BR"))
    }

    @Test
    fun `toIso2B keeps bibliographic distinct where needed`() {
        // The handful of 639-3 codes whose 639-2B form differs must be mapped.
        assertEquals("ger", SubtitleLanguageCodes.toIso2B("deu"))
        assertEquals("fre", SubtitleLanguageCodes.toIso2B("fra"))
        assertEquals("dut", SubtitleLanguageCodes.toIso2B("nld"))
        // Codes with identical 639-2B/639-3 forms pass through unchanged.
        assertEquals("eng", SubtitleLanguageCodes.toIso2B("eng"))
        assertEquals("spa", SubtitleLanguageCodes.toIso2B("spa"))
    }

    @Test
    fun `join converts and comma-separates, dropping unmappable`() {
        // 639-3 inputs → 639-1 outputs.
        assertEquals("en,es", SubtitleLanguageCodes.join(listOf("eng", "spa")) { SubtitleLanguageCodes.toIso1(it) })
        // 639-3 inputs → 639-2B outputs (with the ger/deu override).
        assertEquals("ger,eng", SubtitleLanguageCodes.join(listOf("deu", "eng")) { SubtitleLanguageCodes.toIso2B(it) })
        // Empty input → empty string (no trailing comma).
        assertEquals("", SubtitleLanguageCodes.join(emptyList()) { SubtitleLanguageCodes.toIso1(it) })
    }

    @Test
    fun `displayName resolves a code to a human language name`() {
        assertEquals("English", SubtitleLanguageCodes.displayName("eng"))
        assertEquals("English", SubtitleLanguageCodes.displayName("en"))
        // Unknown codes fall back to the code itself rather than null.
        assertEquals("xxx", SubtitleLanguageCodes.displayName("xxx"))
        assertNull(SubtitleLanguageCodes.displayName(null))
    }

    @Test
    fun `round trip 639-3 to 639-1 and back is stable for common languages`() {
        val langs = listOf("eng", "fra", "deu", "spa", "ita", "por", "jpn", "kor", "rus", "zho")
        for (lang in langs) {
            val asOne = SubtitleLanguageCodes.toIso1(lang)
            assertTrue("639-1 of $lang should be non-null", asOne != null)
            val backToThree = SubtitleLanguageCodes.toIso3(asOne)
            assertEquals("round trip failed for $lang", lang, backToThree)
        }
    }
}
