package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.engine.TrackBadge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the per-series track-preference intent derivations
 * ([SeriesPreferenceIntent.kt]) that the audio-picker and subtitle-hub
 * footers used to inline inside their `onToggle` lambdas: which selected row
 * maps to which writer dispatch, and which role badges a remembered subtitle
 * track pins.
 */
class SeriesPreferenceIntentTest {

    private fun track(
        index: Int,
        language: String?,
        selected: Boolean = false,
        badges: List<TrackBadge> = emptyList(),
    ) = TrackOption(
        index = index,
        label = "Track $index",
        language = language,
        isSelected = selected,
        badges = badges,
    )

    // ── Audio footer ─────────────────────────────────────────────────────────

    @Test
    fun `audio remember pins the selected real track's language`() {
        val tracks = listOf(
            track(index = 0, language = "eng"),
            track(index = 1, language = "jpn", selected = true),
        )
        assertEquals("jpn", seriesAudioPreferenceIntent(tracks, remember = true))
    }

    @Test
    fun `audio remember ignores the Off row and unselected tracks`() {
        // Only the "Off" row (index < 0) is selected — nothing to remember.
        val offOnly = listOf(track(index = -1, language = null, selected = true))
        assertEquals(null, seriesAudioPreferenceIntent(offOnly, remember = true))

        val noneSelected = listOf(track(index = 0, language = "eng"))
        assertEquals(null, seriesAudioPreferenceIntent(noneSelected, remember = true))
    }

    @Test
    fun `audio unremember always forgets`() {
        val tracks = listOf(track(index = 0, language = "eng", selected = true))
        assertEquals(null, seriesAudioPreferenceIntent(tracks, remember = false))
    }

    // ── Subtitle footer: dispatch intents ────────────────────────────────────

    @Test
    fun `selected Off row routes to the off intent carrying the toggle state`() {
        val tracks = listOf(
            track(index = 2, language = "eng"),
            track(index = -1, language = null, selected = true),
        )
        assertEquals(
            SeriesSubtitlePrefIntent.Off(disabled = true),
            seriesSubtitlePreferenceIntent(tracks, remember = true),
        )
        assertEquals(
            SeriesSubtitlePrefIntent.Off(disabled = false),
            seriesSubtitlePreferenceIntent(tracks, remember = false),
        )
    }

    @Test
    fun `selected track with forced and SDH badges pins language plus both roles`() {
        val tracks = listOf(
            track(index = 1, language = "eng", selected = true, badges = listOf(TrackBadge.FORCED, TrackBadge.SDH)),
        )
        assertEquals(
            SeriesSubtitlePrefIntent.Track(language = "eng", forced = true, hearingImpaired = true),
            seriesSubtitlePreferenceIntent(tracks, remember = true),
        )
    }

    @Test
    fun `selected track without role badges pins the language with null roles`() {
        val tracks = listOf(
            track(index = 1, language = "spa", selected = true, badges = listOf(TrackBadge.DEFAULT)),
        )
        assertEquals(
            SeriesSubtitlePrefIntent.Track(language = "spa", forced = null, hearingImpaired = null),
            seriesSubtitlePreferenceIntent(tracks, remember = true),
        )
    }

    @Test
    fun `remember with no selection degrades to a null-language track`() {
        // The writer's null-language convention turns this into a forget —
        // pinned here so the degradation stays visible at the seam.
        val tracks = listOf(track(index = 1, language = "eng"))
        assertEquals(
            SeriesSubtitlePrefIntent.Track(language = null, forced = null, hearingImpaired = null),
            seriesSubtitlePreferenceIntent(tracks, remember = true),
        )
    }

    @Test
    fun `unremember with a track selected forgets`() {
        val tracks = listOf(track(index = 1, language = "eng", selected = true))
        assertEquals(
            SeriesSubtitlePrefIntent.Forget,
            seriesSubtitlePreferenceIntent(tracks, remember = false),
        )
    }

    // ── Subtitle footer: label choice ────────────────────────────────────────

    @Test
    fun `off label while the Off row is selected or an off-intent is saved`() {
        val offSelected = listOf(track(index = -1, language = null, selected = true))
        val trackSelected = listOf(track(index = 1, language = "eng", selected = true))
        assertTrue(seriesSubtitlePrefersOffLabel(offSelected, hasSeriesSubtitleOffPref = false))
        assertTrue(seriesSubtitlePrefersOffLabel(trackSelected, hasSeriesSubtitleOffPref = true))
        assertFalse(seriesSubtitlePrefersOffLabel(trackSelected, hasSeriesSubtitleOffPref = false))
    }
}
