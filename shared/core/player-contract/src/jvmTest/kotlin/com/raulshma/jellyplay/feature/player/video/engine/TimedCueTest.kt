package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the [TimedCue] value type. Ported with the wave-7C review round from
 * the legacy `subtitle`-packaged suite: the sibling `FindActiveCue( s )Test`
 * classes were NOT portable — `SubtitleParserHelper.findActiveCue(s)` stayed
 * in the player-video module's androidMain (media3-coupled), so those tests
 * remain a documented coverage delta there.
 */
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
