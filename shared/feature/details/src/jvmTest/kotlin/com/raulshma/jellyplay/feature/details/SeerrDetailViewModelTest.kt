package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SeerrDetailPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrEpisode
import com.raulshma.jellyplay.core.model.seerr.SeerrExternalIds
import com.raulshma.jellyplay.core.model.seerr.SeerrImdbRating
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaInfo
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrRatings
import com.raulshma.jellyplay.core.model.seerr.SeerrRtRating
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrSeasonDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrTmdbRating
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Tests the [SeerrDetailViewModel] surfaces NOT covered by
 * [SeerrDetailViewModelResolutionTest] (which focuses on Jellyfin-id resolution
 * and the requestMedia optimistic flip): the loadDetails happy path for both
 * movie/tv, ratings merging, recommendations/similar folding, and season
 * toggle / episode loading.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeerrDetailViewModelTest {

    // Legacy :core:testing MainDispatcherRule, inlined (conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    private lateinit var seerrRepository: SeerrRepository
    private lateinit var seerrRequestDelegate: SeerrRequestDelegate
    private lateinit var projections: PreferenceProjections
    private lateinit var seerrPreferencesStore: SeerrPreferencesStore
    private lateinit var mediaRepository: MediaRepository

    private lateinit var viewModel: SeerrDetailViewModel

    @BeforeTest
    fun setUp() {
        seerrRepository = mockk(relaxed = true)
        seerrRequestDelegate = mockk(relaxed = true)
        projections = mockk(relaxed = true)
        seerrPreferencesStore = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)

        every { projections.seerrDetailPreferences } returns MutableStateFlow(SeerrDetailPreferences())
        every { seerrPreferencesStore.preferences } returns MutableStateFlow(SeerrPreferences())
        every { seerrRepository.isConnected() } returns flowOf(false)
        every { seerrRepository.getPreferences() } returns flowOf(SeerrPreferences())
        coEvery { seerrRepository.getRatings(any(), any()) } returns Result.failure(NullPointerException())
        coEvery { seerrRepository.getRecommendations(any(), any()) } returns Result.success(emptySearchResponse())
        coEvery { seerrRepository.getSimilar(any(), any()) } returns Result.success(emptySearchResponse())

        viewModel = SeerrDetailViewModel(
            seerrRepository, seerrRequestDelegate, projections, seerrPreferencesStore, mediaRepository,
        )
    }

    private fun emptySearchResponse() = SeerrSearchResponse(results = emptyList(), page = 1, totalPages = 1, totalResults = 0)

    // ── loadDetails: movie happy path ───────────────────────────────────

    @Test
    fun `loadDetails movie populates movieDetails and clears loading`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val movie = SeerrMovieDetails(id = 123, title = "Test Movie", voteAverage = 7.5f)
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(movie)
        // Not available → no Jellyfin resolution attempted.

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("Test Movie", state.movieDetails?.title)
        assertNull(state.error)
    }

    @Test
    fun `loadDetails movie with rt ratings skips secondary ratings fetch`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val movie = SeerrMovieDetails(
            id = 123,
            ratings = SeerrRatings(rt = SeerrRtRating(criticsScore = 90)),
            voteAverage = 7.5f,
        )
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(movie)

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()

        // Ratings present on the detail → hasRatings=true → getRatings never called.
        coVerify(exactly = 0) { seerrRepository.getRatings(any(), any()) }
        assertNotNull(viewModel.uiState.value.ratings)
    }

    @Test
    fun `loadDetails movie with imdb ratings skips secondary ratings fetch`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val movie = SeerrMovieDetails(
            id = 123,
            ratings = SeerrRatings(imdb = SeerrImdbRating(rating = 8.0f)),
        )
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(movie)

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()

        coVerify(exactly = 0) { seerrRepository.getRatings(any(), any()) }
    }

    @Test
    fun `loadDetails movie merges tmdb score into ratings when none present`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val movie = SeerrMovieDetails(id = 123, voteAverage = 8.2f)
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(movie)
        coEvery { seerrRepository.getRatings(123, "movie") } returns Result.success(
            SeerrRatings(imdb = SeerrImdbRating(rating = 7.5f)),
        )

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()

        val ratings = viewModel.uiState.value.ratings!!
        // voteAverage folds into the empty tmdb slot.
        assertEquals(8.2f, ratings.tmdb!!.rating)
        // Secondary ratings fetch preserved imdb.
        assertEquals(7.5f, ratings.imdb!!.rating)
    }

    // ── loadDetails: tv path ────────────────────────────────────────────

    @Test
    fun `loadDetails tv populates tvDetails`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val tv = SeerrTvDetails(id = 456, name = "Test Show", voteAverage = 9.0f)
        coEvery { seerrRepository.getTvDetails(456) } returns Result.success(tv)

        viewModel.loadDetails(456, "tv")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("Test Show", state.tvDetails?.name)
        assertNull(state.movieDetails)
    }

    @Test
    fun `loadDetails is case-insensitive on mediaType`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { seerrRepository.getTvDetails(any()) } returns Result.success(SeerrTvDetails(id = 456, name = "TV"))
        coEvery { seerrRepository.getRecommendations(456, MediaType.SERIES) } returns Result.success(
            SeerrSearchResponse(results = listOf(SeerrSearchItem(id = 789, name = "Rec"))),
        )

        viewModel.loadDetails(456, "TV")
        advanceUntilIdle()

        // "TV" normalized to SERIES media type for the recommendations fetch.
        coVerify(exactly = 1) { seerrRepository.getRecommendations(456, MediaType.SERIES) }
    }

    // ── loadDetails: recommendations & similar folding ──────────────────

    @Test
    fun `loadDetails folds recommendations and similar into state`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val rec = SeerrSearchItem(id = 1, title = "Rec")
        val sim = SeerrSearchItem(id = 2, title = "Sim")
        coEvery { seerrRepository.getMovieDetails(any()) } returns Result.success(SeerrMovieDetails(id = 123))
        coEvery { seerrRepository.getRecommendations(123, MediaType.MOVIE) } returns Result.success(
            SeerrSearchResponse(results = listOf(rec)),
        )
        coEvery { seerrRepository.getSimilar(123, MediaType.MOVIE) } returns Result.success(
            SeerrSearchResponse(results = listOf(sim)),
        )

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()

        assertEquals(listOf(rec), viewModel.uiState.value.recommendations)
        assertEquals(listOf(sim), viewModel.uiState.value.similar)
    }

    // ── loadDetails: error + available-item resolution ──────────────────

    @Test
    fun `loadDetails movie failure surfaces error`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.failure(RuntimeException("boom"))

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()

        assertEquals("boom", viewModel.uiState.value.error)
    }

    @Test
    fun `loadDetails available movie resolves jellyfinItemId via tmdb`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(
            SeerrMovieDetails(
                id = 123,
                mediaInfo = SeerrMediaInfo(tmdbId = 123, status = SeerrMediaStatus.AVAILABLE.value),
            ),
        )
        coEvery { mediaRepository.findItemByProviderId("tmdb", "123") } returns Result.success("jellyfin-1")

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()

        assertEquals("jellyfin-1", viewModel.uiState.value.jellyfinItemId)
    }

    @Test
    fun `loadDetails pending movie does not resolve jellyfinItemId`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(
            SeerrMovieDetails(
                id = 123,
                mediaInfo = SeerrMediaInfo(tmdbId = 123, status = SeerrMediaStatus.PENDING.value),
            ),
        )

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.findItemByProviderId(any(), any()) }
        assertNull(viewModel.uiState.value.jellyfinItemId)
    }

    @Test
    fun `loadDetails partially available tv resolves via tmdb then tvdb fallback`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { seerrRepository.getTvDetails(123) } returns Result.success(
            SeerrTvDetails(
                id = 123,
                mediaInfo = SeerrMediaInfo(tmdbId = 123, status = SeerrMediaStatus.PARTIALLY_AVAILABLE.value),
                externalIds = SeerrExternalIds(tvdbId = 999),
            ),
        )
        coEvery { mediaRepository.findItemByProviderId("tmdb", "123") } returns Result.success(null)
        coEvery { mediaRepository.findItemByProviderId("tvdb", "999") } returns Result.success("jellyfin-2")

        viewModel.loadDetails(123, "tv")
        advanceUntilIdle()

        assertEquals("jellyfin-2", viewModel.uiState.value.jellyfinItemId)
    }

    @Test
    fun `loadDetails resets prior state for a new load`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(
            SeerrMovieDetails(id = 123, title = "First"),
        )
        coEvery { seerrRepository.getMovieDetails(456) } returns Result.success(
            SeerrMovieDetails(id = 456, title = "Second"),
        )

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()
        assertEquals("First", viewModel.uiState.value.movieDetails?.title)

        viewModel.loadDetails(456, "movie")
        // The reset coroutine clears movieDetails before the new fetch resolves.
        advanceUntilIdle()
        // After the full load, the new title replaces the old and no error lingers.
        assertEquals("Second", viewModel.uiState.value.movieDetails?.title)
        assertNull(viewModel.uiState.value.error)
    }

    // ── toggleSeason / episode loading ──────────────────────────────────

    @Test
    fun `toggleSeason selects season and loads its episodes`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val eps = listOf(SeerrEpisode(id = 1, episodeNumber = 1, name = "Pilot"))
        coEvery { seerrRepository.getTvSeasonDetails(456, 1) } returns Result.success(
            SeerrSeasonDetail(seasonNumber = 1, episodes = eps),
        )

        viewModel.toggleSeason(456, 1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.selectedSeasonNumber)
        assertEquals(eps, state.episodesBySeason[1])
        assertFalse(state.isLoadingEpisodes)
    }

    @Test
    fun `toggleSeason toggling same season deselects without refetching`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { seerrRepository.getTvSeasonDetails(456, 1) } returns Result.success(
            SeerrSeasonDetail(seasonNumber = 1, episodes = listOf(SeerrEpisode(id = 1))),
        )

        viewModel.toggleSeason(456, 1)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.selectedSeasonNumber)

        viewModel.toggleSeason(456, 1) // deselect
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedSeasonNumber)
        coVerify(exactly = 1) { seerrRepository.getTvSeasonDetails(456, 1) }
    }

    @Test
    fun `toggleSeason does not refetch already-cached episodes`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { seerrRepository.getTvSeasonDetails(456, 1) } returns Result.success(
            SeerrSeasonDetail(seasonNumber = 1, episodes = listOf(SeerrEpisode(id = 1))),
        )

        viewModel.toggleSeason(456, 1)
        advanceUntilIdle()

        viewModel.toggleSeason(456, 2) // select a different season
        advanceUntilIdle()
        viewModel.toggleSeason(456, 1) // re-select season 1 (cached)
        advanceUntilIdle()

        coVerify(exactly = 1) { seerrRepository.getTvSeasonDetails(456, 1) }
    }

    @Test
    fun `toggleSeason episode fetch failure leaves isLoadingEpisodes false`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { seerrRepository.getTvSeasonDetails(456, 1) } returns Result.failure(RuntimeException("boom"))

        viewModel.toggleSeason(456, 1)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoadingEpisodes)
        // Selected even though episodes failed to load.
        assertEquals(1, viewModel.uiState.value.selectedSeasonNumber)
    }

    // ── URL helpers ─────────────────────────────────────────────────────

    @Test
    fun `getSeerrPosterUrl and getSeerrBackdropUrl build from path`() {
        // Non-null path returns a URL containing the path segment.
        val poster = viewModel.getSeerrPosterUrl("/abc.jpg")
        val backdrop = viewModel.getSeerrBackdropUrl("/def.jpg")
        assertTrue(poster!!.contains("/abc.jpg") || poster.contains("abc.jpg"))
        assertTrue(backdrop!!.contains("/def.jpg") || backdrop.contains("def.jpg"))
    }

    @Test
    fun `getSeerrPosterUrl null path returns null`() {
        assertNull(viewModel.getSeerrPosterUrl(null))
    }

    // ── requestMedia ───────────────────────────────────────────────────

    @Test
    fun `requestMedia failure sets requestResult with error`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        backgroundScope.launch { viewModel.seerrSnapshot.collect { /* warm */ } }
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(
            SeerrMovieDetails(id = 123),
        )
        coEvery {
            seerrRequestDelegate.requestMedia(
                mediaType = any(), tmdbId = any(), seasons = any(),
                serverId = any(), profileId = any(), rootFolder = any(), tags = any(),
            )
        } returns Result.failure(RuntimeException("denied"))

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()

        viewModel.requestMedia(SeerrSearchItem(id = 123, mediaType = "movie"))
        advanceUntilIdle()

        val result = viewModel.seerrSnapshot.value.requestResult!!
        assertEquals(false, result.isLoading)
        // Holder failure path sets only the error; success stays null (not false).
        assertNull(result.success)
        assertEquals("denied", result.error)
    }

    @Test
    fun `requestMedia for loaded tv flips status to pending`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        backgroundScope.launch { viewModel.seerrSnapshot.collect { /* warm */ } }
        coEvery { seerrRepository.getTvDetails(123) } returns Result.success(
            SeerrTvDetails(id = 123),
        )
        coEvery {
            seerrRequestDelegate.requestMedia(
                mediaType = any(), tmdbId = any(), seasons = any(),
                serverId = any(), profileId = any(), rootFolder = any(), tags = any(),
            )
        } returns Result.success(mockk(relaxed = true))

        viewModel.loadDetails(123, "tv")
        advanceUntilIdle()

        viewModel.requestMedia(SeerrSearchItem(id = 123, mediaType = "tv"))
        advanceUntilIdle()

        assertEquals(
            SeerrMediaStatus.PENDING.value,
            viewModel.uiState.value.tvDetails?.mediaInfo?.status,
        )
        assertTrue(viewModel.seerrSnapshot.value.requestResult?.success == true)
    }

    @Test
    fun `requestMedia does not mutate unrelated detail`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        // Loaded detail id 123; requesting a different id 999 must not touch it.
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(
            SeerrMovieDetails(id = 123),
        )
        coEvery {
            seerrRequestDelegate.requestMedia(
                mediaType = any(), tmdbId = any(), seasons = any(),
                serverId = any(), profileId = any(), rootFolder = any(), tags = any(),
            )
        } returns Result.success(mockk(relaxed = true))

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()

        viewModel.requestMedia(SeerrSearchItem(id = 999, mediaType = "movie"))
        advanceUntilIdle()

        // id mismatch → no optimistic flip; mediaInfo stays null.
        assertNull(viewModel.uiState.value.movieDetails?.mediaInfo)
    }

    // ── dismissRequestDialog ─────────────────────────────────────────────

    @Test
    fun `dismissRequestDialog nulls the result and closes the dialog`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        backgroundScope.launch { viewModel.seerrSnapshot.collect { /* warm */ } }
        coEvery { seerrRepository.getMovieDetails(123) } returns Result.success(
            SeerrMovieDetails(id = 123),
        )
        coEvery {
            seerrRequestDelegate.requestMedia(
                mediaType = any(), tmdbId = any(), seasons = any(),
                serverId = any(), profileId = any(), rootFolder = any(), tags = any(),
            )
        } returns Result.success(mockk(relaxed = true))

        viewModel.loadDetails(123, "movie")
        advanceUntilIdle()
        viewModel.requestMedia(SeerrSearchItem(id = 123, mediaType = "movie"))
        advanceUntilIdle()
        assertNotNull(viewModel.seerrSnapshot.value.requestResult)

        viewModel.dismissRequestDialog()
        advanceUntilIdle()

        assertNull(viewModel.seerrSnapshot.value.requestResult)
        assertNull(viewModel.seerrSnapshot.value.dialogItem)
    }

    // ── openRequestDialog (delegates to holder, cascade included) ───────────────────────

    @Test
    fun `openRequestDialog for tv folds sonarr servers and opens the dialog`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.seerrSnapshot.collect { /* warm */ } }
        val sonarr = com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail(id = 1, name = "Sonarr")
        coEvery { seerrRequestDelegate.fetchServiceDetails("tv") } returns com.raulshma.jellyplay.core.data.seerr.SeerrServiceDetailsResult(
            sonarrServers = listOf(sonarr),
        )
        coEvery { seerrRequestDelegate.fetchTvDetails(any()) } returns null

        viewModel.openRequestDialog(SeerrSearchItem(id = 5, mediaType = "tv"))
        advanceUntilIdle()

        assertEquals(listOf(sonarr), viewModel.seerrSnapshot.value.sonarrServers)
        assertFalse(viewModel.seerrSnapshot.value.isLoadingServices)
        assertNotNull(viewModel.seerrSnapshot.value.dialogItem)
    }

    @Test
    fun `openRequestDialog for movie folds radarr servers and opens the dialog`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.seerrSnapshot.collect { /* warm */ } }
        val radarr = com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail(id = 2, name = "Radarr")
        coEvery { seerrRequestDelegate.fetchServiceDetails("movie") } returns com.raulshma.jellyplay.core.data.seerr.SeerrServiceDetailsResult(
            radarrServers = listOf(radarr),
        )

        viewModel.openRequestDialog(SeerrSearchItem(id = 6, mediaType = "movie"))
        advanceUntilIdle()

        assertEquals(listOf(radarr), viewModel.seerrSnapshot.value.radarrServers)
        assertNotNull(viewModel.seerrSnapshot.value.dialogItem)
    }

    // ── openRequestDialog tv seasons (cascade) ────────────────────────────

    @Test
    fun `openRequestDialog for tv populates tvSeasons filtering specials`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.seerrSnapshot.collect { /* warm */ } }
        coEvery { seerrRequestDelegate.fetchTvDetails(123) } returns com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails(
            seasons = listOf(
                com.raulshma.jellyplay.core.model.seerr.SeerrSeason(seasonNumber = 0, name = "Specials"),
                com.raulshma.jellyplay.core.model.seerr.SeerrSeason(seasonNumber = 1, name = "Season 1"),
            ),
        )

        viewModel.openRequestDialog(SeerrSearchItem(id = 123, mediaType = "tv"))
        advanceUntilIdle()

        assertEquals(listOf(1), viewModel.seerrSnapshot.value.tvSeasons.map { it.seasonNumber })
    }

    // ── prefetchRelatedDetails (delegates to holder) ───────────────────

    @Test
    fun `prefetchRelatedDetails invokes onDone callback after prefetch`() = runTest(mainDispatcher) {
        coEvery { seerrRequestDelegate.prefetchDetails(123, "movie") } returns Unit
        var called = false

        viewModel.prefetchRelatedDetails(123, "movie") { called = true }
        advanceUntilIdle()

        assertTrue(called)
        coVerify(exactly = 1) { seerrRequestDelegate.prefetchDetails(123, "movie") }
    }
}
