package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.playback.VideoPerformanceResolver
import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.CastingStrategy
import com.raulshma.jellyplay.core.model.DevicePerformanceTier
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.GestureMode
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.PerformanceDetection
import com.raulshma.jellyplay.core.model.PerformanceReason
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.VideoPerformanceProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the Playback settings preference-mirror wiring: the screen's state is
 * the [PreferenceProjections.playbackPreferences] slice (LibraryLayout
 * jvmTest pattern — mockk stores, real [MutableStateFlow] stubs, inlined
 * Main-dispatcher rule), and every setter persists through the owning store
 * inside the VM's `edit { }` command. Because a relaxed editor mock records
 * but never runs the edit block, each routed test captures the block and
 * replays it against a stub [PreferencesEditScope] to verify the store call —
 * the toggle/coerce POLICY stays in the store (pinned there), here we pin
 * only the routing.
 *
 * Later top-up round: also pins the cross-slice routing — engine configs land
 * on the engine store, `setAudioDelayMs` on the AUDIO store, sync-play/casting
 * on the syncPlayCast store, segment behavior on the videoPlayer store — plus
 * the single-category `resetCategory` delegation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSettingsViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

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

    private fun viewModel(
        videoPerformanceResolver: VideoPerformanceResolver? = null,
    ) = PlaybackSettingsViewModel(
        projections,
        AdvancedSettingsGate(appearanceStore, editor),
        editor,
        watchNextRefresher,
        videoPerformanceResolver = videoPerformanceResolver,
    )

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
    fun `gesture mode picker persists through the videoPlayer store`() = runTest {
        val viewModel = viewModel()

        viewModel.edit { it.videoPlayer.setVideoGestureMode(GestureMode.TAP_ONLY) }
        advanceUntilIdle()
        replayAllEdits()

        coVerify(exactly = 1) { videoPlayerStore.setVideoGestureMode(GestureMode.TAP_ONLY) }
    }

    @Test
    fun `trickplay toggle persists through the videoPlayer store`() = runTest {
        val viewModel = viewModel()
        val edit = captureEdit()

        viewModel.edit { it.videoPlayer.setTrickplayEnabled(true) }
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

        viewModel.edit { it.engine.setMpvConfig(MpvEngineConfig()) }
        viewModel.edit { it.engine.setLibVlcConfig(LibVlcEngineConfig()) }
        viewModel.edit { it.engine.setExoPlayerConfig(ExoPlayerEngineConfig()) }
        advanceUntilIdle()
        replayAllEdits()

        coVerify(exactly = 1) { engineStore.setMpvConfig(any()) }
        coVerify(exactly = 1) { engineStore.setLibVlcConfig(any()) }
        coVerify(exactly = 1) { engineStore.setExoPlayerConfig(any()) }
    }

    @Test
    fun `audio-delay persists through the AUDIO store, not videoPlayer`() = runTest {
        val viewModel = viewModel()

        viewModel.edit { it.audio.setAudioDelay(250L) }
        advanceUntilIdle()
        replayAllEdits()

        coVerify(exactly = 1) { audioStore.setAudioDelay(250L) }
    }

    @Test
    fun `sync-play and casting setters persist through the syncPlayCast store`() = runTest {
        val viewModel = viewModel()

        viewModel.edit { it.syncPlayCast.setSyncPlayToleranceMs(5_000L) }
        viewModel.edit { it.syncPlayCast.setBackgroundCastingEnabled(true) }
        viewModel.edit { it.syncPlayCast.setPreferredRenderer(null) }
        viewModel.edit { it.syncPlayCast.setDefaultCastingStrategy(CastingStrategy.PREFER_DLNA) }
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

        viewModel.edit { it.videoPlayer.setSegmentBehavior(MediaSegmentType.INTRO, SegmentBehavior.AUTO_SKIP) }
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

    // ------------------------------------------- performance profile (issue #186 §5.14)

    /** A resolver mock exposing [detection] and a controllable [VideoPerformanceResolver.redetect]. */
    private fun resolver(
        detection: PerformanceDetection? = null,
        redetect: suspend () -> PerformanceDetection = { detection!! },
    ): VideoPerformanceResolver {
        val mock = mockk<VideoPerformanceResolver>()
        every { mock.detection } returns MutableStateFlow(detection)
        coEvery { mock.redetect() } coAnswers { redetect() }
        return mock
    }

    private fun detection(
        tier: DevicePerformanceTier,
        vararg reasons: PerformanceReason,
    ) = PerformanceDetection(
        tier = tier,
        score = 0,
        reasons = reasons.toList(),
        fingerprint = "fingerprint",
        detectorVersion = 1,
        detectedAtEpochMs = 0L,
    )

    @Test
    fun `performance detection re-exposes the resolver's sticky cache flow`() = runTest {
        val cached = MutableStateFlow<PerformanceDetection?>(detection(DevicePerformanceTier.LOW, PerformanceReason.LOW_RAM))
        val mock = mockk<VideoPerformanceResolver>()
        every { mock.detection } returns cached

        val viewModel = viewModel(videoPerformanceResolver = mock)

        assertSame(cached, viewModel.performanceDetection)
        assertEquals(DevicePerformanceTier.LOW, viewModel.performanceDetection.value?.tier)
    }

    @Test
    fun `performance detection falls back to a null flow without a resolver`() = runTest {
        val viewModel = viewModel()

        assertEquals(null, viewModel.performanceDetection.value)
    }

    @Test
    fun `redetect flips the loading state around the resolver call`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val mock = resolver(
            detection = detection(DevicePerformanceTier.LOW),
            redetect = { gate.await(); detection(DevicePerformanceTier.HIGH) },
        )
        val viewModel = viewModel(videoPerformanceResolver = mock)

        assertFalse(viewModel.isRedetecting.value, "sanity: idle before the tap")
        viewModel.redetectPerformance()
        advanceUntilIdle()
        assertTrue(viewModel.isRedetecting.value, "the loading state holds while the probe runs")

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.isRedetecting.value, "the loading state clears when the probe lands")
    }

    @Test
    fun `redetect emits the snackbar message with the new tier`() = runTest {
        val fresh = detection(DevicePerformanceTier.HIGH, PerformanceReason.MODERATE_RAM)
        val mock = resolver(redetect = { fresh })
        val viewModel = viewModel(videoPerformanceResolver = mock)
        val messages = mutableListOf<PerformanceRedetectMessage>()
        val collector = launch { viewModel.redetectMessages.toList(messages) }

        viewModel.redetectPerformance()
        advanceUntilIdle()
        collector.cancel()

        // The snackbar payload carries the fresh tier (the screen resolves
        // "Detected: High" from it) and the loading state has settled.
        val expected: List<PerformanceRedetectMessage> = listOf(PerformanceRedetectMessage.Detected(DevicePerformanceTier.HIGH))
        assertEquals(expected, messages)
        assertFalse(viewModel.isRedetecting.value)
    }

    @Test
    fun `a second redetect tap while one runs is swallowed`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val mock = resolver(redetect = { gate.await(); detection(DevicePerformanceTier.MEDIUM) })
        val viewModel = viewModel(videoPerformanceResolver = mock)

        viewModel.redetectPerformance()
        advanceUntilIdle()
        viewModel.redetectPerformance()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { mock.redetect() }
    }

    @Test
    fun `an unexpected redetect failure clears loading and emits Failed`() = runTest {
        val mock = resolver(redetect = { error("probe exploded") })
        val viewModel = viewModel(videoPerformanceResolver = mock)
        val messages = mutableListOf<PerformanceRedetectMessage>()
        val collector = launch { viewModel.redetectMessages.toList(messages) }

        viewModel.redetectPerformance()
        advanceUntilIdle()
        collector.cancel()

        val expected: List<PerformanceRedetectMessage> = listOf(PerformanceRedetectMessage.Failed)
        assertEquals(expected, messages)
        assertFalse(viewModel.isRedetecting.value)
    }

    @Test
    fun `performance profile picker persists through the playback store`() = runTest {
        val viewModel = viewModel()

        viewModel.edit { it.playback.setVideoPerformanceProfile(VideoPerformanceProfile.LOW) }
        advanceUntilIdle()
        replayAllEdits()

        coVerify(exactly = 1) { playbackStore.setVideoPerformanceProfile(VideoPerformanceProfile.LOW) }
    }
}
