package com.raulshma.jellyplay.feature.details

import android.content.Context
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.ThemeMusicPlayer
import com.raulshma.jellyplay.core.data.repository.ArrRepository
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
    private lateinit var themeMusicPlayer: ThemeMusicPlayer
    private lateinit var tmdbApiClient: TmdbApiClient
    private lateinit var arrRepository: ArrRepository

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
        themeMusicPlayer = mockk(relaxed = true)
        tmdbApiClient = mockk(relaxed = true)
        arrRepository = mockk(relaxed = true)

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
        // Provider refresh is a no-op by default; tests that drive refresh override.
        coEvery { mediaDetailProvider.refresh(any()) } returns Unit
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
            themeMusicPlayer = themeMusicPlayer,
            tmdbApiClient = tmdbApiClient,
            arrRepository = arrRepository,
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

        assertEquals("boom", viewModel.uiState.value.error)
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

        assertEquals("unavailable offline", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isAccessDenied)
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

        assertTrue(viewModel.uiState.value.isAccessDenied)
        assertEquals("no access", viewModel.uiState.value.error)
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
        assertFalse(viewModel.uiState.value.isRefreshing)
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
        assertTrue(viewModel.uiState.value.isRefreshing)
        assertFalse(viewModel.uiState.value.isLoading)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRefreshing)
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
        assertFalse(viewModel.uiState.value.isRefreshing)
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

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals("boom", viewModel.uiState.value.error)
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
        coEvery { mediaRepository.markPlayed("movie-1") } returns Result.success(Unit)

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
        coEvery { mediaRepository.markUnplayed("movie-1") } returns Result.success(Unit)

        viewModel.loadItem("movie-1")
        advanceUntilIdle()
        viewModel.markUnplayed()
        advanceUntilIdle()

        val updated = viewModel.uiState.value.detail!!.item
        assertFalse(updated.isPlayed)
        assertEquals(0L, updated.playbackPositionTicks)
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
            coEvery { mediaRepository.markPlayed("season1") } returns Result.success(Unit)

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
            io.mockk.coVerify(exactly = 1) { mediaRepository.markPlayed("season1") }
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
            coEvery { mediaRepository.markPlayed("season1") } returns Result.success(Unit)

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
            coEvery { mediaRepository.markPlayed("season1") } returns Result.success(Unit)

            viewModel.loadItem("s1")
            advanceUntilIdle()

            viewModel.markSeasonPlayed("season1")
            advanceUntilIdle()

            io.mockk.coVerify(exactly = 1) { mediaRepository.markPlayed("season1") }
            // The VM routes through the provider; the provider owns the
            // catalogue invalidation internally (asserted in the provider test).
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
            coEvery { mediaRepository.markPlayed("season1") } returns Result.failure(RuntimeException("boom"))
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
            coEvery { mediaRepository.markUnplayed("season1") } returns Result.success(Unit)

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
            coEvery { mediaRepository.markUnplayed("season1") } returns Result.success(Unit)

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

            io.mockk.coVerify(exactly = 0) { mediaRepository.markPlayed(any()) }
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

    // ── NEW: LOCAL-origin snapshot suppresses smart-play + remote discovery ──

    @Test
    fun localSnapshot_suppressesSmartPlayAndRemoteDiscovery() = runTest(mainDispatcherRule.testDispatcher) {
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

        // LOCAL origin → smart-play target is null (Up Next suppressed).
        assertNull(viewModel.uiState.value.smartPlayTarget)
        assertEquals(DetailOrigin.LOCAL_OFFLINE_MODE, viewModel.uiState.value.origin)
        // No remote-only discovery coroutines fire for a local origin.
        io.mockk.coVerify(exactly = 0) { mediaRepository.getSimilarItems(any(), any()) }
        io.mockk.verify(exactly = 0) { themeMusicPlayer.playThemeFor(any()) }
        io.mockk.coVerify(exactly = 0) { seerrRepository.getMovieDetails(any()) }
        io.mockk.coVerify(exactly = 0) { seerrRepository.getTvDetails(any()) }
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
