package com.raulshma.jellyplay.feature.details

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * JVM (jvmShared) actual of [languageDisplayName] — the Seerr information
 * section's Language row. The actual body is `Locale(languageTag).displayLanguage`
 * verbatim, which pins three behaviors here:
 *
 *  1. a known ISO-639 tag renders its (default-locale) display name —
 *     "en" → "English";
 *  2. an unresolvable tag echoes the tag back (never null);
 *  3. the call never throws for junk input.
 *
 * `displayLanguage` is localized against the JVM default locale, so the tests
 * fix the default locale to `Locale.US` for determinism ("en" would render as
 * "Englisch" under a German default) and restore the original afterwards.
 */
class SeerrLanguageNameJvmTest {

    private val originalLocale = Locale.getDefault()

    @BeforeTest
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @AfterTest
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    // ── known tags ─────────────────────────────────────────────────────

    @Test
    fun `en resolves to English`() {
        assertEquals("English", languageDisplayName("en"))
    }

    @Test
    fun `other known tags resolve to their display names`() {
        assertEquals("French", languageDisplayName("fr"))
        assertEquals("German", languageDisplayName("de"))
        assertEquals("Japanese", languageDisplayName("ja"))
    }

    @Test
    fun `tag case does not matter for resolution`() {
        assertEquals("English", languageDisplayName("EN"))
        assertEquals("English", languageDisplayName("eN"))
    }

    // ── unknown tags echo the tag ──────────────────────────────────────

    @Test
    fun `unknown tag echoes the tag back`() {
        // Unassigned / made-up subtags are echoed verbatim (lowercased by the
        // Locale ctor) — the display java produced where the name cannot be
        // resolved, which the call site renders as-is.
        assertEquals("zz", languageDisplayName("zz"))
        assertEquals("xyz", languageDisplayName("xyz"))
        assertEquals("notalang", languageDisplayName("notalang"))
    }

    @Test
    fun `compound tag echoes its unresolved language subtag`() {
        // Locale(tag) does not split on '-': "en-US" becomes an unresolvable
        // language "en-us", echoed back rather than crashing or nulling.
        assertEquals("en-us", languageDisplayName("en-US"))
    }

    @Test
    fun `empty tag renders empty string`() {
        assertEquals("", languageDisplayName(""))
    }

    // ── total function: never null, never throws ───────────────────────

    @Test
    fun `never returns null and never throws for arbitrary input`() {
        val hostileInputs = listOf(
            "en", "zz", "en-US", "12345", "!!", "de-DE-1901", "x",
            "very-long-not-a-language-tag", "中文", "e n",
        )
        for (input in hostileInputs) {
            assertNotNull(languageDisplayName(input), "languageDisplayName($input) must never return null")
        }
    }
}
