package com.raulshma.jellyplay.feature.player.video.engine

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM-lane pins for the jvmMain actual of [platformLanguageDisplayName]:
 *  - It delegates to `Locale.forLanguageTag(tag).displayLanguage`, so results are
 *    exactly what the JDK's default-locale data provides (asserted via the same
 *    Locale call rather than hardcoded English strings, which would break on a
 *    non-English machine locale).
 *  - "en", the ISO-639-2/B "eng" and the region-qualified "en-US" all resolve.
 *  - The impl reads displayLANGUAGE (not displayName), so a region subtag must not
 *    change the result.
 *  - Ill-formed tags never throw: `Locale.forLanguageTag` stops at the first invalid
 *    subtag instead of throwing, and `getDisplayLanguage` falls back to the raw code
 *    when no localized name exists — so on the JVM the impl's null path is
 *    unreachable and garbage tags ECHO (e.g. "zz" -> "zz") rather than return null.
 */
class LanguageDisplayNameJvmTest {

    @Test
    fun knownTags_resolveToNonNullNonBlankDisplayNames() {
        for (tag in listOf("en", "eng", "en-US")) {
            val name = platformLanguageDisplayName(tag)
            assertNotNull(name, "tag $tag must resolve")
            assertTrue(name.isNotBlank(), "tag $tag resolved to a blank name")
        }
    }

    @Test
    fun results_matchJavaLocaleDisplayLanguageSemantics() {
        // Pin the seam (delegation to Locale) without pinning any machine locale:
        // both sides go through the same default-locale JDK data.
        assertEquals(Locale.forLanguageTag("en").displayLanguage, platformLanguageDisplayName("en"))
        assertEquals(Locale.forLanguageTag("eng").displayLanguage, platformLanguageDisplayName("eng"))
        assertEquals(Locale.forLanguageTag("en-US").displayLanguage, platformLanguageDisplayName("en-US"))
    }

    @Test
    fun regionSubtag_isIgnored_languagePartAloneIsDisplayed() {
        assertEquals(
            platformLanguageDisplayName("en"),
            platformLanguageDisplayName("en-US"),
            "region subtags must not leak into the display LANGUAGE",
        )
    }

    @Test
    fun garbageTags_doNotThrow_andEchoTheCodeInsteadOfNull() {
        for (tag in listOf("zz", "", "!!!")) {
            // Any exception escaping the impl fails this test outright.
            val name = platformLanguageDisplayName(tag)
            assertNotNull(name, "garbage tag \"$tag\" must not produce null on the JVM actual")
        }
        // Unmapped two-letter codes fall back to the code itself (Locale contract:
        // getDisplayLanguage returns the language code when no localized name exists).
        assertEquals("zz", platformLanguageDisplayName("zz"))
    }
}
