package com.raulshma.jellyplay.feature.player.video.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleOcrHelperTextValidationTest {

    private fun isLikelySubtitleText(text: String): Boolean {
        val cleaned = text.replace("\\s+".toRegex(), " ").trim()
        if (cleaned.length < 2) return false
        if (cleaned.length > 200) return false
        val letterRatio = cleaned.count { it.isLetter() || it.isWhitespace() }.toFloat() / cleaned.length
        return letterRatio > 0.5f
    }

    @Test
    fun isLikelySubtitleText_normalSentence_returnsTrue() {
        assertTrue(isLikelySubtitleText("Hello, how are you?"))
    }

    @Test
    fun isLikelySubtitleText_singleLetter_returnsFalse() {
        assertFalse(isLikelySubtitleText("A"))
    }

    @Test
    fun isLikelySubtitleText_emptyString_returnsFalse() {
        assertFalse(isLikelySubtitleText(""))
    }

    @Test
    fun isLikelySubtitleText_whitespaceOnly_returnsFalse() {
        assertFalse(isLikelySubtitleText("   "))
    }

    @Test
    fun isLikelySubtitleText_twoLetters_returnsTrue() {
        assertTrue(isLikelySubtitleText("Hi"))
    }

    @Test
    fun isLikelySubtitleText_longText_over200Chars_returnsFalse() {
        val longText = "A".repeat(201)
        assertFalse(isLikelySubtitleText(longText))
    }

    @Test
    fun isLikelySubtitleText_exactly200Chars_returnsTrue() {
        val text = "A".repeat(200)
        assertTrue(isLikelySubtitleText(text))
    }

    @Test
    fun isLikelySubtitleText_mostlySymbols_returnsFalse() {
        assertFalse(isLikelySubtitleText("!@#$%^&*()"))
    }

    @Test
    fun isLikelySubtitleText_mixedLettersAndSymbols_returnsTrue() {
        assertTrue(isLikelySubtitleText("Hello! @World#"))
    }

    @Test
    fun isLikelySubtitleText_mostlyDigits_returnsFalse() {
        assertFalse(isLikelySubtitleText("1234567890"))
    }

    @Test
    fun isLikelySubtitleText_subtitleWithTimestamp_returnsTrue() {
        assertTrue(isLikelySubtitleText("We need to go now!"))
    }

    @Test
    fun isLikelySubtitleText_multipleSpaces_normalizes() {
        assertTrue(isLikelySubtitleText("Hello    world"))
    }

    @Test
    fun isLikelySubtitleText_singleWord_returnsTrue() {
        assertTrue(isLikelySubtitleText("RUN"))
    }

    @Test
    fun isLikelySubtitleText_numbersAndLetters_returnsTrue() {
        assertTrue(isLikelySubtitleText("Room 237"))
    }
}
