package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.engine.TrackBadge
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

/**
 * Tiered relaxation for the "remember subtitle for series" preference. Each test
 * names the tier exercised so the relaxation contract is documented by the cases.
 */
class SubtitleTrackMatcherTest {

    private fun sub(
        index: Int,
        lang: String? = "eng",
        badges: List<TrackBadge> = emptyList(),
    ) = TrackOption(index = index, label = "sub-$index", language = lang, isSelected = false, badges = badges)

    private val off = TrackOption(index = -1, label = "Off", language = null, isSelected = false)

    // --- Tier 1: exact descriptor ------------------------------------------

    @Test
    fun `tier1 exact match returns sdh track when sdh pinned`() {
        val tracks = listOf(off, sub(0), sub(1, badges = listOf(TrackBadge.SDH)))
        val match = SubtitleTrackMatcher.match(tracks, "eng", forced = null, hearingImpaired = true)
        assertEquals(1, match?.index)
    }

    @Test
    fun `tier1 exact match returns forced track when forced pinned`() {
        val tracks = listOf(off, sub(0, badges = listOf(TrackBadge.FORCED)), sub(1))
        val match = SubtitleTrackMatcher.match(tracks, "eng", forced = true, hearingImpaired = null)
        assertEquals(0, match?.index)
    }

    @Test
    fun `tier1 exact match honours forced=false intent`() {
        // Pinned "not forced": a plain track wins over a forced one.
        val tracks = listOf(off, sub(0, badges = listOf(TrackBadge.FORCED)), sub(1))
        val match = SubtitleTrackMatcher.match(tracks, "eng", forced = false, hearingImpaired = null)
        assertEquals(1, match?.index)
    }

    // --- Tier 2: relax SDH -------------------------------------------------

    @Test
    fun `tier2 relaxes sdh when no sdh track present`() {
        // Pinned "English SDH" but episode has only plain English → plain wins.
        val tracks = listOf(off, sub(0))
        val match = SubtitleTrackMatcher.match(tracks, "eng", forced = null, hearingImpaired = true)
        assertEquals(0, match?.index)
    }

    @Test
    fun `tier2 keeps forced-role when relaxing sdh`() {
        // Pinned "English Forced SDH", episode has English Forced (not SDH) +
        // plain English → forced one wins (forced-role preserved), not plain.
        val tracks = listOf(off, sub(0), sub(1, badges = listOf(TrackBadge.FORCED)))
        val match = SubtitleTrackMatcher.match(tracks, "eng", forced = true, hearingImpaired = true)
        assertEquals(1, match?.index)
    }

    // --- Tier 3: language only --------------------------------------------

    @Test
    fun `tier3 falls back to language-only when forced role absent`() {
        // Pinned "English Forced" but episode has no forced track → plain English.
        val tracks = listOf(off, sub(0))
        val match = SubtitleTrackMatcher.match(tracks, "eng", forced = true, hearingImpaired = null)
        assertEquals(0, match?.index)
    }

    @Test
    fun `legacy all-null intent matches any same-language track`() {
        // Pre-migration row: both role fields null → equivalent to today's
        // firstOrNull(language) behaviour, but with a deterministic tiebreak.
        val tracks = listOf(off, sub(0), sub(1, badges = listOf(TrackBadge.SDH)))
        val match = SubtitleTrackMatcher.match(tracks, "eng", forced = null, hearingImpaired = null)
        // No DEFAULT badge anywhere → lowest index wins.
        assertEquals(0, match?.index)
    }

    // --- "Don't care" role intent (the remember-subtitle save contract) ----

    @Test
    fun `null role intent does not exclude sdh track`() {
        // Regression: the save path pins a role only when the badge is present
        // (null ⇒ "don't care"). A plain track saved with null/null must still
        // match an SDH track when that is the only same-language option — the
        // matcher must NOT treat null as "must not be SDH".
        val tracks = listOf(off, sub(0, badges = listOf(TrackBadge.SDH)))
        val match = SubtitleTrackMatcher.match(tracks, "eng", forced = null, hearingImpaired = null)
        assertEquals(0, match?.index)
    }

    @Test
    fun `null role intent does not exclude forced track`() {
        val tracks = listOf(off, sub(0, badges = listOf(TrackBadge.FORCED)))
        val match = SubtitleTrackMatcher.match(tracks, "eng", forced = null, hearingImpaired = null)
        assertEquals(0, match?.index)
    }

    @Test
    fun `null sdh intent still matches sdh track when both available`() {
        // Picking between a plain and an SDH track with null/ null intent: the
        // exact tier is satisfied by every candidate (null never excludes), so
        // the tiebreak picks the plain (non-badged, lower-indexed) track.
        val tracks = listOf(off, sub(0), sub(1, badges = listOf(TrackBadge.SDH)))
        val match = SubtitleTrackMatcher.match(tracks, "eng", forced = null, hearingImpaired = null)
        assertEquals(0, match?.index)
    }

    // --- No match ----------------------------------------------------------

    @Test
    fun `returns null when no same-language track exists`() {
        val tracks = listOf(off, sub(0, lang = "jpn"))
        val match = SubtitleTrackMatcher.match(tracks, "eng", forced = null, hearingImpaired = null)
        assertNull(match)
    }

    @Test
    fun `returns null for empty track list`() {
        val match = SubtitleTrackMatcher.match(emptyList(), "eng", forced = null, hearingImpaired = null)
        assertNull(match)
    }

    @Test
    fun `ignores off and negative-index tracks`() {
        // Off (index -1) and synthetic markers must never be matched, even when
        // their language accidentally aligns.
        val tracks = listOf(off, TrackOption(-2, "Default", null, false))
        val match = SubtitleTrackMatcher.match(tracks, "eng", forced = null, hearingImpaired = null)
        assertNull(match)
    }

    // --- Tiebreaks ---------------------------------------------------------

    @Test
    fun `tiebreak prefers default-badged track`() {
        // Two exact "English SDH" candidates: the DEFAULT-badged one wins.
        val tracks = listOf(
            off,
            sub(0, badges = listOf(TrackBadge.SDH)),
            sub(1, badges = listOf(TrackBadge.SDH, TrackBadge.DEFAULT)),
        )
        val match = SubtitleTrackMatcher.match(tracks, "eng", forced = null, hearingImpaired = true)
        assertEquals(1, match?.index)
    }

    @Test
    fun `tiebreak prefers lowest index when no default badge`() {
        val tracks = listOf(
            off,
            sub(5, badges = listOf(TrackBadge.SDH)),
            sub(2, badges = listOf(TrackBadge.SDH)),
        )
        val match = SubtitleTrackMatcher.match(tracks, "eng", forced = null, hearingImpaired = true)
        assertEquals(2, match?.index)
    }
}
