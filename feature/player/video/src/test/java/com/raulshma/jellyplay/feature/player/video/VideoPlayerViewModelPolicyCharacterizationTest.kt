package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.remote.ActivePlayerController
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayPlaybackCore
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.EngineError
import com.raulshma.jellyplay.feature.player.video.engine.FakeMediaEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterization tests pinning the three behavior-rich
 * engine-event policies of [VideoPlayerViewModel]:
 * the FORCE_DIRECT_PLAY → transcode one-shot fallback latch, the
 * initial-buffering watchdog, and pass-out protection.
 *
 * These drive the ViewModel end-to-end through a [FakeMediaEngine] behind a
 * mocked [com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory]
 * and must stay green through every refactor step of the plan — they are the
 * permanent guard that the VM *executes* the extracted
 * [EngineEventCoordinator] decisions correctly (reload choreography on
 * `FallbackToTranscode`, pause + pass-out event on `PassOutPause`, dialog
 * slice on `ShowError`).
 *
 * Robolectric is required only for the pass-out tests (the policy reads
 * `SystemClock.elapsedRealtime`, which the shadow clock lets us advance);
 * the rest of the class runs under it for uniformity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class VideoPlayerViewModelPolicyCharacterizationTest {

    private lateinit var fakeEngine: FakeMediaEngine
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var playbackStore: PlaybackStore
    private lateinit var mediaRepository: MediaRepository
    private lateinit var aggregateStore: VideoPlayerAggregateStore

    // ── Harness ─────────────────────────────────────────────────────────────

    /**
     * Builds the full ViewModel with a spied [FakeMediaEngine] behind the
     * engine factory. The spy records pause/play calls so the pass-out policy
     * can assert on the engine side effect without a mockk MediaEngine.
     */
    private fun TestHarness.createViewModel(
        aggregate: VideoPlayerAggregate = VideoPlayerAggregate(),
    ): VideoPlayerViewModel {
        fakeEngine = spyk(FakeMediaEngine())
        val factory = mockk<com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory>()
        every { factory.create(any()) } returns fakeEngine

        val context = mockk<Context>(relaxed = true)
        mediaRepository = mockk<MediaRepository>(relaxed = true)
        playbackRepository = mockk<PlaybackRepository>(relaxed = true)
        val imageUrlProvider = mockk<ImageUrlProvider>(relaxed = true)
        val downloadRepository = mockk<DownloadRepository>(relaxed = true)
        val offlineRepository = mockk<OfflineRepository>(relaxed = true)
        val offlinePlaybackFacade = mockk<com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade>(relaxed = true)
        val playbackSourceResolver = mockk<com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver>(relaxed = true)
        val itemPlaybackPreferenceRepository = mockk<ItemPlaybackPreferenceRepository>(relaxed = true)
        aggregateStore = mockk<VideoPlayerAggregateStore>(relaxed = true)
        val engineStore = mockk<PlayerEngineStore>(relaxed = true)
        val subtitleStore = mockk<SubtitleLanguageStore>(relaxed = true)
        val securityStore = mockk<SecurityStore>(relaxed = true)
        val syncPlayCastStore = mockk<SyncPlayCastStore>(relaxed = true)
        playbackStore = mockk<PlaybackStore>(relaxed = true)
        val audioStore = mockk<AudioStore>(relaxed = true)
        val audioEffectsStore = mockk<AudioEffectsStore>(relaxed = true)
        val videoPlayerStore = mockk<VideoPlayerStore>(relaxed = true)
        val downloadsStore = mockk<DownloadsStore>(relaxed = true)
        val appearanceStore = mockk<AppearanceStore>(relaxed = true)
        val networkOfflineStore = mockk<NetworkOfflineStore>(relaxed = true)
        val sessionManager = mockk<PlaybackSessionManager>(relaxed = true)
        val castManager = mockk<CastManager>(relaxed = true)
        val jellyfinRemotePlayCastStrategy = mockk<com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy>(relaxed = true)
        val syncPlayManager = mockk<SyncPlayManager>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val adaptiveBitrateManager = mockk<AdaptiveBitrateManager>(relaxed = true)
        val networkMonitor = mockk<NetworkMonitor>(relaxed = true).apply {
            every { isMetered } returns MutableStateFlow(false)
        }
        val activePlayerController = mockk<ActivePlayerController>(relaxed = true)
        val playerLifecycleManager = PlayerLifecycleManager(playbackStore)
        val pipController = com.raulshma.jellyplay.core.data.playback.PipController()
        val videoMiniPlayerState = mockk<VideoMiniPlayerState>(relaxed = true)
        val sleepTimerManager = mockk<SleepTimerManager>(relaxed = true)

        every { aggregateStore.aggregate } returns MutableStateFlow(aggregate)
        every { aggregateStore.aggregateRaw } returns flowOf(aggregate)
        every { engineStore.playerEngine } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.engine.PlayerEngineSlice()
        )
        every { subtitleStore.subtitle } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice()
        )
        every { securityStore.security } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.security.SecuritySlice()
        )
        every { syncPlayCastStore.syncPlayCast } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice()
        )
        every { playbackStore.playback } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice()
        )
        every { audioStore.audio } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.audio.AudioSlice()
        )
        every { audioEffectsStore.audioEffects } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice()
        )
        every { videoPlayerStore.videoPlayer } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice()
        )
        every { downloadsStore.downloads } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice()
        )
        every { appearanceStore.appearance } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice()
        )
        every { networkOfflineStore.networkOffline } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice()
        )
        every { sleepTimerManager.remainingMs } returns MutableStateFlow(0L)
        val playbackCore = mockk<SyncPlayPlaybackCore>(relaxed = true)
        every { syncPlayManager.playbackCore } returns playbackCore
        syncPlayManager.stubEmptyEvents()

        // "Play On" remote routing must never trigger in these tests.
        every { jellyfinRemotePlayCastStrategy.isConnected } returns MutableStateFlow(false)

        coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(
            MediaDetail(
                item = MediaItem(
                    id = "item-1",
                    name = "Test Movie",
                    mediaType = MediaType.MOVIE,
                )
            )
        )

        // A deterministic DIRECT_PLAY resolution: the relaxed mock's chained
        // mock would return arbitrary PlayMethod values that can spuriously
        // trip the VM's "Direct Play unavailable" auto-fallback inside
        // reloadPlaybackForMode.
        coEvery {
            playbackRepository.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any())
        } returns com.raulshma.jellyplay.core.model.ResolvedPlayback(
            mediaSourceId = "",
            streamUrl = "",
            playMethod = com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_PLAY,
            playSessionId = null,
            maxStreamingBitrate = null,
        )

        return VideoPlayerViewModel(
            context = context,
            mediaRepository = mediaRepository,
            playbackRepository = playbackRepository,
            subtitleProviderRepository = mockk(relaxed = true),
            streamingSubtitleStore = noOpStreamingSubtitleStore(),
            imageUrlProvider = imageUrlProvider,
            downloadRepository = downloadRepository,
            offlineRepository = offlineRepository,
            offlinePlaybackFacade = offlinePlaybackFacade,
            playbackSourceResolver = playbackSourceResolver,
            episodeCatalogue = mockk(relaxed = true),
            itemPlaybackPreferenceRepository = itemPlaybackPreferenceRepository,
            aggregateStore = aggregateStore,
            engineStore = engineStore,
            subtitleStore = subtitleStore,
            securityStore = securityStore,
            syncPlayCastStore = syncPlayCastStore,
            playbackStore = playbackStore,
            audioStore = audioStore,
            audioEffectsStore = audioEffectsStore,
            videoPlayerStore = videoPlayerStore,
            downloadsStore = downloadsStore,
            appearanceStore = appearanceStore,
            networkOfflineStore = networkOfflineStore,
            sessionManager = sessionManager,
            castManager = castManager,
            jellyfinRemotePlayCastStrategy = jellyfinRemotePlayCastStrategy,
            syncPlayManager = syncPlayManager,
            okHttpClient = okHttpClient,
            adaptiveBitrateManager = adaptiveBitrateManager,
            networkMonitor = networkMonitor,
            activePlayerController = activePlayerController,
            playerLifecycleManager = playerLifecycleManager,
            pipController = pipController,
            videoMiniPlayerState = videoMiniPlayerState,
            sleepTimerManager = sleepTimerManager,
            userMessageBus = UserMessageBus(),
            playerEngineFactory = factory,
            fontProvider = mockk(relaxed = true),
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
            subtitlePreviewRepository = mockk(relaxed = true),
            userDataMutator = mockk(relaxed = true),
        )
    }

    /** Harness carrying the shared test scheduler + helpers. */
    private class TestHarness(val scheduler: kotlinx.coroutines.test.TestCoroutineScheduler)

    private fun policyTest(
        aggregate: VideoPlayerAggregate = VideoPlayerAggregate(),
        block: suspend kotlinx.coroutines.test.TestScope.(vm: VideoPlayerViewModel) -> Unit,
    ) = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val vm = TestHarness(testScheduler).createViewModel(aggregate)
        try {
            // Let the init collectors + the initial load settle (Unconfined
            // already ran them synchronously; runCurrent is belt-and-braces).
            testScheduler.runCurrent()
            block(vm)
        } finally {
            vm.release()
            Dispatchers.resetMain()
        }
    }

    // ── (a) Direct-play fallback offered exactly once per item ──────────────

    @Test
    fun directPlayFallback_offeredOncePerItem_secondErrorSurfacesDialog() = policyTest(
        aggregate = VideoPlayerAggregate(
            playback = PlaybackSlice(playbackMode = PlaybackMode.FORCE_DIRECT_PLAY),
        ),
    ) { vm ->
        vm.initialize("item-1", null, 0L)
        testScheduler.runCurrent()
        // The prefs projection seeded the forced mode from the aggregate.
        assertEquals(PlaybackMode.FORCE_DIRECT_PLAY, vm.uiState.value.playbackMode)

        val error = EngineError.Decoder("h264", null)
        fakeEngine.errorEmissions.tryEmit(error)
        testScheduler.runCurrent()

        // One-shot: mode flipped to transcode + one reload was performed.
        assertEquals(PlaybackMode.FORCE_TRANSCODE, vm.uiState.value.playbackMode)
        coVerify(exactly = 1) {
            playbackRepository.resolvePlayback(
                any(), any(), any(), any(), any(), any(), PlaybackMode.FORCE_TRANSCODE, any(),
            )
        }
        coVerify(exactly = 1) { playbackStore.setPlaybackMode(PlaybackMode.FORCE_TRANSCODE) }
        // The fallback path consumed the error — no dialog.
        assertFalse(vm.uiState.value.showPlaybackErrorDialog)

        // Second error under the same item: latch holds — surface the dialog.
        val secondError = EngineError.Decoder("vpx", null)
        fakeEngine.errorEmissions.tryEmit(secondError)
        testScheduler.runCurrent()

        assertTrue(vm.uiState.value.showPlaybackErrorDialog)
        assertEquals(secondError.message, vm.uiState.value.playerError)
        assertFalse(vm.uiState.value.playerErrorRetryable)
        // Still exactly one transcode reload.
        coVerify(exactly = 1) {
            playbackRepository.resolvePlayback(
                any(), any(), any(), any(), any(), any(), PlaybackMode.FORCE_TRANSCODE, any(),
            )
        }
    }

    @Test
    fun directPlayFallback_capturesEnginePositionInReload() = policyTest(
        aggregate = VideoPlayerAggregate(
            playback = PlaybackSlice(playbackMode = PlaybackMode.FORCE_DIRECT_PLAY),
        ),
    ) { vm ->
        vm.initialize("item-1", null, 0L)
        testScheduler.runCurrent()
        fakeEngine.advanceTo(42_000L)

        fakeEngine.errorEmissions.tryEmit(EngineError.Decoder("hevc", null))
        testScheduler.runCurrent()

        // reloadPlayback was invoked with the engine position captured at
        // fallback time (startTimeTicks = 42_000ms * 10_000).
        coVerify(exactly = 1) {
            playbackRepository.resolvePlayback(
                any(), any(), 42_000L * 10_000L, any(), any(), any(), PlaybackMode.FORCE_TRANSCODE, any(),
            )
        }
    }

    // ── (b) Explicit mode change re-arms the latch ──────────────────────────

    @Test
    fun setPlaybackMode_reArmsDirectPlayFallbackLatch() = policyTest(
        aggregate = VideoPlayerAggregate(
            playback = PlaybackSlice(playbackMode = PlaybackMode.FORCE_DIRECT_PLAY),
        ),
    ) { vm ->
        vm.initialize("item-1", null, 0L)
        testScheduler.runCurrent()

        // First failure: auto-fallback consumes the latch.
        fakeEngine.errorEmissions.tryEmit(EngineError.Decoder("h264", null))
        testScheduler.runCurrent()
        assertEquals(PlaybackMode.FORCE_TRANSCODE, vm.uiState.value.playbackMode)

        // User explicitly goes back to FORCE_DIRECT_PLAY — re-arms the latch.
        vm.setPlaybackMode(PlaybackMode.FORCE_DIRECT_PLAY)
        testScheduler.runCurrent()
        assertEquals(PlaybackMode.FORCE_DIRECT_PLAY, vm.uiState.value.playbackMode)

        // A new failure may fall back again.
        fakeEngine.errorEmissions.tryEmit(EngineError.Decoder("h264", null))
        testScheduler.runCurrent()
        assertEquals(PlaybackMode.FORCE_TRANSCODE, vm.uiState.value.playbackMode)
        assertFalse("fallback should consume the error, not surface a dialog", vm.uiState.value.showPlaybackErrorDialog)
    }

    // ── (c) Initial-buffering watchdog ───────────────────────────────────────

    @Test
    fun watchdog_initialBufferingPastTimeout_surfacesTimeoutDialogAndClearsBuffering() = policyTest { vm ->
        vm.initialize("item-1", null, 0L)
        testScheduler.runCurrent()

        fakeEngine.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.isBuffering)

        testScheduler.advanceTimeBy(20_000L)
        testScheduler.runCurrent()

        assertTrue(vm.uiState.value.showPlaybackErrorDialog)
        assertEquals(EngineError.Timeout().message, vm.uiState.value.playerError)
        assertTrue(vm.uiState.value.playerErrorRetryable)
        // The timeout must lift the stuck buffering spinner.
        assertFalse(vm.uiState.value.isBuffering)
    }

    @Test
    fun watchdog_readyBeforeTimeout_doesNotFire() = policyTest { vm ->
        vm.initialize("item-1", null, 0L)
        testScheduler.runCurrent()

        fakeEngine.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.advanceTimeBy(19_000L)
        fakeEngine.playbackState.value = EnginePlaybackState.READY
        testScheduler.advanceTimeBy(60_000L)
        testScheduler.runCurrent()

        assertFalse(vm.uiState.value.showPlaybackErrorDialog)
        assertNull(vm.uiState.value.playerError)
    }

    @Test
    fun watchdog_bufferingAfterReady_doesNotReArm() = policyTest { vm ->
        vm.initialize("item-1", null, 0L)
        testScheduler.runCurrent()

        // First load reaches READY, then rebuffers mid-playback.
        fakeEngine.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.advanceTimeBy(1_000L)
        fakeEngine.playbackState.value = EnginePlaybackState.READY
        testScheduler.runCurrent()
        fakeEngine.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.runCurrent()

        // Far past the timeout window — a mid-playback rebuffer must not trip
        // the start-up watchdog.
        testScheduler.advanceTimeBy(120_000L)
        testScheduler.runCurrent()

        assertFalse(vm.uiState.value.showPlaybackErrorDialog)
        assertTrue(vm.uiState.value.isBuffering)
    }

    /**
     * follow-up: the watchdog latch is keyed on engine *instance*. A reload
     * that reuses the same engine instance (all real factory reloads create a
     * fresh instance, so this only manifests via engine identity reuse, e.g.
     * the reclaimed mini-player engine) leaves the watchdog disarmed for the
 * subsequent load. Pinned as-is ("moves policy, does not
 * change it"); tracked as a follow-up.
     */
    @Test
    fun watchdog_sameEngineReloadNeverReArms_quirk() = policyTest { vm ->
        vm.initialize("item-1", null, 0L)
        testScheduler.runCurrent()

        // Reach READY so the watchdog latch trips for this engine instance.
        fakeEngine.playbackState.value = EnginePlaybackState.READY
        testScheduler.runCurrent()

        // "Reload" the same engine instance (factory returns the identical
        // fake — the engineFlow does not re-emit, so the per-engine collector
        // and its hasReachedReady latch survive).
        vm.retryPlayback()
        testScheduler.runCurrent()

        fakeEngine.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.advanceTimeBy(30_000L)
        testScheduler.runCurrent()

        assertFalse("same-engine reload leaves the watchdog disarmed (quirk)", vm.uiState.value.showPlaybackErrorDialog)
    }

    // ── (d) Pass-out protection ──────────────────────────────────────────────

    @Test
    fun passOutProtection_elapsedHoursWhilePlaying_pausesAndEmitsEvent() = policyTest(
        aggregate = VideoPlayerAggregate(
            videoPlayer = VideoPlayerSlice(videoPassOutProtectionHours = 1),
        ),
    ) { vm ->
        vm.initialize("item-1", null, 0L)
        testScheduler.runCurrent()
        assertEquals(1, vm.uiState.value.passOutProtectionHours)

        var event: String? = null
        val collector = launch { event = vm.passOutEvents.first() }

        // Playback is active; the user has not interacted for over an hour.
        fakeEngine.isPlayingState.value = true
        testScheduler.runCurrent()
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(61L * 60L * 60L * 1000L))

        // The poller fires every 60 s of virtual time.
        testScheduler.advanceTimeBy(61L * 60L * 1000L)
        testScheduler.runCurrent()

        withTimeout(1_000) { collector.join() }
        verify(atLeast = 1) { fakeEngine.pause() }
        assertEquals("Playback paused — pass-out protection", event)
    }

    @Test
    fun passOutProtection_longPauseThenResume_resetsClock_noImmediateTrip() = policyTest(
        aggregate = VideoPlayerAggregate(
            videoPlayer = VideoPlayerSlice(videoPassOutProtectionHours = 1),
        ),
    ) { vm ->
        vm.initialize("item-1", null, 0L)
        testScheduler.runCurrent()

        var events = 0
        val collector = launch { vm.passOutEvents.collect { events++ } }
        testScheduler.runCurrent()

        // Play, then pause for two hours (paused playback never trips).
        fakeEngine.isPlayingState.value = true
        testScheduler.runCurrent()
        fakeEngine.isPlayingState.value = false
        testScheduler.runCurrent()
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(2L * 60L * 60L * 1000L))
        testScheduler.advanceTimeBy(2L * 60L * 60L * 1000L)
        testScheduler.runCurrent()
        assertEquals(0, events)

        // Resume: the interaction clock resets at the resume transition, so
        // the stale two-hour gap must not immediately trip the timer.
        fakeEngine.isPlayingState.value = true
        testScheduler.runCurrent()
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(1_000L))
        testScheduler.advanceTimeBy(5L * 60L * 1000L)
        testScheduler.runCurrent()

        assertEquals(0, events)
        collector.cancel()
    }

    @Test
    fun passOutProtection_userInteractionResetsClock() = policyTest(
        aggregate = VideoPlayerAggregate(
            videoPlayer = VideoPlayerSlice(videoPassOutProtectionHours = 1),
        ),
    ) { vm ->
        vm.initialize("item-1", null, 0L)
        testScheduler.runCurrent()

        var events = 0
        val collector = launch { vm.passOutEvents.collect { events++ } }
        testScheduler.runCurrent()

        fakeEngine.isPlayingState.value = true
        testScheduler.runCurrent()

        // 50 minutes pass, the user interacts, another 50 minutes pass —
        // no single hour of inactivity ever elapses.
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(50L * 60L * 1000L))
        testScheduler.advanceTimeBy(50L * 60L * 1000L)
        vm.onUserInteraction()
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(50L * 60L * 1000L))
        testScheduler.advanceTimeBy(50L * 60L * 1000L)
        testScheduler.runCurrent()

        assertEquals(0, events)
        collector.cancel()
    }

    @Test
    fun subscriptionTiming_errorCollectorActiveBeforeLoad_canConsumeFirstError() = policyTest(
        aggregate = VideoPlayerAggregate(
            playback = PlaybackSlice(playbackMode = PlaybackMode.AUTO),
        ),
    ) { vm ->
        vm.initialize("item-1", null, 0L)
        testScheduler.runCurrent()

        // The errorFlow collector is subscribed in the VM's init block before
        // the load coroutine's engine error can arrive — an error emitted
        // immediately after the engine binds is consumed synchronously, with
        // no advanceUntilIdle needed (subscription timing).
        fakeEngine.errorEmissions.tryEmit(EngineError.Network(null))
        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.showPlaybackErrorDialog)
        assertEquals(EngineError.Network(null).message, vm.uiState.value.playerError)
    }
}
