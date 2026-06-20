package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.MediaTrack
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackSelectionHelperTest {

    private lateinit var preferencesStore: UserPreferencesStore
    private lateinit var engine: MediaEngine
    private lateinit var availableTracks: MutableStateFlow<List<MediaTrack>>
    private lateinit var state: MutableStateFlow<VideoPlayerUiState>
    private lateinit var helper: TrackSelectionHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        preferencesStore = mockk(relaxed = true)
        every { preferencesStore.preferences } returns MutableStateFlow(UserPreferences())
        engine = mockk(relaxed = true)
        availableTracks = MutableStateFlow(emptyList())
        every { engine.availableTracks } returns availableTracks
        state = MutableStateFlow(VideoPlayerUiState())

        helper = TrackSelectionHelper(
            preferencesStore = preferencesStore,
            getEngine = { engine },
            getUiState = { state.value },
            updateUiState = { transform -> state.value = transform(state.value) },
            getCurrentItemId = { "item1" },
            scope = scope,
        )
    }

    @Test
    fun updateTracksFromEngine_noEngine_returnsEarly() {
        helper = TrackSelectionHelper(
            preferencesStore = preferencesStore,
            getEngine = { null },
            getUiState = { state.value },
            updateUiState = { transform -> state.value = transform(state.value) },
            getCurrentItemId = { "item1" },
            scope = scope,
        )
        helper.updateTracksFromEngine()
        assertTrue(state.value.audioTracks.isEmpty())
    }

    @Test
    fun updateTracksFromEngine_emptyTracks_returnsDefaultAndOffOnly() {
        helper.updateTracksFromEngine()

        assertEquals(1, state.value.audioTracks.size)
        assertEquals("Default", state.value.audioTracks[0].label)
        assertTrue(state.value.audioTracks[0].isSelected)

        // NOTE: production uses "None" for the empty-subtitle placeholder and "Off" for the
        // populated-case header. This test pins the current empty-case behaviour.
        assertEquals(1, state.value.subtitleTracks.size)
        assertEquals("None", state.value.subtitleTracks[0].label)
        assertTrue(state.value.subtitleTracks[0].isSelected)
    }

    @Test
    fun updateTracksFromEngine_audioTracks_prependsDefaultAndMarksEngineAutoSelected() {
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.AUDIO, isSelected = true),
            mediaTrack(1, "Spanish", "spa", TrackType.AUDIO, isSelected = false),
        )
        helper.updateTracksFromEngine()

        val audio = state.value.audioTracks
        assertEquals(3, audio.size) // Default + 2
        assertEquals("Default", audio[0].label)
        assertFalse(audio[0].isSelected) // engine had a selection
        assertTrue(audio[1].isSelected) // English auto-selected by engine
        assertFalse(audio[2].isSelected)
    }

    @Test
    fun updateTracksFromEngine_subtitleTracks_prependsOff() {
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.SUBTITLE, isSelected = true),
        )
        helper.updateTracksFromEngine()

        val subs = state.value.subtitleTracks
        assertEquals(2, subs.size)
        assertEquals("Off", subs[0].label)
        assertTrue(subs[1].isSelected)
    }

    @Test
    fun selectAudioTrack_positiveIndex_callsEngineSelectAndUpdatesState() {
        state.value = VideoPlayerUiState(
            audioTracks = listOf(
                TrackOption(-1, "Default", null, true),
                TrackOption(0, "English", "eng", false),
                TrackOption(1, "Spanish", "spa", false),
            ),
        )
        helper.selectAudioTrack(TrackOption(1, "Spanish", "spa", false))

        verify { engine.selectTrack(TrackType.AUDIO, 1, null) }
        assertFalse(state.value.audioTracks[0].isSelected) // Default
        assertFalse(state.value.audioTracks[1].isSelected) // English
        assertTrue(state.value.audioTracks[2].isSelected)   // Spanish
    }

    @Test
    fun selectAudioTrack_negativeIndex_selectsDefaultAndClearsStoredId() {
        state.value = VideoPlayerUiState(
            audioTracks = listOf(
                TrackOption(-1, "Default", null, false),
                TrackOption(0, "English", "eng", true),
            ),
        )
        helper.selectAudioTrack(TrackOption(-1, "Default", null, true))

        verify { engine.selectTrack(TrackType.AUDIO, -1, null) }
        assertTrue(state.value.audioTracks[0].isSelected)
        assertFalse(state.value.audioTracks[1].isSelected)
    }

    @Test
    fun selectAudioTrack_noEngine_returnsEarly() {
        helper = TrackSelectionHelper(
            preferencesStore = preferencesStore,
            getEngine = { null },
            getUiState = { state.value },
            updateUiState = { transform -> state.value = transform(state.value) },
            getCurrentItemId = { "item1" },
            scope = scope,
        )
        helper.selectAudioTrack(TrackOption(1, "Spanish", "spa", false))
        // No crash, no state change beyond initial
        assertTrue(state.value.audioTracks.isEmpty())
    }

    @Test
    fun selectSubtitleTrack_positiveIndex_callsEngineSelectAndUpdatesState() {
        state.value = VideoPlayerUiState(
            subtitleTracks = listOf(
                TrackOption(-1, "Off", null, true),
                TrackOption(0, "English", "eng", false),
            ),
        )
        helper.selectSubtitleTrack(TrackOption(0, "English", "eng", false))

        verify { engine.selectTrack(TrackType.SUBTITLE, 0, null) }
        assertFalse(state.value.subtitleTracks[0].isSelected)
        assertTrue(state.value.subtitleTracks[1].isSelected)
    }

    @Test
    fun selectSubtitleTrack_negativeIndex_selectsOff() {
        state.value = VideoPlayerUiState(
            subtitleTracks = listOf(
                TrackOption(-1, "Off", null, false),
                TrackOption(0, "English", "eng", true),
            ),
        )
        helper.selectSubtitleTrack(TrackOption(-1, "Off", null, true))

        verify { engine.selectTrack(TrackType.SUBTITLE, -1, null) }
        assertTrue(state.value.subtitleTracks[0].isSelected)
        assertFalse(state.value.subtitleTracks[1].isSelected)
    }

    @Test
    fun updateTracksFromEngine_preferredAudioLanguageMatches_autoSelects() {
        every { preferencesStore.preferences } returns MutableStateFlow(
            UserPreferences(preferredAudioLanguage = "spa"),
        )
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.AUDIO, isSelected = false),
            mediaTrack(1, "Spanish", "spa", TrackType.AUDIO, isSelected = false),
        )
        helper.updateTracksFromEngine()

        verify { engine.selectTrack(TrackType.AUDIO, 1, null) }
    }

    @Test
    fun updateTracksFromEngine_noLanguageMatch_fallsBackToDefault() {
        every { preferencesStore.preferences } returns MutableStateFlow(
            UserPreferences(preferredAudioLanguage = "jpn"),
        )
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.AUDIO, isSelected = false),
        )
        helper.updateTracksFromEngine()

        verify { engine.selectTrack(TrackType.AUDIO, -1, null) }
    }

    @Test
    fun updateTracksFromEngine_pendingAudioIndex_selectsByIndex() {
        state.value = VideoPlayerUiState(
            mediaStreams = listOf(
                MediaStream(index = 1, type = StreamType.AUDIO, displayTitle = "Spanish"),
            ),
        )
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.AUDIO, isSelected = false),
            mediaTrack(1, "Spanish", "spa", TrackType.AUDIO, isSelected = false),
        )
        helper.setPendingStreams(subtitleIndex = null, audioIndex = 1)
        helper.updateTracksFromEngine()

        verify { engine.selectTrack(TrackType.AUDIO, 1, null) }
    }

    @Test
    fun updateTracksFromEngine_pendingAudioNegativeOne_selectsDefault() {
        helper.setPendingStreams(subtitleIndex = null, audioIndex = -1)
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.AUDIO, isSelected = false),
        )
        helper.updateTracksFromEngine()

        verify { engine.selectTrack(TrackType.AUDIO, -1, null) }
    }

    @Test
    fun updateTracksFromEngine_storedSelectionByIndex_resolvesAndSelects() {
        every { preferencesStore.preferences } returns MutableStateFlow(
            UserPreferences(
                mediaStreamSelections = mapOf("item1" to MediaStreamSelection(audioStreamIndex = 1)),
            ),
        )
        state.value = VideoPlayerUiState(
            mediaStreams = listOf(
                MediaStream(index = 1, type = StreamType.AUDIO, displayTitle = "Spanish"),
            ),
        )
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.AUDIO, isSelected = false),
            mediaTrack(1, "Spanish", "spa", TrackType.AUDIO, isSelected = false),
        )
        helper.updateTracksFromEngine()

        verify { engine.selectTrack(TrackType.AUDIO, 1, null) }
    }

    @Test
    fun updateTracksFromEngine_subtitlesForcedOnly_selectsForcedStream() {
        every { preferencesStore.preferences } returns MutableStateFlow(
            UserPreferences(
                preferredSubtitleLanguage = "eng",
                subtitlesForcedOnly = true,
            ),
        )
        state.value = VideoPlayerUiState(
            mediaStreams = listOf(
                MediaStream(index = 0, type = StreamType.SUBTITLE, language = "eng", displayTitle = "English", isForced = true),
            ),
        )
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.SUBTITLE, isSelected = false),
        )
        helper.updateTracksFromEngine()

        verify { engine.selectTrack(TrackType.SUBTITLE, 0, null) }
    }

    @Test
    fun updateTracksFromEngine_reselectsPreviouslyChosenTrackIfBecameUnselected() {
        // First populate, then select track 1, then re-run with it unselected.
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.AUDIO, isSelected = false),
            mediaTrack(1, "Spanish", "spa", TrackType.AUDIO, isSelected = false),
        )
        helper.updateTracksFromEngine()
        helper.selectAudioTrack(TrackOption(1, "Spanish", "spa", false))

        // Now engine reports Spanish unselected; updateTracksFromEngine must re-select it.
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.AUDIO, isSelected = true),
            mediaTrack(1, "Spanish", "spa", TrackType.AUDIO, isSelected = false),
        )
        helper.updateTracksFromEngine()

        // selectTrack(AUDIO, 1) must have been invoked at least once (initial select + reselect)
        verify(atLeast = 1) { engine.selectTrack(TrackType.AUDIO, 1, null) }
    }

    @Test
    fun reset_clearsSelectedTrackId_soLanguageMatchDrivesNextSelection() {
        // preferredAudioLanguage defaults to "eng" when null; use a non-English-only track set so
        // no language match occurs and Default is selected after reset.
        availableTracks.value = listOf(
            mediaTrack(0, "Espanol", "spa", TrackType.AUDIO, isSelected = true),
        )
        helper.selectAudioTrack(TrackOption(0, "Espanol", "spa", false))
        helper.reset()

        helper.updateTracksFromEngine()

        // No stored override (mock store is inert) and no "eng" match → Default selected.
        assertTrue(state.value.audioTracks[0].isSelected) // Default
        assertFalse(state.value.audioTracks[1].isSelected) // Espanol
    }

    @Test
    fun setPendingStreams_storesIndicesForLaterConsumption() {
        helper.setPendingStreams(subtitleIndex = 2, audioIndex = 3)
        // Indices are consumed inside updateTracksFromEngine; verify no crash and state populated.
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.AUDIO, isSelected = false),
        )
        helper.updateTracksFromEngine()
        // Pending audio index 3 has no match → no selection beyond default
        assertEquals(2, state.value.audioTracks.size)
    }

    @Test
    fun resetAudioSelection_clearsStoredAudioSelection_whenNoItemId_returnsEarly() {
        val noItemId = TrackSelectionHelper(
            preferencesStore = preferencesStore,
            getEngine = { engine },
            getUiState = { state.value },
            updateUiState = { transform -> state.value = transform(state.value) },
            getCurrentItemId = { null },
            scope = scope,
        )
        noItemId.resetAudioSelection() // should not throw
    }

    private fun mediaTrack(
        index: Int,
        label: String,
        language: String?,
        type: TrackType,
        isSelected: Boolean,
    ) = MediaTrack(
        id = "$index",
        index = index,
        label = label,
        language = language,
        isSelected = isSelected,
        type = type,
        trackGroup = null,
    )
}
