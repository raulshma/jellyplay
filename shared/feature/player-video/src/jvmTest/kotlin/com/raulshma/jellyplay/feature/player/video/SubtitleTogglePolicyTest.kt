package com.raulshma.jellyplay.feature.player.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the pure toggle decisions ([SubtitleTogglePolicy]): the on/off
 * fold over the selected row, the Off-row fallback when the picker list is
 * momentarily empty, and the restore-resolution ladder (engine index →
 * container stream index → label+language, real tracks only).
 */
class SubtitleTogglePolicyTest {

    private val off = TrackOption(-1, "Off", null, true)
    private val english = TrackOption(0, "English", "eng", false, streamIndex = 3)
    private val spanish = TrackOption(1, "Spanish", "spa", false, streamIndex = 4)

    @Test
    fun isOn_trueOnlyForRealTracks() {
        assertTrue(SubtitleTogglePolicy.isOn(english))
        assertFalse(SubtitleTogglePolicy.isOn(off))
        assertFalse(SubtitleTogglePolicy.isOn(null))
    }

    @Test
    fun selectedOption_returnsTheFlaggedRow() {
        val selected = english.copy(isSelected = true)
        val tracks = listOf(off.copy(isSelected = false), selected, spanish)
        assertEquals(selected, SubtitleTogglePolicy.selectedOption(tracks))
        // No row flagged at all (the Off placeholder unflagged too).
        assertNull(
            SubtitleTogglePolicy.selectedOption(listOf(off.copy(isSelected = false), english, spanish)),
        )
    }

    @Test
    fun offOption_prefersTheListPlaceholder_fallsBackToSynthetic() {
        assertEquals(off, SubtitleTogglePolicy.offOption(listOf(off, english)))
        assertEquals(
            TrackOption(-1, "Off", null, true),
            SubtitleTogglePolicy.offOption(emptyList()),
        )
    }

    @Test
    fun resolveRestore_matchesByLabelAndLanguage() {
        val remembered = spanish
        assertEquals(
            spanish.copy(isSelected = true),
            SubtitleTogglePolicy.resolveRestore(remembered, listOf(off, english, spanish.copy(isSelected = true))),
        )
    }

    @Test
    fun resolveRestore_survivesRenumbering_byLabel() {
        val renumbered = TrackOption(7, "Spanish", "spa", false)
        assertEquals(
            renumbered,
            SubtitleTogglePolicy.resolveRestore(spanish, listOf(off, english, renumbered)),
        )
    }

    @Test
    fun resolveRestore_blankLabel_fallsBackToStreamIndex() {
        val remembered = TrackOption(9, "", null, false, streamIndex = 4)
        assertEquals(
            spanish,
            SubtitleTogglePolicy.resolveRestore(remembered, listOf(off, english, spanish)),
        )
    }

    @Test
    fun resolveRestore_doesNotMatchADifferentTrackAtTheOldIndex() {
        // A re-published / item-switched list put another track at the
        // remembered position: without a label+language or stream-index
        // identity match, the restore no-ops rather than selecting a wrong
        // track (the toggle restores EXACTLY the remembered track).
        val remembered = TrackOption(0, "English", "eng", false)
        assertNull(
            SubtitleTogglePolicy.resolveRestore(remembered, listOf(off, spanish.copy(index = 0))),
        )
    }

    @Test
    fun resolveRestore_noMemoryOrUnresolvable_returnsNull() {
        assertNull(SubtitleTogglePolicy.resolveRestore(null, listOf(off, english)))
        assertNull(SubtitleTogglePolicy.resolveRestore(spanish, listOf(off, english)))
    }

    @Test
    fun resolveRestore_neverRestoresAnOffRow() {
        assertNull(
            SubtitleTogglePolicy.resolveRestore(TrackOption(-1, "Off", null, true), listOf(off, english)),
        )
    }
}
