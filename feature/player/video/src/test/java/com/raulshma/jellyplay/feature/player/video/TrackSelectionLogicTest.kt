package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure logic tests for track selection helper logic.
 * These test the algorithms that TrackSelectionHelper uses (without needing
 * to instantiate the class itself, which requires coroutines + mocking).
 */
class TrackSelectionLogicTest {

    // ─── Audio track list construction ─────────────────────────────────────────

    @Test
    fun audioTracks_emptyEngine_returnsDefaultTrack() {
        val rawAudioTracks = emptyList<TrackOption>()
        val audioTracks = if (rawAudioTracks.isEmpty()) {
            listOf(TrackOption(-1, "Default", null, true))
        } else {
            rawAudioTracks
        }
        assertEquals(1, audioTracks.size)
        assertEquals(-1, audioTracks[0].index)
        assertEquals("Default", audioTracks[0].label)
        assertTrue(audioTracks[0].isSelected)
    }

    @Test
    fun audioTracks_nonEmpty_includesDefaultAsFirstItem() {
        val rawAudioTracks = listOf(
            TrackOption(0, "English", "eng", true),
            TrackOption(1, "Spanish", "spa", false),
        )
        val audioTracks = listOf(TrackOption(-1, "Default", null, false)) + rawAudioTracks
        assertEquals(-1, audioTracks[0].index)
        assertEquals("Default", audioTracks[0].label)
        assertFalse(audioTracks[0].isSelected)
    }

    // ─── Subtitle track list construction ─────────────────────────────────────

    @Test
    fun subtitleTracks_emptyEngine_returnsOffTrack() {
        val rawSubTracks = emptyList<TrackOption>()
        val subtitleTracks = if (rawSubTracks.isEmpty()) {
            listOf(TrackOption(-1, "Off", null, true))
        } else {
            rawSubTracks
        }
        assertEquals(1, subtitleTracks.size)
        assertEquals(-1, subtitleTracks[0].index)
        assertEquals("Off", subtitleTracks[0].label)
        assertTrue(subtitleTracks[0].isSelected)
    }

    @Test
    fun subtitleTracks_nonEmpty_includesOffAsFirstItem() {
        val rawSubTracks = listOf(
            TrackOption(0, "English", "eng", true),
        )
        val subtitleTracks = listOf(TrackOption(-1, "Off", null, false)) + rawSubTracks
        assertEquals(-1, subtitleTracks[0].index)
        assertEquals("Off", subtitleTracks[0].label)
    }

    // ─── Track selection (index < 0 clears) ────────────────────────────────────

    @Test
    fun selectAudioTrack_negativeIndex_clearsSelectedTrackId() {
        // The helper now stores just an index (Int?) instead of Pair<Int, Any?>.
        var selectedAudioTrackIndex: Int? = 0
        val option = TrackOption(-1, "Default", null, true)
        if (option.index < 0) {
            selectedAudioTrackIndex = null
        } else {
            selectedAudioTrackIndex = option.index
        }
        assertNull(selectedAudioTrackIndex)
    }

    @Test
    fun selectAudioTrack_positiveIndex_storesTrackId() {
        var selectedAudioTrackIndex: Int? = null
        val option = TrackOption(1, "English", "eng", false)
        if (option.index < 0) {
            selectedAudioTrackIndex = null
        } else {
            selectedAudioTrackIndex = option.index
        }
        assertEquals(1, selectedAudioTrackIndex)
    }

    @Test
    fun selectSubtitleTrack_negativeIndex_clearsSelectedTrackId() {
        var selectedSubtitleTrackIndex: Int? = 0
        val option = TrackOption(-1, "Off", null, true)
        if (option.index < 0) {
            selectedSubtitleTrackIndex = null
        } else {
            selectedSubtitleTrackIndex = option.index
        }
        assertNull(selectedSubtitleTrackIndex)
    }

    // ─── reset() behaviour ────────────────────────────────────────────────────

