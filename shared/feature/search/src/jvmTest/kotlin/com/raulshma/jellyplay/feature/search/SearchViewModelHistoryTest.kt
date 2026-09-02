package com.raulshma.jellyplay.feature.search

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.search.MediaSearchEngine
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.search.SearchFiltersStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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

/**
 * Pins [SearchViewModel.onSearchResultsShown]'s delegation to
 * [MediaSearchEngine.recordHistory]: only non-blank confirmed queries reach
 * the engine. The engine-side policy (≥2 chars, hide-history preference,
 * active user) is covered by MediaSearchEngineTest in :core:data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelHistoryTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module. runTest shares this scheduler so the
    // viewModelScope work advances with advanceUntilIdle.
    private val mainDispatcher = StandardTestDispatcher()

    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)
    private val seerrRepository: SeerrRepository = mockk(relaxed = true)
    private val seerrRequestDelegate: SeerrRequestDelegate = mockk(relaxed = true)
    private val mediaSearchEngine: MediaSearchEngine = mockk(relaxed = true)
    private val offlineRepository: OfflineRepository = mockk(relaxed = true)
    private val searchFiltersStore: SearchFiltersStore = mockk(relaxed = true)
    private val mediaDownloadActions: com.raulshma.jellyplay.core.data.download.MediaDownloadActions = mockk(relaxed = true)

    private lateinit var viewModel: SearchViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { mediaSearchEngine.debounceMs } returns 300L
        every { mediaSearchEngine.recentHistory() } returns flowOf(emptyList())
        coEvery { mediaSearchEngine.isSeerrSearchAvailable() } returns false
        every { searchFiltersStore.searchFiltersJson } returns MutableStateFlow(null)
        every { seerrRepository.getPreferences() } returns flowOf(
            com.raulshma.jellyplay.core.model.seerr.SeerrPreferences()
        )
        // Stub the init-time repository calls so relaxed-mock defaults don't
        // break the List casts in loadGenres()/loadTags()/loadSuggestions().
        coEvery { mediaRepository.getGenres(any()) } returns Result.success(emptyList())
        coEvery { mediaRepository.getTags(any(), any(), any()) } returns Result.success(emptyList())
        coEvery { mediaRepository.getSearchSuggestions(any()) } returns Result.success(
            com.raulshma.jellyplay.core.model.SearchResult(emptyList(), 0, 0)
        )
        coEvery { offlineRepository.searchOffline(any(), any()) } returns emptyList()

        viewModel = SearchViewModel(
            mediaRepository,
            mockk(relaxed = true),
            imageUrlProvider,
            seerrRepository,
            seerrRequestDelegate,
            mediaSearchEngine,
            offlineRepository,
            searchFiltersStore, mediaDownloadActions,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onSearchResultsShown persists query when it has at least 2 chars`() = runTest(mainDispatcher) {
        viewModel.onSearchResultsShown("matrix")
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaSearchEngine.recordHistory("matrix", jellyfinHadResults = true) }
    }

    @Test
    fun `onSearchResultsShown skips blank queries`() = runTest(mainDispatcher) {
        viewModel.onSearchResultsShown("")
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaSearchEngine.recordHistory(any(), any()) }
    }
}
