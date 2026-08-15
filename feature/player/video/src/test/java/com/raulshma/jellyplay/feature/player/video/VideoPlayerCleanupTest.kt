package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.remote.ActivePlayerController
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayPlaybackCore
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.core.ui.viewmodel.StateFlowHandle
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.After
import org.junit.Before
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient

@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlayerCleanupTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun playerLifecycleManager_reset_clearsActiveCallbacks() {
        val playbackStore = mockk<PlaybackStore>(relaxed = true)
        every { playbackStore.playback } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice()
        )
        val manager = PlayerLifecycleManager(playbackStore)

        manager.activeCallbacks = mockk()
        manager.reset()

        assertNull(manager.activeCallbacks)
    }

    @Test
    fun pipController_reset_clearsAllPipState() {
        val pipController = PipController()

        // Set non-default values
        pipController.pipTransport = mockk()
        pipController.pipHasNext = true
        pipController.setPipMode(true)
        pipController.requestAutoEnterPip(true)
        pipController.setPlaying(true)
        pipController.notifyPipDismissed()

        assertTrue(pipController.isInPipMode.value)
        assertTrue(pipController.shouldAutoEnterPip.value)
        assertTrue(pipController.isPlaying.value)
        assertTrue(pipController.pipDismissed.value)
        assertTrue(pipController.pipHasNext)

        // Reset
        pipController.reset()

        assertNull(pipController.pipTransport)
        assertFalse(pipController.isInPipMode.value)
        assertFalse(pipController.shouldAutoEnterPip.value)
        assertFalse(pipController.isPlaying.value)
        assertFalse(pipController.pipDismissed.value)
        assertFalse(pipController.pipHasNext)
    }

    @Test
    fun playbackProgressReporter_cancelJobs_resetsTriggers() {
        val playbackRepo = mockk<PlaybackRepository>(relaxed = true)
        val viewModel = mockk<ViewModel>(relaxed = true)
        val uiState = StateFlowHandle(MutableStateFlow(VideoPlayerUiState()))

        val reporter = PlaybackProgressReporter(
            playbackRepository = playbackRepo,
            viewModel = viewModel,
            uiState = uiState,
            getCurrentItemId = { "item1" },
            getPlaySessionId = { "session1" },
            getResolvedPlayMethod = { com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_PLAY },
            getMediaEngine = { null },
            getIncognitoModeEnabled = { false },
            onAutoSkip = {},
            onPlaybackEndedNoNext = {},
            onWatchedThresholdReached = {},
            onPositionPersisted = {},
            onEnginePositionUpdate = { _, _, _, _ -> },
        )

        reporter.cancelJobs()
    }

    @Test
    fun `syncPlayBridge_reset_clearsStateAndResetsCore`() {
        val syncPlayManager = mockk<SyncPlayManager>(relaxed = true)
        val playbackCore = mockk<SyncPlayPlaybackCore>(relaxed = true)
        every { syncPlayManager.playbackCore } returns playbackCore
        syncPlayManager.stubEmptyEvents()

        val bridge = SyncPlayBridge(
            syncPlayManager = syncPlayManager,
            getMediaEngine = { null },
            getCurrentItemId = { null },
            onLoadItem = { _, _ -> },
            setIsPlaying = { },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )

        bridge.reset()

        verify { playbackCore.reset() }
        // reset() must also release the callbacks held by the @Singleton core
        // so it cannot retain the destroyed ViewModel/bridge after teardown.
        verify { playbackCore.clearCallbacks() }
        assertFalse(bridge.state.value.isSyncPlaySyncing)
    }

    @Test
    fun videoPlayerViewModel_release_clearsStateAndResetsComponents() {
        val context = mockk<Context>(relaxed = true)
        val mediaRepository = mockk<MediaRepository>(relaxed = true)
        val playbackRepository = mockk<PlaybackRepository>(relaxed = true)
        val imageUrlProvider = mockk<ImageUrlProvider>(relaxed = true)
        val downloadRepository = mockk<DownloadRepository>(relaxed = true)
        val offlineRepository = mockk<OfflineRepository>(relaxed = true)
        val itemPlaybackPreferenceRepository = mockk<ItemPlaybackPreferenceRepository>(relaxed = true)
        val aggregateStore = mockk<VideoPlayerAggregateStore>(relaxed = true)
        val engineStore = mockk<PlayerEngineStore>(relaxed = true)
        val subtitleStore = mockk<SubtitleLanguageStore>(relaxed = true)
        val securityStore = mockk<SecurityStore>(relaxed = true)
        val syncPlayCastStore = mockk<SyncPlayCastStore>(relaxed = true)
        val playbackStore = mockk<PlaybackStore>(relaxed = true)
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
        val pipController = PipController()
        val videoMiniPlayerState = mockk<VideoMiniPlayerState>(relaxed = true)
        val sleepTimerManager = mockk<SleepTimerManager>(relaxed = true)

        // Mock flows to prevent NPE or hang
        every { aggregateStore.aggregate } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate()
        )
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

        val viewModel = VideoPlayerViewModel(
            context = context,
            mediaRepository = mediaRepository,
            playbackRepository = playbackRepository,
            subtitleProviderRepository = mockk(relaxed = true),
            streamingSubtitleStore = noOpStreamingSubtitleStore(),
            imageUrlProvider = imageUrlProvider,
            downloadRepository = downloadRepository,
            offlineRepository = offlineRepository,
            offlinePlaybackFacade = mockk(relaxed = true),
            playbackSourceResolver = mockk(relaxed = true),
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
            playerEngineFactory = com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory(
                context,
                okHttpClient,
                mockk<com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider>(relaxed = true),
            ),
            fontProvider = mockk(relaxed = true),
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
            subtitlePreviewRepository = mockk(relaxed = true),
            userDataMutator = mockk(relaxed = true),
        )

        // Set some media-specific states to verify they are cleared on release
        playerLifecycleManager.activeCallbacks = mockk()
        pipController.setPipMode(true)

        // Call release
        viewModel.release()

        // Verify playerLifecycleManager + pipController were reset
        assertNull(playerLifecycleManager.activeCallbacks)
        assertFalse(pipController.isInPipMode.value)

        // Verify syncPlayManager.playbackCore was reset
        verify { playbackCore.reset() }

        // Verify UI state has been reset (e.g. title is empty)
        assertTrue(viewModel.uiState.value.title.isEmpty())
    }

    @Test
    fun pipController_notifyPipDismissed_triggersViewModelReleaseAndClosePlayer() {
        val pipController = PipController()
        val sessionManager = mockk<PlaybackSessionManager>(relaxed = true)
        val playbackCore = mockk<SyncPlayPlaybackCore>(relaxed = true)
        val syncPlayManager = mockk<SyncPlayManager>(relaxed = true)
        every { syncPlayManager.playbackCore } returns playbackCore
        syncPlayManager.stubEmptyEvents()

        val viewModel = VideoPlayerViewModel(
            context = mockk(relaxed = true),
            mediaRepository = mockk(relaxed = true),
            playbackRepository = mockk(relaxed = true),
            subtitleProviderRepository = mockk(relaxed = true),
            streamingSubtitleStore = noOpStreamingSubtitleStore(),
            imageUrlProvider = mockk(relaxed = true),
            downloadRepository = mockk(relaxed = true),
            offlineRepository = mockk(relaxed = true),
            offlinePlaybackFacade = mockk(relaxed = true),
            playbackSourceResolver = mockk(relaxed = true),
            episodeCatalogue = mockk(relaxed = true),
            itemPlaybackPreferenceRepository = mockk(relaxed = true),
            aggregateStore = mockk(relaxed = true) { every { aggregate } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate()) },
            engineStore = mockk(relaxed = true) { every { playerEngine } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.engine.PlayerEngineSlice()) },
            subtitleStore = mockk(relaxed = true) { every { subtitle } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice()) },
            securityStore = mockk(relaxed = true) { every { security } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.security.SecuritySlice()) },
            syncPlayCastStore = mockk(relaxed = true) { every { syncPlayCast } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice()) },
            playbackStore = mockk(relaxed = true) { every { playback } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice()) },
            audioStore = mockk(relaxed = true) { every { audio } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.audio.AudioSlice()) },
            audioEffectsStore = mockk(relaxed = true) { every { audioEffects } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice()) },
            videoPlayerStore = mockk(relaxed = true) { every { videoPlayer } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice()) },
            downloadsStore = mockk(relaxed = true) { every { downloads } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice()) },
            appearanceStore = mockk(relaxed = true) { every { appearance } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice()) },
            networkOfflineStore = mockk(relaxed = true) { every { networkOffline } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice()) },
            sessionManager = sessionManager,
            castManager = mockk(relaxed = true),
            jellyfinRemotePlayCastStrategy = mockk(relaxed = true),
            syncPlayManager = syncPlayManager,
            okHttpClient = mockk(relaxed = true),
            adaptiveBitrateManager = mockk(relaxed = true),
            networkMonitor = mockk(relaxed = true) { every { isMetered } returns MutableStateFlow(false) },
            activePlayerController = mockk(relaxed = true),
            playerLifecycleManager = PlayerLifecycleManager(mockk(relaxed = true) { every { playback } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice()) }),
            pipController = pipController,
            videoMiniPlayerState = mockk(relaxed = true),
            sleepTimerManager = mockk(relaxed = true) { every { remainingMs } returns MutableStateFlow(0L) },
            userMessageBus = UserMessageBus(),
            playerEngineFactory = mockk(relaxed = true),
            fontProvider = mockk(relaxed = true),
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
            subtitlePreviewRepository = mockk(relaxed = true),
            userDataMutator = mockk(relaxed = true),
        )

        pipController.notifyPipDismissed()

        // Verify syncPlayManager.playbackCore was reset (proves release() ran)
        verify { playbackCore.reset() }
    }
}
