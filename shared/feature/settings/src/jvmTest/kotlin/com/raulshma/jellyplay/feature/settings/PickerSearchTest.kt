package com.raulshma.jellyplay.feature.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PickerSearchTest {
    @Test
    fun matchesLanguageNamesCodesAndTurkishCharacters() {
        assertTrue(matchesPickerQuery("turk", "Türkçe", "tur"))
        assertTrue(matchesPickerQuery("ENG", "English", "eng"))
        assertTrue(matchesPickerQuery("ingilizce", "İngilizce", "eng"))
        assertTrue(matchesPickerQuery("  tur turk  ", "Türkçe", "tur"))
        assertTrue(matchesPickerQuery(" ", "System default"))
        assertFalse(matchesPickerQuery("eng", "Türkçe", "tur"))
    }
}
