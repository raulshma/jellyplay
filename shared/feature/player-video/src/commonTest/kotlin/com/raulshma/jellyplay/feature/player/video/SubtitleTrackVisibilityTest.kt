package com.raulshma.jellyplay.feature.player.video

import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleTrackVisibilityTest {
    private fun track(index: Int, language: String?, selected: Boolean = false) =
        TrackOption(index, "Track $index", language, selected)

    private val off = track(-1, null)
    private val turkish = track(0, "tur")
    private val english = track(1, "en-US")
    private val german = track(2, "ger", selected = true)
    private val unknown = track(3, null)
    private val tracks = listOf(off, turkish, english, german, unknown)

    @Test
    fun disabledFilterPreservesAllTracks() {
        assertEquals(tracks, visibleSubtitleTracks(tracks, false, "tur", emptyList()))
    }

    @Test
    fun combinesPreferredAndOrderedLanguagesWithoutChangingTrackIdentity() {
        assertEquals(
            listOf(off, turkish, english),
            visibleSubtitleTracks(tracks, true, "tr", listOf("eng", "tur")),
        )
        assertEquals(german, tracks.single { it.isSelected })
    }

    @Test
    fun normalizesBibliographicAndRegionalCodes() {
        assertEquals(listOf(off, german), visibleSubtitleTracks(tracks, true, "de-DE", listOf("deu")))
    }

    @Test
    fun missingPreferencesPreserveAllTracks() {
        assertEquals(tracks, visibleSubtitleTracks(tracks, true, null, emptyList()))
        assertEquals(tracks, visibleSubtitleTracks(tracks, true, " ", listOf("und", "")))
    }

    @Test
    fun noMatchingLanguageKeepsOnlyOff() {
        assertEquals(listOf(off), visibleSubtitleTracks(tracks, true, "jpn", emptyList()))
        assertEquals(emptyList(), visibleSubtitleTracks(emptyList(), true, "tur", emptyList()))
    }
}
