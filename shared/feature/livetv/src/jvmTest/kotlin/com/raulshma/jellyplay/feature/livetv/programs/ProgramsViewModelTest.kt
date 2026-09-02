package com.raulshma.jellyplay.feature.livetv.programs

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.ProgramFilters
import com.raulshma.jellyplay.feature.livetv.components.RecordDialogState
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramsViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var viewModel: ProgramsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        coEvery { mediaRepository.getRecommendedPrograms(any(), any()) } returns Result.success(emptyList())
        viewModel = ProgramsViewModel(mediaRepository, imageUrlProvider)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_fires_On_Now_airing_query_and_5_category_queries() = runTest(mainDispatcher) {
        advanceUntilIdle()
        coVerify { mediaRepository.getRecommendedPrograms(match { it.isAiring == true }, any()) }
        coVerify { mediaRepository.getRecommendedPrograms(match { it.isSeries == true && it.hasAired == false }, any()) }
        coVerify { mediaRepository.getRecommendedPrograms(match { it.isMovie == true && it.hasAired == false }, any()) }
        coVerify { mediaRepository.getRecommendedPrograms(match { it.isSports == true && it.hasAired == false }, any()) }
        coVerify { mediaRepository.getRecommendedPrograms(match { it.isKids == true && it.hasAired == false }, any()) }
        coVerify { mediaRepository.getRecommendedPrograms(match { it.isNews == true && it.hasAired == false }, any()) }
    }

    @Test
    fun rows_are_built_only_for_non_empty_categories() = runTest(mainDispatcher) {
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
    fun requestRecord_opens_AwaitingChoice_dialog_for_the_program() {
        val program = sampleProgram("Late Show")
        viewModel.requestRecord(program)

        val state = viewModel.uiState.value.recordDialog
        assertTrue(state is RecordDialogState.AwaitingChoice, "Expected AwaitingChoice, got $state")
        assertEquals("Late Show", (state as RecordDialogState.AwaitingChoice).program.name)
    }

    @Test
    fun recordOnce_calls_createTimer_with_the_program_id() = runTest(mainDispatcher) {
        coEvery { mediaRepository.createTimer("prog-1") } returns Result.success(Unit)
        val program = sampleProgram(id = "prog-1")
        viewModel.recordOnce(program)
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.createTimer("prog-1") }
    }

    @Test
    fun recordSeries_calls_createSeriesTimer_with_the_program_id() = runTest(mainDispatcher) {
        coEvery { mediaRepository.createSeriesTimer("prog-1") } returns Result.success(Unit)
        viewModel.recordSeries(sampleProgram(id = "prog-1"))
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.createSeriesTimer("prog-1") }
    }

    @Test
    fun cancelTimer_forwards_the_program_timerId() = runTest(mainDispatcher) {
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
