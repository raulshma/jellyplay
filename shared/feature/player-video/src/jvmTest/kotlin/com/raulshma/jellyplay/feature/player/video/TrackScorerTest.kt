package com.raulshma.jellyplay.feature.player.video

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class TrackScorerTest {

    private fun cand(label: String, lang: String, idx: Int = -1, candIdx: Int = -1, id: Int = 0) =
        TrackScorer.Candidate(language = lang, label = label, indexWithinLanguage = idx, candidateIndexWithinLanguage = candIdx, optionId = id)

    @Test
    fun `same language and label reaches threshold`() {
        val candidates = listOf(
            cand("English", "eng", id = 1),
            cand("Spanish", "spa", id = 2),
        )
        val winner = TrackScorer.bestMatch("eng", "English", candidates = candidates)
        assertEquals(1, winner?.optionId)
    }

    @Test
    fun `language only falls below threshold and returns null`() {
        // +2 for language, no label/codec/index match → score 2 < 3.
        val candidates = listOf(
            cand("English · Stereo", "eng", id = 1),
        )
        val winner = TrackScorer.bestMatch("eng", "English · 5.1", candidates = candidates)
        assertNull(winner)
    }

    @Test
    fun `language plus positional index reaches threshold`() {
        // +2 language, +1 positional → 3.
        val candidates = listOf(
            cand("English · Stereo", "eng", idx = 0, candIdx = 0, id = 1),
        )
        val winner = TrackScorer.bestMatch("eng", "English · 5.1", candidates = candidates)
        assertEquals(1, winner?.optionId)
    }

    @Test
    fun `subtitle commentary title carries across episodes`() {
        val candidates = listOf(
            cand("English", "eng", id = 1),
            cand("English · Commentary", "eng", id = 2),
            cand("Spanish", "spa", id = 3),
        )
        val winner = TrackScorer.bestMatch("eng", "English · Commentary", candidates = candidates)
        assertEquals(2, winner?.optionId)
    }

    @Test
    fun `codec folded into label contributes via substring branch`() {
        // The codec is part of the label (TrackOption folds it in), so the label
        // substring branch credits it. "English DTS" ⊃ "English" (+1 substring)
        // plus +2 language → 3, clearing the threshold. The removed `lastCodec`
        // param would have double-counted this; this test pins that it isn't.
        val candidates = listOf(
            cand("English DTS", "eng", id = 1),
        )
        val winner = TrackScorer.bestMatch("eng", "English", candidates = candidates)
        assertEquals(1, winner?.optionId)
    }

    @Test
    fun `highest score wins among multiple matches`() {
        val candidates = listOf(
            cand("English", "eng", id = 1),                  // score 2 (lang only)
            cand("English · 5.1", "eng", idx = 0, candIdx = 0, id = 2), // score 2 lang + 2 label + 1 idx = 5
        )
        val winner = TrackScorer.bestMatch("eng", "English · 5.1", candidates = candidates)
        assertEquals(2, winner?.optionId)
    }

    @Test
    fun `empty candidates returns null`() {
        assertNull(TrackScorer.bestMatch("eng", "English", candidates = emptyList()))
    }

    @Test
    fun `case insensitive matching`() {
        val candidates = listOf(cand("ENGLISH", "ENG", id = 1))
        val winner = TrackScorer.bestMatch("eng", "english", candidates = candidates)
        assertEquals(1, winner?.optionId)
    }
}
