package com.raulshma.jellyplay.feature.search

import com.raulshma.jellyplay.core.data.download.MediaDownloadActions
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.search.MediaSearchEngine
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.search.SearchFiltersStore
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import io.mockk.clearMocks
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Query-state and debounce gaps in [SearchViewModel] NOT pinned by
 * [SearchViewModelTest] / [SearchViewModelFilterPersistenceTest] /
 * [SearchViewModelHistoryTest]:
 *
 * 1. [SearchViewModel.search] mirrors the query into the public `query`
 *    property (the screen's field state + the seerr retry read it).
 * 2. Discovery suggestions reload when the query is cleared back to blank —
 *    the `suggestionsLoaded` latch resets on the blank transition, so a fresh
 *    random selection is fetched (and a second blank→blank no-op re-set does
 *    not re-fetch while already blank).
 * 3. A failed discovery-suggestion fetch degrades to an empty list (the
 *    `getOrElse` fallback), never a crashed state.
 * 4. The debounce+distinctUntilChanged pipeline: identical query re-sets within
 *    the debounce window coalesce into ONE side-search execution (StateFlow
 *    conflation upstream of `distinctUntilChanged`).
 * 5. The offline side-search stale-job guard: a newer query cancels the older
 *    query's in-flight scan, and only the newest query's results land in
 *    [SearchViewModel.offlineResults].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelQueryStateGapsTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (SearchViewModelTest pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private val userDataMutator: UserDataMutator = mockk(relaxed = true)
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)
    private val seerrRepository: SeerrRepository = mockk(relaxed = true)
    private val seerrRequestDelegate: SeerrRequestDelegate = mockk(relaxed = true)
    private val mediaSearchEngine: MediaSearchEngine = mockk(relaxed = true)
    private val offlineRepository: OfflineRepository = mockk(relaxed = true)
    private val searchFiltersStore: SearchFiltersStore = mockk(relaxed = true)
    private val mediaDownloadActions: MediaDownloadActions = mockk(relaxed = true)

    private lateinit var viewModel: SearchViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)

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

        viewModel = createViewModel()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SearchViewModel(
        mediaRepository,
        userDataMutator,
        imageUrlProvider,
        seerrRepository,
        seerrRequestDelegate,
        mediaSearchEngine,
        offlineRepository,
        searchFiltersStore,
        mediaDownloadActions,
    )

    @Test
    fun `search mirrors the query into the public query property`() {
        viewModel.search("matrix")
        assertEquals("matrix", viewModel.query)

        viewModel.search("  ")
        assertEquals("  ", viewModel.query)
    }

    @Test
    fun `clearing the query back to blank reloads discovery suggestions`() = runTest(mainDispatcher) {
        // The setUp VM's init-block suggestion fetch is still queued on the
        // shared test scheduler. Run it against the default stub and drop the
        // recorded call so the returnsMany sequence and the exact-count
        // verifications below measure only the VM under test — otherwise the
        // leftover fetch consumes the first stub answer and shifts everything.
        advanceUntilIdle()
        clearMocks(
            mediaRepository,
            answers = false,
            recordedCalls = true,
            childMocks = false,
            verificationMarks = false,
            exclusionRules = false,
        )

        val first = com.raulshma.jellyplay.core.model.MediaItem(
            id = "s1", name = "First Pick", mediaType = MediaType.MOVIE,
        )
        val second = com.raulshma.jellyplay.core.model.MediaItem(
            id = "s2", name = "Second Pick", mediaType = MediaType.MOVIE,
        )
        coEvery { mediaRepository.getSearchSuggestions(any()) } returnsMany listOf(
            Result.success(SearchResult(listOf(first), 1, 0)),
            Result.success(SearchResult(listOf(second), 1, 0)),
        )
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.suggestions.collect { } }
        advanceUntilIdle()
        assertEquals(listOf(first), viewModel.suggestions.value)

        // Typing hides the suggestions…
        viewModel.search("matrix")
        advanceUntilIdle()
        assertTrue(viewModel.suggestions.value.isEmpty())

        // …and clearing back to blank fetches a FRESH selection (the latch
        // reset — the second stub answer proves the second fetch happened).
        viewModel.search("")
        advanceUntilIdle()
        assertEquals(listOf(second), viewModel.suggestions.value)

        // A no-op re-set of the already-blank query must not re-fetch again
        // (StateFlow conflation — no new emission reaches the reload
        // collector). search("") clears the list synchronously, and without a
        // new debounced emission nothing re-populates it.
        coVerify(exactly = 2) { mediaRepository.getSearchSuggestions(any()) }
        viewModel.search("")
        advanceUntilIdle()
        assertTrue(viewModel.suggestions.value.isEmpty())
        coVerify(exactly = 2) { mediaRepository.getSearchSuggestions(any()) }
    }

    @Test
    fun `a failed discovery fetch degrades to empty suggestions`() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getSearchSuggestions(any()) } returns
            Result.failure(RuntimeException("network down"))
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.suggestions.collect { } }
        advanceUntilIdle()

        assertTrue(viewModel.suggestions.value.isEmpty())
    }

    @Test
    fun `identical query re-sets within the debounce window run the seerr search once`() =
        runTest(mainDispatcher) {
            coEvery { mediaSearchEngine.isSeerrSearchAvailable() } returns true
            coEvery { seerrRepository.search(any(), any()) } returns Result.success(
                SeerrSearchResponse(results = listOf(SeerrSearchItem(id = 1, title = "X")))
            )
            viewModel = createViewModel()
            val pagedJob = launch { viewModel.pagedResults.collect { } }
            try {
                viewModel.search("matrix")
                advanceTimeBy(299)
                // Same value: StateFlow conflation upstream of distinctUntilChanged
                // means no new debounced emission — the restart-due timer keeps
                // waiting on the original emission.
                viewModel.search("matrix")
                advanceUntilIdle()

                coVerify(exactly = 1) { seerrRepository.search(any(), any()) }
            } finally {
                pagedJob.cancel()
            }
        }

    @Test
    fun `a newer query cancels the in-flight offline scan and its results never land`() =
        runTest(mainDispatcher) {
            val staleItem = OfflineMediaItem(id = "stale", name = "Stale", mediaType = MediaType.MOVIE)
            val freshItem = OfflineMediaItem(id = "fresh", name = "Fresh", mediaType = MediaType.MOVIE)
            // The stale scan parks on a gate: it would publish ONLY if its job
            // were still alive when the gate opens.
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            coEvery { offlineRepository.searchOffline("stale", any()) } coAnswers {
                gate.await()
                listOf(staleItem)
            }
            coEvery { offlineRepository.searchOffline("fresh", any()) } returns listOf(freshItem)
            viewModel = createViewModel()
            val pagedJob = launch { viewModel.pagedResults.collect { } }
            try {
                viewModel.search("stale")
                advanceUntilIdle() // debounce fires; the stale scan suspends on the gate
                assertTrue(viewModel.offlineResults.value.isEmpty())

                viewModel.search("fresh")
                advanceUntilIdle() // fresh scan completes; the stale job is cancelled

                assertEquals(listOf(freshItem), viewModel.offlineResults.value)

                // Opening the gate changes nothing — the stale scan is dead and
                // its results can never overwrite the fresh ones.
                gate.complete(Unit)
                advanceUntilIdle()
                assertEquals(listOf(freshItem), viewModel.offlineResults.value)
                coVerify(exactly = 1) { offlineRepository.searchOffline("stale", any()) }
                coVerify(exactly = 1) { offlineRepository.searchOffline("fresh", any()) }
            } finally {
                pagedJob.cancel()
            }
        }
}
