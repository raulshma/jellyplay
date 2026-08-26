package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Unit tests for the cue-accumulation merge used by ExoPlayerEngine's onCues
 * callback. The merge is a pure function (no ExoPlayer instance needed), so it
 * is extracted to [mergeAccumulatedCues] and tested directly here.
 */
class CueAccumulatorTest {

    @Test
    fun `first emission seeds the list and dedupes same-text within the group`() {
        val incoming = listOf(
            TimedCue(1_000_000L, Long.MAX_VALUE, "Hello"),
            TimedCue(1_000_000L, Long.MAX_VALUE, "Hello"), // dup text
            TimedCue(1_000_000L, Long.MAX_VALUE, "World"),
        )
        val result = mergeAccumulatedCues(emptyList(), incoming)
        assertEquals(2, result.size)
        assertEquals(result[0].text, "Hello")
        assertEquals(result[1].text, "World")
    }

    @Test
    fun `second emission closes the prior open-ended span at the new start`() {
        val seed = listOf(TimedCue(1_000_000L, Long.MAX_VALUE, "First line"))
        val next = listOf(TimedCue(5_000_000L, Long.MAX_VALUE, "Second line"))
        val result = mergeAccumulatedCues(seed, next)
        assertEquals(2, result.size)
        // Prior cue's MAX end resolved to the new cue's start (5s).
        assertEquals(5_000_000L, result[0].endTimeUs)
        assertEquals(Long.MAX_VALUE, result[1].endTimeUs)
    }

    @Test
    fun `re-emitted identical active line is dropped not duplicated`() {
        val existing = listOf(
            TimedCue(1_000_000L, 5_000_000L, "Same"),
            TimedCue(5_000_000L, Long.MAX_VALUE, "Active line"),
        )
        // ExoPlayer re-emits the still-active cue on a render refresh at the
        // same start time — must not duplicate.
        val refresh = listOf(TimedCue(5_000_000L, Long.MAX_VALUE, "Active line"))
        val result = mergeAccumulatedCues(existing, refresh)
        assertEquals(2, result.size)
        assertEquals(result.last().text, "Active line")
    }

    @Test
    fun `list stays sorted by start time`() {
        val existing = listOf(TimedCue(5_000_000L, Long.MAX_VALUE, "B"))
        val incoming = listOf(TimedCue(2_000_000L, Long.MAX_VALUE, "A"))
        val result = mergeAccumulatedCues(existing, incoming)
        assertEquals(2_000_000L, result[0].startTimeUs)
        assertEquals(5_000_000L, result[1].startTimeUs)
    }

    @Test
    fun `list is capped to MAX_ACCUMULATED_CUES most recent entries`() {
        // Seed a full list at 0us..(MAX-1)us.
        val seed = (0 until MAX_ACCUMULATED_CUES).map {
            TimedCue(it.toLong(), Long.MAX_VALUE, "line $it")
        }
        // New cue far ahead should evict the oldest.
        val result = mergeAccumulatedCues(seed, listOf(TimedCue(10_000_000L, Long.MAX_VALUE, "new")))
        assertEquals(MAX_ACCUMULATED_CUES, result.size)
        assertTrue(result.none { it.text == "line 0" }, "oldest entry evicted")
        assertTrue(result.any { it.text == "new" }, "newest entry present")
    }

    @Test
    fun `isPathologicalCueBatch is false at and under the threshold, true above`() {
        assertFalse(isPathologicalCueBatch(0))
        assertFalse(isPathologicalCueBatch(1))
        assertFalse(isPathologicalCueBatch(MAX_INCOMING_CUES_PER_BATCH))
        assertTrue(isPathologicalCueBatch(MAX_INCOMING_CUES_PER_BATCH + 1))
        assertTrue(isPathologicalCueBatch(10_000))
    }

    @Test
    fun `incoming batch larger than the cap is truncated by the merge`() {
        // A pathological onCues delivery (e.g. a malformed SRT whose lines all
        // parse as simultaneous cues) must not flood the merge/sort. The merge
        // caps incoming at MAX_INCOMING_CUES_PER_BATCH regardless.
        val pathological = (0..MAX_INCOMING_CUES_PER_BATCH + 50).map {
            TimedCue(1_000_000L, Long.MAX_VALUE, "line $it")
        }
        val result = mergeAccumulatedCues(emptyList(), pathological)
        assertEquals(MAX_INCOMING_CUES_PER_BATCH, result.size)
    }
}
