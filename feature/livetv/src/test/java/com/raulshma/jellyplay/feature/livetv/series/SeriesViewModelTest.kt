package com.raulshma.jellyplay.feature.livetv.series

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var viewModel: SeriesViewModel

    @Before
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        coEvery { mediaRepository.getSeriesTimers(any()) } returns Result.success(emptyList())
        viewModel = SeriesViewModel(mediaRepository)
    }

    @Test
    fun `getSeriesTimers called with SortName on init`() = runTest {
        advanceUntilIdle()
        coVerify { mediaRepository.getSeriesTimers(sortBy = "SortName") }
    }

    @Test
    fun `showDetail exposes the selected series timer`() = runTest {
        advanceUntilIdle()
        val timer = DvrSeriesTimer(id = "st-1", name = " nightly News")
        viewModel.showDetail(timer)
        assertEquals(timer, viewModel.uiState.value.selectedTimer)
    }

    @Test
    fun `cancelSeries calls cancelSeriesTimer and reloads`() = runTest {
        coEvery { mediaRepository.cancelSeriesTimer("st-1") } returns Result.success(Unit)
        viewModel.cancelSeries("st-1")
        advanceUntilIdle()

        coVerify(atLeast = 1) { mediaRepository.cancelSeriesTimer("st-1") }
        assertNull(viewModel.uiState.value.selectedTimer)
    }

    @Test
    fun `load surfaces error when getSeriesTimers fails`() = runTest {
        coEvery { mediaRepository.getSeriesTimers(any()) } returns Result.failure(RuntimeException("boom"))
        viewModel.load()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error?.contains("boom") == true)
    }
}
