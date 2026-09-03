package com.raulshma.jellyplay.feature.search

import com.raulshma.jellyplay.core.data.download.MediaDownloadActions
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.search.MediaSearchEngine
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.util.FilterCodec
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.search.SearchFiltersStore
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [SearchViewModel]'s filter-persistence and quick-action wiring — the
 * public surface NOT exercised by [SearchViewModelTest] /
 * [SearchViewModelHistoryTest]:
 *
 * 1. Init restores the persisted filter blob from [SearchFiltersStore]
 *    (single-key JSON decoded with the shared lenient [FilterCodec]); a
 *    corrupt or forward-incompatible blob falls back to the default filter
 *    set (never blocks search), and a null blob is a no-op.
 * 2. Every filter mutation ([SearchViewModel.toggleMediaType],
 *    [SearchViewModel.setSortBy], [SearchViewModel.setPlayedStatus],
 *    [SearchViewModel.updateFilters]) writes the new snapshot back to the
 *    store; [SearchViewModel.clearFilters] clears the stored blob.
 * 3. A persist write failure is swallowed — the in-memory filter session is
 *    never disrupted by a DataStore hiccup.
 * 4. Quick actions delegate: [SearchViewModel.markItemPlayed] →
 *    [UserDataMutator.setPlayed] (silent-mode default — containers untouched),
 *    [SearchViewModel.downloadItem] →
 *    [MediaDownloadActions.downloadAndReport] with the host's open-detail
 *    callback, [SearchViewModel.removeItemDownload] →
 *    [MediaDownloadActions.removeDownload], and the `downloadedIds` exposure
 *    is the actions' own flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelFilterPersistenceTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (SearchViewModelTest pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var userDataMutator: UserDataMutator
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var seerrRepository: SeerrRepository
    private lateinit var seerrRequestDelegate: SeerrRequestDelegate
    private lateinit var mediaSearchEngine: MediaSearchEngine
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var searchFiltersStore: SearchFiltersStore
    private lateinit var mediaDownloadActions: MediaDownloadActions

    /** Backs [SearchFiltersStore.searchFiltersJson]; reseated per test. */
    private val persistedJson = MutableStateFlow<String?>(null)
    private val downloadedIds = MutableStateFlow<Set<String>>(emptySet())

    private lateinit var viewModel: SearchViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        userDataMutator = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        seerrRepository = mockk(relaxed = true)
        seerrRequestDelegate = mockk(relaxed = true)
        mediaSearchEngine = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        searchFiltersStore = mockk(relaxed = true)
        mediaDownloadActions = mockk(relaxed = true)

        every { mediaSearchEngine.debounceMs } returns 300L
        every { mediaSearchEngine.recentHistory() } returns flowOf(emptyList())
        coEvery { mediaSearchEngine.isSeerrSearchAvailable() } returns false
        every { searchFiltersStore.searchFiltersJson } returns persistedJson
        every { mediaDownloadActions.downloadedIds } returns downloadedIds
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

    // ── Persisted-filter restoration ─────────────────────────────────────

    @Test
    fun `init restores a persisted filter blob into the filters state`() = runTest(mainDispatcher) {
        val persisted = LibraryFilters(
            mediaTypes = listOf(MediaType.SERIES),
            genres = listOf("Comedy"),
            years = listOf(2021),
            sortBy = SortOption.SORT_NAME,
            playedStatus = PlayedStatus.UNPLAYED,
            minRating = 3.5f,
        )
        persistedJson.value = FilterCodec.encodeToString(persisted)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(persisted, viewModel.filters.value)
    }

    @Test
    fun `a corrupt persisted blob falls back to the default filter set`() = runTest(mainDispatcher) {
        persistedJson.value = "{ this is not json"

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(LibraryFilters(), viewModel.filters.value)
    }

    @Test
    fun `a null blob keeps the default filter set`() = runTest(mainDispatcher) {
        persistedJson.value = null

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(LibraryFilters(), viewModel.filters.value)
    }

    // ── Mutation → persistence round-trips ───────────────────────────────

    @Test
    fun `setSortBy updates the filter and persists the snapshot`() = runTest(mainDispatcher) {
        val persisted = slot<String>()
        coEvery { searchFiltersStore.setSearchFilters(capture(persisted)) } returns Unit

        viewModel.setSortBy(SortOption.RATING)
        advanceUntilIdle()

        assertEquals(SortOption.RATING, viewModel.filters.value.sortBy)
        coVerify(exactly = 1) { searchFiltersStore.setSearchFilters(any()) }
        assertEquals(SortOption.RATING, FilterCodec.decodeFromString<LibraryFilters>(persisted.captured).sortBy)
    }

    @Test
    fun `setPlayedStatus updates the filter and persists the snapshot`() = runTest(mainDispatcher) {
        val persisted = slot<String>()
        coEvery { searchFiltersStore.setSearchFilters(capture(persisted)) } returns Unit

        viewModel.setPlayedStatus(PlayedStatus.PLAYED)
        advanceUntilIdle()

        assertEquals(PlayedStatus.PLAYED, viewModel.filters.value.playedStatus)
        assertEquals(
            PlayedStatus.PLAYED,
            FilterCodec.decodeFromString<LibraryFilters>(persisted.captured).playedStatus,
        )
    }

    @Test
    fun `toggleMediaType persists the new filter snapshot`() = runTest(mainDispatcher) {
        val persisted = slot<String>()
        coEvery { searchFiltersStore.setSearchFilters(capture(persisted)) } returns Unit

        viewModel.toggleMediaType(MediaType.MOVIE)
        advanceUntilIdle()

        assertEquals(
            listOf(MediaType.MOVIE),
            FilterCodec.decodeFromString<LibraryFilters>(persisted.captured).mediaTypes,
        )
    }

    @Test
    fun `updateFilters persists the replaced filter set`() = runTest(mainDispatcher) {
        val filters = LibraryFilters(genres = listOf("Action"), years = listOf(1999))
        val persisted = slot<String>()
        coEvery { searchFiltersStore.setSearchFilters(capture(persisted)) } returns Unit

        viewModel.updateFilters(filters)
        advanceUntilIdle()

        assertEquals(filters, FilterCodec.decodeFromString<LibraryFilters>(persisted.captured))
    }

    @Test
    fun `clearFilters resets state and clears the stored blob`() = runTest(mainDispatcher) {
        viewModel.updateFilters(LibraryFilters(genres = listOf("Action")))
        advanceUntilIdle()

        viewModel.clearFilters()
        advanceUntilIdle()

        assertEquals(LibraryFilters(), viewModel.filters.value)
        coVerify(exactly = 1) { searchFiltersStore.clearSearchFilters() }
    }

    @Test
    fun `a persist write failure is swallowed and the in-memory filter wins`() = runTest(mainDispatcher) {
        coEvery { searchFiltersStore.setSearchFilters(any()) } throws RuntimeException("disk full")

        viewModel.toggleMediaType(MediaType.SERIES)
        advanceUntilIdle()

        // In-memory session survives the DataStore hiccup; no crash.
        assertEquals(listOf(MediaType.SERIES), viewModel.filters.value.mediaTypes)
    }

    // ── Quick actions ────────────────────────────────────────────────────

    @Test
    fun `markItemPlayed delegates to the silent mutator`() = runTest(mainDispatcher) {
        val item = com.raulshma.jellyplay.core.model.MediaItem(
            id = "m1", name = "Movie", mediaType = MediaType.MOVIE,
        )

        viewModel.markItemPlayed(item, played = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { userDataMutator.setPlayed("m1", true) }
    }

    @Test
    fun `downloadItem routes through downloadAndReport with the open-detail callback`() = runTest(mainDispatcher) {
        val item = com.raulshma.jellyplay.core.model.MediaItem(
            id = "series-9", name = "Show", mediaType = MediaType.SERIES,
        )
        var routedTo: String? = null
        coEvery { mediaDownloadActions.downloadAndReport(any(), any()) } coAnswers {
            secondArg<(String) -> Unit>().invoke("series-9")
        }

        viewModel.downloadItem(item) { routedTo = it }
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaDownloadActions.downloadAndReport(item, any()) }
        assertEquals("series-9", routedTo)
    }

    @Test
    fun `removeItemDownload delegates to the actions' local delete`() {
        val item = com.raulshma.jellyplay.core.model.MediaItem(
            id = "m2", name = "Movie", mediaType = MediaType.MOVIE,
        )

        viewModel.removeItemDownload(item)

        io.mockk.verify(exactly = 1) { mediaDownloadActions.removeDownload(item) }
    }

    @Test
    fun `downloadedIds exposes the actions' flow`() {
        downloadedIds.value = setOf("m1", "series-9")

        assertEquals(setOf("m1", "series-9"), viewModel.downloadedIds.value)
    }
}
