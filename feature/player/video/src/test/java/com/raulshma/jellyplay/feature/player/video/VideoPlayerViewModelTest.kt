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
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayPlaybackCore
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.feature.player.video.components.AspectRatio
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
class VideoPlayerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: VideoPlayerViewModel
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var offlineRepository: OfflineRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val context = mockk<Context>(relaxed = true)
        val mediaRepository = mockk<MediaRepository>(relaxed = true)
        val playbackRepository = mockk<PlaybackRepository>(relaxed = true)
        val imageUrlProvider = mockk<ImageUrlProvider>(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        val itemPlaybackPreferenceRepository = mockk<ItemPlaybackPreferenceRepository>(relaxed = true)
        val preferencesStore = mockk<UserPreferencesStore>(relaxed = true)
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
    fun seekTo_updatesCurrentPosition() {
        viewModel.seekTo(5_000L)
        // Position now lives on the dedicated currentPositionMs flow.
        assertEquals(5_000L, viewModel.currentPositionMs.value)
    }

    @Test
    fun seekTo_multipleCalls_keepsLatestPosition() {
        viewModel.seekTo(1_000L)
        viewModel.seekTo(2_000L)
        viewModel.seekTo(3_000L)
        assertEquals(3_000L, viewModel.currentPositionMs.value)
    }

    @Test
    fun getReportPositionMs_afterRecentSeek_returnsSeekPosition() {
        viewModel.seekTo(42_000L)
        assertEquals(42_000L, callGetReportPositionMs())
    }

    @Test
    fun getReportPositionMs_withNoPriorSeek_returnsZero() {
        assertEquals(0L, callGetReportPositionMs())
    }

    @Test
    fun setPlaybackSpeed_updatesState() {
        viewModel.setPlaybackSpeed(1.5f)
        assertEquals(1.5f, viewModel.uiState.value.playbackSpeed, 0.001f)
    }

    @Test
    fun setPlaybackSpeed_zeroOrNegative_stillSetsState() {
        viewModel.setPlaybackSpeed(0f)
        assertEquals(0f, viewModel.uiState.value.playbackSpeed, 0.001f)
    }

    @Test
    fun startHoldSpeed_activatesAndStoresHoldMultiplier() {
        viewModel.setPlaybackSpeed(1.0f)
        viewModel.startHoldSpeed()
        assertTrue(viewModel.uiState.value.isHoldSpeedActive)
        assertEquals(viewModel.uiState.value.holdSpeedMultiplier, viewModel.uiState.value.playbackSpeed, 0.001f)
    }

    @Test
    fun startHoldSpeed_whenAlreadyActive_isIdempotent() {
        viewModel.startHoldSpeed()
        val first = viewModel.uiState.value.playbackSpeed
        viewModel.startHoldSpeed()
        assertTrue(viewModel.uiState.value.isHoldSpeedActive)
        assertEquals(first, viewModel.uiState.value.playbackSpeed, 0.001f)
    }

    @Test
    fun stopHoldSpeed_restoresPreviousSpeed() {
        viewModel.setPlaybackSpeed(1.25f)
        viewModel.startHoldSpeed()
        assertTrue(viewModel.uiState.value.isHoldSpeedActive)

        viewModel.stopHoldSpeed()
        assertFalse(viewModel.uiState.value.isHoldSpeedActive)
        assertEquals(1.25f, viewModel.uiState.value.playbackSpeed, 0.001f)
    }

    @Test
    fun stopHoldSpeed_whenNotActive_isNoOp() {
        viewModel.stopHoldSpeed()
        assertFalse(viewModel.uiState.value.isHoldSpeedActive)
    }

    @Test
    fun setAspectRatio_updatesStateExplicitRatio() {
        viewModel.setAspectRatio(AspectRatio.RATIO_21_9)
        assertEquals(AspectRatio.RATIO_21_9, viewModel.uiState.value.aspectRatio)
    }

    @Test
    fun setAspectRatio_off_doesNotMutateDetected() {
        viewModel.setAspectRatio(AspectRatio.FIT)
        assertEquals(AspectRatio.FIT, viewModel.uiState.value.aspectRatio)
    }

    @Test
    fun toggleDialogueBoost_flipsEnabled() {
        val before = viewModel.uiState.value.dialogueBoostEnabled
        viewModel.toggleDialogueBoost()
        assertEquals(!before, viewModel.uiState.value.dialogueBoostEnabled)
        viewModel.toggleDialogueBoost()
        assertEquals(before, viewModel.uiState.value.dialogueBoostEnabled)
    }

    @Test
    fun setDialogueBoostStrength_updatesState() {
        viewModel.setDialogueBoostStrength(EffectStrength.HIGH)
        assertEquals(EffectStrength.HIGH, viewModel.uiState.value.dialogueBoostStrength)
    }

    @Test
    fun toggleNightMode_flipsEnabled() {
        val before = viewModel.uiState.value.nightModeEnabled
        viewModel.toggleNightMode()
        assertEquals(!before, viewModel.uiState.value.nightModeEnabled)
    }

    @Test
    fun setNightModeStrength_updatesState() {
        viewModel.setNightModeStrength(EffectStrength.HIGH)
        assertEquals(EffectStrength.HIGH, viewModel.uiState.value.nightModeStrength)
    }

    @Test
    fun setPlaybackMode_updatesState() {
        val target = if (viewModel.uiState.value.playbackMode == PlaybackMode.FORCE_TRANSCODE)
            PlaybackMode.FORCE_DIRECT_PLAY else PlaybackMode.FORCE_TRANSCODE
        viewModel.setPlaybackMode(target)
        assertEquals(target, viewModel.uiState.value.playbackMode)
    }

    @Test
    fun toggleVideoStats_flipsEnabled() {
        val before = viewModel.uiState.value.showVideoStats
        viewModel.toggleVideoStats()
        assertEquals(!before, viewModel.uiState.value.showVideoStats)
    }

    @Test
    fun release_resetsUiState() {
        viewModel.setPlaybackSpeed(2.0f)
        viewModel.seekTo(9_000L)

        viewModel.release()

        assertTrue(viewModel.uiState.value.title.isEmpty())
        assertEquals(1.0f, viewModel.uiState.value.playbackSpeed, 0.001f)
    }

    private fun callGetReportPositionMs(): Long {
        val fn = VideoPlayerViewModel::class.java.getDeclaredMethod("getReportPositionMs")
        fn.isAccessible = true
        return fn.invoke(viewModel) as Long
    }

    // ── Offline resume position resolution ────────────────────────────

    @Test
    fun resolveOfflineResumeTicks_completedDownloadWithProgress_returnsStoredPosition() = runBlocking {
        val itemId = "item-movie"
        val savedTicks = 5 * 60 * 1_000L * 10_000L // 5 min, in ticks
        coEvery { downloadRepository.getDownloadByMediaItemId(itemId) } returns downloadItem(itemId)
        coEvery { offlineRepository.getOfflineItem(itemId) } returns offlineMediaItem(itemId, savedTicks)

        val resolved = callResolveOfflineResumeTicks(itemId, startPositionTicks = 0L)

        assertEquals(savedTicks, resolved)
    }

    @Test
    fun resolveOfflineResumeTicks_withExplicitStartPosition_keepsCallerValue() = runBlocking {
        val itemId = "item-movie"
        val explicitTicks = 30 * 1_000L * 10_000L
        coEvery { downloadRepository.getDownloadByMediaItemId(itemId) } returns downloadItem(itemId)
        coEvery { offlineRepository.getOfflineItem(itemId) } returns
            offlineMediaItem(itemId, 10 * 60 * 1_000L * 10_000L)

        val resolved = callResolveOfflineResumeTicks(itemId, explicitTicks)

        assertEquals(explicitTicks, resolved)
        // The offline store should not even be consulted when the caller
        // already provided a start position.
        io.mockk.coVerify(exactly = 0) { offlineRepository.getOfflineItem(any()) }
    }

    @Test
    fun resolveOfflineResumeTicks_nonCompletedDownload_returnsZero() = runBlocking {
        val itemId = "item-movie"
        coEvery { downloadRepository.getDownloadByMediaItemId(itemId) } returns
            downloadItem(itemId, status = DownloadStatus.DOWNLOADING)

        val resolved = callResolveOfflineResumeTicks(itemId, startPositionTicks = 0L)

        assertEquals(0L, resolved)
    }

    @Test
    fun resolveOfflineResumeTicks_noDownload_returnsZero() = runBlocking {
        val itemId = "item-movie"
        coEvery { downloadRepository.getDownloadByMediaItemId(itemId) } returns null

        val resolved = callResolveOfflineResumeTicks(itemId, startPositionTicks = 0L)

        assertEquals(0L, resolved)
    }

    @Test
    fun resolveOfflineResumeTicks_completedDownloadWithoutProgress_returnsZero() = runBlocking {
        val itemId = "item-movie"
        coEvery { downloadRepository.getDownloadByMediaItemId(itemId) } returns downloadItem(itemId)
        coEvery { offlineRepository.getOfflineItem(itemId) } returns offlineMediaItem(itemId, null)

        val resolved = callResolveOfflineResumeTicks(itemId, startPositionTicks = 0L)

        assertEquals(0L, resolved)
    }

    private fun downloadItem(
        itemId: String,
        status: DownloadStatus = DownloadStatus.COMPLETED,
    ) = DownloadItem(
        id = "dl-$itemId",
        mediaItemId = itemId,
        name = "Test Movie",
        mediaType = MediaType.MOVIE,
        downloadPath = "/data/media/movie.mp4",
        downloadUrl = "http://example.com/movie",
        totalSizeBytes = 1_000_000L,
        downloadedBytes = 1_000_000L,
        status = status,
    )

    private fun offlineMediaItem(itemId: String, positionTicks: Long?) = OfflineMediaItem(
        id = itemId,
        name = "Test Movie",
        mediaType = MediaType.MOVIE,
        runTimeTicks = 3_600_000 * 10_000L,
        playbackPositionTicks = positionTicks,
    )

    private suspend fun callResolveOfflineResumeTicks(itemId: String, startPositionTicks: Long): Long =
        viewModel.resolveOfflineResumeTicks(itemId, startPositionTicks)
}
