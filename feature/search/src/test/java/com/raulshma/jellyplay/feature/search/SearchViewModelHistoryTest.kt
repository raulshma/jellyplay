package com.raulshma.jellyplay.feature.search

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.search.SearchFiltersStore
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelHistoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var seerrRepository: SeerrRepository
    private lateinit var seerrRequestDelegate: SeerrRequestDelegate
    private lateinit var searchHistoryRepository: SearchHistoryRepository
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var experimentalStore: ExperimentalStore
    private lateinit var serverIdentityStore: ServerIdentityStore
    private lateinit var searchFiltersStore: SearchFiltersStore

    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        seerrRepository = mockk(relaxed = true)
        seerrRequestDelegate = mockk(relaxed = true)
        searchHistoryRepository = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        experimentalStore = mockk(relaxed = true)
        serverIdentityStore = mockk(relaxed = true)
        searchFiltersStore = mockk(relaxed = true)

        every { experimentalStore.experimental } returns MutableStateFlow(ExperimentalSlice())
        every { serverIdentityStore.activeUserId } returns flowOf("user-1")
        every { searchFiltersStore.searchFiltersJson } returns MutableStateFlow(null)
        every { seerrRepository.isConnected() } returns flowOf(false)
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
        coEvery { searchHistoryRepository.getRecent(any(), any()) } returns flowOf(emptyList())
        coEvery { offlineRepository.searchOffline(any(), any()) } returns emptyList()

        viewModel = SearchViewModel(
            mediaRepository,
            mockk(relaxed = true),
            imageUrlProvider,
            seerrRepository,
            seerrRequestDelegate,
            searchHistoryRepository,
            offlineRepository,
            experimentalStore,
            serverIdentityStore,
            searchFiltersStore,
        )
    }

    @Test
    fun `onSearchResultsShown persists query when it has at least 2 chars`() = runTest {
        viewModel.onSearchResultsShown("matrix")
        advanceUntilIdle()

        coVerify(exactly = 1) { searchHistoryRepository.saveQuery("matrix", "user-1") }
    }

    @Test
    fun `onSearchResultsShown skips queries shorter than 2 chars`() = runTest {
        // Guards against typo'd single-char queries polluting history.
        viewModel.onSearchResultsShown("a")
        advanceUntilIdle()

        coVerify(exactly = 0) { searchHistoryRepository.saveQuery(any(), any()) }
    }

    @Test
    fun `onSearchResultsShown skips blank queries`() = runTest {
        viewModel.onSearchResultsShown("")
        advanceUntilIdle()

        coVerify(exactly = 0) { searchHistoryRepository.saveQuery(any(), any()) }
    }
}
