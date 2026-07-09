package com.raulshma.jellyplay.feature.livetv.programs

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.ProgramFilters
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import com.raulshma.jellyplay.feature.livetv.components.RecordDialogState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var viewModel: ProgramsViewModel

    @Before
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        coEvery { mediaRepository.getRecommendedPrograms(any(), any()) } returns Result.success(emptyList())
        viewModel = ProgramsViewModel(mediaRepository, imageUrlProvider)
    }

    @Test
    fun `init fires On Now airing query and 5 category queries`() = runTest {
        advanceUntilIdle()
        coVerify { mediaRepository.getRecommendedPrograms(match { it.isAiring == true }, any()) }
        coVerify { mediaRepository.getRecommendedPrograms(match { it.isSeries == true && it.hasAired == false }, any()) }
        coVerify { mediaRepository.getRecommendedPrograms(match { it.isMovie == true && it.hasAired == false }, any()) }
        coVerify { mediaRepository.getRecommendedPrograms(match { it.isSports == true && it.hasAired == false }, any()) }
        coVerify { mediaRepository.getRecommendedPrograms(match { it.isKids == true && it.hasAired == false }, any()) }
        coVerify { mediaRepository.getRecommendedPrograms(match { it.isNews == true && it.hasAired == false }, any()) }
    }

    @Test
    fun `rows are built only for non-empty categories`() = runTest {
        val onNow = listOf(sampleProgram("On Air Show"))
        coEvery {
            mediaRepository.getRecommendedPrograms(match<ProgramFilters> { it.isAiring == true }, any())
        } returns Result.success(onNow)
        // every other category empty (default relaxed mock)

        viewModel.load()
        advanceUntilIdle()

        val rows = viewModel.uiState.value.rows
        assertEquals(1, rows.size)
        assertEquals("On Now", rows.first().title)
    }

    @Test
    fun `requestRecord opens AwaitingChoice dialog for the program`() {
        val program = sampleProgram("Late Show")
        viewModel.requestRecord(program)

        val state = viewModel.uiState.value.recordDialog
        assertTrue("Expected AwaitingChoice, got $state", state is RecordDialogState.AwaitingChoice)
        assertEquals("Late Show", (state as RecordDialogState.AwaitingChoice).program.name)
    }

    @Test
    fun `recordOnce calls createTimer with the program id`() = runTest {
        coEvery { mediaRepository.createTimer("prog-1") } returns Result.success(Unit)
        val program = sampleProgram(id = "prog-1")
        viewModel.recordOnce(program)
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.createTimer("prog-1") }
    }

    @Test
    fun `recordSeries calls createSeriesTimer with the program id`() = runTest {
        coEvery { mediaRepository.createSeriesTimer("prog-1") } returns Result.success(Unit)
        viewModel.recordSeries(sampleProgram(id = "prog-1"))
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.createSeriesTimer("prog-1") }
    }

    @Test
    fun `cancelTimer forwards the program timerId`() = runTest {
        coEvery { mediaRepository.cancelTimer("timer-9") } returns Result.success(Unit)
        viewModel.cancelTimer(sampleProgram(id = "prog-1", timerId = "timer-9"))
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.cancelTimer("timer-9") }
    }

    private fun sampleProgram(name: String = "Evening News", id: String = "prog-1", timerId: String? = null) =
        LiveTvProgram(
            id = id,
            name = name,
            channelId = "chan-1",
            startDate = "2026-07-01T19:00:00Z",
            endDate = "2026-07-01T19:30:00Z",
            timerId = timerId,
        )
}
