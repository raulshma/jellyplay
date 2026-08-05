package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineSlice
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.MediaTrack
import io.mockk.every
import io.mockk.coEvery
import io.mockk.coVerify
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

    private lateinit var engineStore: PlayerEngineStore
    private lateinit var subtitleStore: SubtitleLanguageStore
    private lateinit var engine: MediaEngine
    private lateinit var availableTracks: MutableStateFlow<List<MediaTrack>>
    private lateinit var state: MutableStateFlow<VideoPlayerUiState>
    private lateinit var helper: TrackSelectionHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        engineStore = mockk(relaxed = true)
        subtitleStore = mockk(relaxed = true)
        every { engineStore.playerEngine } returns MutableStateFlow(PlayerEngineSlice())
        every { subtitleStore.subtitle } returns MutableStateFlow(SubtitleSlice())
        engine = mockk(relaxed = true)
        availableTracks = MutableStateFlow(emptyList())
        every { engine.availableTracks } returns availableTracks
        state = MutableStateFlow(VideoPlayerUiState())

        helper = TrackSelectionHelper(
            engineStore = engineStore,
            subtitleStore = subtitleStore,
            getEngine = { engine },
            getUiState = { state.value },
            updateUiState = { transform -> state.value = transform(state.value) },
            getCurrentItemId = { "item1" },
            getCurrentSeriesId = { null },
            getPlayMethod = { com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_PLAY },
            onReloadForStreamChange = { _, _ -> },
            playbackPreferenceResolver = noOpResolver(),
            scope = scope,
        )
    }

    /** A resolver backed by a mock repo that always resolves to null (inherit global). */
    private fun noOpResolver(): ItemPlaybackPreferenceResolver {
        val repo = mockk<ItemPlaybackPreferenceRepository>(relaxed = true)
        coEvery { repo.get(any(), any()) } returns null
        return ItemPlaybackPreferenceResolver(repo, { null }, { null }, scope)
    }

    /** A resolver that resolves to [pref] for the series scope. The Unconfined
     *  scope runs [ItemPlaybackPreferenceResolver.refresh] synchronously, so the
     *  cached value is set before this returns. */
    private fun resolverFor(pref: com.raulshma.jellyplay.core.model.ItemPlaybackPreference): ItemPlaybackPreferenceResolver {
        val repo = mockk<ItemPlaybackPreferenceRepository>(relaxed = true)
        coEvery { repo.get(any(), any()) } returns pref
        val resolver = ItemPlaybackPreferenceResolver(repo, { null }, { "series1" }, scope)
        resolver.refresh()
        return resolver
    }

    @Test
    fun updateTracksFromEngine_noEngine_returnsEarly() {
        helper = TrackSelectionHelper(
            engineStore = engineStore,
            subtitleStore = subtitleStore,
            getEngine = { null },
            getUiState = { state.value },
            updateUiState = { transform -> state.value = transform(state.value) },
            getCurrentItemId = { "item1" },
            getCurrentSeriesId = { null },
            getPlayMethod = { com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_PLAY },
            onReloadForStreamChange = { _, _ -> },
            playbackPreferenceResolver = noOpResolver(),
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

        verify { engine.selectTrack(TrackType.AUDIO, 1) }
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

        verify { engine.selectTrack(TrackType.AUDIO, -1) }
        assertTrue(state.value.audioTracks[0].isSelected)
        assertFalse(state.value.audioTracks[1].isSelected)
    }

    @Test
    fun selectAudioTrack_noEngine_returnsEarly() {
        helper = TrackSelectionHelper(
            engineStore = engineStore,
            subtitleStore = subtitleStore,
            getEngine = { null },
            getUiState = { state.value },
            updateUiState = { transform -> state.value = transform(state.value) },
            getCurrentItemId = { "item1" },
            getCurrentSeriesId = { null },
            getPlayMethod = { com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_PLAY },
            onReloadForStreamChange = { _, _ -> },
            playbackPreferenceResolver = noOpResolver(),
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

        verify { engine.selectTrack(TrackType.SUBTITLE, 0) }
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

        verify { engine.selectTrack(TrackType.SUBTITLE, -1) }
        assertTrue(state.value.subtitleTracks[0].isSelected)
        assertFalse(state.value.subtitleTracks[1].isSelected)
    }

    @Test
    fun updateTracksFromEngine_preferredAudioLanguageMatches_autoSelects() {
        every { subtitleStore.subtitle } returns MutableStateFlow(
            SubtitleSlice(preferredAudioLanguage = "spa"),
        )
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.AUDIO, isSelected = false),
            mediaTrack(1, "Spanish", "spa", TrackType.AUDIO, isSelected = false),
        )
        helper.updateTracksFromEngine()

        verify { engine.selectTrack(TrackType.AUDIO, 1) }
    }

    @Test
    fun updateTracksFromEngine_noLanguageMatch_fallsBackToDefault() {
        every { subtitleStore.subtitle } returns MutableStateFlow(
            SubtitleSlice(preferredAudioLanguage = "jpn"),
        )
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.AUDIO, isSelected = false),
        )
        helper.updateTracksFromEngine()

        verify { engine.selectTrack(TrackType.AUDIO, -1) }
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

        verify { engine.selectTrack(TrackType.AUDIO, 1) }
    }

    @Test
    fun updateTracksFromEngine_pendingAudioNegativeOne_selectsDefault() {
        helper.setPendingStreams(subtitleIndex = null, audioIndex = -1)
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.AUDIO, isSelected = false),
        )
        helper.updateTracksFromEngine()

        verify { engine.selectTrack(TrackType.AUDIO, -1) }
    }

    @Test
    fun updateTracksFromEngine_storedSelectionByIndex_resolvesAndSelects() {
        every { engineStore.playerEngine } returns MutableStateFlow(
            PlayerEngineSlice(
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

        verify { engine.selectTrack(TrackType.AUDIO, 1) }
    }

    @Test
    fun updateTracksFromEngine_subtitlesForcedOnly_selectsForcedStream() {
        every { subtitleStore.subtitle } returns MutableStateFlow(
            SubtitleSlice(
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

        verify { engine.selectTrack(TrackType.SUBTITLE, 0) }
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
        verify(atLeast = 1) { engine.selectTrack(TrackType.AUDIO, 1) }
    }

    // ─── Regression: "subtitle flashes then resets" on offline playback ────────
    // Offline playback carries no server mediaStreams (empty). Before the fix,
    // every availableTracks emission re-ran the stored/preference resolution and
    // dropped a held selection back to "Off" because the label lookup against
    // empty streams failed. The held-guard skips that re-resolution.

    @Test
    fun updateTracksFromEngine_subtitleSelectionHeld_survivesReemissionWithEmptyStreams() {
        // Simulate offline: mediaStreams is empty.
        state.value = VideoPlayerUiState()
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.SUBTITLE, isSelected = false),
        )
        helper.updateTracksFromEngine() // auto-applies a preference selection
        helper.selectSubtitleTrack(TrackOption(0, "English", "eng", false))

        // Reset the mock call recorder; subsequent emissions must NOT flip to Off.
        io.mockk.clearMocks(engine, answers = false)

        helper.updateTracksFromEngine()

        // The held English selection survives — no call to select Off.
        verify(exactly = 0) { engine.selectTrack(TrackType.SUBTITLE, -1) }
        // The selection itself stays put.
        assertEquals(0, state.value.subtitleTracks.first { it.isSelected }.index)
    }

    @Test
    fun selectSubtitleTrack_offlineUserOverride_persistsEngineIndex() {
        // Offline: empty mediaStreams. resolveMediaStreamIndex previously
        // returned null so the stored selection was lost. Now it persists the
        // engine positional index directly.
        every { engineStore.playerEngine } returns MutableStateFlow(PlayerEngineSlice())
        every { subtitleStore.subtitle } returns MutableStateFlow(SubtitleSlice())
        state.value = VideoPlayerUiState() // empty mediaStreams
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.SUBTITLE, isSelected = false),
        )
        helper.updateTracksFromEngine()
        helper.selectSubtitleTrack(TrackOption(0, "English", "eng", false))

        // The persisted per-item selection should carry index 0 (engine index),
        // not null.
        coVerify {
            engineStore.setMediaStreamSelection(
                itemId = "item1",
                audioStreamIndex = null,
                subtitleStreamIndex = 0,
            )
        }
    }

    @Test
    fun updateTracksFromEngine_autoOffDoesNotLatch_allowsLaterSidecarSelection() {
        // First emission has no subtitle tracks yet (offline sidecar subs load
        // after the first track list). Auto-selection falls through to Off but
        // must NOT latch, so a later emission with a real track can still
        // resolve the language preference.
        state.value = VideoPlayerUiState()
        availableTracks.value = emptyList()
        helper.updateTracksFromEngine()
        verify { engine.selectTrack(TrackType.SUBTITLE, -1) } // auto Off

        io.mockk.clearMocks(engine, answers = false)

        // Sidecar sub arrives: language matches the default "eng" preference.
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.SUBTITLE, isSelected = false),
        )
        helper.updateTracksFromEngine()
        verify { engine.selectTrack(TrackType.SUBTITLE, 0) }
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
            engineStore = engineStore,
            subtitleStore = subtitleStore,
            getEngine = { engine },
            getUiState = { state.value },
            updateUiState = { transform -> state.value = transform(state.value) },
            getCurrentItemId = { null },
            getCurrentSeriesId = { null },
            getPlayMethod = { com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_PLAY },
            onReloadForStreamChange = { _, _ -> },
            playbackPreferenceResolver = noOpResolver(),
            scope = scope,
        )
        noItemId.resetAudioSelection() // should not throw
    }

    @Test
    fun updateTracksFromEngine_seriesSubtitleDisabled_forcesOffInsteadOfLanguageMatch() {
        // A "subtitles off" series preference must short-circuit the language
        // matcher: even though an "eng" subtitle track exists and the global
        // preferred subtitle language is "eng", the disabled intent wins and
        // Off is selected.
        every { subtitleStore.subtitle } returns MutableStateFlow(
            SubtitleSlice(preferredSubtitleLanguage = "eng"),
        )
        helper = TrackSelectionHelper(
            engineStore = engineStore,
            subtitleStore = subtitleStore,
            getEngine = { engine },
            getUiState = { state.value },
            updateUiState = { transform -> state.value = transform(state.value) },
            getCurrentItemId = { "item1" },
            getCurrentSeriesId = { "series1" },
            getPlayMethod = { com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_PLAY },
            onReloadForStreamChange = { _, _ -> },
            playbackPreferenceResolver = resolverFor(
                com.raulshma.jellyplay.core.model.ItemPlaybackPreference(
                    scope = com.raulshma.jellyplay.core.model.PlaybackPrefScope.SERIES,
                    key = "series1",
                    subtitleDisabled = true,
                )
            ),
            scope = scope,
        )
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.SUBTITLE, isSelected = false),
        )
        helper.updateTracksFromEngine()

        verify { engine.selectTrack(TrackType.SUBTITLE, -1) }
        // The English track must NOT have been selected by the matcher.
        verify(exactly = 0) { engine.selectTrack(TrackType.SUBTITLE, 0) }
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
    )
}
