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
import com.raulshma.jellyplay.feature.player.video.state.TrackState
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [TrackSelectionHelper] after state ownership moved into the helper:
 * the test surface is the helper's [TrackState] flow — no
 * [VideoPlayerUiState], no ViewModel. Server `mediaStreams` (session state the
 * helper only reads) enter through a plain variable captured by the
 * `getMediaStreams` lambda.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackSelectionHelperTest {

    private lateinit var engineStore: PlayerEngineStore
    private lateinit var subtitleStore: SubtitleLanguageStore
    private lateinit var engine: MediaEngine
    private lateinit var availableTracks: MutableStateFlow<List<MediaTrack>>
    private var mediaStreams: List<MediaStream> = emptyList()
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
        mediaStreams = emptyList()

        helper = TrackSelectionHelper(
            engineStore = engineStore,
            subtitleStore = subtitleStore,
            getEngine = { engine },
            getMediaStreams = { mediaStreams },
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

    private fun makeHelper(
        getEngine: () -> MediaEngine? = { engine },
        getCurrentItemId: () -> String? = { "item1" },
        getCurrentSeriesId: () -> String? = { null },
        resolver: ItemPlaybackPreferenceResolver = noOpResolver(),
    ) = TrackSelectionHelper(
        engineStore = engineStore,
        subtitleStore = subtitleStore,
        getEngine = getEngine,
        getMediaStreams = { mediaStreams },
        getCurrentItemId = getCurrentItemId,
        getCurrentSeriesId = getCurrentSeriesId,
        getPlayMethod = { com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_PLAY },
        onReloadForStreamChange = { _, _ -> },
        playbackPreferenceResolver = resolver,
        scope = scope,
    )

    @Test
    fun updateTracksFromEngine_noEngine_returnsEarly() {
        helper = makeHelper(getEngine = { null })
        helper.updateTracksFromEngine()
        assertTrue(helper.state.value.audioTracks.isEmpty())
    }

    @Test
    fun updateTracksFromEngine_emptyTracks_returnsDefaultAndOffOnly() {
        helper.updateTracksFromEngine()

        assertEquals(1, helper.state.value.audioTracks.size)
        assertEquals("Default", helper.state.value.audioTracks[0].label)
        assertTrue(helper.state.value.audioTracks[0].isSelected)

        // NOTE: production uses "None" for the empty-subtitle placeholder and "Off" for the
        // populated-case header. This test pins the current empty-case behaviour.
        assertEquals(1, helper.state.value.subtitleTracks.size)
        assertEquals("None", helper.state.value.subtitleTracks[0].label)
        assertTrue(helper.state.value.subtitleTracks[0].isSelected)
    }

    @Test
    fun updateTracksFromEngine_audioTracks_prependsDefaultAndMarksEngineAutoSelected() {
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.AUDIO, isSelected = true),
            mediaTrack(1, "Spanish", "spa", TrackType.AUDIO, isSelected = false),
        )
        helper.updateTracksFromEngine()

        val audio = helper.state.value.audioTracks
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

        val subs = helper.state.value.subtitleTracks
        assertEquals(2, subs.size)
        assertEquals("Off", subs[0].label)
        assertTrue(subs[1].isSelected)
    }

    @Test
    fun selectAudioTrack_positiveIndex_callsEngineSelectAndUpdatesState() {
        // Populate via the engine (Default unselected, English auto-selected by
        // the "eng" preference, Spanish unselected), then user-select Spanish.
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.AUDIO, isSelected = false),
            mediaTrack(1, "Spanish", "spa", TrackType.AUDIO, isSelected = false),
        )
        helper.updateTracksFromEngine()
        helper.selectAudioTrack(TrackOption(1, "Spanish", "spa", false))

        verify { engine.selectTrack(TrackType.AUDIO, 1) }
        assertFalse(helper.state.value.audioTracks[0].isSelected) // Default
        assertFalse(helper.state.value.audioTracks[1].isSelected) // English
        assertTrue(helper.state.value.audioTracks[2].isSelected)   // Spanish
    }

    @Test
    fun selectAudioTrack_negativeIndex_selectsDefaultAndClearsStoredId() {
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.AUDIO, isSelected = false),
        )
        helper.updateTracksFromEngine()
        helper.selectAudioTrack(TrackOption(-1, "Default", null, true))

        verify { engine.selectTrack(TrackType.AUDIO, -1) }
        assertTrue(helper.state.value.audioTracks[0].isSelected)
        assertFalse(helper.state.value.audioTracks[1].isSelected)
    }

    @Test
    fun selectAudioTrack_noEngine_returnsEarly() {
        helper = makeHelper(getEngine = { null })
        helper.selectAudioTrack(TrackOption(1, "Spanish", "spa", false))
        // No crash, no state change beyond initial
        assertTrue(helper.state.value.audioTracks.isEmpty())
    }

    @Test
    fun selectSubtitleTrack_positiveIndex_callsEngineSelectAndUpdatesState() {
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.SUBTITLE, isSelected = false),
        )
        helper.updateTracksFromEngine()
        helper.selectSubtitleTrack(TrackOption(0, "English", "eng", false))

        verify { engine.selectTrack(TrackType.SUBTITLE, 0) }
        assertFalse(helper.state.value.subtitleTracks[0].isSelected) // Off
        assertTrue(helper.state.value.subtitleTracks[1].isSelected)  // English
    }

    @Test
    fun selectSubtitleTrack_negativeIndex_selectsOff() {
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.SUBTITLE, isSelected = false),
        )
        helper.updateTracksFromEngine()
        helper.selectSubtitleTrack(TrackOption(-1, "Off", null, true))

        verify { engine.selectTrack(TrackType.SUBTITLE, -1) }
        assertTrue(helper.state.value.subtitleTracks[0].isSelected)
        assertFalse(helper.state.value.subtitleTracks[1].isSelected)
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
        mediaStreams = listOf(
            MediaStream(index = 1, type = StreamType.AUDIO, displayTitle = "Spanish"),
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
        mediaStreams = listOf(
            MediaStream(index = 1, type = StreamType.AUDIO, displayTitle = "Spanish"),
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
        mediaStreams = listOf(
            MediaStream(index = 0, type = StreamType.SUBTITLE, language = "eng", displayTitle = "English", isForced = true),
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
        assertEquals(0, helper.state.value.subtitleTracks.first { it.isSelected }.index)
    }

    @Test
    fun selectSubtitleTrack_offlineUserOverride_persistsEngineIndex() {
        // Offline: empty mediaStreams. resolveMediaStreamIndex previously
        // returned null so the stored selection was lost. Now it persists the
        // engine positional index directly.
        every { engineStore.playerEngine } returns MutableStateFlow(PlayerEngineSlice())
        every { subtitleStore.subtitle } returns MutableStateFlow(SubtitleSlice())
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

    // ─── Offline subtitle restore via the "offline:${index}" id ──────────────
    //
    // The detail screen's local-subtitle selector writes the chosen
    // OfflineSubtitleEntry.index (== the original server stream index) into the
    // per-item subtitleStreamIndex. PlayerSessionManager.loadOfflineSubtitles
    // stamps id == "offline:${index}" onto each side-loaded SubtitleSource,
    // and both ExoPlayer and mpv propagate that id into MediaTrack.id. The
    // restore path must resolve the stored index to that track — NOT to the
    // engine positional index, which is unrelated to the server index.

    @Test
    fun updateTracksFromEngine_offlineStoredIndex_resolvesByOfflineSubtitleId() {
        // Empty server mediaStreams (offline), stored subtitleStreamIndex = 2
        // (the original server stream index), and two side-loaded subs whose
        // ids encode their server indices. The restore path must pick the
        // offline:2 sub (engine index 1), not the positional-index match.
        every { engineStore.playerEngine } returns MutableStateFlow(
            PlayerEngineSlice(
                mediaStreamSelections = mapOf("item1" to MediaStreamSelection(subtitleStreamIndex = 2)),
            ),
        )
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.SUBTITLE, isSelected = false, id = "offline:0"),
            mediaTrack(1, "Spanish", "spa", TrackType.SUBTITLE, isSelected = false, id = "offline:2"),
        )
        helper.updateTracksFromEngine()

        verify { engine.selectTrack(TrackType.SUBTITLE, 1) }
        assertEquals(1, helper.state.value.subtitleTracks.first { it.isSelected }.index)
    }

    @Test
    fun updateTracksFromEngine_offlinePendingIndex_resolvesByOfflineSubtitleId() {
        // Same outcome via the pending route-param path: a pending
        // subtitleStreamIndex threaded from MediaDetailScreen.onPlayClick for a
        // LOCAL origin must resolve to the offline:${pending} sub even with
        // empty server mediaStreams (which previously dropped it silently).
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.SUBTITLE, isSelected = false, id = "offline:0"),
            mediaTrack(1, "Spanish", "spa", TrackType.SUBTITLE, isSelected = false, id = "offline:2"),
        )
        helper.setPendingStreams(subtitleIndex = 2, audioIndex = null)
        helper.updateTracksFromEngine()

        verify { engine.selectTrack(TrackType.SUBTITLE, 1) }
    }

    @Test
    fun updateTracksFromEngine_offlineStoredIndex_fallsBackToPositionalWhenNoId() {
        // Legacy / engine that does not propagate the offline id: the restore
        // path must still fall back to the positional-index match so behaviour
        // does not regress for tracks without the "offline:${index}" id.
        every { engineStore.playerEngine } returns MutableStateFlow(
            PlayerEngineSlice(
                mediaStreamSelections = mapOf("item1" to MediaStreamSelection(subtitleStreamIndex = 0)),
            ),
        )
        availableTracks.value = listOf(
            // No offline: id — synthetic engine ids only.
            mediaTrack(0, "English", "eng", TrackType.SUBTITLE, isSelected = false, id = "mpv_sub_1"),
        )
        helper.updateTracksFromEngine()

        // Falls back to positional-index match (subIdx 0 == engine index 0).
        verify { engine.selectTrack(TrackType.SUBTITLE, 0) }
    }

    @Test
    fun updateTracksFromEngine_autoOffDoesNotLatch_allowsLaterSidecarSelection() {
        // First emission has no subtitle tracks yet (offline sidecar subs load
        // after the first track list). Auto-selection falls through to Off but
        // must NOT latch, so a later emission with a real track can still
        // resolve the language preference.
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
        assertTrue(helper.state.value.audioTracks[0].isSelected) // Default
        assertFalse(helper.state.value.audioTracks[1].isSelected) // Espanol
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
        assertEquals(2, helper.state.value.audioTracks.size)
    }

    @Test
    fun resetAudioSelection_clearsStoredAudioSelection_whenNoItemId_returnsEarly() {
        val noItemId = makeHelper(getCurrentItemId = { null })
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
        helper = makeHelper(
            getCurrentSeriesId = { "series1" },
            resolver = resolverFor(
                com.raulshma.jellyplay.core.model.ItemPlaybackPreference(
                    scope = com.raulshma.jellyplay.core.model.PlaybackPrefScope.SERIES,
                    key = "series1",
                    subtitleDisabled = true,
                )
            ),
        )
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.SUBTITLE, isSelected = false),
        )
        helper.updateTracksFromEngine()

        verify { engine.selectTrack(TrackType.SUBTITLE, -1) }
        // The English track must NOT have been selected by the matcher.
        verify(exactly = 0) { engine.selectTrack(TrackType.SUBTITLE, 0) }
    }

    // ─── Item-switch semantics ─────────────────────────────────────────────────

    /**
     * Track state is per-item: resetForItem clears the picker lists and the
     * override/preference flags — the explicit form of the implicit reset the
     * former UiState rebuild performed (none of these fields were whitelisted).
     */
    @Test
    fun `resetForItem clears tracks and override flags`() {
        availableTracks.value = listOf(
            mediaTrack(0, "English", "eng", TrackType.AUDIO, isSelected = false),
            mediaTrack(0, "English", "eng", TrackType.SUBTITLE, isSelected = false),
        )
        helper.updateTracksFromEngine()
        helper.onStoredSelectionChanged(MediaStreamSelection(audioStreamIndex = 1, subtitleStreamIndex = 1))
        helper.onSeriesPreferenceResolved(
            com.raulshma.jellyplay.core.model.ItemPlaybackPreference(
                scope = com.raulshma.jellyplay.core.model.PlaybackPrefScope.SERIES,
                key = "series1",
                audioLanguage = "eng",
                subtitleDisabled = true,
                dialogueBoostStrength = com.raulshma.jellyplay.core.model.EffectStrength.HIGH,
            )
        )
        assertTrue(helper.state.value.audioTracks.isNotEmpty())
        assertTrue(helper.state.value.hasAudioOverride)
        assertTrue(helper.state.value.hasSeriesAudioPref)

        helper.resetForItem()

        assertEquals(TrackState(), helper.state.value)
    }

    /** The stored per-item override flags derive from the stored stream selection. */
    @Test
    fun `onStoredSelectionChanged reflects override flags`() {
        helper.onStoredSelectionChanged(null)
        assertFalse(helper.state.value.hasAudioOverride)
        assertFalse(helper.state.value.hasSubtitleOverride)

        helper.onStoredSelectionChanged(MediaStreamSelection(audioStreamIndex = 1))
        assertTrue(helper.state.value.hasAudioOverride)
        assertFalse(helper.state.value.hasSubtitleOverride)

        helper.onStoredSelectionChanged(MediaStreamSelection(audioStreamIndex = null, subtitleStreamIndex = 2))
        assertFalse(helper.state.value.hasAudioOverride)
        assertTrue(helper.state.value.hasSubtitleOverride)
    }

    /** The series-pref flags derive from the resolved series-scope preference. */
    @Test
    fun `onSeriesPreferenceResolved reflects series preference flags`() {
        helper.onSeriesPreferenceResolved(null)
        assertEquals(TrackState(), helper.state.value.copy(audioTracks = helper.state.value.audioTracks, subtitleTracks = helper.state.value.subtitleTracks))

        helper.onSeriesPreferenceResolved(
            com.raulshma.jellyplay.core.model.ItemPlaybackPreference(
                scope = com.raulshma.jellyplay.core.model.PlaybackPrefScope.SERIES,
                key = "series1",
                audioLanguage = "eng",
                subtitleDisabled = true,
                dialogueBoostStrength = com.raulshma.jellyplay.core.model.EffectStrength.HIGH,
            )
        )
        val s = helper.state.value
        assertTrue(s.hasSeriesAudioPref)
        assertTrue(s.hasSeriesSubtitlePref)
        assertTrue(s.hasSeriesSubtitleOffPref)
        assertTrue(s.hasSeriesDialogueBoostPref)

        // Item-scope preferences are NOT series prefs.
        helper.onSeriesPreferenceResolved(
            com.raulshma.jellyplay.core.model.ItemPlaybackPreference(
                scope = com.raulshma.jellyplay.core.model.PlaybackPrefScope.ITEM,
                key = "item1",
                audioLanguage = "eng",
            )
        )
        assertFalse(helper.state.value.hasSeriesAudioPref)
    }

    private fun mediaTrack(
        index: Int,
        label: String,
        language: String?,
        type: TrackType,
        isSelected: Boolean,
        id: String = "$index",
    ) = MediaTrack(
        id = id,
        index = index,
        label = label,
        language = language,
        isSelected = isSelected,
        type = type,
    )
}
