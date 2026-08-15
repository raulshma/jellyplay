package com.raulshma.jellyplay.feature.details

import android.content.Context
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.playback.ThemeMusicPlayer
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.AppliedMutation
import com.raulshma.jellyplay.core.data.repository.DetailLoadState
import com.raulshma.jellyplay.core.data.repository.DetailLoadError
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.sync.OfflineSyncManager
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineSlice
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.library.LibrarySlice
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.DetailAssets
import com.raulshma.jellyplay.core.model.DetailCapabilities
import com.raulshma.jellyplay.core.model.DetailContext
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.ExternalUrl
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaDetailSnapshot
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.RemoteConnectivity
import com.raulshma.jellyplay.core.model.ResyncStep
import com.raulshma.jellyplay.core.model.ResyncStepResult
import com.raulshma.jellyplay.core.model.ResyncResult
import com.raulshma.jellyplay.core.network.api.TmdbApiClient
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var mediaRepository: MediaRepository
    private lateinit var mediaDetailProvider: MediaDetailProvider
    private lateinit var userDataMutator: FakeUserDataMutator
    private lateinit var offlineSyncManager: OfflineSyncManager
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var downloadIntake: DownloadIntake
    private lateinit var projections: PreferenceProjections
    private lateinit var libraryStore: LibraryStore
    private lateinit var homeDiscoveryStore: HomeDiscoveryStore
    private lateinit var experimentalStore: ExperimentalStore
    private lateinit var downloadsStore: DownloadsStore
    private lateinit var appRuntimeStateStore: AppRuntimeStateStore
    private lateinit var engineStore: PlayerEngineStore
    private lateinit var offlineModeManager: OfflineModeManager
    private lateinit var adaptiveBitrateManager: AdaptiveBitrateManager
    private lateinit var seerrRepository: SeerrRepository
    private lateinit var seerrRequestDelegate: SeerrRequestDelegate
    private lateinit var audioPlaybackManager: AudioPlaybackManager
    private lateinit var audioQueueFacade: AudioQueueFacade
    private lateinit var themeMusicPlayer: ThemeMusicPlayer
    private lateinit var tmdbApiClient: TmdbApiClient
    private lateinit var arrRepository: ArrRepository
    private lateinit var syncPlayManager: SyncPlayManager

    private lateinit var viewModel: DetailViewModel

    /** Per-item provider flow, so tests can emit Loaded/Error/attachment ticks. */
    private val providerFlows = mutableMapOf<String, MutableStateFlow<DetailLoadState>>()

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        mediaDetailProvider = mockk(relaxed = false)
        offlineSyncManager = mockk(relaxed = true)
        playbackRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        downloadIntake = mockk(relaxed = true)
        projections = mockk(relaxed = true)
        libraryStore = mockk(relaxed = true)
        homeDiscoveryStore = mockk(relaxed = true)
        experimentalStore = mockk(relaxed = true)
        downloadsStore = mockk(relaxed = true)
        appRuntimeStateStore = mockk(relaxed = true)
        engineStore = mockk(relaxed = true)
        offlineModeManager = mockk(relaxed = true)
        adaptiveBitrateManager = mockk(relaxed = true)
        seerrRepository = mockk(relaxed = true)
        seerrRequestDelegate = mockk(relaxed = true)
        audioPlaybackManager = mockk(relaxed = true)
        audioQueueFacade = mockk()
        themeMusicPlayer = mockk(relaxed = true)
        tmdbApiClient = mockk(relaxed = true)
        arrRepository = mockk(relaxed = true)
        syncPlayManager = mockk(relaxed = true)

        every { projections.detailPreferences } returns MutableStateFlow(DetailPreferences())
        every { libraryStore.library } returns MutableStateFlow(LibrarySlice())
        every { homeDiscoveryStore.homeDiscovery } returns MutableStateFlow(HomeDiscoverySlice())
        every { experimentalStore.experimental } returns MutableStateFlow(ExperimentalSlice())
        every { downloadsStore.downloads } returns MutableStateFlow(DownloadsSlice())
        every { engineStore.playerEngine } returns MutableStateFlow(PlayerEngineSlice())
        every { seerrRepository.isConnected() } returns flowOf(false)
        every { seerrRepository.isRecommendationsEnabled() } returns flowOf(false)
        // Default: online. Prevents the Seerr Local-skip from falsely firing on
        // a relaxed-mock NetworkStatus when a REMOTE snapshot triggers discovery.
        every { offlineModeManager.networkStatus } returns MutableStateFlow(NetworkStatus.Online)
        // Default stub for the similar-items fetch so the REMOTE side-effect launch
        // doesn't crash casting the relaxed-mock default.
        coEvery { mediaRepository.getSimilarItems(any(), any()) } returns Result.success(emptyList())
        // Default stub for the special-features fetch so its REMOTE side-effect
        // launch doesn't crash casting the relaxed-mock Result default. Individual
        // tests override this to drive the specialFeatures list.
        coEvery { mediaRepository.getSpecialFeatures(any()) } returns Result.success(emptyList())
        // Default stub for the media-segments pre-warm fetch so its REMOTE
        // side-effect launch doesn't crash casting the relaxed-mock Result default.
        // Individual tests override this to drive the availability booleans.
        coEvery { playbackRepository.getMediaSegments(any()) } returns Result.success(emptyList())
        // Provider refresh is a no-op by default; tests that drive refresh override.
        coEvery { mediaDetailProvider.refresh(any()) } returns Unit
        // Successful user-data mutations update the provider's active replay
        // snapshot in production; most tests only exercise the ViewModel state.
        coEvery { mediaDetailProvider.applyOptimisticItemState(any(), any(), any()) } returns Unit
        // loadItemInternal invalidates the previous series through the provider on
        // re-entry; stub it as a no-op so strict-mock calls don't throw.
        every { mediaDetailProvider.invalidate(any()) } just Runs

        // The ViewModel resolves localized labels via context.getString(resId, vararg). As a
        // pure unit test (no Robolectric/instrumentation), stub the smart-play templates to
        // reconstruct their canonical form so assertions stay focused on target selection.
        every {
            context.getString(R.string.detail_resume_episode, any(), any())
        } answers {
            val fmt = args[1] as Array<*>
            "Resume S${fmt[0]}:E${fmt[1]}"
        }
        every {
            context.getString(R.string.detail_next_up_episode, any(), any())
        } answers {
            val fmt = args[1] as Array<*>
            "NextUp S${fmt[0]}:E${fmt[1]}"
        }
        every {
            context.getString(R.string.detail_play_episode, any(), any())
        } answers {
            val fmt = args[1] as Array<*>
            "Play S${fmt[0]}:E${fmt[1]}"
        }
        every {
            context.getString(R.string.detail_replay_episode, any(), any())
        } answers {
            val fmt = args[1] as Array<*>
            "Replay S${fmt[0]}:E${fmt[1]}"
        }
        every { context.getString(R.string.detail_error_load_failed) } returns "Failed to load details"
        every { context.getString(R.string.detail_error_access_denied) } returns "no access"
        every { context.getString(R.string.detail_error_unavailable_offline) } returns "unavailable offline"

        // Behavior fake (see FakeUserDataMutator): mirrors the real module's
        // success path so mutation tests drive the same observable sequence.
        userDataMutator = FakeUserDataMutator(mediaDetailProvider)

        buildViewModel()
    }

    /**
     * Constructs (or reconstructs) the [DetailViewModel] under test. Tests that
     * need to flip a stub the uiState combine captures at construction time
     * override the stub first, then call this to rebuild.
     */
    private fun buildViewModel() {
        viewModel = DetailViewModel(
            context = context,
            mediaRepository = mediaRepository,
            userDataMutator = userDataMutator,
            mediaDetailProvider = mediaDetailProvider,
            offlineSyncManager = offlineSyncManager,
            playbackRepository = playbackRepository,
            imageUrlProvider = imageUrlProvider,
            downloadRepository = downloadRepository,
            offlineRepository = offlineRepository,
            downloadIntake = downloadIntake,
            projections = projections,
            libraryStore = libraryStore,
            homeDiscoveryStore = homeDiscoveryStore,
            experimentalStore = experimentalStore,
            downloadsStore = downloadsStore,
            appRuntimeStateStore = appRuntimeStateStore,
            engineStore = engineStore,
            offlineModeManager = offlineModeManager,
            adaptiveBitrateManager = adaptiveBitrateManager,
            seerrRepository = seerrRepository,
            seerrRequestDelegate = seerrRequestDelegate,
            audioPlaybackManager = audioPlaybackManager,
            audioQueueFacade = audioQueueFacade,
            themeMusicPlayer = themeMusicPlayer,
            tmdbApiClient = tmdbApiClient,
            arrRepository = arrRepository,
            syncPlayManager = syncPlayManager,
        )
    }

    /**
     * Creates (or fetches) the controllable [MutableStateFlow] the fake provider
     * emits for [itemId], and wires `mediaDetailProvider.observe(itemId)` to it.
     */
    private fun stubProvider(
        itemId: String,
        initial: DetailLoadState = DetailLoadState.Loading,
    ): MutableStateFlow<DetailLoadState> {
        val flow = MutableStateFlow(initial)
        providerFlows[itemId] = flow
        every { mediaDetailProvider.observe(itemId) } returns flow
        return flow
    }

    /** Mirrors the provider's optimistic item rewrite for re-entry tests. */
    private fun stubOptimisticItemState(
        itemId: String,
        flow: MutableStateFlow<DetailLoadState>,
    ) {
        coEvery {
            mediaDetailProvider.applyOptimisticItemState(itemId, any(), any())
        } coAnswers {
            val isFavorite = args[1] as Boolean?
            val isPlayed = args[2] as Boolean?
            val current = (flow.value as DetailLoadState.Loaded).snapshot
            val currentItem = current.detail.item
            val updateItem: (MediaItem) -> MediaItem = { item ->
                if (item.id == itemId) {
                    item.copy(
                        isFavorite = isFavorite ?: item.isFavorite,
                        isPlayed = isPlayed ?: item.isPlayed,
                        playbackPositionTicks = if (isPlayed != null) 0L else item.playbackPositionTicks,
                    )
                } else item
            }
            flow.value = DetailLoadState.Loaded(
                current.copy(
                    detail = current.detail.copy(
                        item = if (currentItem.id == itemId) updateItem(currentItem) else currentItem,
                    ),
                    episodesBySeason = current.episodesBySeason.mapValues { (_, episodes) ->
                        episodes.map { episode ->
                            if (episode.id == itemId) updateItem(episode) else episode
                        }
                    },
                    sortedEpisodes = current.sortedEpisodes.map { episode ->
                        if (episode.id == itemId) updateItem(episode) else episode
                    },
                ),
            )
        }
    }

    // ── Snapshot builders ────────────────────────────────────────────────

    /** A REMOTE-origin Loaded snapshot with discovery/stream capabilities on. */
    private fun remoteSnapshot(
        detail: MediaDetail,
        seasons: List<MediaItem> = emptyList(),
        episodesBySeason: Map<String, List<MediaItem>> = emptyMap(),
        fetchedSeasonIds: Set<String> = emptySet(),
        sortedEpisodes: List<MediaItem> = episodesBySeason.values.flatten(),
        albumTracks: List<MediaItem> = emptyList(),
        contentGeneration: Long = 0L,
        download: com.raulshma.jellyplay.core.model.DownloadAttachment? = null,
    ): DetailLoadState.Loaded = DetailLoadState.Loaded(
        MediaDetailSnapshot(
            detail = detail,
            context = DetailContext(
                origin = DetailOrigin.REMOTE,
                connectivity = RemoteConnectivity.AVAILABLE,
                download = download,
                syncState = null,
                seriesAggregate = null,
            ),
            capabilities = DetailCapabilities(
                remoteDiscovery = true,
                remoteStreamSelection = true,
                localSubtitleSelection = false,
                localStreamInfo = false,
                personNavigation = true,
                studioNavigation = detail.studios.isNotEmpty(),
                smartPlay = true,
                remoteWorkAllowed = true,
                localDownloadManagement = download?.isCompleted == true,
                tagNavigation = true,
                chapters = detail.chapters.isNotEmpty(),
            ),
            assets = DetailAssets(),
            seasons = seasons,
            episodesBySeason = episodesBySeason,
            fetchedSeasonIds = fetchedSeasonIds,
            sortedEpisodes = sortedEpisodes,
            albumTracks = albumTracks,
            localSubtitles = emptyList(),
            contentGeneration = contentGeneration,
        ),
    )

    /** A LOCAL-origin Loaded snapshot (offline mode fallback). Discovery off. */
    private fun localSnapshot(
        detail: MediaDetail,
        seasons: List<MediaItem> = emptyList(),
        episodesBySeason: Map<String, List<MediaItem>> = emptyMap(),
        fetchedSeasonIds: Set<String> = emptySet(),
        sortedEpisodes: List<MediaItem> = episodesBySeason.values.flatten(),
        albumTracks: List<MediaItem> = emptyList(),
        localSubtitles: List<com.raulshma.jellyplay.core.model.LocalSubtitleOption> = emptyList(),
        contentGeneration: Long = 0L,
    ): DetailLoadState.Loaded = DetailLoadState.Loaded(
        MediaDetailSnapshot(
            detail = detail,
            context = DetailContext(
                origin = DetailOrigin.LOCAL_OFFLINE_MODE,
                connectivity = RemoteConnectivity.BLOCKED,
                download = null,
                syncState = null,
                seriesAggregate = null,
            ),
            capabilities = DetailCapabilities(
                remoteDiscovery = false,
                remoteStreamSelection = false,
                localSubtitleSelection = localSubtitles.isNotEmpty(),
                localStreamInfo = false,
                personNavigation = false,
                studioNavigation = false,
                smartPlay = false,
                remoteWorkAllowed = false,
                localDownloadManagement = false,
                tagNavigation = false,
                chapters = false,
            ),
            assets = DetailAssets(),
            seasons = seasons,
            episodesBySeason = episodesBySeason,
            fetchedSeasonIds = fetchedSeasonIds,
            sortedEpisodes = sortedEpisodes,
            albumTracks = albumTracks,
            localSubtitles = localSubtitles,
            contentGeneration = contentGeneration,
        ),
    )

    // ── Load state ───────────────────────────────────────────────────────

    @Test
    fun loadItem_failure_setsErrorMessage() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val flow = stubProvider("m1", DetailLoadState.Error(DetailLoadError("boom")))

        viewModel.loadItem("m1")
        advanceUntilIdle()

        val err = viewModel.uiState.value.loadState
        assertTrue(err is DetailUiLoadState.Error)
        assertEquals("boom", (err as DetailUiLoadState.Error).message)
        assertFalse((err as DetailUiLoadState.Error).unavailableOffline)
        assertNull(viewModel.uiState.value.detail)
        // The flow was consumed.
        assertNotNull(flow)
    }

    @Test
    fun loadItem_unavailableOffline_setsUnavailableMessage() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubProvider("m1", DetailLoadState.Error(DetailLoadError("x", isUnavailableOffline = true)))

        viewModel.loadItem("m1")
        advanceUntilIdle()

        val err = viewModel.uiState.value.loadState as DetailUiLoadState.Error
        assertEquals("unavailable offline", err.message)
        assertFalse(err.accessDenied)
        assertTrue(err.unavailableOffline)
    }

    @Test
    fun loadItem_accessDenied_setsAccessDeniedFlag() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubProvider(
            "m1",
            DetailLoadState.Error(DetailLoadError("x", isAccessDenied = true)),
        )

        viewModel.loadItem("m1")
        advanceUntilIdle()

        val err = viewModel.uiState.value.loadState as DetailUiLoadState.Error
        assertTrue(err.accessDenied)
        assertEquals("no access", err.message)
        assertFalse(err.unavailableOffline)
    }

    // The single SharedFlow<DetailMessage> must surface exactly one event per
    // action, even when the precondition fails synchronously (no detail loaded
    // yet). Regression for the former nullable-field approach where the screen
    // had to clear the field after showing.
    @Test
    fun messages_downloadSeriesWithNoDetail_emitsSeriesDownloadError() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.messages.collect { /* warm */ } }
            every { context.getString(R.string.detail_error_details_not_loaded) } returns "no detail"

            viewModel.downloadSeries()

            val message = withTimeout(1_000) { viewModel.messages.first() }
            assertTrue(message is DetailMessage.SeriesDownload)
            assertEquals("no detail", (message as DetailMessage.SeriesDownload).error)
        }

    @Test
    fun loadItem_movie_clearsSmartPlayTarget() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubProvider(
            "m1",
            remoteSnapshot(MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE))),
        )

        viewModel.loadItem("m1")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.detail)
        assertNull(viewModel.uiState.value.smartPlayTarget)
    }

    // ---- Pull-to-refresh ---------------------------------------------------
    // The VM now delegates cache invalidation + refetch to MediaDetailProvider.refresh;
    // per-type invalidation is owned by the provider (covered by
    // UnifiedMediaDetailProviderImplTest). These tests assert the VM-level
    // delegation, content-visibility, and error-surfacing behaviour.

    @Test
    fun forceRefresh_withoutLoadedDetail_isNoOp() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }

        viewModel.forceRefresh()
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaDetailProvider.refresh(any()) }
        assertFalse(viewModel.uiState.value.loadState is DetailUiLoadState.Refreshing)
    }

    @Test
    fun forceRefresh_keepsContentVisibleWhileRefetching() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val flow = stubProvider("m1")
        flow.value = remoteSnapshot(
            MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)),
        )

        viewModel.loadItem("m1")
        advanceUntilIdle()

        // Hold the provider's refresh open so the in-flight state is observable.
        val gate = CompletableDeferred<Unit>()
        coEvery { mediaDetailProvider.refresh("m1") } coAnswers { gate.await() }

        viewModel.forceRefresh()
        advanceUntilIdle()

        // Unlike loadItem, the detail must stay on screen during the refresh —
        // no full-screen loading state, just the pull-to-refresh indicator.
        assertNotNull(viewModel.uiState.value.detail)
        assertTrue(viewModel.uiState.value.loadState is DetailUiLoadState.Refreshing)
        assertFalse(viewModel.uiState.value.loadState is DetailUiLoadState.Loading)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.loadState is DetailUiLoadState.Refreshing)
        assertEquals("m1", viewModel.uiState.value.detail?.item?.id)
    }

    @Test
    fun forceRefresh_delegatesToProviderRefresh() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val flow = stubProvider("m1")
        flow.value = remoteSnapshot(
            MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)),
        )

        viewModel.loadItem("m1")
        advanceUntilIdle()

        viewModel.forceRefresh()
        advanceUntilIdle()

        // The VM delegates invalidation + refetch to the provider (called once).
        coVerify(exactly = 1) { mediaDetailProvider.refresh("m1") }
        assertFalse(viewModel.uiState.value.loadState is DetailUiLoadState.Refreshing)
    }

    @Test
    fun forceRefresh_series_dropsCatalogueSnapshotForReEntry() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
        stubSeries("s1", season, listOf(episode("e1", 1, 1, isPlayed = false)))

        viewModel.loadItem("s1")
        advanceUntilIdle()

        viewModel.forceRefresh()
        advanceUntilIdle()

        // The reset invalidates the current series through the provider so
        // re-entry reloads rather than serving a stale snapshot.
        io.mockk.verify(atLeast = 1) { mediaDetailProvider.invalidate("s1") }
        coVerify(exactly = 1) { mediaDetailProvider.refresh("s1") }
    }

    @Test
    fun forceRefresh_refetchFailure_surfacesErrorAndClearsIndicator() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val flow = stubProvider("m1")
        flow.value = remoteSnapshot(
            MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)),
        )
        viewModel.loadItem("m1")
        advanceUntilIdle()

        // The provider's re-resolution fails: emit Error.
        flow.value = DetailLoadState.Error(DetailLoadError("boom"))
        viewModel.forceRefresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.loadState is DetailUiLoadState.Refreshing)
        val err = viewModel.uiState.value.loadState as DetailUiLoadState.Error
        assertEquals("boom", err.message)
    }

    @Test
    fun smartPlay_seriesWithUnplayedFirstEpisode_showsPlayLabel() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
        val ep1 = episode("e1", 1, 1, isPlayed = false)
        val ep2 = episode("e2", 1, 2, isPlayed = false)
        stubSeries("s1", season, listOf(ep1, ep2))

        viewModel.loadItem("s1")
        advanceUntilIdle()

        val target = viewModel.uiState.value.smartPlayTarget
        assertNotNull(target)
        assertEquals("Play S1:E1", target!!.label)
        assertEquals("e1", target.episode.id)
        assertEquals(0L, target.startPositionTicks)
    }

    @Test
    fun smartPlay_seriesWithResumeProgress_showsResumeLabel() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
        val ep1 = episode("e1", 1, 1, isPlayed = false)
        val ep2 = episode("e2", 1, 2, isPlayed = false, positionTicks = 50_000_000L)
        stubSeries("s1", season, listOf(ep1, ep2))

        viewModel.loadItem("s1")
        advanceUntilIdle()

        val target = viewModel.uiState.value.smartPlayTarget!!
        assertEquals("Resume S1:E2", target.label)
        assertEquals("e2", target.episode.id)
        assertEquals(50_000_000L, target.startPositionTicks)
    }

    @Test
    fun smartPlay_seriesWhereEarlierEpisodePlayed_showsNextUpLabel() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
        val ep1 = episode("e1", 1, 1, isPlayed = true)
        val ep2 = episode("e2", 1, 2, isPlayed = false)
        stubSeries("s1", season, listOf(ep1, ep2))

        viewModel.loadItem("s1")
        advanceUntilIdle()

        val target = viewModel.uiState.value.smartPlayTarget!!
        assertEquals("NextUp S1:E2", target.label)
    }

    @Test
    fun smartPlay_allPlayed_showsReplayLabel() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
        val ep1 = episode("e1", 1, 1, isPlayed = true)
        val ep2 = episode("e2", 1, 2, isPlayed = true)
        stubSeries("s1", season, listOf(ep1, ep2))

        viewModel.loadItem("s1")
        advanceUntilIdle()

        val target = viewModel.uiState.value.smartPlayTarget!!
        assertEquals("Replay S1:E1", target.label)
        assertEquals("e1", target.episode.id)
    }

    @Test
    fun smartPlay_resumeTakesPrecedenceOverNextUp() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
        val ep1 = episode("e1", 1, 1, isPlayed = false, positionTicks = 10_000_000L)
        val ep2 = episode("e2", 1, 2, isPlayed = false)
        stubSeries("s1", season, listOf(ep1, ep2))

        viewModel.loadItem("s1")
        advanceUntilIdle()

        val target = viewModel.uiState.value.smartPlayTarget!!
        assertEquals("Resume S1:E1", target.label)
    }

    @Test
    fun smartPlay_episodesUnsortedInResponse_areSortedBeforeSelection() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
        val ep2 = episode("e2", 1, 2, isPlayed = false)
        val ep1 = episode("e1", 1, 1, isPlayed = false)
        stubSeries("s1", season, listOf(ep2, ep1))

        viewModel.loadItem("s1")
        advanceUntilIdle()

        val target = viewModel.uiState.value.smartPlayTarget!!
        assertEquals("e1", target.episode.id)
    }

    @Test
    fun smartPlay_seriesWithPendingOrEmptySeason_stillResolvesSmartPlayTarget() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val s0 = MediaItem(id = "season0", name = "Specials", mediaType = MediaType.SEASON, indexNumber = 0)
        val s1 = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
        val ep1 = episode("e1", 1, 1, isPlayed = false)
        val detail = MediaDetail(item = MediaItem(id = "s1", name = "Show", mediaType = MediaType.SERIES))
        val comparator = playbackOrderComparator()
        val sorted = listOf(ep1).sortedWith(comparator)
        // season0 is in seasons, but NOT in fetchedSeasonIds (e.g. empty or not yet fetched)
        stubProvider(
            "s1",
            remoteSnapshot(
                detail = detail,
                seasons = listOf(s0, s1),
                episodesBySeason = mapOf(s1.id to listOf(ep1)),
                fetchedSeasonIds = setOf(s1.id),
                sortedEpisodes = sorted,
            ),
        )

        viewModel.loadItem("s1")
        advanceUntilIdle()

        val target = viewModel.uiState.value.smartPlayTarget
        assertNotNull("SmartPlayTarget must not be null even if some season is pending/empty", target)
        assertEquals("Play S1:E1", target!!.label)
        assertEquals("e1", target.episode.id)
    }

    // ---- Item-level mark played / unplayed ----------------------------------

    @Test
    fun markPlayed_clearsResumePositionInDetailUi() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val item = MediaItem(
            id = "movie-1",
            name = "Movie",
            mediaType = MediaType.MOVIE,
            playbackPositionTicks = 5_000_000_000L,
        )
        stubProvider("movie-1", remoteSnapshot(MediaDetail(item = item)))

        viewModel.loadItem("movie-1")
        advanceUntilIdle()
        viewModel.markPlayed()
        advanceUntilIdle()

        val updated = viewModel.uiState.value.detail!!.item
        assertTrue(updated.isPlayed)
        assertEquals(0L, updated.playbackPositionTicks)
    }

    @Test
    fun markUnplayed_clearsResumePositionInDetailUi() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val item = MediaItem(
            id = "movie-1",
            name = "Movie",
            mediaType = MediaType.MOVIE,
            isPlayed = true,
            playbackPositionTicks = 5_000_000_000L,
        )
        stubProvider("movie-1", remoteSnapshot(MediaDetail(item = item)))

        viewModel.loadItem("movie-1")
        advanceUntilIdle()
        viewModel.markUnplayed()
        advanceUntilIdle()

        val updated = viewModel.uiState.value.detail!!.item
        assertFalse(updated.isPlayed)
        assertEquals(0L, updated.playbackPositionTicks)
    }

    @Test
    fun toggleFavorite_reentryKeepsTheNewFavoriteState() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val item = MediaItem(
            id = "movie-1",
            name = "Movie",
            mediaType = MediaType.MOVIE,
            isFavorite = true,
        )
        val flow = stubProvider("movie-1", remoteSnapshot(MediaDetail(item = item)))
        userDataMutator.favoriteResult = { Result.success(AppliedMutation("movie-1", favorite = false)) }
        stubOptimisticItemState("movie-1", flow)

        viewModel.loadItem("movie-1")
        advanceUntilIdle()
        viewModel.toggleFavorite()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.detail!!.item.isFavorite)

        // Re-entry replays the provider's source snapshot, just as a real
        // detail screen does after navigating home and opening the item again.
        viewModel.loadItem("movie-1")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.detail!!.item.isFavorite)
        assertEquals(false, (flow.value as DetailLoadState.Loaded).snapshot.detail.item.isFavorite)
    }

    @Test
    fun toggleFavorite_rapidTapsApplyAuthoritativeTargetsInOrder() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            val item = MediaItem(
                id = "movie-1",
                name = "Movie",
                mediaType = MediaType.MOVIE,
                isFavorite = true,
            )
            stubProvider("movie-1", remoteSnapshot(MediaDetail(item = item)))
            var calls = 0
            userDataMutator.favoriteResult = {
                calls += 1
                Result.success(AppliedMutation("movie-1", favorite = calls == 2))
            }

            viewModel.loadItem("movie-1")
            advanceUntilIdle()
            viewModel.toggleFavorite()
            viewModel.toggleFavorite()
            advanceUntilIdle()

            // Two toggles return to the original server state; the second
            // result must not be overwritten by a stale captured boolean.
            assertTrue(viewModel.uiState.value.detail!!.item.isFavorite)
            assertEquals(2, calls)
            // Both taps routed through the mutator in order.
            assertEquals(listOf("movie-1" to null, "movie-1" to null), userDataMutator.favoriteCalls)
        }

    // Item-level failure must surface the localized snackbar and leave every
    // projection untouched (no flip without a successful write).
    @Test
    fun toggleFavorite_failure_emitsMessageAndLeavesStateIntact() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            val item = MediaItem(
                id = "movie-1",
                name = "Movie",
                mediaType = MediaType.MOVIE,
                isFavorite = true,
            )
            stubProvider("movie-1", remoteSnapshot(MediaDetail(item = item)))
            userDataMutator.favoriteResult = { Result.failure(RuntimeException("boom")) }
            every { context.getString(R.string.detail_msg_couldnt_update_favorite) } returns "couldn't update"

            viewModel.loadItem("movie-1")
            advanceUntilIdle()

            viewModel.toggleFavorite()

            val message = withTimeout(1_000) { viewModel.messages.first() }
            assertTrue(message is DetailMessage.Text)
            assertEquals("couldn't update", (message as DetailMessage.Text).text)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.detail!!.item.isFavorite)
        }

    @Test
    fun markPlayed_reentryKeepsTheNewWatchedState() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val item = MediaItem(id = "movie-1", name = "Movie", mediaType = MediaType.MOVIE)
        val flow = stubProvider("movie-1", remoteSnapshot(MediaDetail(item = item)))
        stubOptimisticItemState("movie-1", flow)

        viewModel.loadItem("movie-1")
        advanceUntilIdle()
        viewModel.markPlayed()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.detail!!.item.isPlayed)

        viewModel.loadItem("movie-1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.detail!!.item.isPlayed)
        assertTrue((flow.value as DetailLoadState.Loaded).snapshot.detail.item.isPlayed)
    }

    @Test
    fun markUnplayed_reentryKeepsTheNewUnwatchedState() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val item = MediaItem(
            id = "movie-1",
            name = "Movie",
            mediaType = MediaType.MOVIE,
            isPlayed = true,
        )
        val flow = stubProvider("movie-1", remoteSnapshot(MediaDetail(item = item)))
        stubOptimisticItemState("movie-1", flow)

        viewModel.loadItem("movie-1")
        advanceUntilIdle()
        viewModel.markUnplayed()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.detail!!.item.isPlayed)

        viewModel.loadItem("movie-1")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.detail!!.item.isPlayed)
        assertFalse((flow.value as DetailLoadState.Loaded).snapshot.detail.item.isPlayed)
    }

    @Test
    fun markEpisodePlayed_reentryKeepsTheNewWatchedState() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val season = MediaItem(id = "season-1", name = "Season", mediaType = MediaType.SEASON)
        val episode = episode("episode-1", 1, 1, isPlayed = false)
        val flow = stubProvider(
            "series-1",
            remoteSnapshot(
                detail = MediaDetail(
                    item = MediaItem(id = "series-1", name = "Series", mediaType = MediaType.SERIES),
                ),
                seasons = listOf(season),
                episodesBySeason = mapOf("season-1" to listOf(episode)),
                fetchedSeasonIds = setOf("season-1"),
            ),
        )
        stubOptimisticItemState("episode-1", flow)

        viewModel.loadItem("series-1")
        advanceUntilIdle()
        viewModel.markEpisodePlayed("episode-1", played = true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.episodes["season-1"]!!.single().isPlayed)

        viewModel.loadItem("series-1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.episodes["season-1"]!!.single().isPlayed)
        assertTrue((flow.value as DetailLoadState.Loaded).snapshot.sortedEpisodes.single().isPlayed)
    }

    @Test
    fun markRowItemPlayed_reentryKeepsTheNewWatchedState() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val season = MediaItem(id = "season-1", name = "Season", mediaType = MediaType.SEASON)
        val item = episode("episode-1", 1, 1, isPlayed = false)
        val flow = stubProvider(
            "series-1",
            remoteSnapshot(
                detail = MediaDetail(
                    item = MediaItem(id = "series-1", name = "Series", mediaType = MediaType.SERIES),
                ),
                seasons = listOf(season),
                episodesBySeason = mapOf("season-1" to listOf(item)),
                fetchedSeasonIds = setOf("season-1"),
            ),
        )
        stubOptimisticItemState("episode-1", flow)

        viewModel.loadItem("series-1")
        advanceUntilIdle()
        // The row action is fed directly with the card item, as MediaDetailScreen does.
        viewModel.markRowItemPlayed(item, played = true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.episodes["season-1"]!!.single().isPlayed)
        viewModel.loadItem("series-1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.episodes["season-1"]!!.single().isPlayed)
        assertTrue((flow.value as DetailLoadState.Loaded).snapshot.sortedEpisodes.single().isPlayed)
        // Routed through the mutator with the row item's series scope.
        assertEquals(listOf(Triple("episode-1", true, "s1")), userDataMutator.playedCalls)
    }

    // ---- Season-level mark played / unplayed --------------------------------

    // markSeasonPlayed flips every episode in that season to isPlayed=true in
    // the optimistic uiState snapshot (no re-fetch), so the WATCHED badge and
    // the next-up target update immediately. The Jellyfin endpoint recurses
    // into the season's children, so this is a single markPlayed(seasonId) call.
    @Test
    fun markSeasonPlayed_flipsAllEpisodesAndRecomputesSmartPlay() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
            val ep1 = episode("e1", 1, 1, isPlayed = false)
            val ep2 = episode("e2", 1, 2, isPlayed = false)
            stubSeries("s1", season, listOf(ep1, ep2))

            viewModel.loadItem("s1")
            advanceUntilIdle()
            // Sanity: before the action, smart-play points at the first unplayed ep.
            assertEquals("e1", viewModel.uiState.value.smartPlayTarget!!.episode.id)

            viewModel.markSeasonPlayed("season1")
            advanceUntilIdle()

            val episodes = viewModel.uiState.value.episodes["season1"]!!
            assertTrue(episodes.all { it.isPlayed })
            // Position is cleared server-side on mark-played; mirror locally.
            assertEquals(0L, episodes.first().playbackPositionTicks)
            // markSeason routes the optimistic flip through the provider
            // (applyOptimisticSeasonRewrite re-emits; no server refetch). Verify
            // no getMediaDetail refetch either.
            io.mockk.coVerify(exactly = 0) { mediaRepository.getMediaDetail("s1") }
            // Every episode now played → smart-play falls back to a replay of S1:E1.
            val target = awaitSmartPlayTarget { it.label == "Replay S1:E1" }
            assertEquals("Replay S1:E1", target.label)
            // The reactor delegated to the mutator's season variant with the
            // screen's series scope.
            assertEquals(listOf(Triple("s1", "season1", true)), userDataMutator.seasonCalls)
        }

    // markSeasonPlayed only affects the targeted season — episodes in a sibling
    // season are left untouched (regression guard for an over-broad map rewrite).
    @Test
    fun markSeasonPlayed_leavesSiblingSeasonUnchanged() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            val s1 = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
            val s2 = MediaItem(id = "season2", name = "Season 2", mediaType = MediaType.SEASON, indexNumber = 2)
            val s1e1 = episode("e1", 1, 1, isPlayed = false).copy(seasonId = "season1")
            val s2e1 = episode("e2", 2, 1, isPlayed = false).copy(seasonId = "season2")
            stubTwoSeasonSeries("s1", s1, s2, s1e1, s2e1)

            viewModel.loadItem("s1")
            advanceUntilIdle()

            viewModel.markSeasonPlayed("season1")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.episodes["season1"]!!.all { it.isPlayed })
            assertTrue(viewModel.uiState.value.episodes["season2"]!!.none { it.isPlayed })
        }

    // markSeasonPlayed routes the optimistic rewrite through the provider's
    // applyOptimisticSeasonRewrite, which owns the re-entry invalidation
    // internally (the cache drop happens inside the provider, not the VM).
    // Regression guard: the VM must not reach past the provider seam to
    // invalidate the catalogue directly.
    @Test
    fun markSeasonPlayed_invalidatesSeriesCacheForReEntry() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
            val ep1 = episode("e1", 1, 1, isPlayed = false)
            stubSeries("s1", season, listOf(ep1))

            viewModel.loadItem("s1")
            advanceUntilIdle()

            viewModel.markSeasonPlayed("season1")
            advanceUntilIdle()

            assertEquals(listOf(Triple("s1", "season1", true)), userDataMutator.seasonCalls)
            // The VM routes through the mutator; the mutator/provider owns the
            // catalogue invalidation internally (asserted in their own tests).
            io.mockk.coVerify(exactly = 1) {
                mediaDetailProvider.applyOptimisticSeasonRewrite("s1", "season1", any())
            }
        }

    // Repository failure must surface the localized snackbar and leave the
    // episodes map unchanged (no optimistic corruption on error).
    @Test
    fun markSeasonPlayed_repositoryFailure_emitsMessageAndLeavesStateIntact() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
            val ep1 = episode("e1", 1, 1, isPlayed = false)
            stubSeries("s1", season, listOf(ep1))
            userDataMutator.seasonResult = { _, _, _ -> Result.failure(RuntimeException("boom")) }
            every { context.getString(R.string.detail_msg_couldnt_mark_played) } returns "couldn't mark"

            viewModel.loadItem("s1")
            advanceUntilIdle()

            viewModel.markSeasonPlayed("season1")

            val message = withTimeout(1_000) { viewModel.messages.first() }
            assertTrue(message is DetailMessage.Text)
            assertEquals("couldn't mark", (message as DetailMessage.Text).text)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.episodes["season1"]!!.any { it.isPlayed })
        }

    // markSeasonUnplayed is the mirror image — flips isPlayed=false, clears the
    // resume position (so the progress bar / "time left" label don't linger and
    // the episodes leave continue watching locally), and lets smart-play target
    // the now-unplayed first episode again.
    @Test
    fun markSeasonUnplayed_flipsEpisodesAndRetargets() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
            val ep1 = episode("e1", 1, 1, isPlayed = true, positionTicks = 5_000_000_000L)
            val ep2 = episode("e2", 1, 2, isPlayed = true, positionTicks = 5_000_000_000L)
            stubSeries("s1", season, listOf(ep1, ep2))

            viewModel.loadItem("s1")
            advanceUntilIdle()
            // Before: all played → replay label.
            assertEquals("Replay S1:E1", viewModel.uiState.value.smartPlayTarget!!.label)

            viewModel.markSeasonUnplayed("season1")
            advanceUntilIdle()

            val episodes = viewModel.uiState.value.episodes["season1"]!!
            assertTrue(episodes.none { it.isPlayed })
            // Position cleared on unplayed, mirroring mark-played.
            assertEquals(0L, episodes.first().playbackPositionTicks)
            val target = awaitSmartPlayTarget { it.label == "Play S1:E1" }
            assertEquals("Play S1:E1", target.label)
        }

    // Regression for the user-reported re-entry bug: the mark-unplayed path
    // must NOT issue a refetch that writes a stale pre-cascade snapshot back.
    // The optimistic flip keeps the current screen correct; the invalidateSeries
    // call forces re-entry to miss the cache and re-hit the server.
    @Test
    fun markSeasonUnplayed_doesNotRefetchSoReEntryNeverServesStaleWatchedState() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
            val ep1 = episode("e1", 1, 1, isPlayed = true)
            val ep2 = episode("e2", 1, 2, isPlayed = true)
            stubSeries("s1", season, listOf(ep1, ep2))

            viewModel.loadItem("s1")
            advanceUntilIdle()

            viewModel.markSeasonUnplayed("season1")
            advanceUntilIdle()

            // No post-mutation refetch from the VM.
            io.mockk.coVerify(exactly = 0) { mediaRepository.getMediaDetail("s1") }
            val episodes = viewModel.uiState.value.episodes["season1"]!!
            assertTrue(episodes.none { it.isPlayed })
        }

    // When every episode is already in the target state, the call short-circuits
    // — no network round-trip, no spurious cache invalidation.
    @Test
    fun markSeasonPlayed_alreadyAllPlayed_isNoOp() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
            val ep1 = episode("e1", 1, 1, isPlayed = true)
            stubSeries("s1", season, listOf(ep1))
            viewModel.loadItem("s1")
            advanceUntilIdle()

            viewModel.markSeasonPlayed("season1")
            advanceUntilIdle()

            // The idempotence guard short-circuited before reaching the mutator.
            assertTrue(userDataMutator.seasonCalls.isEmpty())
            io.mockk.coVerify(exactly = 0) { mediaRepository.getMediaDetail("s1") }
        }

    // Regression: loadSeerrData must read isSeerrConnected/isSeerrRecommendationsEnabled
    // from the PUBLISHED uiState (where the seerr-flags combine folds them in),
    // not from _uiState. When connected+enabled, the Seerr recommendation/similar/video
    // fetches must actually run. The VM now fires this internally on a REMOTE resolution.
    @Test
    fun loadSeerrData_whenConnectedAndEnabled_fetchesSeerrRecommendations() =
        runTest(mainDispatcherRule.testDispatcher) {
            // Flip the connection-flag stubs BEFORE constructing the ViewModel:
            // the uiState combine captures the flows returned by isConnected() /
            // isRecommendationsEnabled() at construction time, so a stub flipped
            // after construction has no effect.
            every { seerrRepository.isConnected() } returns MutableStateFlow(true)
            every { seerrRepository.isRecommendationsEnabled() } returns MutableStateFlow(true)
            every { offlineModeManager.networkStatus } returns MutableStateFlow(NetworkStatus.Online)
            buildViewModel()

            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }

            stubProvider(
                "m1",
                remoteSnapshot(
                    MediaDetail(
                        item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
                        providerIds = mapOf("tmdb" to "123"),
                    ),
                ),
            )
            val recItem = com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem(id = 99, title = "Rec")
            coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(
                com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails(),
            )
            coEvery { seerrRepository.getRecommendations(123, MediaType.MOVIE) } returns Result.success(
                com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse(results = listOf(recItem)),
            )
            coEvery { seerrRepository.getSimilar(123, MediaType.MOVIE) } returns Result.success(
                com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse(results = listOf(recItem)),
            )

            viewModel.loadItem("m1")
            advanceUntilIdle()

            assertEquals(listOf(recItem), viewModel.uiState.value.seerrRecommendations)
            assertEquals(listOf(recItem), viewModel.uiState.value.seerrSimilar)
        }

    // Regression: when the provider snapshot groups episodes under a key that
    // does NOT match any season id, the affected season must NOT be marked
    // fetched — otherwise loadEpisodesForSeason short-circuits and the season is
    // pinned empty. The per-season refetch must still fire and populate it.
    @Test
    fun episodes_batchReturnsMismatchedSeasonKey_leavesSeasonUnfetchedForRefetch() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
            val eps = listOf(episode("e1", 1, 1), episode("e2", 1, 2))
            // The provider snapshot groups under "" (null seasonId on the server),
            // so season1 is absent from fetchedSeasonIds — the on-demand per-season
            // refetch must still fire and populate it.
            val flow = stubProvider(
                "s1",
                remoteSnapshot(
                    MediaDetail(item = MediaItem(id = "s1", name = "Show", mediaType = MediaType.SERIES)),
                    seasons = listOf(season),
                    episodesBySeason = mapOf("" to listOf(episode("e1", 1, 1))),
                    fetchedSeasonIds = emptySet(),
                ),
            )
            // expandSeason mirrors the real provider: fetches the season, merges
            // it into the snapshot, bumps the generation, re-emits via the flow.
            coEvery { mediaDetailProvider.expandSeason("s1", "season1") } answers {
                val current = (flow.value as DetailLoadState.Loaded).snapshot
                flow.value = DetailLoadState.Loaded(
                    current.copy(
                        episodesBySeason = current.episodesBySeason + ("season1" to eps),
                        fetchedSeasonIds = current.fetchedSeasonIds + "season1",
                        contentGeneration = current.contentGeneration + 1,
                    ),
                )
                eps
            }

            viewModel.loadItem("s1")
            advanceUntilIdle()

            // season1 was not in the snapshot, so it must NOT be marked fetched.
            assertEquals(false, viewModel.uiState.value.fetchedSeasonIds.contains("season1"))
            // On-demand load for season1 must fire the per-season refetch.
            viewModel.loadEpisodesForSeason("s1", "season1")
            advanceUntilIdle()

            assertEquals(setOf("season1"), viewModel.uiState.value.fetchedSeasonIds)
            assertEquals(eps, viewModel.uiState.value.episodes["season1"])
        }

    @Test
    fun episodes_emptySeason_updatesFetchedSeasonIdsAndEmptyList() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
            val flow = stubProvider(
                "s1",
                remoteSnapshot(
                    MediaDetail(item = MediaItem(id = "s1", name = "Show", mediaType = MediaType.SERIES)),
                    seasons = listOf(season),
                    episodesBySeason = emptyMap(),
                    fetchedSeasonIds = emptySet(),
                ),
            )
            coEvery { mediaDetailProvider.expandSeason("s1", "season1") } answers {
                val current = (flow.value as DetailLoadState.Loaded).snapshot
                flow.value = DetailLoadState.Loaded(
                    current.copy(
                        episodesBySeason = current.episodesBySeason + ("season1" to emptyList()),
                        fetchedSeasonIds = current.fetchedSeasonIds + "season1",
                        contentGeneration = current.contentGeneration + 1,
                    ),
                )
                emptyList()
            }

            viewModel.loadItem("s1")
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.fetchedSeasonIds.contains("season1"))

            viewModel.loadEpisodesForSeason("s1", "season1")
            advanceUntilIdle()

            assertEquals(setOf("season1"), viewModel.uiState.value.fetchedSeasonIds)
            assertEquals(emptyList<MediaItem>(), viewModel.uiState.value.episodes["season1"])
        }

    // ── LOCAL-origin snapshot computes smart-play but suppresses remote discovery ──

    @Test
    fun localSnapshot_computesSmartPlayButSuppressesRemoteDiscovery() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
        val ep1 = episode("e1", 1, 1, isPlayed = false)
        val ep2 = episode("e2", 1, 2, isPlayed = false)
        val detail = MediaDetail(item = MediaItem(id = "s1", name = "Show", mediaType = MediaType.SERIES))
        stubProvider(
            "s1",
            localSnapshot(
                detail,
                seasons = listOf(season),
                episodesBySeason = mapOf(season.id to listOf(ep1, ep2)),
                fetchedSeasonIds = setOf(season.id),
            ),
        )

        viewModel.loadItem("s1")
        advanceUntilIdle()

        // LOCAL origin now resolves the smart-play target from downloaded
        // episodes, matching the online play button + Up Next behavior.
        val target = viewModel.uiState.value.smartPlayTarget
        assertNotNull(target)
        assertEquals("e1", target!!.episode.id)
        assertEquals(DetailOrigin.LOCAL_OFFLINE_MODE, viewModel.uiState.value.origin)
        // Remote-only discovery coroutines still never fire for a local origin.
        io.mockk.coVerify(exactly = 0) { mediaRepository.getSimilarItems(any(), any()) }
        io.mockk.verify(exactly = 0) { themeMusicPlayer.playThemeFor(any()) }
        io.mockk.coVerify(exactly = 0) { seerrRepository.getMovieDetails(any()) }
        io.mockk.coVerify(exactly = 0) { seerrRepository.getTvDetails(any()) }
    }

    // ── Intro/credits segment pre-warm + detail-side availability chip ─────
    // A REMOTE load fires playbackRepository.getMediaSegments to (a) pre-warm the
    // player's segment TTL cache so its first skip is instant and (b) project the
    // intro/credits availability onto uiState for the chip near the Play button.

    @Test
    fun loadItem_remote_fetchesMediaSegmentsAndSurfacesAvailability() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            stubProvider(
                "m1",
                remoteSnapshot(MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE))),
            )
            val introSegment = MediaSegment(
                id = "intro-1",
                itemId = "m1",
                type = MediaSegmentType.INTRO,
                startTicks = 0L,
                endTicks = 5_000_000L,
            )
            coEvery { playbackRepository.getMediaSegments("m1") } returns Result.success(listOf(introSegment))

            viewModel.loadItem("m1")
            advanceUntilIdle()

            // The pre-warm fetch fired (and warmed the player's segment cache).
            coVerify(exactly = 1) { playbackRepository.getMediaSegments("m1") }
            // The intro segment landed on uiState for the detail chip.
            assertTrue(viewModel.uiState.value.hasIntroSegment)
            assertFalse(viewModel.uiState.value.hasCreditSegment)
        }

    @Test
    fun loadItem_local_doesNotFetchMediaSegments() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
            val ep1 = episode("e1", 1, 1, isPlayed = false)
            val detail = MediaDetail(item = MediaItem(id = "s1", name = "Show", mediaType = MediaType.SERIES))
            stubProvider(
                "s1",
                localSnapshot(
                    detail,
                    seasons = listOf(season),
                    episodesBySeason = mapOf(season.id to listOf(ep1)),
                    fetchedSeasonIds = setOf(season.id),
                ),
            )

            viewModel.loadItem("s1")
            advanceUntilIdle()

            // A LOCAL origin short-circuits remote side effects — no segment
            // pre-warm fetch, and the chip booleans stay false.
            coVerify(exactly = 0) { playbackRepository.getMediaSegments(any()) }
            assertFalse(viewModel.uiState.value.hasIntroSegment)
            assertFalse(viewModel.uiState.value.hasCreditSegment)
        }

    // ── LOCAL-origin "More like this" mined from the on-device library ──────
    // A LOCAL load with genres/studios asks OfflineRepository.getLocalRelated
    // for on-device titles sharing a genre/studio and surfaces them as
    // localRelatedItems (excluding the item itself), so offline browsing isn't
    // an island.

    @Test
    fun loadItem_local_surfacesLocalRelatedFromGenresOrStudios() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            val detail = MediaDetail(
                item = MediaItem(
                    id = "m1",
                    name = "Movie",
                    mediaType = MediaType.MOVIE,
                    genres = listOf("Sci-Fi"),
                    studios = listOf("Studio Ghibli"),
                ),
            )
            stubProvider("m1", localSnapshot(detail))
            coEvery {
                offlineRepository.getLocalRelated(
                    currentId = "m1",
                    genres = listOf("Sci-Fi"),
                    studios = listOf("Studio Ghibli"),
                    limit = 12,
                )
            } returns listOf(
                MediaItem(id = "m2", name = "Related Movie", mediaType = MediaType.MOVIE),
                // The current item must be filtered out of the surfaced row.
                MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            )

            viewModel.loadItem("m1")
            advanceUntilIdle()

            // The on-device mining fired for the LOCAL origin's genres/studios…
            coVerify(exactly = 1) {
                offlineRepository.getLocalRelated(
                    currentId = "m1",
                    genres = listOf("Sci-Fi"),
                    studios = listOf("Studio Ghibli"),
                    limit = 12,
                )
            }
            // …and the surfaced row excludes the item itself.
            assertEquals(
                listOf("m2"),
                viewModel.uiState.value.localRelatedItems.map { it.id },
            )
        }

    @Test
    fun loadItem_localWithoutGenresOrStudios_skipsLocalRelatedFetch() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            // No genres or studios: triggerLocalSideEffects early-returns, so the
            // on-device mining never fires and the row stays empty.
            val detail = MediaDetail(
                item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            )
            stubProvider("m1", localSnapshot(detail))

            viewModel.loadItem("m1")
            advanceUntilIdle()

            coVerify(exactly = 0) { offlineRepository.getLocalRelated(any(), any(), any(), any()) }
            assertTrue(viewModel.uiState.value.localRelatedItems.isEmpty())
        }

    @Test
    fun loadItem_remoteThenLocal_clearsSegmentAvailabilityOnNavigation() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            // First: a REMOTE item whose intro segment sets the chip booleans.
            stubProvider(
                "m1",
                remoteSnapshot(MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE))),
            )
            val introSegment = MediaSegment(
                id = "intro-1",
                itemId = "m1",
                type = MediaSegmentType.INTRO,
                startTicks = 0L,
                endTicks = 5_000_000L,
            )
            coEvery { playbackRepository.getMediaSegments("m1") } returns Result.success(listOf(introSegment))
            viewModel.loadItem("m1")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.hasIntroSegment)

            // Then: navigate to a LOCAL item. The atomic reset in loadItem must
            // clear the prior item's segment availability so the chip can't
            // advertise "skip available" for an item with no segments.
            val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
            val ep1 = episode("e1", 1, 1, isPlayed = false)
            val detail = MediaDetail(item = MediaItem(id = "s1", name = "Show", mediaType = MediaType.SERIES))
            stubProvider(
                "s1",
                localSnapshot(
                    detail,
                    seasons = listOf(season),
                    episodesBySeason = mapOf(season.id to listOf(ep1)),
                    fetchedSeasonIds = setOf(season.id),
                ),
            )
            viewModel.loadItem("s1")
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.hasIntroSegment)
            assertFalse(viewModel.uiState.value.hasCreditSegment)
        }

    // ── Special features / extras ───────────────────────────────────────────
    // A REMOTE load fires mediaRepository.getSpecialFeatures (sourced from
    // Jellyfin's /Items/{id}/SpecialFeatures) and projects the result onto
    // uiState.specialFeatures so the "Special Features" row can render.

    @Test
    fun loadItem_remote_fetchesSpecialFeaturesAndSurfacesExtras() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            stubProvider(
                "m1",
                remoteSnapshot(MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE))),
            )
            val extras = listOf(
                MediaItem(id = "extra-1", name = "Making Of", mediaType = MediaType.MOVIE),
                MediaItem(id = "extra-2", name = "Deleted Scenes", mediaType = MediaType.MOVIE),
            )
            coEvery { mediaRepository.getSpecialFeatures("m1") } returns Result.success(extras)

            viewModel.loadItem("m1")
            advanceUntilIdle()

            // The fetch fired exactly once for the resolved item.
            coVerify(exactly = 1) { mediaRepository.getSpecialFeatures("m1") }
            // The extras landed on uiState for the detail row.
            assertEquals(extras, viewModel.uiState.value.specialFeatures)
        }

    @Test
    fun loadItem_remoteThenLocal_clearsSpecialFeaturesOnNavigation() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            // First: a REMOTE item whose special features populate the row.
            stubProvider(
                "m1",
                remoteSnapshot(MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE))),
            )
            val extras = listOf(MediaItem(id = "extra-1", name = "Making Of", mediaType = MediaType.MOVIE))
            coEvery { mediaRepository.getSpecialFeatures("m1") } returns Result.success(extras)
            viewModel.loadItem("m1")
            advanceUntilIdle()
            assertEquals(extras, viewModel.uiState.value.specialFeatures)

            // Then: navigate to a LOCAL item. The atomic reset in loadItem must
            // clear the prior item's extras so the row can't render a stale
            // special-feature set for an item that has none.
            val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
            val ep1 = episode("e1", 1, 1, isPlayed = false)
            val detail = MediaDetail(item = MediaItem(id = "s1", name = "Show", mediaType = MediaType.SERIES))
            stubProvider(
                "s1",
                localSnapshot(
                    detail,
                    seasons = listOf(season),
                    episodesBySeason = mapOf(season.id to listOf(ep1)),
                    fetchedSeasonIds = setOf(season.id),
                ),
            )
            viewModel.loadItem("s1")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.specialFeatures.isEmpty())
            // A LOCAL origin short-circuits remote discovery — no extras fetch.
            coVerify(exactly = 0) { mediaRepository.getSpecialFeatures("s1") }
        }

    // ── NEW: resync() maps OfflineSyncManager results to ResyncUiState ──────

    @Test
    fun resync_success_mapsToDone() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubProvider(
            "m1",
            remoteSnapshot(MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE))),
        )
        viewModel.loadItem("m1")
        advanceUntilIdle()

        val ok = ResyncResult(
            "m1",
            listOf(ResyncStepResult("m1", ResyncStep.FETCH_DETAIL, success = true)),
            mediaFileChanged = false,
        )
        coEvery { offlineSyncManager.resyncItem("m1") } returns ok

        viewModel.resync()
        advanceUntilIdle()

        val state = viewModel.uiState.value.resyncState
        assertTrue(state is ResyncUiState.Done)
        assertEquals(ok, (state as ResyncUiState.Done).result)
    }

    @Test
    fun resync_failure_mapsToError() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubProvider(
            "m1",
            remoteSnapshot(MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE))),
        )
        viewModel.loadItem("m1")
        advanceUntilIdle()

        val failed = ResyncResult(
            "m1",
            listOf(ResyncStepResult("m1", ResyncStep.FETCH_DETAIL, success = false, message = "boom")),
            mediaFileChanged = false,
        )
        coEvery { offlineSyncManager.resyncItem("m1") } returns failed

        viewModel.resync()
        advanceUntilIdle()

        val state = viewModel.uiState.value.resyncState
        assertTrue(state is ResyncUiState.Error)
        assertEquals("boom", (state as ResyncUiState.Error).message)
    }

    // ── Instant Mix ───────────────────────────────────────────────────────
    // startInstantMix delegates the whole concern (mix fetch, queue build,
    // dispatcher hop, navigation-drift guard) to AudioQueueFacade; these tests
    // pin the VM-side contract only: the seed/fallback arguments, the guard,
    // and the outcome → DetailMessage mapping.

    @Test
    fun startInstantMix_success_delegatesToFacadeWithSeedAndFallback() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            val album = MediaItem(
                id = "album1",
                name = "Album",
                mediaType = MediaType.ALBUM,
                album = "Album",
            )
            stubProvider("album1", remoteSnapshot(MediaDetail(item = album)))
            coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns
                AudioQueueOutcome.Started(emptyList(), 0)

            viewModel.loadItem("album1")
            advanceUntilIdle()

            viewModel.startInstantMix()
            advanceUntilIdle()

            coVerify(exactly = 1) { audioQueueFacade.startInstantMix("album1", "Album", any()) }
            io.mockk.verify(exactly = 0) { context.getString(R.string.detail_instant_mix_empty) }
            io.mockk.verify(exactly = 0) { context.getString(R.string.detail_instant_mix_failed) }
        }

    @Test
    fun startInstantMix_success_fallsBackToItemNameWhenAlbumMissing() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            val track = MediaItem(id = "t1", name = "Track", mediaType = MediaType.AUDIO)
            stubProvider("t1", remoteSnapshot(MediaDetail(item = track)))
            coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns
                AudioQueueOutcome.Started(emptyList(), 0)

            viewModel.loadItem("t1")
            advanceUntilIdle()

            viewModel.startInstantMix()
            advanceUntilIdle()

            coVerify(exactly = 1) { audioQueueFacade.startInstantMix("t1", "Track", any()) }
        }

    @Test
    fun startInstantMix_emptyMix_emitsEmptyMessageAndDoesNotPlay() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            val album = MediaItem(id = "album1", name = "Album", mediaType = MediaType.ALBUM)
            stubProvider("album1", remoteSnapshot(MediaDetail(item = album)))
            coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns AudioQueueOutcome.Empty
            val firstMessage = CompletableDeferred<DetailMessage>()
            // Unconfined so the collector subscribes immediately (replay = 0 — an
            // emission before subscription is dropped); backgroundScope so the
            // never-completing collector is cancelled when the test body ends.
            backgroundScope.launch(UnconfinedTestDispatcher(mainDispatcherRule.testDispatcher.scheduler)) {
                viewModel.messages.collect { firstMessage.complete(it) }
            }
            every { context.getString(R.string.detail_instant_mix_empty) } returns "no mix"

            viewModel.loadItem("album1")
            advanceUntilIdle()

            viewModel.startInstantMix()
            advanceUntilIdle()

            assertTrue("instant mix empty message was not emitted", firstMessage.isCompleted)
            assertEquals("no mix", (firstMessage.await() as DetailMessage.Text).text)
        }

    @Test
    fun startInstantMix_failure_emitsFailureMessageAndDoesNotPlay() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            val album = MediaItem(id = "album1", name = "Album", mediaType = MediaType.ALBUM)
            stubProvider("album1", remoteSnapshot(MediaDetail(item = album)))
            coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns
                AudioQueueOutcome.Failed(RuntimeException("boom"))
            val firstMessage = CompletableDeferred<DetailMessage>()
            // Unconfined so the collector subscribes immediately (replay = 0); see
            // the empty-mix test above for the pattern rationale.
            backgroundScope.launch(UnconfinedTestDispatcher(mainDispatcherRule.testDispatcher.scheduler)) {
                viewModel.messages.collect { firstMessage.complete(it) }
            }
            every { context.getString(R.string.detail_instant_mix_failed) } returns "failed mix"

            viewModel.loadItem("album1")
            advanceUntilIdle()

            viewModel.startInstantMix()
            advanceUntilIdle()

            assertTrue("instant mix failure message was not emitted", firstMessage.isCompleted)
            assertEquals("failed mix", (firstMessage.await() as DetailMessage.Text).text)
            io.mockk.verify(exactly = 0) { context.getString(R.string.detail_instant_mix_empty) }
        }

    @Test
    fun startInstantMix_suppressedByGuard_emitsNoMessage() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            val album = MediaItem(id = "album1", name = "Album", mediaType = MediaType.ALBUM)
            stubProvider("album1", remoteSnapshot(MediaDetail(item = album)))
            coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns AudioQueueOutcome.Suppressed
            val firstMessage = CompletableDeferred<DetailMessage>()
            // Unconfined so the collector subscribes immediately (replay = 0); see
            // the empty-mix test above for the pattern rationale.
            backgroundScope.launch(UnconfinedTestDispatcher(mainDispatcherRule.testDispatcher.scheduler)) {
                viewModel.messages.collect { firstMessage.complete(it) }
            }

            viewModel.loadItem("album1")
            advanceUntilIdle()

            viewModel.startInstantMix()
            advanceUntilIdle()

            // A suppressed start (navigation drift) is silent by design.
            assertFalse(firstMessage.isCompleted)
            io.mockk.verify(exactly = 0) { context.getString(R.string.detail_instant_mix_empty) }
            io.mockk.verify(exactly = 0) { context.getString(R.string.detail_instant_mix_failed) }
        }

    @Test
    fun startInstantMix_nonAudioItem_isNoOp() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            stubProvider(
                "m1",
                remoteSnapshot(MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE))),
            )

            viewModel.loadItem("m1")
            advanceUntilIdle()

            viewModel.startInstantMix()
            advanceUntilIdle()

            // Non-audio items short-circuit before the facade is touched.
            coVerify(exactly = 0) { audioQueueFacade.startInstantMix(any(), any(), any()) }
        }

    @Test
    fun resolveTmdbId_providerIdTmdb_returnsParsed() = runTest(mainDispatcherRule.testDispatcher) {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            providerIds = mapOf("tmdb" to "12345"),
        )
        assertEquals(12345, callResolveTmdbId(detail))
    }

    @Test
    fun resolveTmdbId_providerIdTmdbid_returnsParsed() = runTest(mainDispatcherRule.testDispatcher) {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            providerIds = mapOf("tmdbid" to "67890"),
        )
        assertEquals(67890, callResolveTmdbId(detail))
    }

    @Test
    fun resolveTmdbId_externalUrlWithTmdbHost_extractsId() = runTest(mainDispatcherRule.testDispatcher) {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            externalUrls = listOf(ExternalUrl(name = "TMDB", url = "https://www.themoviedb.org/movie/55512")),
        )
        assertEquals(55512, callResolveTmdbId(detail))
    }

    @Test
    fun resolveTmdbId_noProviderOrTmdbUrl_returnsNull() = runTest(mainDispatcherRule.testDispatcher) {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            externalUrls = listOf(ExternalUrl(name = "IMDb", url = "https://www.imdb.com/title/tt123")),
        )
        assertNull(callResolveTmdbId(detail))
    }

    @Test
    fun resolveTmdbId_nonNumericProviderId_returnsNull() = runTest(mainDispatcherRule.testDispatcher) {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            providerIds = mapOf("tmdb" to "abc-not-a-number"),
        )
        assertNull(callResolveTmdbId(detail))
    }

    private fun callResolveTmdbId(detail: MediaDetail): Int? = resolveTmdbId(detail)

    /**
     * Stubs a single-season REMOTE series snapshot via the fake provider, plus
     * [MediaDetailProvider.applyOptimisticSeasonRewrite] so markSeason's
     * optimistic flip + smart-play recompute resolve. The rewrite stub mirrors
     * the real provider: applies the transform, rebuilds the snapshot's episode
     * sections, bumps the content generation, and pushes a new Loaded through
     * the provider flow so the VM's reducer adopts it. (The real provider also
     * invalidates the catalogue for re-entry; that's covered by the provider's
     * own tests, not asserted here.)
     */
    private fun stubSeries(seriesId: String, season: MediaItem, episodes: List<MediaItem>) {
        val detail = MediaDetail(item = MediaItem(id = seriesId, name = "Show", mediaType = MediaType.SERIES))
        val comparator = playbackOrderComparator()
        val sorted = episodes.sortedWith(comparator)
        val flow = stubProvider(
            seriesId,
            remoteSnapshot(
                detail = detail,
                seasons = listOf(season),
                episodesBySeason = mapOf(season.id to episodes),
                fetchedSeasonIds = setOf(season.id),
                sortedEpisodes = sorted,
            ),
        )
        coEvery { mediaDetailProvider.applyOptimisticSeasonRewrite(seriesId, season.id, any()) } answers {
            val transform = thirdArg<(List<MediaItem>) -> List<MediaItem>>()
            val rewritten = transform(episodes)
            val current = (flow.value as DetailLoadState.Loaded).snapshot
            flow.value = DetailLoadState.Loaded(
                current.copy(
                    episodesBySeason = mapOf(season.id to rewritten),
                    sortedEpisodes = rewritten.sortedWith(comparator),
                    contentGeneration = current.contentGeneration + 1,
                ),
            )
        }
    }

    /** Two-season variant of [stubSeries] for the sibling-season regression. */
    private fun stubTwoSeasonSeries(
        seriesId: String,
        s1: MediaItem,
        s2: MediaItem,
        s1e1: MediaItem,
        s2e1: MediaItem,
    ) {
        val detail = MediaDetail(item = MediaItem(id = seriesId, name = "Show", mediaType = MediaType.SERIES))
        val comparator = playbackOrderComparator()
        val episodesBySeason = mapOf(s1.id to listOf(s1e1), s2.id to listOf(s2e1))
        val sorted = episodesBySeason.values.flatten().sortedWith(comparator)
        val flow = stubProvider(
            seriesId,
            remoteSnapshot(
                detail = detail,
                seasons = listOf(s1, s2),
                episodesBySeason = episodesBySeason,
                fetchedSeasonIds = setOf(s1.id, s2.id),
                sortedEpisodes = sorted,
            ),
        )
        coEvery { mediaDetailProvider.applyOptimisticSeasonRewrite(seriesId, s1.id, any()) } answers {
            val transform = thirdArg<(List<MediaItem>) -> List<MediaItem>>()
            val rewritten = transform(listOf(s1e1))
            val current = (flow.value as DetailLoadState.Loaded).snapshot
            flow.value = DetailLoadState.Loaded(
                current.copy(
                    episodesBySeason = episodesBySeason + (s1.id to rewritten),
                    sortedEpisodes = (rewritten + listOf(s2e1)).sortedWith(comparator),
                    contentGeneration = current.contentGeneration + 1,
                ),
            )
        }
    }

    private fun playbackOrderComparator() = compareBy<MediaItem>(
        { it.seasonNumber ?: Int.MAX_VALUE },
        { it.episodeNumber ?: it.indexNumber ?: Int.MAX_VALUE },
        { it.name },
    )

    private fun episode(
        id: String,
        season: Int,
        episode: Int,
        isPlayed: Boolean = false,
        positionTicks: Long? = null,
    ) = MediaItem(
        id = id,
        name = "Episode $episode",
        mediaType = MediaType.EPISODE,
        seasonNumber = season,
        episodeNumber = episode,
        indexNumber = episode,
        isPlayed = isPlayed,
        playbackPositionTicks = positionTicks,
        seriesId = "s1",
        seasonId = "season1",
    )

    @Test
    fun setShowDetailUpNext_delegatesToLibraryStore() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.setShowDetailUpNext(false)
        advanceUntilIdle()
        coVerify { libraryStore.setShowDetailUpNext(false) }

        viewModel.setShowDetailUpNext(true)
        advanceUntilIdle()
        coVerify { libraryStore.setShowDetailUpNext(true) }
    }

    /**
     * Awaits a [DetailUiState.SmartPlayTarget] matching [predicate].
     *
     * The smart-play recompute launches on `Dispatchers.Default`, which is NOT
     * driven by the test dispatcher — so `advanceUntilIdle()` alone can return
     * before that launch posts its update. Yielding the test coroutine lets the
     * Default thread actually run; we poll up to a hard cap so a genuine logic
     * failure still fails the test instead of hanging.
     */
    private suspend fun TestScope.awaitSmartPlayTarget(
        predicate: (DetailUiState.SmartPlayTarget) -> Boolean,
    ): DetailUiState.SmartPlayTarget {
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline) {
            val target = viewModel.uiState.value.smartPlayTarget
            if (target != null && predicate(target)) return target
            // Let the Dispatchers.Default recompute make progress before re-checking.
            delay(10)
            advanceUntilIdle()
        }
        error(
            "smartPlayTarget never matched within timeout. " +
                "Last value: ${viewModel.uiState.value.smartPlayTarget}",
        )
    }
}