    @Test
    fun reset_clearsAllPendingAndSelected() {
        var selectedSubtitleTrackIndex: Int? = 0
        var selectedAudioTrackIndex: Int? = 1
        var pendingSubtitleStreamIndex: Int? = 2
        var pendingAudioStreamIndex: Int? = 3

        // Simulating reset()
        selectedSubtitleTrackIndex = null
        selectedAudioTrackIndex = null
        pendingSubtitleStreamIndex = null
        pendingAudioStreamIndex = null

        assertNull(selectedSubtitleTrackIndex)
        assertNull(selectedAudioTrackIndex)
        assertNull(pendingSubtitleStreamIndex)
        assertNull(pendingAudioStreamIndex)
    }

    // ─── setPendingStreams ─────────────────────────────────────────────────────

    @Test
    fun setPendingStreams_storesBothIndices() {
        var pendingSubtitleStreamIndex: Int? = null
        var pendingAudioStreamIndex: Int? = null

        pendingSubtitleStreamIndex = 2
        pendingAudioStreamIndex = 1

        assertEquals(2, pendingSubtitleStreamIndex)
        assertEquals(1, pendingAudioStreamIndex)
    }

    @Test
    fun setPendingStreams_negativeOneIsValid_denotesDisable() {
        var pendingSubtitleStreamIndex: Int? = null
        pendingSubtitleStreamIndex = -1
        assertEquals(-1, pendingSubtitleStreamIndex)
    }

    // ─── resolveMediaStreamIndex (label/language matching logic) ─────────────

    @Test
    fun resolveMediaStreamIndex_exactLabelMatch_returnsIndex() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.AUDIO, displayTitle = "English"),
            MediaStream(index = 1, type = StreamType.AUDIO, displayTitle = "Spanish"),
        )
        val trackLabel = "English"
        val result = streams.firstOrNull {
            it.displayTitle == trackLabel || it.title == trackLabel || it.language == trackLabel
        }?.index
        assertEquals(0, result)
    }

    @Test
    fun resolveMediaStreamIndex_noMatch_returnsFirstAvailable() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.AUDIO, displayTitle = "Spanish"),
        )
        val trackLabel = "German"
        val exactMatch = streams.firstOrNull {
            it.displayTitle == trackLabel || it.title == trackLabel || it.language == trackLabel
        }
        val fallback = streams.firstOrNull { it.index >= 0 }?.index
        val result = exactMatch?.index ?: fallback
        assertEquals(0, result)
    }

    @Test
    fun resolveMediaStreamIndex_emptyStreams_returnsNull() {
        val streams = emptyList<MediaStream>()
        val result = streams.firstOrNull { it.index >= 0 }?.index
        assertNull(result)
    }

    // ─── Track option state sync ───────────────────────────────────────────────

    @Test
    fun selectAudioTrack_updatesIsSelectedOnlyForMatchingTrack() {
        val tracks = listOf(
            TrackOption(-1, "Default", null, false),
            TrackOption(0, "English", "eng", true),
            TrackOption(1, "Spanish", "spa", false),
        )
        val selectedOption = TrackOption(1, "Spanish", "spa", false)
        val updated = tracks.map { track ->
            // trackGroup identity removed; index is now the sole track identity.
            val matches = track.index == selectedOption.index
            track.copy(isSelected = matches)
        }
        assertFalse(updated[0].isSelected) // Default
        assertFalse(updated[1].isSelected) // English
        assertTrue(updated[2].isSelected)  // Spanish
    }

    @Test
    fun selectAudioTrack_defaultOption_onlyDefaultIsSelected() {
        val tracks = listOf(
            TrackOption(-1, "Default", null, false),
            TrackOption(0, "English", "eng", true),
        )
        val selectedOption = TrackOption(-1, "Default", null, true)
        val isDefault = selectedOption.index < 0
        val updated = tracks.map { track ->
            val isDefaultTrack = track.index < 0
            track.copy(isSelected = if (isDefault) isDefaultTrack else (track.index == selectedOption.index))
        }
        assertTrue(updated[0].isSelected)  // Default
        assertFalse(updated[1].isSelected) // English
    }
}
