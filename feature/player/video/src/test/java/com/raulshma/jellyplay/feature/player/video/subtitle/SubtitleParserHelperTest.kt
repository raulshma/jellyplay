package com.raulshma.jellyplay.feature.player.video.subtitle

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class SubtitleParserHelperTest {

    @Test
    fun findActiveCue_findsFirstMatchingCueAtPosition() {
        val cues = listOf(
            TimedCue(startTimeUs = 1_000_000L, endTimeUs = 3_000_000L, text = "Cue 1"),
            TimedCue(startTimeUs = 4_000_000L, endTimeUs = 6_000_000L, text = "Cue 2"),
        )

        val activeCue = SubtitleParserHelper.findActiveCue(cues, positionUs = 2_000_000L, offsetUs = 0L)
        assertNotNull(activeCue)
        assertEquals("Cue 1", activeCue?.text)
    }

    @Test
    fun findActiveCues_honorsOffsetAndFindsMultipleOverlappingCues() {
        val cues = listOf(
            TimedCue(startTimeUs = 1_000_000L, endTimeUs = 5_000_000L, text = "Cue A"),
            TimedCue(startTimeUs = 2_000_000L, endTimeUs = 6_000_000L, text = "Cue B"),
            TimedCue(startTimeUs = 7_000_000L, endTimeUs = 9_000_000L, text = "Cue C"),
        )

        // position 2s + offset 1s = 3s -> Cue A and Cue B are active
        val active = SubtitleParserHelper.findActiveCues(cues, positionUs = 2_000_000L, offsetUs = 1_000_000L)
        assertEquals(2, active.size)
        assertEquals("Cue A", active[0].text)
        assertEquals("Cue B", active[1].text)
    }

    @Test
    fun findActiveCues_returnsEmptyWhenNoCueActiveOrListIsEmpty() {
        val cues = listOf(
            TimedCue(startTimeUs = 1_000_000L, endTimeUs = 3_000_000L, text = "Cue 1"),
        )

        val emptyResult = SubtitleParserHelper.findActiveCues(emptyList(), positionUs = 2_000_000L, offsetUs = 0L)
        assertTrue("Empty cues list returns empty result", emptyResult.isEmpty())

        val noMatchResult = SubtitleParserHelper.findActiveCues(cues, positionUs = 5_000_000L, offsetUs = 0L)
        assertTrue("Position past all cues returns empty result", noMatchResult.isEmpty())
    }

    private fun assertTrue(message: String, condition: Boolean) {
        org.junit.Assert.assertTrue(message, condition)
    }
}
