package com.raulshma.jellyplay.feature.livetv.series

import com.raulshma.jellyplay.core.data.repository.MediaRepository
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
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
}
