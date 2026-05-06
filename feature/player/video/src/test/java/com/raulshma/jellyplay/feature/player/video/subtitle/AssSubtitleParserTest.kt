package com.raulshma.jellyplay.feature.player.video.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssSubtitleParserTest {

    private val parser = AssSubtitleParser()

    @Test
    fun parse_simpleDialogue_extractsCue() {
        val input = """
            [Script Info]
            Title: Test

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:04.00,Default,,0,0,0,,Hello world
        """.trimIndent()

        val cues = parser.parseAssEvents(input)
        assertEquals(1, cues.size)
        assertEquals("Hello world", cues[0].text.toString())
    }

    @Test
    fun parse_multipleDialogues_extractsAllCues() {
        val input = """
            [Script Info]
            Title: Test

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,First line
            Dialogue: 0,0:00:04.00,0:00:06.00,Default,,0,0,0,,Second line
            Dialogue: 0,0:00:07.00,0:00:09.00,Default,,0,0,0,,Third line
        """.trimIndent()

        val cues = parser.parseAssEvents(input)
        assertEquals(3, cues.size)
        assertEquals("First line", cues[0].text.toString())
        assertEquals("Second line", cues[1].text.toString())
        assertEquals("Third line", cues[2].text.toString())
    }

    @Test
    fun parse_newlineTags_convertedToNewlines() {
        val input = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:04.00,Default,,0,0,0,,Line one{\pos(400,570)}Line two
        """.trimIndent()

        val cues = parser.parseAssEvents(input)
        assertEquals(1, cues.size)
    }

    @Test
    fun parse_timestamps_parsedCorrectly() {
        val input = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:01:30.50,0:01:35.00,Default,,0,0,0,,Test
        """.trimIndent()

        val cues = parser.parseAssEvents(input)
        assertEquals(1, cues.size)
        val expectedStartUs = (90 * 1000L + 50 * 10L) * 1000L
        assertEquals(expectedStartUs, cues[0].startTimeUs)
    }

    @Test
    fun parse_invalidTimestamp_skipsCue() {
        val input = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,invalid,0:00:04.00,Default,,0,0,0,,Skipped
            Dialogue: 0,0:00:01.00,0:00:04.00,Default,,0,0,0,,Valid
        """.trimIndent()

        val cues = parser.parseAssEvents(input)
        assertEquals(1, cues.size)
        assertEquals("Valid", cues[0].text.toString())
    }

    @Test
    fun parse_blankText_skipsCue() {
        val input = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:04.00,Default,,0,0,0,,
        """.trimIndent()

        val cues = parser.parseAssEvents(input)
        assertTrue(cues.isEmpty())
    }

    @Test
    fun parse_overridesTags_removed() {
        val input = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:04.00,Default,,0,0,0,,{\\b1}Bold text{\\b0} normal
        """.trimIndent()

        val cues = parser.parseAssEvents(input)
        assertEquals(1, cues.size)
        assertEquals("Bold text normal", cues[0].text.toString())
    }

    @Test
    fun parse_noEventsSection_returnsEmpty() {
        val input = """
            [Script Info]
            Title: Test
        """.trimIndent()

        val cues = parser.parseAssEvents(input)
        assertTrue(cues.isEmpty())
    }

    @Test
    fun parse_emptyInput_returnsEmpty() {
        val cues = parser.parseAssEvents("")
        assertTrue(cues.isEmpty())
    }

    @Test
    fun parse_hardSpaceTag_convertedToSpace() {
        val input = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:04.00,Default,,0,0,0,,Hello\hworld
        """.trimIndent()

        val cues = parser.parseAssEvents(input)
        assertEquals(1, cues.size)
        assertEquals("Hello world", cues[0].text.toString())
    }

    @Test
    fun parse_cuesSortedByStartTime() {
        val input = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:05.00,0:00:08.00,Default,,0,0,0,,Second
            Dialogue: 0,0:00:01.00,0:00:04.00,Default,,0,0,0,,First
        """.trimIndent()

        val cues = parser.parseAssEvents(input)
        assertEquals(2, cues.size)
        assertEquals("First", cues[0].text.toString())
        assertEquals("Second", cues[1].text.toString())
    }
}
