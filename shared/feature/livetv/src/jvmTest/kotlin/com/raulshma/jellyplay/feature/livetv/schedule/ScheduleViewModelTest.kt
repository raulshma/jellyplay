package com.raulshma.jellyplay.feature.livetv.schedule

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.DvrTimer
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
class ScheduleViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var viewModel: ScheduleViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        coEvery { mediaRepository.getRecordings(any(), any()) } returns Result.success(emptyList())
        coEvery { mediaRepository.getTimers(any(), any()) } returns Result.success(emptyList())
        viewModel = ScheduleViewModel(mediaRepository, imageUrlProvider)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_fetches_active_recordings_and_upcoming_timers() = runTest(mainDispatcher) {
        advanceUntilIdle()
        // Active: isInProgress = true, no limit
        coVerify { mediaRepository.getRecordings(limit = null, isInProgress = true) }
        // Upcoming: isActive=false, isScheduled=true
        coVerify { mediaRepository.getTimers(isActive = false, isScheduled = true) }
    }

    @Test
    fun timers_grouped_by_their_start_date() = runTest(mainDispatcher) {
        val timers = listOf(
            DvrTimer(id = "t1", programId = "p1", programName = "A", channelId = "c1", channelName = "Ch1",
                startDate = "2026-07-14T10:00:00Z", endDate = "2026-07-14T11:00:00Z"),
            DvrTimer(id = "t2", programId = "p2", programName = "B", channelId = "c2", channelName = "Ch2",
                startDate = "2026-07-15T20:00:00Z", endDate = "2026-07-15T21:00:00Z"),
        )
        coEvery { mediaRepository.getTimers(any(), any()) } returns Result.success(timers)

        viewModel.load()
        advanceUntilIdle()

        val groups = viewModel.uiState.value.upcomingGroups
        assertEquals(2, groups.size)
    }

    @Test
    fun cancelTimer_calls_repo_cancelTimer_then_reloads() = runTest(mainDispatcher) {
        coEvery { mediaRepository.cancelTimer("t1") } returns Result.success(Unit)
        viewModel.cancelTimer("t1")
        advanceUntilIdle()

        coVerify(atLeast = 1) { mediaRepository.cancelTimer("t1") }
        assertNull(viewModel.uiState.value.selectedTimer)
    }

    @Test
    fun load_surfaces_error_when_getTimers_fails() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getTimers(any(), any()) } returns Result.failure(RuntimeException("nope"))
        viewModel.load()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error?.contains("nope") == true)
    }
}
