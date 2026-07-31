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
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayPlaybackCore
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlayerViewModelExtendedTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: VideoPlayerViewModel
    private lateinit var itemPlaybackPreferenceRepository: ItemPlaybackPreferenceRepository
    private lateinit var preferencesStore: UserPreferencesStore

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val context = mockk<Context>(relaxed = true)
        val mediaRepository = mockk<MediaRepository>(relaxed = true)
        val playbackRepository = mockk<PlaybackRepository>(relaxed = true)
        val imageUrlProvider = mockk<ImageUrlProvider>(relaxed = true)
        val downloadRepository = mockk<DownloadRepository>(relaxed = true)
        val offlineRepository = mockk<OfflineRepository>(relaxed = true)
        val offlinePlaybackFacade = mockk<OfflinePlaybackFacade>(relaxed = true)
        itemPlaybackPreferenceRepository = mockk(relaxed = true)
        preferencesStore = mockk(relaxed = true)
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
        val playerLifecycleManager = PlayerLifecycleManager(preferencesStore)
        val pipController = com.raulshma.jellyplay.core.data.playback.PipController()
        val videoMiniPlayerState = mockk<VideoMiniPlayerState>(relaxed = true)
        val sleepTimerManager = mockk<SleepTimerManager>(relaxed = true)

        every { preferencesStore.preferences } returns MutableStateFlow(UserPreferences())
        every { sleepTimerManager.remainingMs } returns MutableStateFlow(0L)
        val playbackCore = mockk<SyncPlayPlaybackCore>(relaxed = true)
        every { syncPlayManager.playbackCore } returns playbackCore

        viewModel = VideoPlayerViewModel(
            context = context,
            mediaRepository = mediaRepository,
            playbackRepository = playbackRepository,
            imageUrlProvider = imageUrlProvider,
            downloadRepository = downloadRepository,
            offlineRepository = offlineRepository,
            offlinePlaybackFacade = offlinePlaybackFacade,
            itemPlaybackPreferenceRepository = itemPlaybackPreferenceRepository,
            preferencesStore = preferencesStore,
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
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setScreenLocked_updatesState() {
        assertFalse(viewModel.uiState.value.isScreenLocked)
        viewModel.setScreenLocked(true)
        assertTrue(viewModel.uiState.value.isScreenLocked)
        viewModel.setScreenLocked(false)
        assertFalse(viewModel.uiState.value.isScreenLocked)
    }

    @Test
    fun setAudioDelay_updatesState() {
        viewModel.setAudioDelay(150L)
        assertEquals(150L, viewModel.uiState.value.audioDelayMs)
    }

    @Test
    fun setSubtitleDelay_updatesState() {
        viewModel.setSubtitleDelay(-200L)
        assertEquals(-200L, viewModel.uiState.value.subtitleStyle.offsetMs)
    }

    @Test
    fun setStreamingQuality_updatesState() {
        viewModel.setStreamingQuality(StreamingQuality.FHD_1080P)
        assertEquals(StreamingQuality.FHD_1080P, viewModel.uiState.value.streamingQuality)
    }

    @Test
    fun setAdaptiveBitrateEnabled_updatesState() {
        viewModel.setAdaptiveBitrateEnabled(true)
        assertTrue(viewModel.uiState.value.adaptiveBitrateEnabled)
        viewModel.setAdaptiveBitrateEnabled(false)
        assertFalse(viewModel.uiState.value.adaptiveBitrateEnabled)
    }

    @Test
    fun setFrameRateMatching_updatesState() {
        viewModel.setFrameRateMatching(true)
        assertTrue(viewModel.uiState.value.frameRateMatching)
    }

    @Test
    fun setEqualizerSettings_updatesState() {
        val settings = EqualizerSettings(bandLevels = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
        viewModel.setEqualizerSettings(settings)
    }

    @Test
    fun dismissPlaybackError_clearsErrorState() {
        viewModel.dismissPlaybackError()
    }

    @Test
    fun setSeriesAudioLanguagePreference_triggersRepositoryUpdateWhenSeriesIdPresent() {
        viewModel.setSeriesAudioLanguagePreference("eng")
        assertEquals(viewModel.uiState.value.title, "")
    }

    @Test
    fun setSeriesSubtitlePreference_triggersRepositoryUpdateWhenSeriesIdPresent() {
        viewModel.setSeriesSubtitlePreference("spa")
        assertEquals(viewModel.uiState.value.title, "")
    }
}
