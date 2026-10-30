package com.raulshma.jellyplay.core.model.subtitle

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Unit tests for [SubtitleLanguageCodes] — the cross-dialect normalization that
 * lets a single user-selected ISO 639-3 culture feed Wyzie (639-1),
 * OpenSubtitles (639-2B), and Jellyfin (639-3).
 */
class SubtitleLanguageCodesTest {

    @Test
    fun `toIso3 normalizes 2-letter codes`() {
        assertEquals(
SubtitleLanguageCodes.toIso3("en"),
"eng",
)
        assertEquals(
SubtitleLanguageCodes.toIso3("fr"),
"fra",
)
        assertEquals(
SubtitleLanguageCodes.toIso3("de"),
"deu",
)
        assertEquals(
SubtitleLanguageCodes.toIso3("es"),
"spa",
)
        assertEquals(
SubtitleLanguageCodes.toIso3("ja"),
"jpn",
)
    }

    @Test
    fun `toIso3 passes through 3-letter terminologic codes`() {
        assertEquals(
SubtitleLanguageCodes.toIso3("eng"),
"eng",
)
        // A bibliographic (639-2B) code entering should map to its terminologic (639-3) form.
        assertEquals(
SubtitleLanguageCodes.toIso3("ger"),
"deu",
) // ger is 639-2B for deu
        assertEquals(
SubtitleLanguageCodes.toIso3("fre"),
"fra",
) // fre is 639-2B for fra
    }

    @Test
    fun `toIso3 strips region suffixes`() {
        assertEquals(
SubtitleLanguageCodes.toIso3("en-US"),
"eng",
)
        assertEquals(
SubtitleLanguageCodes.toIso3("en_GB"),
"eng",
)
        assertEquals(
SubtitleLanguageCodes.toIso3("pt-BR"),
"por",
)
    }

    @Test
    fun `toIso3 handles null and blank`() {
        assertNull(SubtitleLanguageCodes.toIso3(null))
        assertNull(SubtitleLanguageCodes.toIso3(""))
        assertNull(SubtitleLanguageCodes.toIso3("   "))
    }

    @Test
    fun `toIso1 converts 3-letter back to 2-letter`() {
        assertEquals(
SubtitleLanguageCodes.toIso1("eng"),
"en",
)
        assertEquals(
SubtitleLanguageCodes.toIso1("fra"),
"fr",
)
        assertEquals(
SubtitleLanguageCodes.toIso1("deu"),
"de",
)
    }

    @Test
    fun `toIso1 passes through 2-letter codes`() {
        assertEquals(
SubtitleLanguageCodes.toIso1("en"),
"en",
)
        assertEquals(
SubtitleLanguageCodes.toIso1("pt-BR"),
"pt",
)
    }

    @Test
    fun `toIso2B keeps bibliographic distinct where needed`() {
        // The handful of 639-3 codes whose 639-2B form differs must be mapped.
        assertEquals(
SubtitleLanguageCodes.toIso2B("deu"),
"ger",
)
        assertEquals(
SubtitleLanguageCodes.toIso2B("fra"),
"fre",
)
        assertEquals(
SubtitleLanguageCodes.toIso2B("nld"),
"dut",
)
        // Codes with identical 639-2B/639-3 forms pass through unchanged.
        assertEquals(
SubtitleLanguageCodes.toIso2B("eng"),
"eng",
)
        assertEquals(
SubtitleLanguageCodes.toIso2B("spa"),
"spa",
)
    }

    @Test
    fun `join converts and comma-separates, dropping unmappable`() {
        // 639-3 inputs → 639-1 outputs.
        assertEquals(
SubtitleLanguageCodes.join(listOf("eng", "spa")) { SubtitleLanguageCodes.toIso1(it) },
"en,es",
)
        // 639-3 inputs → 639-2B outputs (with the ger/deu override).
        assertEquals(
SubtitleLanguageCodes.join(listOf("deu", "eng")) { SubtitleLanguageCodes.toIso2B(it) },
"ger,eng",
)
        // Empty input → empty string (no trailing comma).
        assertEquals(
SubtitleLanguageCodes.join(emptyList()) { SubtitleLanguageCodes.toIso1(it) },
"",
)
    }

    @Test
    fun `displayName resolves a code to a human language name`() {
        assertEquals(
SubtitleLanguageCodes.displayName("eng"),
"English",
)
        assertEquals(
SubtitleLanguageCodes.displayName("en"),
"English",
)
        // Unknown codes fall back to the code itself rather than null.
        assertEquals(
SubtitleLanguageCodes.displayName("xxx"),
"xxx",
)
        assertNull(SubtitleLanguageCodes.displayName(null))
    }

    @Test
    fun `round trip 639-3 to 639-1 and back is stable for common languages`() {
        val langs = listOf("eng", "fra", "deu", "spa", "ita", "por", "jpn", "kor", "rus", "zho")
        for (lang in langs) {
            val asOne = SubtitleLanguageCodes.toIso1(lang)
            assertTrue(
asOne != null,
"639-1 of $lang should be non-null",
)
            val backToThree = SubtitleLanguageCodes.toIso3(asOne)
            assertEquals(
lang, backToThree,
"round trip failed for $lang",
)
        }
    }
}
