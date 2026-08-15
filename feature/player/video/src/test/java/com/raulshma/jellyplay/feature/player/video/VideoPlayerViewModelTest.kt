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
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
    private lateinit var mediaRepository: MediaRepository
    private lateinit var aggregateStore: VideoPlayerAggregateStore
    private lateinit var jellyfinRemotePlayCastStrategy: com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var offlinePlaybackFacade: com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
    private lateinit var playbackSourceResolver: com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val context = mockk<Context>(relaxed = true)
        mediaRepository = mockk<MediaRepository>(relaxed = true)
        val playbackRepository = mockk<PlaybackRepository>(relaxed = true)
        val imageUrlProvider = mockk<ImageUrlProvider>(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        offlinePlaybackFacade = mockk(relaxed = true)
        playbackSourceResolver = mockk(relaxed = true)
        val itemPlaybackPreferenceRepository = mockk<ItemPlaybackPreferenceRepository>(relaxed = true)
        aggregateStore = mockk<VideoPlayerAggregateStore>(relaxed = true)
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
        jellyfinRemotePlayCastStrategy = mockk<com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy>(relaxed = true)
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
    fun initialize_seedsResumePositionAndDurationBeforeEngineTick() {
        // Regression for the #122 follow-up (v0.10.3): the progress-reset fix
        // left resumed media showing 0 until the engine's first *playing* tick
        // (20-30s with MPV + slow buffering). Opening a resumed item must seed
        // the seek-bar display flows with the resume position and total runtime
        // immediately, before any engine tick. EXTERNAL short-circuits engine
        // creation (PlayerSessionManager returns after populating mediaDetail),
        // so we assert the pre-tick state with no real player.
        every { aggregateStore.aggregate } returns MutableStateFlow(
            VideoPlayerAggregate(playback = PlaybackSlice(preferredPlayer = PlayerType.EXTERNAL))
        )
        every { aggregateStore.aggregateRaw } returns flowOf(
            VideoPlayerAggregate(playback = PlaybackSlice(preferredPlayer = PlayerType.EXTERNAL))
        )
        // Relaxed mock's StateFlow<Boolean>.value returns an Object that won't
        // cast to Boolean — stub it so the "Play On" routing check is skipped.
        every { jellyfinRemotePlayCastStrategy.isConnected } returns MutableStateFlow(false)
        val resumeTicks = 10_290_000_000L // 17:09, the reporter's example
        val runtimeTicks = 54_000_000_000L // 90 min total runtime
        // The pipeline seeds the playhead from the RESOLVED ticks; explicit
        // nonzero ticks pass through the resolver unchanged (its real rule,
        // pinned by PlaybackSourceResolverTest).
        coEvery {
            playbackSourceResolver.resolveStartPositionTicks("item-1", resumeTicks)
        } returns resumeTicks
        coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(
            MediaDetail(
                item = MediaItem(
                    id = "item-1",
                    name = "Test Movie",
                    mediaType = MediaType.MOVIE,
                    runTimeTicks = runtimeTicks,
                )
            )
        )

        viewModel.initialize("item-1", null, resumeTicks)

        // Position is seeded synchronously at load — before any engine tick.
        assertEquals(resumeTicks / 10_000, viewModel.currentPositionMs.value)
        // Duration is seeded from the resolved item's runTimeTicks once loaded.
        assertEquals(runtimeTicks / 10_000, viewModel.durationMs.value)
        // The loading screen lifts once the load completes, so the seek bar's
        // first paint (with both values seeded) is the resume fraction.
        assertFalse(viewModel.uiState.value.isInitializing)
    }

    /**
     * Zero-tick entries (Downloads list, episode browser) resolve the resume
     * position from the offline mirror inside the load pipeline. The playhead
     * seed must use those RESOLVED ticks — not the raw request ticks — or the
     * seek bar paints 0 at the loader lift and jumps to the resume position on
     * the engine's first tick.
     */
    @Test
    fun initialize_zeroRequestTicksWithOfflineResume_seedsResolvedPlayhead() {
        every { aggregateStore.aggregate } returns MutableStateFlow(
            VideoPlayerAggregate(playback = PlaybackSlice(preferredPlayer = PlayerType.EXTERNAL))
        )
        every { aggregateStore.aggregateRaw } returns flowOf(
            VideoPlayerAggregate(playback = PlaybackSlice(preferredPlayer = PlayerType.EXTERNAL))
        )
        every { jellyfinRemotePlayCastStrategy.isConnected } returns MutableStateFlow(false)
        val storedTicks = 5L * 60L * 1000L * 10_000L // 5 min resume in the offline mirror
        coEvery { playbackSourceResolver.resolveStartPositionTicks("item-1", 0L) } returns storedTicks
        coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(
            MediaDetail(
                item = MediaItem(
                    id = "item-1",
                    name = "Test Movie",
                    mediaType = MediaType.MOVIE,
                    runTimeTicks = 54_000_000_000L,
                )
            )
        )

        viewModel.initialize("item-1", null, 0L)

        assertEquals(storedTicks / 10_000, viewModel.currentPositionMs.value)
        assertFalse(viewModel.uiState.value.isInitializing)
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
        val before = viewModel.effectsState.value.nightModeEnabled
        viewModel.toggleNightMode()
        assertEquals(!before, viewModel.effectsState.value.nightModeEnabled)
    }

    @Test
    fun setNightModeStrength_updatesState() {
        viewModel.setNightModeStrength(EffectStrength.HIGH)
        assertEquals(EffectStrength.HIGH, viewModel.effectsState.value.nightModeStrength)
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
    //
    // [resolveOfflineResumeTicks] is now a thin pass-through to
    // [PlaybackSourceResolver.resolveStartPositionTicks]; these tests pin the
    // delegation contract. The explicit-vs-stored-vs-zero rule itself is
    // owned by the resolver and covered by PlaybackSourceResolverTest.

    @Test
    fun resolveOfflineResumeTicks_completedDownloadWithProgress_returnsStoredPosition() = runBlocking {
        val itemId = "item-movie"
        val savedTicks = 5 * 60 * 1_000L * 10_000L // 5 min, in ticks
        coEvery { playbackSourceResolver.resolveStartPositionTicks(itemId, 0L) } returns savedTicks

        val resolved = callResolveOfflineResumeTicks(itemId, startPositionTicks = 0L)

        assertEquals(savedTicks, resolved)
    }

    @Test
    fun resolveOfflineResumeTicks_withExplicitStartPosition_keepsCallerValue() = runBlocking {
        val itemId = "item-movie"
        val explicitTicks = 30 * 1_000L * 10_000L
        coEvery { playbackSourceResolver.resolveStartPositionTicks(itemId, explicitTicks) } returns explicitTicks

        val resolved = callResolveOfflineResumeTicks(itemId, explicitTicks)

        assertEquals(explicitTicks, resolved)
    }

    @Test
    fun resolveOfflineResumeTicks_nonCompletedDownload_returnsZero() = runBlocking {
        val itemId = "item-movie"
        coEvery { playbackSourceResolver.resolveStartPositionTicks(itemId, 0L) } returns 0L

        val resolved = callResolveOfflineResumeTicks(itemId, startPositionTicks = 0L)

        assertEquals(0L, resolved)
    }

    @Test
    fun resolveOfflineResumeTicks_noDownload_returnsZero() = runBlocking {
        val itemId = "item-movie"
        coEvery { playbackSourceResolver.resolveStartPositionTicks(itemId, 0L) } returns 0L

        val resolved = callResolveOfflineResumeTicks(itemId, startPositionTicks = 0L)

        assertEquals(0L, resolved)
    }

    @Test
    fun resolveOfflineResumeTicks_completedDownloadWithoutProgress_returnsZero() = runBlocking {
        val itemId = "item-movie"
        coEvery { playbackSourceResolver.resolveStartPositionTicks(itemId, 0L) } returns 0L

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
