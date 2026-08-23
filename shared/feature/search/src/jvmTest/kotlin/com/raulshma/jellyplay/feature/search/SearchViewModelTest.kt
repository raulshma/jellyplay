package com.raulshma.jellyplay.feature.search

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.search.MediaSearchEngine
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.seerr.SeerrServiceDetailsResult
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.search.SearchFiltersStore
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrKeyword
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coverage for [SearchViewModel]'s public surface NOT exercised by
 * [SearchViewModelHistoryTest] (which focuses on `onSearchResultsShown` query
 * persistence): filter toggling, the paged search pipeline (Seerr + offline),
 * discovery suggestions, genre/tag loading + retry, and Seerr request
 * delegation to [SeerrRequestStateHolder].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (SyncStatusStateHolderTest pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository

    /** Plan 03: silent grid mutations delegate here; relaxed mock is enough. */
    private val userDataMutator: UserDataMutator = mockk(relaxed = true)
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var seerrRepository: SeerrRepository
    private lateinit var seerrRequestDelegate: SeerrRequestDelegate

    /** Search choreography (history, gate, debounce) delegates here. */
    private val mediaSearchEngine: MediaSearchEngine = mockk(relaxed = true)
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var searchFiltersStore: SearchFiltersStore

    private lateinit var viewModel: SearchViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        seerrRepository = mockk(relaxed = true)
        seerrRequestDelegate = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        searchFiltersStore = mockk(relaxed = true)

        every { mediaSearchEngine.debounceMs } returns 300L
        every { mediaSearchEngine.recentHistory() } returns flowOf(emptyList())
        coEvery { mediaSearchEngine.isSeerrSearchAvailable() } returns false
        every { searchFiltersStore.searchFiltersJson } returns MutableStateFlow(null)
        every { seerrRepository.getPreferences() } returns flowOf(SeerrPreferences())
        coEvery { mediaRepository.getGenres(any()) } returns Result.success(emptyList())
        coEvery { mediaRepository.getTags(any(), any(), any()) } returns Result.success(emptyList())
        coEvery { mediaRepository.getSearchSuggestions(any()) } returns Result.success(
            SearchResult(emptyList(), 0, 0)
        )
        coEvery { offlineRepository.searchOffline(any(), any()) } returns emptyList()

        viewModel = SearchViewModel(
            mediaRepository,
            userDataMutator,
            imageUrlProvider,
            seerrRepository,
            seerrRequestDelegate,
            mediaSearchEngine,
            offlineRepository,
            searchFiltersStore,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Filters ────────────────────────────────────────────────────────

    @Test
    fun `toggleMediaType adds then removes a media type`() {
        viewModel.toggleMediaType(MediaType.MOVIE)
        assertEquals(listOf(MediaType.MOVIE), viewModel.filters.value.mediaTypes)

        viewModel.toggleMediaType(MediaType.SERIES)
        assertEquals(listOf(MediaType.MOVIE, MediaType.SERIES), viewModel.filters.value.mediaTypes)

        viewModel.toggleMediaType(MediaType.MOVIE) // remove
        assertEquals(listOf(MediaType.SERIES), viewModel.filters.value.mediaTypes)
    }

    @Test
    fun `updateFilters replaces the whole filter set`() {
        val filters = LibraryFilters(
            mediaTypes = listOf(MediaType.MOVIE),
            genres = listOf("Action"),
            years = listOf(2020),
            tags = listOf("fav"),
            minRating = 4f,
        )

        viewModel.updateFilters(filters)

        assertEquals(filters, viewModel.filters.value)
    }

    @Test
    fun `clearFilters resets to an empty SearchFilters`() {
        viewModel.toggleMediaType(MediaType.MOVIE)
        viewModel.updateFilters(LibraryFilters(genres = listOf("Action")))

        viewModel.clearFilters()

        assertEquals(LibraryFilters(), viewModel.filters.value)
    }

    @Test
    fun `toggleShowFilters flips visibility`() {
        assertFalse(viewModel.showFilters.value)
        viewModel.toggleShowFilters()
        assertTrue(viewModel.showFilters.value)
        viewModel.toggleShowFilters()
        assertFalse(viewModel.showFilters.value)
    }

    // ── Discovery suggestions ───────────────────────────────────────────

    @Test
    fun `empty query loads discovery suggestions`() = runTest(mainDispatcher) {
        val suggestion = com.raulshma.jellyplay.core.model.MediaItem(
            id = "s1", name = "Fav Movie", mediaType = MediaType.MOVIE,
        )
        coEvery { mediaRepository.getSearchSuggestions(any()) } returns Result.success(
            SearchResult(listOf(suggestion), 1, 0)
        )
        // Recreate so the init-time suggestion load picks up the stub.
        viewModel = SearchViewModel(
            mediaRepository, userDataMutator, imageUrlProvider, seerrRepository, seerrRequestDelegate,
            mediaSearchEngine, offlineRepository, searchFiltersStore,
        )

        // Warm the flow; the empty initial query triggers loadDiscoverySuggestions().
        backgroundScope.launch { viewModel.suggestions.collect { } }
        advanceUntilIdle()

        assertEquals(listOf(suggestion), viewModel.suggestions.value)
    }

    @Test
    fun `typing a query clears suggestions`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.suggestions.collect { } }
        advanceUntilIdle()

        viewModel.search("matrix")
        advanceUntilIdle()

        assertTrue(viewModel.suggestions.value.isEmpty())
    }

    @Test
    fun `search empty query clears seerr results error and offline results`() = runTest(mainDispatcher) {
        viewModel.search("matrix")
        advanceUntilIdle()

        viewModel.search("")
        advanceUntilIdle()

        assertTrue(viewModel.seerrResults.value.isEmpty())
        assertFalse(viewModel.seerrSearchError.value)
        assertTrue(viewModel.offlineResults.value.isEmpty())
    }

    // ── Genres / tags loading + retry ───────────────────────────────────

    @Test
    fun `loadGenres publishes genres on success`() = runTest(mainDispatcher) {
        val genres = listOf(Genre(id = "1", name = "Action"), Genre(id = "2", name = "Comedy"))
        coEvery { mediaRepository.getGenres(any()) } returns Result.success(genres)

        viewModel = SearchViewModel(
            mediaRepository, userDataMutator, imageUrlProvider, seerrRepository, seerrRequestDelegate,
            mediaSearchEngine, offlineRepository, searchFiltersStore,
        )
        backgroundScope.launch { viewModel.genres.collect { } }
        advanceUntilIdle()

        assertEquals(genres, viewModel.genres.value)
    }

    @Test
    fun `loadGenres retries once after a transient failure`() = runTest(mainDispatcher) {
        val genres = listOf(Genre(id = "1", name = "Action"))
        coEvery { mediaRepository.getGenres(any()) } returnsMany listOf(
            Result.failure(RuntimeException("blip")),
            Result.success(genres),
        )

        viewModel = SearchViewModel(
            mediaRepository, userDataMutator, imageUrlProvider, seerrRepository, seerrRequestDelegate,
            mediaSearchEngine, offlineRepository, searchFiltersStore,
        )
        backgroundScope.launch { viewModel.genres.collect { } }
        advanceUntilIdle()

        assertEquals(genres, viewModel.genres.value)
        coVerify(atLeast = 2) { mediaRepository.getGenres(any()) }
    }

    @Test
    fun `loadTags publishes tags on success`() = runTest(mainDispatcher) {
        val tags = listOf("fav", "4k")
        coEvery { mediaRepository.getTags(any(), any(), any()) } returns Result.success(tags)

        viewModel = SearchViewModel(
            mediaRepository, userDataMutator, imageUrlProvider, seerrRepository, seerrRequestDelegate,
            mediaSearchEngine, offlineRepository, searchFiltersStore,
        )
        backgroundScope.launch { viewModel.tags.collect { } }
        advanceUntilIdle()

        assertEquals(tags, viewModel.tags.value)
    }

    // ── Seerr connectivity / search-enable flags ───────────────────────

    @Test
    fun `isSeerrConnected reflects serverUrl presence`() = runTest(mainDispatcher) {
        every { seerrRepository.getPreferences() } returns flowOf(
            SeerrPreferences(serverUrl = "https://seerr.example")
        )
        viewModel = SearchViewModel(
            mediaRepository, userDataMutator, imageUrlProvider, seerrRepository, seerrRequestDelegate,
            mediaSearchEngine, offlineRepository, searchFiltersStore,
        )
        backgroundScope.launch { viewModel.isSeerrConnected.collect { } }
        advanceUntilIdle()

        assertTrue(viewModel.isSeerrConnected.value)
    }

    @Test
    fun `isSeerrSearchEnabled reflects searchEnabled preference`() = runTest(mainDispatcher) {
        every { seerrRepository.getPreferences() } returns flowOf(
            SeerrPreferences(serverUrl = "https://seerr.example", searchEnabled = true)
        )
        viewModel = SearchViewModel(
            mediaRepository, userDataMutator, imageUrlProvider, seerrRepository, seerrRequestDelegate,
            mediaSearchEngine, offlineRepository, searchFiltersStore,
        )
        backgroundScope.launch { viewModel.isSeerrSearchEnabled.collect { } }
        advanceUntilIdle()

        assertTrue(viewModel.isSeerrSearchEnabled.value)
    }

    // ── Seerr search pipeline ──────────────────────────────────────────

    @Test
    fun `seerr search publishes up to 10 results when connected and enabled`() = runTest(mainDispatcher) {
        val items = (1..12).map { SeerrSearchItem(id = it, title = "Item $it") }
        every { seerrRepository.getPreferences() } returns flowOf(
            SeerrPreferences(serverUrl = "https://seerr.example", searchEnabled = true)
        )
        coEvery { mediaSearchEngine.isSeerrSearchAvailable() } returns true
        coEvery { seerrRepository.search(any(), any()) } returns Result.success(
            SeerrSearchResponse(results = items, totalResults = items.size)
        )
        viewModel = SearchViewModel(
            mediaRepository, userDataMutator, imageUrlProvider, seerrRepository, seerrRequestDelegate,
            mediaSearchEngine, offlineRepository, searchFiltersStore,
        )
        // searchSeerr()/searchOffline() are launched from the pagedResults
        // pipeline, so that flow must be collected for the search to run. The
        // collector must be a FOREGROUND child of the test scope: under
        // kotlinx-coroutines 1.11, advanceUntilIdle() skips background-scope
        // tasks once no foreground work remains, so a backgroundScope collector
        // would never see its debounce fire.
        val pagedJob = launch { viewModel.pagedResults.collect { } }
        try {
            viewModel.search("matrix")
            advanceUntilIdle()

            assertEquals(10, viewModel.seerrResults.value.size)
            assertFalse(viewModel.seerrSearchError.value)
        } finally {
            pagedJob.cancel()
        }
    }

    @Test
    fun `seerr search sets error flag on repository failure`() = runTest(mainDispatcher) {
        every { seerrRepository.getPreferences() } returns flowOf(
            SeerrPreferences(serverUrl = "https://seerr.example", searchEnabled = true)
        )
        coEvery { mediaSearchEngine.isSeerrSearchAvailable() } returns true
        coEvery { seerrRepository.search(any(), any()) } returns Result.failure(RuntimeException("500"))
        viewModel = SearchViewModel(
            mediaRepository, userDataMutator, imageUrlProvider, seerrRepository, seerrRequestDelegate,
            mediaSearchEngine, offlineRepository, searchFiltersStore,
        )
        val pagedJob = launch { viewModel.pagedResults.collect { } }
        try {
            viewModel.search("matrix")
            advanceUntilIdle()

            assertTrue(viewModel.seerrSearchError.value)
            assertTrue(viewModel.seerrResults.value.isEmpty())
        } finally {
            pagedJob.cancel()
        }
    }

    @Test
    fun `seerr search no-op when not connected`() = runTest(mainDispatcher) {
        every { seerrRepository.getPreferences() } returns flowOf(SeerrPreferences())
        viewModel = SearchViewModel(
            mediaRepository, userDataMutator, imageUrlProvider, seerrRepository, seerrRequestDelegate,
            mediaSearchEngine, offlineRepository, searchFiltersStore,
        )
        val pagedJob = launch { viewModel.pagedResults.collect { } }
        try {
            viewModel.search("matrix")
            advanceUntilIdle()

            coVerify(exactly = 0) { seerrRepository.search(any(), any()) }
            assertTrue(viewModel.seerrResults.value.isEmpty())
            assertFalse(viewModel.seerrSearchError.value)
        } finally {
            pagedJob.cancel()
        }
    }

    @Test
    fun `retrySeerrSearch re-runs search for the current query`() = runTest(mainDispatcher) {
        every { seerrRepository.getPreferences() } returns flowOf(
            SeerrPreferences(serverUrl = "https://seerr.example", searchEnabled = true)
        )
        coEvery { mediaSearchEngine.isSeerrSearchAvailable() } returns true
        coEvery { seerrRepository.search(any(), any()) } returns Result.success(
            SeerrSearchResponse(results = listOf(SeerrSearchItem(id = 1, title = "X")))
        )
        viewModel = SearchViewModel(
            mediaRepository, userDataMutator, imageUrlProvider, seerrRepository, seerrRequestDelegate,
            mediaSearchEngine, offlineRepository, searchFiltersStore,
        )
        val pagedJob = launch { viewModel.pagedResults.collect { } }
        try {
            viewModel.search("matrix")
            advanceUntilIdle()
            viewModel.retrySeerrSearch()
            advanceUntilIdle()

            assertEquals(1, viewModel.seerrResults.value.size)
            assertFalse(viewModel.seerrSearchError.value)
        } finally {
            pagedJob.cancel()
        }
    }

    @Test
    fun `retrySeerrSearch is a no-op for a blank query`() = runTest(mainDispatcher) {
        viewModel.retrySeerrSearch()
        advanceUntilIdle()

        coVerify(exactly = 0) { seerrRepository.search(any(), any()) }
    }

    // ── Offline search ─────────────────────────────────────────────────

    @Test
    fun `search publishes offline results alongside paged results`() = runTest(mainDispatcher) {
        val offline = listOf(
            OfflineMediaItem(id = "o1", name = "Offline Movie", mediaType = MediaType.MOVIE),
        )
        coEvery { offlineRepository.searchOffline(any(), any()) } returns offline
        viewModel = SearchViewModel(
            mediaRepository, userDataMutator, imageUrlProvider, seerrRepository, seerrRequestDelegate,
            mediaSearchEngine, offlineRepository, searchFiltersStore,
        )
        val pagedJob = launch { viewModel.pagedResults.collect { } }
        try {
            viewModel.search("offline")
            advanceUntilIdle()

            assertEquals(offline, viewModel.offlineResults.value)
        } finally {
            pagedJob.cancel()
        }
    }

    @Test
    fun `search swallows offline failures and clears offline results`() = runTest(mainDispatcher) {
        coEvery { offlineRepository.searchOffline(any(), any()) } throws RuntimeException("db locked")
        viewModel = SearchViewModel(
            mediaRepository, userDataMutator, imageUrlProvider, seerrRepository, seerrRequestDelegate,
            mediaSearchEngine, offlineRepository, searchFiltersStore,
        )
        val pagedJob = launch { viewModel.pagedResults.collect { } }
        try {
            viewModel.search("offline")
            advanceUntilIdle()

            assertTrue(viewModel.offlineResults.value.isEmpty())
        } finally {
            pagedJob.cancel()
        }
    }

    // ── Search history mutations ───────────────────────────────────────

    @Test
    fun `deleteHistoryItem delegates to the search engine`() = runTest(mainDispatcher) {
        viewModel.deleteHistoryItem(42L)
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaSearchEngine.deleteHistoryItem(42L) }
    }

    @Test
    fun `clearHistory delegates to the search engine`() = runTest(mainDispatcher) {
        viewModel.clearHistory()
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaSearchEngine.clearHistory() }
    }

    @Test
    fun `search history exposes whatever the engine's recentHistory produces`() = runTest(mainDispatcher) {
        // The user-keying and hide-preference gating live in the engine (see
        // MediaSearchEngineTest); the VM only mirrors the flow into state.
        val history = listOf(SearchHistoryItem(id = 1L, query = "matrix", searchedAt = 0L))
        every { mediaSearchEngine.recentHistory() } returns flowOf(history)

        viewModel = SearchViewModel(
            mediaRepository, userDataMutator, imageUrlProvider, seerrRepository, seerrRequestDelegate,
            mediaSearchEngine, offlineRepository, searchFiltersStore,
        )
        backgroundScope.launch { viewModel.searchHistory.collect { } }
        advanceUntilIdle()

        assertEquals(history, viewModel.searchHistory.value)
    }

    // ── URL helpers ────────────────────────────────────────────────────

    @Test
    fun `getImageUrl delegates to ImageUrlProvider`() {
        every { imageUrlProvider.getImageUrl("item-1", any()) } returns "https://img/item-1"
        assertEquals("https://img/item-1", viewModel.getImageUrl("item-1"))
    }

    @Test
    fun `getSeerrPosterUrl builds a url for a non-null path`() {
        val url = viewModel.getSeerrPosterUrl("/abc.jpg")
        assertNotNull(url)
        assertTrue(url!!.contains("/abc.jpg"))
    }

    @Test
    fun `getSeerrPosterUrl returns null for a null path`() {
        assertNull(viewModel.getSeerrPosterUrl(null))
    }

    // ── Seerr request delegation (SeerrRequestStateHolder) ─────────────

    @Test
    fun `requestSeerrMedia success sets requestResult success`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.seerrSnapshot.collect { } }
        coEvery {
            seerrRequestDelegate.requestMedia(
                mediaType = any(), tmdbId = any(), seasons = any(),
                serverId = any(), profileId = any(), rootFolder = any(), tags = any(),
            )
        } returns Result.success(mockk(relaxed = true))

        viewModel.requestSeerrMedia(SeerrSearchItem(id = 123, mediaType = "movie"))
        advanceUntilIdle()

        assertEquals(true, viewModel.seerrSnapshot.value.requestResult?.success)
    }

    @Test
    fun `requestSeerrMedia failure surfaces the error message`() = runTest(mainDispatcher) {
        coEvery {
            seerrRequestDelegate.requestMedia(
                mediaType = any(), tmdbId = any(), seasons = any(),
                serverId = any(), profileId = any(), rootFolder = any(), tags = any(),
            )
        } returns Result.failure(RuntimeException("denied"))

        backgroundScope.launch { viewModel.seerrSnapshot.collect { } }
        viewModel.requestSeerrMedia(SeerrSearchItem(id = 123, mediaType = "movie"))
        advanceUntilIdle()

        val result = viewModel.seerrSnapshot.value.requestResult!!
        // Failure path sets the error message; success stays null (not false).
        assertEquals("denied", result.error)
    }

    @Test
    fun `clearRequestResult nulls the exposed result`() = runTest(mainDispatcher) {
        coEvery {
            seerrRequestDelegate.requestMedia(
                mediaType = any(), tmdbId = any(), seasons = any(),
                serverId = any(), profileId = any(), rootFolder = any(), tags = any(),
            )
        } returns Result.success(mockk(relaxed = true))
        backgroundScope.launch { viewModel.seerrSnapshot.collect { } }
        viewModel.requestSeerrMedia(SeerrSearchItem(id = 123, mediaType = "movie"))
        advanceUntilIdle()
        assertNotNull(viewModel.seerrSnapshot.value.requestResult)

        viewModel.clearRequestResult()
        advanceUntilIdle()

        assertNull(viewModel.seerrSnapshot.value.requestResult)
    }

    @Test
    fun `loadSeerrServiceDetails folds sonarr servers for tv`() = runTest(mainDispatcher) {
        val sonarr = SeerrSonarrServiceDetail(id = 1, name = "Sonarr")
        coEvery { seerrRequestDelegate.fetchServiceDetails("tv") } returns SeerrServiceDetailsResult(
            sonarrServers = listOf(sonarr),
        )
        backgroundScope.launch { viewModel.seerrSnapshot.collect { } }

        viewModel.loadSeerrServiceDetails("tv")
        advanceUntilIdle()

        assertEquals(listOf(sonarr), viewModel.seerrSnapshot.value.sonarrServers)
        assertFalse(viewModel.seerrSnapshot.value.isLoadingServices)
    }

    @Test
    fun `loadSeerrServiceDetails folds radarr servers for movie`() = runTest(mainDispatcher) {
        val radarr = SeerrRadarrServiceDetail(id = 2, name = "Radarr")
        coEvery { seerrRequestDelegate.fetchServiceDetails("movie") } returns SeerrServiceDetailsResult(
            radarrServers = listOf(radarr),
        )
        backgroundScope.launch { viewModel.seerrSnapshot.collect { } }

        viewModel.loadSeerrServiceDetails("movie")
        advanceUntilIdle()

        assertEquals(listOf(radarr), viewModel.seerrSnapshot.value.radarrServers)
    }

    @Test
    fun `loadTvSeasons populates tvSeasons from delegate`() = runTest(mainDispatcher) {
        val tvDetails = SeerrTvDetails(
            id = 123,
            seasons = listOf(SeerrSeason(seasonNumber = 1, name = "Season 1")),
        )
        coEvery { seerrRequestDelegate.fetchTvDetails(123) } returns tvDetails
        backgroundScope.launch { viewModel.seerrSnapshot.collect { } }

        viewModel.loadTvSeasons(123)
        advanceUntilIdle()

        assertEquals(listOf(SeerrSeason(seasonNumber = 1, name = "Season 1")), viewModel.seerrSnapshot.value.tvSeasons)
        assertEquals(false, viewModel.seerrSnapshot.value.tvIsAnime)
    }

    @Test
    fun `loadTvSeasons flags anime shows via tmdb keyword`() = runTest(mainDispatcher) {
        val tvDetails = SeerrTvDetails(
            id = 123,
            seasons = listOf(SeerrSeason(seasonNumber = 1, name = "Season 1")),
            keywords = listOf(SeerrKeyword(id = 210024, name = "anime")),
        )
        coEvery { seerrRequestDelegate.fetchTvDetails(123) } returns tvDetails
        backgroundScope.launch { viewModel.seerrSnapshot.collect { } }

        viewModel.loadTvSeasons(123)
        advanceUntilIdle()

        assertEquals(true, viewModel.seerrSnapshot.value.tvIsAnime)
    }

    @Test
    fun `prefetchSeerrDetails invokes onDone after prefetch`() = runTest(mainDispatcher) {
        var called = false
        viewModel.prefetchSeerrDetails(123, "movie") { called = true }
        advanceUntilIdle()

        assertTrue(called)
        coVerify(exactly = 1) { seerrRequestDelegate.prefetchDetails(123, "movie") }
    }

}
