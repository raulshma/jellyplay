package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the [OfflineLibraryViewModel] query boundary semantics NOT exercised by
 * [OfflineLibraryViewModelTest]:
 *
 * 1. A query is trimmed before matching — whitespace-padded input still
 *    filters (and a whitespace-only query degrades to "no filter" once
 *    trimmed below the 2-char gate).
 * 2. The 2-char gate is inclusive: exactly two significant chars filter,
 *    one does not (the `< 2` boundary).
 * 3. The raw [OfflineLibraryViewModel.query]/[OfflineLibraryViewModel.sort]/
 *    [OfflineLibraryViewModel.filter] state holders hold what was set
 *    (untrimmed — trimming is applied at filter time only).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineLibraryViewModelQueryBoundaryTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (OfflineLibraryViewModelTest pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var offlineRepository: OfflineRepository
    private lateinit var viewModel: OfflineLibraryViewModel
    private lateinit var libraryFlow: MutableStateFlow<List<OfflineMediaItem>>

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        offlineRepository = mockk(relaxed = true)
        libraryFlow = MutableStateFlow(emptyList())
        every { offlineRepository.getOfflineLibrary() } returns libraryFlow
        viewModel = OfflineLibraryViewModel(offlineRepository, mockk(relaxed = true))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.collectAndSettle(): kotlinx.coroutines.Job {
        val job = launch { viewModel.offlineLibrary.collect {} }
        advanceUntilIdle()
        repeat(5) {
            Thread.sleep(20)
            advanceUntilIdle()
        }
        return job
    }

    private fun TestScope.settle() {
        advanceUntilIdle()
        repeat(5) {
            Thread.sleep(20)
            advanceUntilIdle()
        }
    }

    @Test
    fun a_two_char_query_filters_inclusively() = runTest(mainDispatcher) {
        libraryFlow.value = listOf(
            OfflineMediaItem(id = "hit", name = "Dune", mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE),
            OfflineMediaItem(id = "miss", name = "Other", mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE),
        )
        val job = collectAndSettle()

        viewModel.setQuery("du")
        settle()

        assertEquals(listOf("hit"), viewModel.offlineLibrary.value.map { it.id })
        job.cancel()
    }

    @Test
    fun a_whitespace_padded_query_is_trimmed_before_matching() = runTest(mainDispatcher) {
        libraryFlow.value = listOf(
            OfflineMediaItem(id = "hit", name = "Dune", mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE),
            OfflineMediaItem(id = "miss", name = "Other", mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE),
        )
        val job = collectAndSettle()

        viewModel.setQuery("  dune  ")
        settle()

        assertEquals(listOf("hit"), viewModel.offlineLibrary.value.map { it.id })
        job.cancel()
    }

    @Test
    fun a_whitespace_only_query_does_not_filter() = runTest(mainDispatcher) {
        libraryFlow.value = listOf(
            OfflineMediaItem(id = "a", name = "Dune", mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE),
            OfflineMediaItem(id = "b", name = "Other", mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE),
        )
        val job = collectAndSettle()

        viewModel.setQuery("   ")
        settle()

        // Trimmed below the 2-char gate → everything stays visible.
        assertEquals(listOf("a", "b"), viewModel.offlineLibrary.value.map { it.id })
        job.cancel()
    }

    @Test
    fun the_raw_query_state_holds_the_untrimmed_input() = runTest(mainDispatcher) {
        viewModel.setQuery("  matrix ")
        assertEquals("  matrix ", viewModel.query.value)
    }

    @Test
    fun sort_and_filter_setters_hold_their_values() {
        viewModel.setSort(OfflineLibrarySort.SIZE)
        viewModel.setFilter(OfflineLibraryFilter.MUSIC)
        assertEquals(OfflineLibrarySort.SIZE, viewModel.sort.value)
        assertEquals(OfflineLibraryFilter.MUSIC, viewModel.filter.value)
    }
}
