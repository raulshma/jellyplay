package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class LanguageUtilsTest {

    @Test
    fun `isLanguageMatch checks match correctly`() {
        assertTrue(isLanguageMatch("eng", "en"))
        assertTrue(isLanguageMatch("eng", "eng"))
        assertTrue(isLanguageMatch("en", "eng"))
        assertTrue(isLanguageMatch("en-US", "en"))
        assertTrue(isLanguageMatch("spa", "es"))
        assertTrue(isLanguageMatch("fra", "fr"))
        
        assertFalse(isLanguageMatch("eng", "es"))
        assertFalse(isLanguageMatch(null, "en"))
        assertFalse(isLanguageMatch("eng", null))
    }

    @Test
    fun `parseLanguageFromLabel parses names and codes`() {
        assertEquals(
parseLanguageFromLabel("English"),
"eng",
)
        assertEquals(
parseLanguageFromLabel("Track 1 - [English]"),
"eng",
)
        assertEquals(
parseLanguageFromLabel("Stream 1 - Audio - [eng]"),
"eng",
)
        assertEquals(
parseLanguageFromLabel("eng"),
"eng",
)
        assertEquals(
parseLanguageFromLabel("en"),
"eng",
)

        assertEquals(
parseLanguageFromLabel("Spanish"),
"spa",
)
        assertEquals(
parseLanguageFromLabel("es"),
"spa",
)
        assertEquals(
parseLanguageFromLabel("spa"),
"spa",
)
        assertEquals(
parseLanguageFromLabel("Spanish (AAC 5.1)"),
"spa",
)

        assertEquals(
parseLanguageFromLabel("German"),
"deu",
)
        assertEquals(
parseLanguageFromLabel("French"),
"fra",
)
        assertEquals(
parseLanguageFromLabel("Chinese"),
"zho",
)

        assertEquals(null, parseLanguageFromLabel(null))
        assertEquals(null, parseLanguageFromLabel(""))
        assertEquals(null, parseLanguageFromLabel("Audio Track 1"))
    }
}
