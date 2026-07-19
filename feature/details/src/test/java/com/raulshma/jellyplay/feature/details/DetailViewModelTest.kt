package com.raulshma.jellyplay.feature.details

import android.content.Context
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
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.ExternalUrl
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.network.api.TmdbApiClient
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    private lateinit var preferencesStore: UserPreferencesStore
    private lateinit var offlineModeManager: OfflineModeManager
    private lateinit var adaptiveBitrateManager: AdaptiveBitrateManager
    private lateinit var seerrRepository: SeerrRepository
    private lateinit var seerrRequestDelegate: SeerrRequestDelegate
    private lateinit var audioPlaybackManager: AudioPlaybackManager
    private lateinit var themeMusicPlayer: ThemeMusicPlayer
    private lateinit var tmdbApiClient: TmdbApiClient
    private lateinit var arrRepository: ArrRepository

    private lateinit var viewModel: DetailViewModel

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        playbackRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        preferencesStore = mockk(relaxed = true)
        offlineModeManager = mockk(relaxed = true)
        adaptiveBitrateManager = mockk(relaxed = true)
        seerrRepository = mockk(relaxed = true)
        seerrRequestDelegate = mockk(relaxed = true)
        audioPlaybackManager = mockk(relaxed = true)
        themeMusicPlayer = mockk(relaxed = true)
        tmdbApiClient = mockk(relaxed = true)
        arrRepository = mockk(relaxed = true)

        every { preferencesStore.preferences } returns MutableStateFlow(UserPreferences())
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
            playbackRepository = playbackRepository,
            imageUrlProvider = imageUrlProvider,
            downloadRepository = downloadRepository,
            preferencesStore = preferencesStore,
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
            // Batched response groups under "" (null seasonId on the server) —
            // does not contain a "season1" entry.
            coEvery { mediaRepository.getMediaDetail("s1") } returns Result.success(
                MediaDetail(item = MediaItem(id = "s1", name = "Show", mediaType = MediaType.SERIES)),
            )
            coEvery { mediaRepository.getSeasons("s1") } returns Result.success(listOf(season))
            coEvery { mediaRepository.getAllEpisodesGrouped("s1") } returns Result.success(
                mapOf("" to listOf(episode("e1", 1, 1))),
            )
            val eps = listOf(episode("e1", 1, 1), episode("e2", 1, 2))
            coEvery { mediaRepository.getEpisodes("s1", "season1") } returns Result.success(eps)

            viewModel.loadItem("s1")
            advanceUntilIdle()

            // season1 was not in the batched map, so it must NOT be marked fetched.
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

    private fun callResolveTmdbId(detail: MediaDetail): Int? {
        val fn = DetailViewModel::class.java.getDeclaredMethod("resolveTmdbId", MediaDetail::class.java)
        fn.isAccessible = true
        return fn.invoke(viewModel, detail) as Int?
    }

    private fun stubSeries(seriesId: String, season: MediaItem, episodes: List<MediaItem>) {
        coEvery { mediaRepository.getMediaDetail(seriesId) } returns Result.success(
            MediaDetail(item = MediaItem(id = seriesId, name = "Show", mediaType = MediaType.SERIES)),
        )
        coEvery { mediaRepository.getSeasons(seriesId) } returns Result.success(listOf(season))
        coEvery { mediaRepository.getEpisodes(seriesId, season.id) } returns Result.success(episodes)
        coEvery { mediaRepository.getAllEpisodesGrouped(seriesId) } returns Result.success(mapOf(season.id to episodes))
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
}
