package com.raulshma.jellyplay.feature.music.moodplaylist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins [parseMoodThemeColor]'s accepted formats — "#RRGGBB" and "#AARRGGBB"
 * (the only forms the mood presets emit) — with exact Compose [androidx.compose.ui.graphics.Color]
 * components, and the null fallback for anything else: a null result is what
 * makes callers keep their primary-container fallback, the same outcome the
 * legacy `android.graphics.Color.parseColor` try/catch produced.
 */
class ParseMoodThemeColorTest {

    @Test
    fun sixDigitHex_parsesOpaqueSrgbComponents() {
        val color = parseMoodThemeColor("#FFD700")!!

        assertEquals(1.0f, color.alpha)
        assertEquals(0xFF / 255f, color.red)
        assertEquals(0xD7 / 255f, color.green)
        assertEquals(0x00 / 255f, color.blue)
    }

    @Test
    fun eightDigitHex_parsesAlphaComponent() {
        val color = parseMoodThemeColor("#80FF0000")!!

        assertEquals(0x80 / 255f, color.alpha)
        assertEquals(1.0f, color.red)
        assertEquals(0.0f, color.green)
        assertEquals(0.0f, color.blue)
    }

    @Test
    fun lowercaseHex_parsesSameComponents() {
        val color = parseMoodThemeColor("#ff8800")!!

        assertEquals(1.0f, color.alpha)
        assertEquals(0xFF / 255f, color.red)
        assertEquals(0x88 / 255f, color.green)
        assertEquals(0x00 / 255f, color.blue)
    }

    @Test
    fun wrongLength_returnsNull() {
        assertNull(parseMoodThemeColor("#FFF"))
        assertNull(parseMoodThemeColor("#FFD70"))
        assertNull(parseMoodThemeColor("#FFD7000"))
        assertNull(parseMoodThemeColor(""))
    }

    @Test
    fun missingHash_returnsNull() {
        assertNull(parseMoodThemeColor("FFD700"))
        assertNull(parseMoodThemeColor(" FFD700"))
    }

    @Test
    fun nonHexCharacters_returnsNull() {
        assertNull(parseMoodThemeColor("#GGGGGG"))
        assertNull(parseMoodThemeColor("#ZZZZZZZZ"))
    }
}
