package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Coherence suite for the ONE speed-ladder home ([PlaybackSpeedLadder]): the
 * rungs stay inside the slider range and stay sorted + unique, the audio
 * settings subset stays a subset of the full rungs, the derived slider step
 * count still matches the hand constant it replaced (94), and [format]
 * spells the labels the settings rows hand-rolled before the move.
 */
class PlaybackSpeedLadderTest {

    @Test
    fun testLadderValuesMatchTheLiteralsTheyReplaced() {
        // Lock the ladder to commit e404b42be's rungs — the values the six
        // former sites stated byte-identically.
        assertEquals(
            listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f, 5.0f),
            PlaybackSpeedLadder.fullRungs.toList(),
        )
        assertEquals(
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 5.0f),
            PlaybackSpeedLadder.audioSettingsRungs.toList(),
        )
        assertEquals(0.25f..5.0f, PlaybackSpeedLadder.range)
        assertEquals(0.05f, PlaybackSpeedLadder.stepSize)
    }

    @Test
    fun testEveryRungInsideSliderRange() {
        // Range and rungs are two deliberate concepts (4x stays slider-only),
        // but no rung may fall OUTSIDE the slider span — the slider must be
        // able to reach every chip the sheets offer.
        for (rung in PlaybackSpeedLadder.fullRungs) {
            assertTrue(rung in PlaybackSpeedLadder.range, "fullRung $rung outside ${PlaybackSpeedLadder.range}")
        }
        for (rung in PlaybackSpeedLadder.audioSettingsRungs) {
            assertTrue(rung in PlaybackSpeedLadder.range, "audioSettingsRung $rung outside ${PlaybackSpeedLadder.range}")
        }
    }

    @Test
    fun testRungsSortedAndUnique() {
        for (rungs in listOf(PlaybackSpeedLadder.fullRungs, PlaybackSpeedLadder.audioSettingsRungs)) {
            val list = rungs.toList()
            assertEquals(list.size, list.distinct().size, "duplicate rung in $list")
            assertTrue(
                list.zipWithNext().all { (a, b) -> a < b },
                "rungs not strictly ascending: $list",
            )
        }
    }

    @Test
    fun testAudioSettingsRungsAreASubsetOfFullRungs() {
        val full = PlaybackSpeedLadder.fullRungs.toList()
        for (rung in PlaybackSpeedLadder.audioSettingsRungs) {
            assertTrue(rung in full, "audioSettingsRung $rung missing from fullRungs")
        }
        // The subset is missing exactly 0.25x and 1.75x — the deliberate
        // product choice, not an accident.
        val audio = PlaybackSpeedLadder.audioSettingsRungs.toList()
        assertFalse(0.25f in audio)
        assertFalse(1.75f in audio)
    }

    @Test
    fun testDerivedStepCountMatchesFormerHandConstant() {
        // The dead hand constant was `94 // ((5.0 - 0.25) / 0.05) - 1`;
        // the derivation must land on the same value (roundToInt absorbing
        // the float fuzz of 0.05f).
        assertEquals(94, PlaybackSpeedLadder.stepCount)
    }

    @Test
    fun testFormatSpellsTheFormerHandRolledLabels() {
        // Verbatim from the settings rows' `if (speed == 1.0f) "1x" else
        // "${speed}x"`: 1x spelled bare, other speeds render the float as-is.
        assertEquals("1x", PlaybackSpeedLadder.format(1.0f))
        assertEquals("1.25x", PlaybackSpeedLadder.format(1.25f))
        assertEquals("1.5x", PlaybackSpeedLadder.format(1.5f))
        assertEquals("0.25x", PlaybackSpeedLadder.format(0.25f))
        assertEquals("5.0x", PlaybackSpeedLadder.format(5.0f))
    }
}
