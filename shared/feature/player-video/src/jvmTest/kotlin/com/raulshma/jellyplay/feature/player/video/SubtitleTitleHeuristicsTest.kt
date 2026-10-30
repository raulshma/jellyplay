package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.SubtitleTitleClass
import com.raulshma.jellyplay.core.model.SubtitleTitleHeuristics.classifySubtitleTitle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The [classifySubtitleTitle] heuristics table (issue #23): one row per title
 * shape the rule engine's content-kind discrimination must tell apart, plus
 * the case-insensitivity / bracket-style / word-boundary guards.
 */
class SubtitleTitleHeuristicsTest {

    private fun classify(title: String?) = classifySubtitleTitle(title)

    // ── The core table ──────────────────────────────────────────────────────

    @Test
    fun `null and blank titles classify unknown`() {
        assertEquals(SubtitleTitleClass.UNKNOWN, classify(null))
        assertEquals(SubtitleTitleClass.UNKNOWN, classify(""))
        assertEquals(SubtitleTitleClass.UNKNOWN, classify("   "))
    }

    @Test
    fun `plain language titles classify unknown`() {
        assertEquals(SubtitleTitleClass.UNKNOWN, classify("English"))
        assertEquals(SubtitleTitleClass.UNKNOWN, classify("Deutsch"))
    }

    @Test
    fun `full-dialogue and sdh titles classify full`() {
        assertEquals(SubtitleTitleClass.FULL_SUBTITLE, classify("English SDH"))
        assertEquals(SubtitleTitleClass.FULL_SUBTITLE, classify("[SDH] English"))
        assertEquals(SubtitleTitleClass.FULL_SUBTITLE, classify("English (CC)"))
        assertEquals(SubtitleTitleClass.FULL_SUBTITLE, classify("English hearing impaired"))
        assertEquals(SubtitleTitleClass.FULL_SUBTITLE, classify("English dialogue"))
    }

    @Test
    fun `signs and songs titles classify signs`() {
        assertEquals(SubtitleTitleClass.SIGNS_SONGS, classify("Signs & Songs"))
        assertEquals(SubtitleTitleClass.SIGNS_SONGS, classify("Signs and Songs"))
        assertEquals(SubtitleTitleClass.SIGNS_SONGS, classify("Songs & Signs"))
        assertEquals(SubtitleTitleClass.SIGNS_SONGS, classify("English (Signs)"))
        assertEquals(SubtitleTitleClass.SIGNS_SONGS, classify("Signs & Songs - English - text/x-ssa"))
        assertEquals(SubtitleTitleClass.SIGNS_SONGS, classify("Opening & Ending"))
        assertEquals(SubtitleTitleClass.SIGNS_SONGS, classify("Lyrics"))
        assertEquals(SubtitleTitleClass.SIGNS_SONGS, classify("Songs track"))
    }

    @Test
    fun `commentary titles classify commentary`() {
        assertEquals(SubtitleTitleClass.COMMENTARY, classify("English (Commentary)"))
        assertEquals(SubtitleTitleClass.COMMENTARY, classify("Director's Commentary"))
        // Commentary outranks the other markers: a commentary track may also
        // carry SDH or signs text — the commentary intent is load-bearing.
        assertEquals(SubtitleTitleClass.COMMENTARY, classify("English SDH (Commentary)"))
        assertEquals(SubtitleTitleClass.COMMENTARY, classify("Commentary with Signs"))
    }

    @Test
    fun `forced titles classify forced`() {
        assertEquals(SubtitleTitleClass.FORCED, classify("English Forced"))
        assertEquals(SubtitleTitleClass.FORCED, classify("English (forced)"))
    }

    // ── Guards ──────────────────────────────────────────────────────────────

    @Test
    fun `classification is case-insensitive`() {
        assertEquals(SubtitleTitleClass.SIGNS_SONGS, classify("SIGNS & SONGS"))
        assertEquals(SubtitleTitleClass.COMMENTARY, classify("COMMENTARY"))
        assertEquals(SubtitleTitleClass.FULL_SUBTITLE, classify("sdh"))
        assertEquals(SubtitleTitleClass.FORCED, classify("FORCED"))
    }

    @Test
    fun `markers are word-bounded`() {
        // "legendary" contains "ending", "editor" contains "ed" — neither is
        // a signs/songs title. Same word-boundary contract the signs regex
        // shipped with.
        assertEquals(SubtitleTitleClass.UNKNOWN, classify("legendary"))
        assertEquals(SubtitleTitleClass.UNKNOWN, classify("editor"))
        // "accept" contains "cc" only across a boundary — not an SDH title.
        assertEquals(SubtitleTitleClass.UNKNOWN, classify("accept"))
    }

    @Test
    fun `signs outranks forced when both markers appear`() {
        // "Signs & Songs (forced)" — the signs intent is the more specific
        // description of what the track carries.
        assertEquals(SubtitleTitleClass.SIGNS_SONGS, classify("Signs & Songs (forced)"))
    }
}
