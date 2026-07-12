package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
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
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
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
    fun playerLifecycleManager_reset_clearsAllState() {
        val prefsStore = mockk<UserPreferencesStore>(relaxed = true)
        val manager = PlayerLifecycleManager(prefsStore)

        // Set non-default values
        manager.activeCallbacks = mockk()
        manager.setPipMode(true)
        manager.requestAutoEnterPip(true)
        manager.notifyPipDismissed()

        assertTrue(manager.isInPipMode.value)
        assertTrue(manager.shouldAutoEnterPip.value)
        assertTrue(manager.pipDismissed.value)

        // Reset
        manager.reset()

        assertNull(manager.activeCallbacks)
        assertFalse(manager.isInPipMode.value)
        assertFalse(manager.shouldAutoEnterPip.value)
        assertFalse(manager.pipDismissed.value)
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
            getIsLive = { false },
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

        val uiState = StateFlowHandle(MutableStateFlow(VideoPlayerUiState()))
        val bridge = SyncPlayBridge(
            syncPlayManager = syncPlayManager,
            uiState = uiState,
            getMediaEngine = { null },
            getCurrentItemId = { null },
            onLoadItem = { _, _ -> },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )

        bridge.reset()

        verify { playbackCore.reset() }
        // reset() must also release the callbacks held by the @Singleton core
        // so it cannot retain the destroyed ViewModel/bridge after teardown.
        verify { playbackCore.clearCallbacks() }
        assertFalse(uiState.value.isSyncPlaySyncing)
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
        val preferencesStore = mockk<UserPreferencesStore>(relaxed = true)
        val sessionManager = mockk<PlaybackSessionManager>(relaxed = true)
        val castManager = mockk<CastManager>(relaxed = true)
        val jellyfinRemotePlayCastStrategy = mockk<com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy>(relaxed = true)
        val syncPlayManager = mockk<SyncPlayManager>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val adaptiveBitrateManager = mockk<AdaptiveBitrateManager>(relaxed = true)
        val activePlayerController = mockk<ActivePlayerController>(relaxed = true)
        val playerLifecycleManager = PlayerLifecycleManager(preferencesStore)
        val videoMiniPlayerState = mockk<VideoMiniPlayerState>(relaxed = true)
        val sleepTimerManager = mockk<SleepTimerManager>(relaxed = true)

        // Mock flows to prevent NPE or hang
        every { preferencesStore.preferences } returns MutableStateFlow(com.raulshma.jellyplay.core.model.UserPreferences())
        every { sleepTimerManager.remainingMs } returns MutableStateFlow(0L)
        val playbackCore = mockk<SyncPlayPlaybackCore>(relaxed = true)
        every { syncPlayManager.playbackCore } returns playbackCore

        val viewModel = VideoPlayerViewModel(
            context = context,
            mediaRepository = mediaRepository,
            playbackRepository = playbackRepository,
            imageUrlProvider = imageUrlProvider,
            downloadRepository = downloadRepository,
            offlineRepository = offlineRepository,
            itemPlaybackPreferenceRepository = itemPlaybackPreferenceRepository,
            preferencesStore = preferencesStore,
            sessionManager = sessionManager,
            castManager = castManager,
            jellyfinRemotePlayCastStrategy = jellyfinRemotePlayCastStrategy,
            syncPlayManager = syncPlayManager,
            okHttpClient = okHttpClient,
            adaptiveBitrateManager = adaptiveBitrateManager,
            activePlayerController = activePlayerController,
            playerLifecycleManager = playerLifecycleManager,
            videoMiniPlayerState = videoMiniPlayerState,
            sleepTimerManager = sleepTimerManager,
            userMessageBus = UserMessageBus(),
            playerEngineFactory = com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory(context, okHttpClient),
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
        )

        // Set some media-specific states to verify they are cleared on release
        playerLifecycleManager.activeCallbacks = mockk()
        playerLifecycleManager.setPipMode(true)

        // Call release
        viewModel.release()

        // Verify playerLifecycleManager was reset
        assertNull(playerLifecycleManager.activeCallbacks)
        assertFalse(playerLifecycleManager.isInPipMode.value)

        // Verify syncPlayManager.playbackCore was reset
        verify { playbackCore.reset() }

        // Verify UI state has been reset (e.g. title is empty)
        assertTrue(viewModel.uiState.value.title.isEmpty())
    }
}
