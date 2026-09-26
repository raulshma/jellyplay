package com.raulshma.jellyplay.feature.search

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.search.MediaSearchEngine
import com.raulshma.jellyplay.core.data.search.MediaSideSearchState
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.seerr.SeerrServiceDetailsResult
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.search.SearchFiltersStore
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
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
 * persistence): filter toggling, the paged search pipeline, side-search
 * mirroring (the Seerr + offline rows ride [MediaSearchEngine.sideSearch]),
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

    /** Search choreography (history, side rows, debounce) delegates here. */
    private val mediaSearchEngine: MediaSearchEngine = mockk(relaxed = true)
    private lateinit var searchFiltersStore: SearchFiltersStore
    private val quickDownloadActions: com.raulshma.jellyplay.core.data.download.QuickDownloadActions = mockk(relaxed = true)

    private lateinit var viewModel: SearchViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        seerrRepository = mockk(relaxed = true)
        seerrRequestDelegate = mockk(relaxed = true)
        searchFiltersStore = mockk(relaxed = true)

        every { mediaSearchEngine.debounceMs } returns 300L
        every { mediaSearchEngine.recentHistory() } returns flowOf(emptyList())
        every { mediaSearchEngine.sideSearch(any()) } returns flowOf()
        every { searchFiltersStore.searchFiltersJson } returns MutableStateFlow(null)
        every { seerrRepository.getPreferences() } returns flowOf(SeerrPreferences())
        coEvery { mediaRepository.getGenres(any()) } returns Result.success(emptyList())
        coEvery { mediaRepository.getTags(any(), any(), any()) } returns Result.success(emptyList())
        coEvery { mediaRepository.getSearchSuggestions(any()) } returns Result.success(
            SearchResult(emptyList(), 0, 0)
        )

        viewModel = SearchViewModel(
            mediaRepository,
            userDataMutator,
            imageUrlProvider,
            seerrRepository,
            seerrRequestDelegate,
            mediaSearchEngine,
            searchFiltersStore, quickDownloadActions,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Filters ────────────────────────────────────────────────────────

    @Test
    fun `toggleMediaType adds then removes a media type`() {
        viewModel.onEvent(SearchUiEvent.ToggleMediaType(MediaType.MOVIE))
        assertEquals(listOf(MediaType.MOVIE), viewModel.filters.value.mediaTypes)

        viewModel.onEvent(SearchUiEvent.ToggleMediaType(MediaType.SERIES))
        assertEquals(listOf(MediaType.MOVIE, MediaType.SERIES), viewModel.filters.value.mediaTypes)

        viewModel.onEvent(SearchUiEvent.ToggleMediaType(MediaType.MOVIE)) // remove
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

        viewModel.onEvent(SearchUiEvent.UpdateFilters(filters))

        assertEquals(filters, viewModel.filters.value)
    }

    @Test
    fun `clearFilters resets to an empty SearchFilters`() {
        viewModel.onEvent(SearchUiEvent.ToggleMediaType(MediaType.MOVIE))
        viewModel.onEvent(SearchUiEvent.UpdateFilters(LibraryFilters(genres = listOf("Action"))))

        viewModel.onEvent(SearchUiEvent.ClearFilters)

        assertEquals(LibraryFilters(), viewModel.filters.value)
    }

    @Test
    fun `toggleShowFilters flips visibility`() {
        assertFalse(viewModel.showFilters.value)
        viewModel.onEvent(SearchUiEvent.ToggleFilters)
        assertTrue(viewModel.showFilters.value)
        viewModel.onEvent(SearchUiEvent.ToggleFilters)
        assertFalse(viewModel.showFilters.value)
    }

    @Test
    fun `onEvent funnel routes the pure-forwarding intents`() = runTest(mainDispatcher) {
        viewModel.onEvent(SearchUiEvent.ToggleFilters)
        assertTrue(viewModel.showFilters.value)

        viewModel.onEvent(SearchUiEvent.Search("matrix"))
        assertEquals("matrix", viewModel.query)

        viewModel.onEvent(SearchUiEvent.SetSortBy(SortOption.RATING))
        advanceUntilIdle()
        assertEquals(SortOption.RATING, viewModel.filters.value.sortBy)
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
            mediaSearchEngine, searchFiltersStore, quickDownloadActions,
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

        viewModel.onEvent(SearchUiEvent.Search("matrix"))
        advanceUntilIdle()

        assertTrue(viewModel.suggestions.value.isEmpty())
    }

    @Test
    fun `search empty query clears seerr results error and offline results`() = runTest(mainDispatcher) {
        viewModel.onEvent(SearchUiEvent.Search("matrix"))
        advanceUntilIdle()

        viewModel.onEvent(SearchUiEvent.Search(""))
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
            mediaSearchEngine, searchFiltersStore, quickDownloadActions,
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
            mediaSearchEngine, searchFiltersStore, quickDownloadActions,
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
            mediaSearchEngine, searchFiltersStore, quickDownloadActions,
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
            mediaSearchEngine, searchFiltersStore, quickDownloadActions,
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
            mediaSearchEngine, searchFiltersStore, quickDownloadActions,
        )
        backgroundScope.launch { viewModel.isSeerrSearchEnabled.collect { } }
        advanceUntilIdle()

        assertTrue(viewModel.isSeerrSearchEnabled.value)
    }

    // ── Side-search mirroring (the engine's sideSearch seam) ───────────

    @Test
    fun `side search mirrors engine seerr and offline rows into the row states`() = runTest(mainDispatcher) {
        val seerrItems = (1..10).map { SeerrSearchItem(id = it, title = "Item $it") }
        val offline = listOf(
            OfflineMediaItem(id = "o1", name = "Offline Movie", mediaType = MediaType.MOVIE),
        )
        every { mediaSearchEngine.sideSearch(any()) } returns flowOf(
            MediaSideSearchState(query = "matrix", seerr = seerrItems, seerrError = false, offline = offline)
        )
        viewModel = SearchViewModel(
            mediaRepository, userDataMutator, imageUrlProvider, seerrRepository, seerrRequestDelegate,
            mediaSearchEngine, searchFiltersStore, quickDownloadActions,
        )
        advanceUntilIdle()

        assertEquals(seerrItems, viewModel.seerrResults.value)
        assertFalse(viewModel.seerrSearchError.value)
        assertEquals(offline, viewModel.offlineResults.value)
    }

    @Test
    fun `side search mirrors the engine error flag into seerrSearchError`() = runTest(mainDispatcher) {
        every { mediaSearchEngine.sideSearch(any()) } returns flowOf(
            MediaSideSearchState(query = "matrix", seerr = emptyList(), seerrError = true, offline = emptyList())
        )
        viewModel = SearchViewModel(
            mediaRepository, userDataMutator, imageUrlProvider, seerrRepository, seerrRequestDelegate,
            mediaSearchEngine, searchFiltersStore, quickDownloadActions,
        )
        advanceUntilIdle()

        assertTrue(viewModel.seerrSearchError.value)
        assertTrue(viewModel.seerrResults.value.isEmpty())
    }

    @Test
    fun `retrySeerrSearch re-kicks the engine round for the current query`() = runTest(mainDispatcher) {
        // A stateful stub that consumes the queries argument — the retry is a
        // re-emission of the unchanged query into the engine, so the stub must
        // observe the query flow to react to it.
        var failing = true
        every { mediaSearchEngine.sideSearch(any()) } answers {
            val queries: Flow<String> = firstArg()
            queries.mapLatest { q ->
                if (failing) {
                    MediaSideSearchState(q, emptyList(), seerrError = true, offline = emptyList())
                } else {
                    MediaSideSearchState(q, listOf(SeerrSearchItem(id = 1, title = "X")), seerrError = false, offline = emptyList())
                }
            }
        }
        viewModel = SearchViewModel(
            mediaRepository, userDataMutator, imageUrlProvider, seerrRepository, seerrRequestDelegate,
            mediaSearchEngine, searchFiltersStore, quickDownloadActions,
        )
        viewModel.onEvent(SearchUiEvent.Search("matrix"))
        advanceUntilIdle()
        assertTrue(viewModel.seerrSearchError.value)

        // The retry re-kicks the query flow; the engine's cancel-and-replace
        // re-runs the round and the recovered state lands.
        failing = false
        viewModel.onEvent(SearchUiEvent.RetrySeerrSearch)
        advanceUntilIdle()

        assertEquals(1, viewModel.seerrResults.value.size)
        assertFalse(viewModel.seerrSearchError.value)
    }

    @Test
    fun `retrySeerrSearch is a no-op for a blank query`() = runTest(mainDispatcher) {
        // Drain the setUp VM's queued init work against the setUp stub first —
        // otherwise its collector would also consume the answers stub below
        // and pollute the emission count.
        advanceUntilIdle()
        var emissions = 0
        every { mediaSearchEngine.sideSearch(any()) } answers {
            val queries: Flow<String> = firstArg()
            queries.mapLatest { q ->
                emissions++
                MediaSideSearchState(q, emptyList(), seerrError = false, offline = emptyList())
            }
        }
        viewModel = SearchViewModel(
            mediaRepository, userDataMutator, imageUrlProvider, seerrRepository, seerrRequestDelegate,
            mediaSearchEngine, searchFiltersStore, quickDownloadActions,
        )
        viewModel.onEvent(SearchUiEvent.RetrySeerrSearch)
        advanceUntilIdle()

        // Only the initial blank emission ever reached the engine — a blank
        // retry never re-kicks the round.
        assertEquals(1, emissions)
    }

    // ── Deferred refresh (user-data changes while off-screen) ───────────────

    /** Driven by the deferred-refresh tests; collected by the VM for its lifetime. */
    private val userDataEvents = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)

    @Test
    fun `userData change while inactive defers the paged refresh to the next entry`() = runTest(mainDispatcher) {
        every { mediaRepository.userDataChanges } returns userDataEvents
        coEvery { mediaRepository.searchPaged(any(), any()) } returns
            flowOf(androidx.paging.PagingData.empty<com.raulshma.jellyplay.core.model.MediaItem>())
        viewModel = SearchViewModel(
            mediaRepository, userDataMutator, imageUrlProvider, seerrRepository, seerrRequestDelegate,
            mediaSearchEngine, searchFiltersStore, quickDownloadActions,
        )
        val pagedJob = launch { viewModel.pagedResults.collect { } }
        try {
            viewModel.onEvent(SearchUiEvent.Search("breaking"))
            advanceUntilIdle()
            coVerify(exactly = 1) { mediaRepository.searchPaged(any(), any()) }

            // A write confirmed while the screen is NOT on screen only marks stale.
            viewModel.deferredRefresher.onScreenActiveChanged(false)
            userDataEvents.emit(UserDataChange("user-1", listOf("m1")))
            advanceUntilIdle()
            coVerify(exactly = 1) { mediaRepository.searchPaged(any(), any()) }

            // Re-entry fires the single deferred regeneration.
            viewModel.deferredRefresher.onScreenActiveChanged(true)
            advanceUntilIdle()
            coVerify(exactly = 2) { mediaRepository.searchPaged(any(), any()) }
        } finally {
            pagedJob.cancel()
        }
    }

    @Test
    fun `userData change while active does not regenerate the pager`() = runTest(mainDispatcher) {
        every { mediaRepository.userDataChanges } returns userDataEvents
        coEvery { mediaRepository.searchPaged(any(), any()) } returns
            flowOf(androidx.paging.PagingData.empty<com.raulshma.jellyplay.core.model.MediaItem>())
        viewModel = SearchViewModel(
            mediaRepository, userDataMutator, imageUrlProvider, seerrRepository, seerrRequestDelegate,
            mediaSearchEngine, searchFiltersStore, quickDownloadActions,
        )
        val pagedJob = launch { viewModel.pagedResults.collect { } }
        try {
            viewModel.onEvent(SearchUiEvent.Search("breaking"))
            advanceUntilIdle()

            // Silent contract: no mid-scroll pager swap for on-screen events.
            viewModel.deferredRefresher.onScreenActiveChanged(true)
            userDataEvents.emit(UserDataChange("user-1", listOf("m1")))
            advanceUntilIdle()
            coVerify(exactly = 1) { mediaRepository.searchPaged(any(), any()) }
        } finally {
            pagedJob.cancel()
        }
    }

    // ── Search history mutations ───────────────────────────────────────

    @Test
    fun `deleteHistoryItem delegates to the search engine`() = runTest(mainDispatcher) {
        viewModel.onEvent(SearchUiEvent.DeleteSearchHistoryItem(42L))
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaSearchEngine.deleteHistoryItem(42L) }
    }

    @Test
    fun `clearHistory delegates to the search engine`() = runTest(mainDispatcher) {
        viewModel.onEvent(SearchUiEvent.ClearSearchHistory)
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
            mediaSearchEngine, searchFiltersStore, quickDownloadActions,
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

        viewModel.onEvent(SearchUiEvent.RequestSeerrMedia(SeerrSearchItem(id = 123, mediaType = "movie")))
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
        viewModel.onEvent(SearchUiEvent.RequestSeerrMedia(SeerrSearchItem(id = 123, mediaType = "movie")))
        advanceUntilIdle()

        val result = viewModel.seerrSnapshot.value.requestResult!!
        // Failure path sets the error message; success stays null (not false).
        assertEquals("denied", result.error)
    }

    @Test
    fun `dismissSeerrRequestDialog nulls the exposed result and closes the dialog`() = runTest(mainDispatcher) {
        coEvery {
            seerrRequestDelegate.requestMedia(
                mediaType = any(), tmdbId = any(), seasons = any(),
                serverId = any(), profileId = any(), rootFolder = any(), tags = any(),
            )
        } returns Result.success(mockk(relaxed = true))
        backgroundScope.launch { viewModel.seerrSnapshot.collect { } }
        viewModel.onEvent(SearchUiEvent.RequestSeerrMedia(SeerrSearchItem(id = 123, mediaType = "movie")))
        advanceUntilIdle()
        assertNotNull(viewModel.seerrSnapshot.value.requestResult)

        viewModel.onEvent(SearchUiEvent.DismissSeerrRequestDialog)
        advanceUntilIdle()

        assertNull(viewModel.seerrSnapshot.value.requestResult)
        assertNull(viewModel.seerrSnapshot.value.dialogItem)
    }

    @Test
    fun `openSeerrRequestDialog for tv folds sonarr servers and opens the dialog`() = runTest(mainDispatcher) {
        val sonarr = SeerrSonarrServiceDetail(id = 1, name = "Sonarr")
        coEvery { seerrRequestDelegate.fetchServiceDetails("tv") } returns SeerrServiceDetailsResult(
            sonarrServers = listOf(sonarr),
        )
        backgroundScope.launch { viewModel.seerrSnapshot.collect { } }

        coEvery { seerrRequestDelegate.fetchTvDetails(any()) } returns null
        viewModel.onEvent(SearchUiEvent.OpenSeerrRequestDialog(SeerrSearchItem(id = 5, mediaType = "tv")))
        advanceUntilIdle()

        assertEquals(listOf(sonarr), viewModel.seerrSnapshot.value.sonarrServers)
        assertFalse(viewModel.seerrSnapshot.value.isLoadingServices)
        assertNotNull(viewModel.seerrSnapshot.value.dialogItem)
    }

    @Test
    fun `openSeerrRequestDialog for movie folds radarr servers and opens the dialog`() = runTest(mainDispatcher) {
        val radarr = SeerrRadarrServiceDetail(id = 2, name = "Radarr")
        coEvery { seerrRequestDelegate.fetchServiceDetails("movie") } returns SeerrServiceDetailsResult(
            radarrServers = listOf(radarr),
        )
        backgroundScope.launch { viewModel.seerrSnapshot.collect { } }

        viewModel.onEvent(SearchUiEvent.OpenSeerrRequestDialog(SeerrSearchItem(id = 6, mediaType = "movie")))
        advanceUntilIdle()

        assertEquals(listOf(radarr), viewModel.seerrSnapshot.value.radarrServers)
        assertNotNull(viewModel.seerrSnapshot.value.dialogItem)
    }

    @Test
    fun `openSeerrRequestDialog for tv populates tvSeasons from delegate`() = runTest(mainDispatcher) {
        val tvDetails = SeerrTvDetails(
            id = 123,
            seasons = listOf(SeerrSeason(seasonNumber = 1, name = "Season 1")),
        )
        coEvery { seerrRequestDelegate.fetchTvDetails(123) } returns tvDetails
        backgroundScope.launch { viewModel.seerrSnapshot.collect { } }

        viewModel.onEvent(SearchUiEvent.OpenSeerrRequestDialog(SeerrSearchItem(id = 123, mediaType = "tv")))
        advanceUntilIdle()

        assertEquals(listOf(SeerrSeason(seasonNumber = 1, name = "Season 1")), viewModel.seerrSnapshot.value.tvSeasons)
        assertNotNull(viewModel.seerrSnapshot.value.dialogItem)
        assertEquals(false, viewModel.seerrSnapshot.value.tvIsAnime)
    }

    @Test
    fun `openSeerrRequestDialog for tv flags anime shows via tmdb keyword`() = runTest(mainDispatcher) {
        val tvDetails = SeerrTvDetails(
            id = 123,
            seasons = listOf(SeerrSeason(seasonNumber = 1, name = "Season 1")),
            keywords = listOf(SeerrKeyword(id = 210024, name = "anime")),
        )
        coEvery { seerrRequestDelegate.fetchTvDetails(123) } returns tvDetails
        backgroundScope.launch { viewModel.seerrSnapshot.collect { } }

        viewModel.onEvent(SearchUiEvent.OpenSeerrRequestDialog(SeerrSearchItem(id = 123, mediaType = "tv")))
        advanceUntilIdle()

        assertEquals(true, viewModel.seerrSnapshot.value.tvIsAnime)
    }

    @Test
    fun `prefetchSeerrDetails invokes onDone after prefetch`() = runTest(mainDispatcher) {
        var called = false
        viewModel.onEvent(SearchUiEvent.PrefetchSeerrDetails(123, "movie") { called = true })
        advanceUntilIdle()

        assertTrue(called)
        coVerify(exactly = 1) { seerrRequestDelegate.prefetchDetails(123, "movie") }
    }

}
