package com.raulshma.jellyplay.feature.player.video.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimedCueTest {

    @Test
    fun timedCue_storesFieldsCorrectly() {
        val cue = TimedCue(
            startTimeUs = 1_000_000L,
            endTimeUs = 3_000_000L,
            text = "Hello world",
        )
        assertEquals(1_000_000L, cue.startTimeUs)
        assertEquals(3_000_000L, cue.endTimeUs)
        assertEquals("Hello world", cue.text.toString())
    }

    @Test
    fun timedCue_dataClassEquality() {
        val a = TimedCue(100L, 200L, "test")
        val b = TimedCue(100L, 200L, "test")
        assertEquals(a, b)
    }

    @Test
    fun timedCue_dataClassCopy() {
        val original = TimedCue(100L, 200L, "original")
        val modified = original.copy(text = "modified")
        assertEquals("original", original.text.toString())
        assertEquals("modified", modified.text.toString())
    }
}

class FindActiveCueTest {

    private val cues = listOf(
        TimedCue(0L, 1_000_000L, "First"),
        TimedCue(1_000_000L, 2_000_000L, "Second"),
        TimedCue(2_000_000L, 3_000_000L, "Third"),
        TimedCue(5_000_000L, 6_000_000L, "Fourth"),
    )

    @Test
    fun findActiveCue_atStartOfCue_returnsCue() {
        val result = SubtitleParserHelper.findActiveCue(cues, 0L, 0L)
        assertEquals("First", result?.text.toString())
    }

    @Test
    fun findActiveCue_middleOfCue_returnsCue() {
        val result = SubtitleParserHelper.findActiveCue(cues, 1_500_000L, 0L)
        assertEquals("Second", result?.text.toString())
    }

    @Test
    fun findActiveCue_exactlyAtBoundary_returnsNextCue() {
        val result = SubtitleParserHelper.findActiveCue(cues, 1_000_000L, 0L)
        assertEquals("Second", result?.text.toString())
    }

    @Test
    fun findActiveCue_inGap_returnsNull() {
        val result = SubtitleParserHelper.findActiveCue(cues, 3_500_000L, 0L)
        assertNull(result)
    }

    @Test
    fun findActiveCue_beforeAllCues_returnsNull() {
        val result = SubtitleParserHelper.findActiveCue(cues, -1L, 0L)
        assertNull(result)
    }

    @Test
    fun findActiveCue_afterAllCues_returnsNull() {
        val result = SubtitleParserHelper.findActiveCue(cues, 7_000_000L, 0L)
        assertNull(result)
    }

    @Test
    fun findActiveCue_positiveOffset_shiftsCuesEarlier() {
        val result = SubtitleParserHelper.findActiveCue(cues, 500_000L, 1_000_000L)
        assertEquals("Second", result?.text.toString())
    }

    @Test
    fun findActiveCue_positiveOffset_revealsLaterCue() {
        val result = SubtitleParserHelper.findActiveCue(cues, 3_500_000L, 2_000_000L)
        assertEquals("Fourth", result?.text.toString())
    }

    @Test
    fun findActiveCue_negativeOffset_shiftsCuesLater() {
        val result = SubtitleParserHelper.findActiveCue(cues, 500_000L, -1_000_000L)
        assertNull(result)
    }

    @Test
    fun findActiveCue_negativeOffset_hidesEarlierCue() {
        val result = SubtitleParserHelper.findActiveCue(cues, 1_000_000L, -1_000_000L)
        assertEquals("First", result?.text.toString())
    }

    @Test
    fun findActiveCue_emptyCueList_returnsNull() {
        val result = SubtitleParserHelper.findActiveCue(emptyList(), 1_000_000L, 0L)
        assertNull(result)
    }

    @Test
    fun findActiveCue_exactlyAtEnd_returnsNull() {
        val result = SubtitleParserHelper.findActiveCue(cues, 3_000_000L, 0L)
        assertNull(result)
    }

    @Test
    fun findActiveCue_justBeforeEnd_returnsCue() {
        val result = SubtitleParserHelper.findActiveCue(cues, 2_999_999L, 0L)
        assertEquals("Third", result?.text.toString())
    }

    @Test
    fun findActiveCue_largeOffset_shiftsPastAllCues() {
        val result = SubtitleParserHelper.findActiveCue(cues, 0L, 10_000_000L)
        assertNull(result)
    }
}
