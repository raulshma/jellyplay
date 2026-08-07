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

    @Test
    fun findAdjacentCues_positionBeforeFirstCue_returnsNextOnly() {
        val cues = listOf(
            TimedCue(startTimeUs = 10_000_000L, endTimeUs = 12_000_000L, text = "Cue A"),
            TimedCue(startTimeUs = 14_000_000L, endTimeUs = 16_000_000L, text = "Cue B"),
        )

        val ctx = SubtitleParserHelper.findAdjacentCues(cues, positionUs = 5_000_000L, offsetUs = 0L)
        assertNull("No previous cue before the first", ctx.previous)
        assertNull("No active cue before the first", ctx.active)
        assertEquals("Next cue is the first", "Cue A", ctx.next?.text)
    }

    @Test
    fun findAdjacentCues_positionInCue_returnsAllThreeNeighbors() {
        val cues = listOf(
            TimedCue(startTimeUs = 1_000_000L, endTimeUs = 3_000_000L, text = "Cue A"),
            TimedCue(startTimeUs = 4_000_000L, endTimeUs = 6_000_000L, text = "Cue B"),
            TimedCue(startTimeUs = 7_000_000L, endTimeUs = 9_000_000L, text = "Cue C"),
        )

        val ctx = SubtitleParserHelper.findAdjacentCues(cues, positionUs = 5_000_000L, offsetUs = 0L)
        assertEquals("Previous is Cue A", "Cue A", ctx.previous?.text)
        assertEquals("Active is Cue B", "Cue B", ctx.active?.text)
        assertEquals("Next is Cue C", "Cue C", ctx.next?.text)
    }

    @Test
    fun findAdjacentCues_positionInGap_returnsNeighborsWithoutActive() {
        val cues = listOf(
            TimedCue(startTimeUs = 1_000_000L, endTimeUs = 3_000_000L, text = "Cue A"),
            TimedCue(startTimeUs = 4_000_000L, endTimeUs = 6_000_000L, text = "Cue B"),
            TimedCue(startTimeUs = 10_000_000L, endTimeUs = 12_000_000L, text = "Cue C"),
        )

        // Gap between Cue B (ends 6s) and Cue C (starts 10s).
        val ctx = SubtitleParserHelper.findAdjacentCues(cues, positionUs = 8_000_000L, offsetUs = 0L)
        assertNull("No active cue in a gap", ctx.active)
        assertEquals("Previous is the last cue before the gap", "Cue B", ctx.previous?.text)
        assertEquals("Next is the first cue after the gap", "Cue C", ctx.next?.text)
    }

    @Test
    fun findAdjacentCues_positionAfterLastCue_returnsPreviousOnly() {
        val cues = listOf(
            TimedCue(startTimeUs = 1_000_000L, endTimeUs = 3_000_000L, text = "Cue A"),
            TimedCue(startTimeUs = 4_000_000L, endTimeUs = 6_000_000L, text = "Cue B"),
        )

        val ctx = SubtitleParserHelper.findAdjacentCues(cues, positionUs = 20_000_000L, offsetUs = 0L)
        assertEquals("Previous is the last cue", "Cue B", ctx.previous?.text)
        assertNull("No active cue past the end", ctx.active)
        assertNull("No next cue past the end", ctx.next)
    }

    @Test
    fun findAdjacentCues_honorsOffsetWhenResolvingNeighbors() {
        val cues = listOf(
            TimedCue(startTimeUs = 1_000_000L, endTimeUs = 3_000_000L, text = "Cue A"),
            TimedCue(startTimeUs = 4_000_000L, endTimeUs = 6_000_000L, text = "Cue B"),
            TimedCue(startTimeUs = 7_000_000L, endTimeUs = 9_000_000L, text = "Cue C"),
        )

        // Raw position 3.5s lands in the gap; +1s offset shifts it into Cue B.
        val ctx = SubtitleParserHelper.findAdjacentCues(cues, positionUs = 3_500_000L, offsetUs = 1_000_000L)
        assertEquals("Active is Cue B with offset applied", "Cue B", ctx.active?.text)
        assertEquals("Previous is Cue A", "Cue A", ctx.previous?.text)
        assertEquals("Next is Cue C", "Cue C", ctx.next?.text)
    }

    @Test
    fun findAdjacentCues_overlappingCues_firstActiveWithBlockAwareNext() {
        val cues = listOf(
            TimedCue(startTimeUs = 1_000_000L, endTimeUs = 5_000_000L, text = "Cue A"),
            TimedCue(startTimeUs = 2_000_000L, endTimeUs = 6_000_000L, text = "Cue B"),
            TimedCue(startTimeUs = 7_000_000L, endTimeUs = 9_000_000L, text = "Cue C"),
        )

        val ctx = SubtitleParserHelper.findAdjacentCues(cues, positionUs = 3_000_000L, offsetUs = 0L)
        assertEquals("First active cue wins the highlight", "Cue A", ctx.active?.text)
        assertNull("No previous cue for the first cue", ctx.previous)
        assertEquals("Next skips the overlapping active block", "Cue C", ctx.next?.text)
    }

    @Test
    fun findAdjacentCues_emptyList_returnsAllNull() {
        val ctx = SubtitleParserHelper.findAdjacentCues(emptyList(), positionUs = 2_000_000L, offsetUs = 0L)
        assertNull(ctx.previous)
        assertNull(ctx.active)
        assertNull(ctx.next)
    }

    private fun assertTrue(message: String, condition: Boolean) {
        org.junit.Assert.assertTrue(message, condition)
    }
}
