package com.raulshma.jellyplay.feature.details

import android.content.Context
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.ThemeMusicPlayer
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
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
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.ExternalUrl
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.network.api.TmdbApiClient
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var downloadRepository: DownloadRepository
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
    private lateinit var episodeCatalogue: com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue

    private lateinit var viewModel: DetailViewModel

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        playbackRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
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
        episodeCatalogue = mockk(relaxed = true)

        every { projections.detailPreferences } returns MutableStateFlow(DetailPreferences())
        every { libraryStore.library } returns MutableStateFlow(LibrarySlice())
        every { homeDiscoveryStore.homeDiscovery } returns MutableStateFlow(HomeDiscoverySlice())
        every { experimentalStore.experimental } returns MutableStateFlow(ExperimentalSlice())
        every { downloadsStore.downloads } returns MutableStateFlow(DownloadsSlice())
        every { engineStore.playerEngine } returns MutableStateFlow(PlayerEngineSlice())
        every { seerrRepository.isConnected() } returns flowOf(false)
        every { seerrRepository.isRecommendationsEnabled() } returns flowOf(false)
        // Default stub for the similar-items fetch so loadItem's concurrent
        // related-items launch doesn't crash casting the relaxed-mock default.
        // Individual tests override this when they assert on related items.
        coEvery { mediaRepository.getSimilarItems(any(), any()) } returns Result.success(emptyList())

        // The ViewModel resolves localized labels via context.getString(resId, vararg). As a
        // pure unit test (no Robolectric/instrumentation), stub the smart-play templates to
        // reconstruct their canonical form so assertions stay focused on target selection.
        // Context.getString(int, Object...) is a vararg; mockk collects the trailing args into
        // args[1] as an Array, so the two format Ints are (args[1] as Array<*>)[0] and [1].
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
            "Next Up S${fmt[0]}:E${fmt[1]}"
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

        buildViewModel()
    }

    /**
     * Constructs (or reconstructs) the [DetailViewModel] under test. Tests that
     * need to flip a stub the uiState combine captures at construction time
     * (e.g. [loadSeerrData_whenConnectedAndEnabled_fetchesSeerrRecommendations])
     * override the stub first, then call this to rebuild.
     */
    private fun buildViewModel() {
        viewModel = DetailViewModel(
            context = context,
            mediaRepository = mediaRepository,
            episodeCatalogue = episodeCatalogue,
            playbackRepository = playbackRepository,
            imageUrlProvider = imageUrlProvider,
            downloadRepository = downloadRepository,
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

    @Test
    fun loadItem_failure_setsErrorMessage() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.failure(RuntimeException("boom"))

        viewModel.loadItem("m1")
        advanceUntilIdle()

        assertEquals("boom", viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.detail)
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
        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)),
        )

        viewModel.loadItem("m1")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.detail)
        assertNull(viewModel.uiState.value.smartPlayTarget)
    }

    // ---- Pull-to-refresh ---------------------------------------------------

    @Test
    fun forceRefresh_withoutLoadedDetail_isNoOp() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }

        viewModel.forceRefresh()
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.invalidateDetailCache(any()) }
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun forceRefresh_keepsContentVisibleWhileRefetching() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)),
        )

        viewModel.loadItem("m1")
        advanceUntilIdle()

        // Hold the refresh's re-fetch open so the in-flight state is observable.
        val gate = CompletableDeferred<Unit>()
        coEvery { mediaRepository.getMediaDetail("m1") } coAnswers {
            gate.await()
            Result.success(MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)))
        }

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
    fun forceRefresh_movie_invalidatesDetailCacheAndRefetches() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)),
        )

        viewModel.loadItem("m1")
        advanceUntilIdle()

        viewModel.forceRefresh()
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.invalidateDetailCache("m1") }
        coVerify(exactly = 0) { mediaRepository.invalidateSeriesCache(any()) }
        // Cache dropped, so the refetch must hit the repo again (2 calls total).
        coVerify(exactly = 2) { mediaRepository.getMediaDetail("m1") }
    }

    @Test
    fun forceRefresh_series_invalidatesSeriesCachesAndRefetches() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
        stubSeries("s1", season, listOf(episode("e1", 1, 1, isPlayed = false)))

        viewModel.loadItem("s1")
        advanceUntilIdle()

        viewModel.forceRefresh()
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.invalidateDetailCache("s1") }
        // Seasons/episodes caches now live in the catalogue; force-refresh drops
        // them via episodeCatalogue.invalidateSeries (the load reset also drops
        // the prior series' snapshot, so the call count is >= 1).
        coVerify(atLeast = 1) { episodeCatalogue.invalidateSeries("s1") }
        coVerify(exactly = 0) { mediaRepository.invalidateUserDataCaches(any()) }
        // Fresh seasons + episodes must be fetched after the cache drop — the
        // detail screen reloads via the catalogue snapshot.
        coVerify(atLeast = 2) { episodeCatalogue.loadSeriesEpisodes("s1", any()) }
    }

    @Test
    fun forceRefresh_episode_invalidatesParentSeriesCaches() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("e1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "e1", name = "Ep 1", mediaType = MediaType.EPISODE, seriesId = "s1")),
        )
        val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
        // The parent series' catalogue snapshot (empty episodes suffice — this
        // test only asserts the invalidation, not the loaded episodes).
        val snapshot = com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot(
            seriesId = "s1",
            seasons = listOf(season),
            episodesBySeason = emptyMap(),
            fetchedSeasonIds = emptySet(),
            sortedEpisodes = emptyList(),
            epoch = 0L,
        )
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } returns Result.success(snapshot)

        viewModel.loadItem("e1")
        advanceUntilIdle()

        viewModel.forceRefresh()
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.invalidateDetailCache("e1") }
        // Episode belongs to a series → its catalogue snapshot must drop (the
        // load reset also drops the prior series' snapshot, so >= 1).
        coVerify(atLeast = 1) { episodeCatalogue.invalidateSeries("s1") }
        coVerify(exactly = 0) { mediaRepository.invalidateUserDataCaches(any()) }
    }

    @Test
    fun forceRefresh_album_invalidatesUserDataCachesForAlbumTracks() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("al1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "al1", name = "Album", mediaType = MediaType.ALBUM)),
        )
        coEvery { mediaRepository.getAlbumTracks("al1") } returns Result.success(emptyList())

        viewModel.loadItem("al1")
        advanceUntilIdle()

        viewModel.forceRefresh()
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.invalidateDetailCache("al1") }
        // Album tracks have no scoped series cache — invalidateUserDataCaches
        // drops the `tracks_$itemId` entry so the refetch is truly fresh.
        coVerify(exactly = 1) { mediaRepository.invalidateUserDataCaches("al1") }
        coVerify(exactly = 0) { mediaRepository.invalidateSeriesCache(any()) }
        coVerify(exactly = 2) { mediaRepository.getAlbumTracks("al1") }
    }

    @Test
    fun forceRefresh_collection_invalidatesCollectionItemsCache() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("c1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "c1", name = "Collection", mediaType = MediaType.COLLECTION)),
        )
        coEvery { mediaRepository.getCollectionItems("c1", limit = 100) } returns Result.success(
            com.raulshma.jellyplay.core.model.SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
        )

        viewModel.loadItem("c1")
        advanceUntilIdle()

        viewModel.forceRefresh()
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.invalidateDetailCache("c1") }
        coVerify(exactly = 1) { mediaRepository.invalidateCollectionItemsCache("c1") }
        coVerify(exactly = 0) { mediaRepository.invalidateSeriesCache(any()) }
        coVerify(exactly = 2) { mediaRepository.getCollectionItems("c1", limit = 100) }
    }

    @Test
    fun forceRefresh_refetchFailure_surfacesErrorAndClearsIndicator() = runTest(mainDispatcherRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)),
        )
        viewModel.loadItem("m1")
        advanceUntilIdle()

        coEvery { mediaRepository.getMediaDetail("m1") } returns Result.failure(RuntimeException("boom"))
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
        assertEquals("Next Up S1:E2", target.label)
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
        coEvery { mediaRepository.getMediaDetail("movie-1") } returns Result.success(MediaDetail(item = item))
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
        coEvery { mediaRepository.getMediaDetail("movie-1") } returns Result.success(MediaDetail(item = item))
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
            // markSeason no longer issues a post-mutation getEpisodes refetch: the
            // optimistic flip is the source of truth for this screen, and the
            // refetch would write a stale pre-cascade snapshot back into the repo's
            // episodesCache (bitten on re-entry). Verify the refetch never fires.
            io.mockk.coVerify(exactly = 0) { mediaRepository.getEpisodes("s1", "season1") }
            // Every episode now played → smart-play falls back to a replay of S1:E1.
            // The recompute launches on Dispatchers.Default, so poll the uiState
            // flow until the label settles (avoids a race where advanceUntilIdle
            // returns before the Default launch posts its update). Match on the
            // label, not the episode id — the episode (e1) is the same before
            // and after, so an id-only predicate would return the stale "Play"
            // target before the recompute lands the "Replay" target.
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
            coEvery { mediaRepository.getMediaDetail("s1") } returns Result.success(
                MediaDetail(item = MediaItem(id = "s1", name = "Show", mediaType = MediaType.SERIES)),
            )
            // Two-season catalogue snapshot stub (the detail screen reads both
            // seasons + episodes from the consolidated snapshot now).
            val snapshot = com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot(
                seriesId = "s1",
                seasons = listOf(s1, s2),
                episodesBySeason = mapOf("season1" to listOf(s1e1), "season2" to listOf(s2e1)),
                fetchedSeasonIds = setOf("season1", "season2"),
                sortedEpisodes = listOf(s1e1, s2e1),
                epoch = 0L,
            )
            coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } returns Result.success(snapshot)
            coEvery { episodeCatalogue.updateSeasonEpisodes("s1", "season1", any()) } answers {
                val transform = thirdArg<(List<MediaItem>) -> List<MediaItem>>()
                val rewritten = transform(listOf(s1e1))
                snapshot.copy(
                    episodesBySeason = snapshot.episodesBySeason + ("season1" to rewritten),
                    sortedEpisodes = (rewritten + listOf(s2e1)),
                )
            }
            coEvery { mediaRepository.markPlayed("season1") } returns Result.success(Unit)

            viewModel.loadItem("s1")
            advanceUntilIdle()

            viewModel.markSeasonPlayed("season1")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.episodes["season1"]!!.all { it.isPlayed })
            assertTrue(viewModel.uiState.value.episodes["season2"]!!.none { it.isPlayed })
        }

    // markSeasonPlayed must drop the repo-level seasons/episodes caches for the
    // series on success — the in-place optimistic flip keeps the current screen
    // correct, but re-entering the detail (back/foreground) reads through
    // `getSeasons`/`getEpisodes` whose cache `invalidateUserDataCaches(seasonId)`
    // cannot reach (seasons aren't standalone detail-cache entries). Regression
    // guard against serving a stale pre-mutation snapshot on re-entry.
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
            // markSeason drops the catalogue snapshot for re-entry (the series
            // cache now lives in the catalogue, not the repo).
            io.mockk.verify(exactly = 1) { episodeCatalogue.invalidateSeries("s1") }
        }

    // Repository failure must surface the localized snackbar and leave the
    // episodes map unchanged (no optimistic corruption on error). Mirrors the
    // existing messages_* tests: `withTimeout { first() }` drives virtual time
    // forward until the async emit from markSeason's launch lands.
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
            // Played episodes with a residual resume position: after mark-unplayed
            // the position MUST be cleared, or the progress bar and "time left"
            // label linger on-screen (and the episode stays in continue watching
            // locally until the next re-fetch).
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
            // After: first episode unplayed → play label, no longer a replay.
            // (The recompute launches on Dispatchers.Default — poll for the
            // label to settle rather than asserting the instant `advanceUntilIdle`
            // returns. Match on the label: the episode id is unchanged, so an
            // id-only predicate would return the stale "Replay" target.)
            val target = awaitSmartPlayTarget { it.label == "Play S1:E1" }
            assertEquals("Play S1:E1", target.label)
        }

    // Regression for the user-reported re-entry bug: "marked a season unwatched,
    // it removed the badge; went back and came to the detail again — it showed the
    // badge again; but the per-episode detail screen shows unwatched". The mark-
    // unplayed path must NOT issue a getEpisodes refetch that writes a stale
    // pre-cascade snapshot back into the repo's episodesCache, which re-entry's
    // getAllEpisodesGrouped would then HIT and serve as stale watched state. The
    // optimistic flip keeps the current screen correct; the invalidateSeriesCache
    // call forces re-entry to miss the cache and re-hit the server (now fully
    // cascaded). Verified here by asserting markUnplayed neither issues a refetch
    // nor touches the unplayed badge.
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

            // No post-mutation refetch → the repo cache is not re-populated with a
            // stale single-season slice, so re-entry's getAllEpisodesGrouped misses.
            io.mockk.coVerify(exactly = 0) { mediaRepository.getEpisodes("s1", "season1") }
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
            // No stub for markPlayed → relaxed mock would return Unit, so verify
            // it was never invoked instead.
            viewModel.loadItem("s1")
            advanceUntilIdle()

            viewModel.markSeasonPlayed("season1")
            advanceUntilIdle()

            io.mockk.coVerify(exactly = 0) { mediaRepository.markPlayed(any()) }
        }

    // Regression: loadSeerrData must read isSeerrConnected/isSeerrRecommendationsEnabled
    // from the PUBLISHED uiState (where the seerr-flags combine folds them in),
    // not from _uiState (the Group-1 primary flow, where they are never written
    // and always read as the default false). When connected+enabled, the Seerr
    // recommendation/similar/video fetches must actually run.
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

            coEvery { mediaRepository.getMediaDetail("m1") } returns Result.success(
                MediaDetail(
                    item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
                    providerIds = mapOf("tmdb" to "123"),
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
            // loadSeerrDataIfNeeded is normally driven by composition; invoke it
            // directly (after loadItem resolved currentItemId) to mirror the UI.
            viewModel.uiState.value.detail?.let { viewModel.loadSeerrDataIfNeeded(it) }
            advanceUntilIdle()

            assertEquals(listOf(recItem), viewModel.uiState.value.seerrRecommendations)
            assertEquals(listOf(recItem), viewModel.uiState.value.seerrSimilar)
        }

    // Regression: when the batched getAllEpisodesGrouped returns episodes grouped
    // under a key that does NOT match any season id (e.g. "" from a null seasonId),
    // the affected season must NOT be marked fetched — otherwise loadEpisodesForSeason
    // short-circuits and the season is pinned empty. The per-season refetch must
    // still fire and populate it.
    @Test
    fun episodes_batchReturnsMismatchedSeasonKey_leavesSeasonUnfetchedForRefetch() =
        runTest(mainDispatcherRule.testDispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
            val season = MediaItem(id = "season1", name = "Season 1", mediaType = MediaType.SEASON, indexNumber = 1)
            coEvery { mediaRepository.getMediaDetail("s1") } returns Result.success(
                MediaDetail(item = MediaItem(id = "s1", name = "Show", mediaType = MediaType.SERIES)),
            )
            val eps = listOf(episode("e1", 1, 1), episode("e2", 1, 2))
            // The catalogue snapshot groups under "" (null seasonId on the
            // server), so season1 is absent from fetchedSeasonIds — the on-demand
            // per-season refetch must still fire and populate it.
            val mismatchedSnapshot = com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot(
                seriesId = "s1",
                seasons = listOf(season),
                episodesBySeason = mapOf("" to listOf(episode("e1", 1, 1))),
                fetchedSeasonIds = emptySet(),
                sortedEpisodes = listOf(episode("e1", 1, 1)),
                epoch = 0L,
            )
            coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } returns Result.success(mismatchedSnapshot)
            coEvery { episodeCatalogue.loadSeasonEpisodes("s1", "season1", any()) } returns Result.success(eps)

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

    private fun stubSeries(seriesId: String, season: MediaItem, episodes: List<MediaItem>) {
        coEvery { mediaRepository.getMediaDetail(seriesId) } returns Result.success(
            MediaDetail(item = MediaItem(id = seriesId, name = "Show", mediaType = MediaType.SERIES)),
        )
        // The detail screen now reads seasons + episodes from the consolidated
        // catalogue snapshot, so stub the catalogue directly. The snapshot's
        // sortedEpisodes mirrors the canonical playback order the VM consumes.
        val snapshot = com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot(
            seriesId = seriesId,
            seasons = listOf(season),
            episodesBySeason = mapOf(season.id to episodes),
            fetchedSeasonIds = setOf(season.id),
            sortedEpisodes = episodes.sortedWith(
                compareBy<MediaItem>(
                    { it.seasonNumber ?: Int.MAX_VALUE },
                    { it.episodeNumber ?: it.indexNumber ?: Int.MAX_VALUE },
                    { it.name },
                ),
            ),
            epoch = 0L,
        )
        coEvery { episodeCatalogue.loadSeriesEpisodes(seriesId, any()) } returns Result.success(snapshot)
        coEvery { episodeCatalogue.loadSeasonEpisodes(seriesId, season.id, any()) } returns Result.success(episodes)
        // updateSeasonEpisodes applies the optimistic transform against the
        // stubbed snapshot, mirroring the real catalogue's rewrite-and-rebuild
        // so markSeasonPlayed's in-place flip + smart-play recompute resolve.
        coEvery { episodeCatalogue.updateSeasonEpisodes(seriesId, season.id, any()) } answers {
            val transform = thirdArg<(List<MediaItem>) -> List<MediaItem>>()
            val rewritten = transform(episodes)
            snapshot.copy(
                episodesBySeason = snapshot.episodesBySeason + (season.id to rewritten),
                sortedEpisodes = rewritten.sortedWith(
                    compareBy<MediaItem>(
                        { it.seasonNumber ?: Int.MAX_VALUE },
                        { it.episodeNumber ?: it.indexNumber ?: Int.MAX_VALUE },
                        { it.name },
                    ),
                ),
            )
        }
    }

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
