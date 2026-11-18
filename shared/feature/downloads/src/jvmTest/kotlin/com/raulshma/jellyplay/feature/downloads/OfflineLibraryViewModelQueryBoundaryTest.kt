package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.testfixtures.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule

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

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var offlineRepository: OfflineRepository
    private lateinit var viewModel: OfflineLibraryViewModel
    private lateinit var libraryFlow: MutableStateFlow<List<OfflineMediaItem>>

    @BeforeTest
    fun setUp() {
        offlineRepository = mockk(relaxed = true)
        libraryFlow = MutableStateFlow(emptyList())
        every { offlineRepository.getOfflineLibrary() } returns libraryFlow
        viewModel = OfflineLibraryViewModel(offlineRepository, mockk(relaxed = true))
    }

    private fun TestScope.collectAndSettle(): kotlinx.coroutines.Job {
        val job = launch { viewModel.offlineLibrary.collect {} }
        advanceUntilIdle()
        return job
    }

    /**
     * Waits for [OfflineLibraryViewModel.offlineLibrary] to show [expectedIds].
     * The filter pipeline hops to Dispatchers.Default and back (a real
     * thread), and the virtual scheduler cannot see the returning
     * continuation — so poll with real time until the projection lands, the
     * same idiom the music suites' awaitGenerated uses. A fixed sleep budget
     * loses that race on a loaded machine.
     */
    private fun TestScope.awaitLibrary(expectedIds: List<String>) {
        val deadline = System.currentTimeMillis() + 5_000
        while (viewModel.offlineLibrary.value.map { it.id } != expectedIds &&
            System.currentTimeMillis() < deadline
        ) {
            advanceUntilIdle()
            Thread.sleep(10)
        }
        advanceUntilIdle()
        assertEquals(expectedIds, viewModel.offlineLibrary.value.map { it.id })
    }

    @Test
    fun a_two_char_query_filters_inclusively() = runTest(mainDispatcherRule.testDispatcher) {
        libraryFlow.value = listOf(
            OfflineMediaItem(id = "hit", name = "Dune", mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE),
            OfflineMediaItem(id = "miss", name = "Other", mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE),
        )
        val job = collectAndSettle()

        viewModel.setQuery("du")

        awaitLibrary(listOf("hit"))
        job.cancel()
    }

    @Test
    fun a_whitespace_padded_query_is_trimmed_before_matching() = runTest(mainDispatcherRule.testDispatcher) {
        libraryFlow.value = listOf(
            OfflineMediaItem(id = "hit", name = "Dune", mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE),
            OfflineMediaItem(id = "miss", name = "Other", mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE),
        )
        val job = collectAndSettle()

        viewModel.setQuery("  dune  ")

        awaitLibrary(listOf("hit"))
        job.cancel()
    }

    @Test
    fun a_whitespace_only_query_does_not_filter() = runTest(mainDispatcherRule.testDispatcher) {
        libraryFlow.value = listOf(
            OfflineMediaItem(id = "a", name = "Dune", mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE),
            OfflineMediaItem(id = "b", name = "Other", mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE),
        )
        val job = collectAndSettle()

        viewModel.setQuery("   ")

        // Trimmed below the 2-char gate → everything stays visible.
        awaitLibrary(listOf("a", "b"))
        job.cancel()
    }

    @Test
    fun the_raw_query_state_holds_the_untrimmed_input() = runTest(mainDispatcherRule.testDispatcher) {
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
