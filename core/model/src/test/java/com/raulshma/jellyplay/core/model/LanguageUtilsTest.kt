package com.raulshma.jellyplay.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals("eng", parseLanguageFromLabel("English"))
        assertEquals("eng", parseLanguageFromLabel("Track 1 - [English]"))
        assertEquals("eng", parseLanguageFromLabel("Stream 1 - Audio - [eng]"))
        assertEquals("eng", parseLanguageFromLabel("eng"))
        assertEquals("eng", parseLanguageFromLabel("en"))

        assertEquals("spa", parseLanguageFromLabel("Spanish"))
        assertEquals("spa", parseLanguageFromLabel("es"))
        assertEquals("spa", parseLanguageFromLabel("spa"))
        assertEquals("spa", parseLanguageFromLabel("Spanish (AAC 5.1)"))

        assertEquals("deu", parseLanguageFromLabel("German"))
        assertEquals("fra", parseLanguageFromLabel("French"))
        assertEquals("zho", parseLanguageFromLabel("Chinese"))

        assertEquals(null, parseLanguageFromLabel(null))
        assertEquals(null, parseLanguageFromLabel(""))
        assertEquals(null, parseLanguageFromLabel("Audio Track 1"))
    }
}
