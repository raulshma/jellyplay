package com.raulshma.jellyplay.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integrity tests for the ISO-639/2 language table backing the subtitle /
 * audio language pickers ([SettingsLanguageData.kt], SettingsSearchCatalogTest
 * precedent): codes must stay unique, well-formed ISO 639-2/B identifiers and
 * display names non-blank, and the [languageNameByCode] lookup map must mirror
 * the display list exactly (the picker renders the list but resolves back
 * through the map, so a divergence shows the user one language and saves
 * another).
 */
class SettingsLanguageDataTest {

    @Test
    fun `every non-null language code is unique`() {
        val codes = languages.mapNotNull { it.first }
        assertEquals(
            codes.size,
            codes.toSet().size,
            "duplicate codes: " + codes.groupBy { it }.filterValues { it.size > 1 }.keys,
        )
    }

    @Test
    fun `every code is a lowercase three-letter ISO tag`() {
        languages.forEach { (code, _) ->
            assertTrue(
                code == null || Regex("[a-z]{3}").matches(code),
                "malformed language code: $code",
            )
        }
    }

    @Test
    fun `every display name is non-blank and trimmed`() {
        languages.forEach { (code, name) ->
            assertTrue(name.isNotBlank(), "blank display name for $code")
            assertEquals(name.trim(), name, "untrimmed display name for $code")
        }
    }

    @Test
    fun `the default sentinel entry is present`() {
        // The picker's "System Default" row is the null-code entry.
        assertEquals(1, languages.count { it.first == null })
        assertEquals("Default", languageNameByCode[null])
    }

    @Test
    fun `the lookup map mirrors the display list`() {
        assertEquals(
            languages.size,
            languageNameByCode.size,
            "the map must carry exactly one lookup per list row (including the null default)",
        )
        languages.forEach { (code, name) ->
            assertEquals(name, languageNameByCode[code], "lookup diverges from the list for $code")
        }
    }

    @Test
    fun `baseline entries survive edits`() {
        // Spot pins from the original table — the picker's test row and the
        // alphabet's start/end sentinels.
        assertEquals("English", languageNameByCode["eng"])
        assertEquals("Afar", languageNameByCode["aar"])
        assertEquals("Zulu", languageNameByCode["zul"])
    }

    @Test
    fun `the table keeps its full catalog size`() {
        // 184 rows as shipped (null default + 183 ISO-639/2 codes). Bump
        // deliberately when adding a language.
        assertEquals(184, languages.size)
    }
}
