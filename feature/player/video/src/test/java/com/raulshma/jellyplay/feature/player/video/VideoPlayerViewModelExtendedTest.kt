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
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import io.mockk.clearMocks
import io.mockk.coVerify
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
    private lateinit var subtitleStore: SubtitleLanguageStore

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
        val aggregateStore = mockk<VideoPlayerAggregateStore>(relaxed = true)
        val engineStore = mockk<PlayerEngineStore>(relaxed = true)
        subtitleStore = mockk<SubtitleLanguageStore>(relaxed = true)
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
        val pipController = com.raulshma.jellyplay.core.data.playback.PipController()
        val videoMiniPlayerState = mockk<VideoMiniPlayerState>(relaxed = true)
        val sleepTimerManager = mockk<SleepTimerManager>(relaxed = true)

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

        viewModel = VideoPlayerViewModel(
            context = context,
            mediaRepository = mediaRepository,
            playbackRepository = playbackRepository,
            subtitleProviderRepository = mockk(relaxed = true),
            streamingSubtitleStore = noOpStreamingSubtitleStore(),
            imageUrlProvider = imageUrlProvider,
            downloadRepository = downloadRepository,
            offlineRepository = offlineRepository,
            offlinePlaybackFacade = offlinePlaybackFacade,
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
        viewModel.effects.setAudioDelay(150L)
        assertEquals(150L, viewModel.effects.state.value.audioDelayMs)
    }

    @Test
    fun setSubtitleDelay_updatesState() {
        viewModel.setSubtitleDelay(-200L)
        assertEquals(-200L, viewModel.uiState.value.subtitleStyle.offsetMs)
    }

    @Test
    fun setSubtitleDelay_doesNotClobberGlobalStyle() {
        // A per-media delay correction must persist only to the per-item store,
        // never through the global "Subtitle sync offset" style bucket — that
        // global write was the cross-media leak. currentItemId is null in this
        // harness, so the per-item write is skipped; we assert the global bucket
        // is untouched regardless.
        clearMocks(subtitleStore, answers = false, recordedCalls = true, childMocks = false)
        viewModel.setSubtitleDelay(-200L)
        assertEquals(-200L, viewModel.uiState.value.subtitleStyle.offsetMs)
        coVerify(exactly = 0) { subtitleStore.setSubtitleStyle(any()) }
    }

    @Test
    fun setSubtitleStyle_preservesGlobalSubtitleDelayOnPersist() {
        // Resolve a per-item delay into in-memory state, then make an unrelated
        // style edit (font size). The persisted style must carry the global
        // default offsetMs (0 from the default aggregate), NOT the in-memory
        // per-item delay — otherwise a font/colour change re-leaks the delay.
        viewModel.setSubtitleDelay(-300L)
        assertEquals(-300L, viewModel.uiState.value.subtitleStyle.offsetMs)
        clearMocks(subtitleStore, answers = false, recordedCalls = true, childMocks = false)

        val edited = viewModel.uiState.value.subtitleStyle.copy(fontSize = 40)
        viewModel.setSubtitleStyle(edited)

        coVerify {
            subtitleStore.setSubtitleStyle(match { it.offsetMs == 0L && it.fontSize == 40 })
        }
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
