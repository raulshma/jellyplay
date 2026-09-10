package com.raulshma.jellyplay.feature.livetv.series

import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: LiveTvRepository
    private lateinit var viewModel: SeriesViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        coEvery { mediaRepository.getSeriesTimers(any()) } returns Result.success(emptyList())
        viewModel = SeriesViewModel(mediaRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun getSeriesTimers_called_with_SortName_on_init() = runTest(mainDispatcher) {
        advanceUntilIdle()
        coVerify { mediaRepository.getSeriesTimers(sortBy = "SortName") }
    }

    @Test
    fun showDetail_exposes_the_selected_series_timer() = runTest(mainDispatcher) {
        advanceUntilIdle()
        val timer = DvrSeriesTimer(id = "st-1", name = " nightly News")
        viewModel.showDetail(timer)
        assertEquals(timer, viewModel.uiState.value.selectedTimer)
    }

    @Test
    fun cancelSeries_calls_cancelSeriesTimer_and_reloads() = runTest(mainDispatcher) {
        coEvery { mediaRepository.cancelSeriesTimer("st-1") } returns Result.success(Unit)
        viewModel.cancelSeries("st-1")
        advanceUntilIdle()

        coVerify(atLeast = 1) { mediaRepository.cancelSeriesTimer("st-1") }
        assertNull(viewModel.uiState.value.selectedTimer)
    }

    @Test
    fun load_surfaces_error_when_getSeriesTimers_fails() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getSeriesTimers(any()) } returns Result.failure(RuntimeException("boom"))
        viewModel.load()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error?.contains("boom") == true)
    }

    // ── Detail sheet round trip ──────────────────────────────────────────────

    @Test
    fun dismissDetail_clears_the_selected_series_timer() = runTest(mainDispatcher) {
        val timer = DvrSeriesTimer(id = "st-1", name = "Nightly News")
        viewModel.showDetail(timer)
        assertEquals(timer, viewModel.uiState.value.selectedTimer)

        viewModel.dismissDetail()

        assertNull(viewModel.uiState.value.selectedTimer)
    }

    // ── Cancel paths ─────────────────────────────────────────────────────────

    @Test
    fun cancelSeries_failure_surfaces_the_error_and_keeps_the_sheet_open() = runTest(mainDispatcher) {
        coEvery { mediaRepository.cancelSeriesTimer("st-1") } returns Result.failure(RuntimeException("denied"))
        val timer = DvrSeriesTimer(id = "st-1", name = "Nightly News")
        viewModel.showDetail(timer)

        viewModel.cancelSeries("st-1")
        advanceUntilIdle()

        assertEquals("denied", viewModel.uiState.value.error)
        assertEquals(timer, viewModel.uiState.value.selectedTimer)
    }

    @Test
    fun cancelSeries_success_clears_the_selection_and_reloads_the_list() = runTest(mainDispatcher) {
        val timers = listOf(DvrSeriesTimer(id = "st-1", name = "Nightly News"))
        coEvery { mediaRepository.getSeriesTimers(any()) } returns Result.success(timers)
        coEvery { mediaRepository.cancelSeriesTimer("st-1") } returns Result.success(Unit)

        viewModel.cancelSeries("st-1")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedTimer)
        assertEquals(timers, viewModel.uiState.value.seriesTimers)
        assertFalse(viewModel.uiState.value.isLoading)
        // init load + post-cancel reload.
        coVerify(exactly = 2) { mediaRepository.getSeriesTimers(any()) }
    }
}
