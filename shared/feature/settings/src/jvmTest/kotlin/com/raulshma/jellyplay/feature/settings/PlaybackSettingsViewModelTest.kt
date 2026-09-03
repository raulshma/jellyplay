package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.CastingStrategy
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.SegmentBehavior
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Pins the Playback settings preference-mirror wiring: the screen's state is
 * the [PreferenceProjections.playbackPreferences] slice (LibraryLayout
 * jvmTest pattern — mockk stores, real [MutableStateFlow] stubs, inlined
 * Main-dispatcher rule), named editor setters route to [PreferencesEditor],
 * and lambda-routed setters persist through the owning store inside
 * `editor.edit { }`. Because a relaxed editor mock records but never runs the
 * edit block, each routed test captures the block and replays it against a
 * stub [PreferencesEditScope] to verify the store call — the toggle/coerce
 * POLICY stays in the store (pinned there), here we pin only the routing.
 *
 * Later top-up round: also pins the cross-slice routing — engine configs land
 * on the engine store, `setAudioDelayMs` on the AUDIO store, sync-play/casting
 * on the syncPlayCast store, segment behavior on the videoPlayer store — plus
 * the single-category `resetCategory` delegation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSettingsViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var store: UserPreferencesStore
    private lateinit var projections: PreferenceProjections
    private lateinit var appearanceStore: AppearanceStore
    private lateinit var editor: PreferencesEditor
    private lateinit var watchNextRefresher: WatchNextRefresher
    private lateinit var editScope: PreferencesEditScope
    private lateinit var playbackStore: PlaybackStore
    private lateinit var videoPlayerStore: VideoPlayerStore
    private lateinit var engineStore: PlayerEngineStore
    private lateinit var audioStore: AudioStore
    private lateinit var syncPlayCastStore: SyncPlayCastStore

    /** Every `edit { }` block the VM hands the editor, in call order. */
    private val editBlocks = mutableListOf<suspend PreferencesEditScope.() -> Unit>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        store = mockk(relaxed = true)
        projections = mockk(relaxed = true)
        appearanceStore = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        watchNextRefresher = mockk(relaxed = true)
        editScope = mockk(relaxed = true)
        playbackStore = mockk(relaxed = true)
        videoPlayerStore = mockk(relaxed = true)
        engineStore = mockk(relaxed = true)
        audioStore = mockk(relaxed = true)
        syncPlayCastStore = mockk(relaxed = true)
        editBlocks.clear()
        every { projections.playbackPreferences } returns MutableStateFlow(PlaybackPreferences())
        every { appearanceStore.showAdvancedSettings } returns MutableStateFlow(false)
        every { editScope.playback } returns playbackStore
        every { editScope.videoPlayer } returns videoPlayerStore
        every { editScope.engine } returns engineStore
        every { editScope.audio } returns audioStore
        every { editScope.syncPlayCast } returns syncPlayCastStore
        // List capture for the multi-block top-ups below (the per-test slot in
        // [captureEdit] re-stubs over this when a suite wants a single block).
        every { editor.edit(capture(editBlocks)) } returns mockk<Job>()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Captures the `edit { }` block so the test can run it against the stub scope. */
    private fun captureEdit() = slot<suspend PreferencesEditScope.() -> Unit>().also { captured ->
        every { editor.edit(capture(captured)) } returns mockk<Job>()
    }

    private fun viewModel() = PlaybackSettingsViewModel(store, projections, appearanceStore, editor, watchNextRefresher)

    @Test
    fun `preferences exposes the playback projection flow`() = runTest {
        val seeded = MutableStateFlow(PlaybackPreferences(preferredPlayer = PlayerType.MPV))
        every { projections.playbackPreferences } returns seeded
        val viewModel = viewModel()
        advanceUntilIdle()

        assertSame(seeded, viewModel.preferences)
        assertEquals(PlayerType.MPV, viewModel.preferences.value.preferredPlayer)
    }

    @Test
    fun `gesture toggle delegates to the editor named setter`() = runTest {
        val viewModel = viewModel()

        viewModel.setVideoGesturesEnabled(false)
        advanceUntilIdle()

        verify(exactly = 1) { editor.setVideoGesturesEnabled(false) }
    }

    @Test
    fun `trickplay toggle persists through the videoPlayer store`() = runTest {
        val viewModel = viewModel()
        val edit = captureEdit()

        viewModel.setTrickplayEnabled(true)
        advanceUntilIdle()
        edit.captured.invoke(editScope)

        coVerify(exactly = 1) { videoPlayerStore.setTrickplayEnabled(true) }
    }

    @Test
    fun `watch-next toggle persists and schedules a refresh in the same edit`() = runTest {
        val viewModel = viewModel()
        val edit = captureEdit()

        viewModel.setAndroidTvWatchNextEnabled(true)
        advanceUntilIdle()
        edit.captured.invoke(editScope)

        coVerify(exactly = 1) { playbackStore.setAndroidTvWatchNextEnabled(true) }
        // The scheduler poke rides the same edit block, so a dropped write can
        // never leave a stale Watch Next schedule behind.
        verify(exactly = 1) { watchNextRefresher.scheduleRefresh() }
    }

    @Test
    fun `screen reset clears both playback categories`() = runTest {
        val viewModel = viewModel()

        viewModel.resetPlaybackSettings()
        advanceUntilIdle()

        verify(exactly = 1) { editor.resetCategory(PreferenceResetCategory.PLAYBACK) }
        verify(exactly = 1) { editor.resetCategory(PreferenceResetCategory.PLAYER_ENGINES) }
    }

    // ------------------------------------------------------- top-up round

    /** Replays the blocks captured by the setUp list stub, in call order. */
    private suspend fun replayAllEdits() = editBlocks.forEach { it.invoke(editScope) }

    @Test
    fun `engine-config setters persist through the engine store`() = runTest {
        val viewModel = viewModel()

        viewModel.setMpvConfig(MpvEngineConfig())
        viewModel.setLibVlcConfig(LibVlcEngineConfig())
        viewModel.setExoPlayerConfig(ExoPlayerEngineConfig())
        advanceUntilIdle()
        replayAllEdits()

        coVerify(exactly = 1) { engineStore.setMpvConfig(any()) }
        coVerify(exactly = 1) { engineStore.setLibVlcConfig(any()) }
        coVerify(exactly = 1) { engineStore.setExoPlayerConfig(any()) }
    }

    @Test
    fun `audio-delay persists through the AUDIO store, not videoPlayer`() = runTest {
        val viewModel = viewModel()

        viewModel.setAudioDelayMs(250L)
        advanceUntilIdle()
        replayAllEdits()

        coVerify(exactly = 1) { audioStore.setAudioDelay(250L) }
    }

    @Test
    fun `sync-play and casting setters persist through the syncPlayCast store`() = runTest {
        val viewModel = viewModel()

        viewModel.setSyncPlayToleranceMs(5_000L)
        viewModel.setBackgroundCastingEnabled(true)
        viewModel.setPreferredRenderer(null)
        viewModel.setDefaultCastingStrategy(CastingStrategy.PREFER_DLNA)
        advanceUntilIdle()
        replayAllEdits()

        coVerify(exactly = 1) { syncPlayCastStore.setSyncPlayToleranceMs(5_000L) }
        coVerify(exactly = 1) { syncPlayCastStore.setBackgroundCastingEnabled(true) }
        coVerify(exactly = 1) { syncPlayCastStore.setPreferredRenderer(null) }
        coVerify(exactly = 1) { syncPlayCastStore.setDefaultCastingStrategy(CastingStrategy.PREFER_DLNA) }
    }

    @Test
    fun `segment behavior persists through the videoPlayer store`() = runTest {
        val viewModel = viewModel()

        viewModel.setSegmentBehavior(MediaSegmentType.INTRO, SegmentBehavior.AUTO_SKIP)
        advanceUntilIdle()
        replayAllEdits()

        coVerify(exactly = 1) { videoPlayerStore.setSegmentBehavior(MediaSegmentType.INTRO, SegmentBehavior.AUTO_SKIP) }
    }

    @Test
    fun `single-category reset delegates to the editor`() = runTest {
        val viewModel = viewModel()

        viewModel.resetCategory(PreferenceResetCategory.PLAYBACK)
        advanceUntilIdle()

        verify(exactly = 1) { editor.resetCategory(PreferenceResetCategory.PLAYBACK) }
    }
}
