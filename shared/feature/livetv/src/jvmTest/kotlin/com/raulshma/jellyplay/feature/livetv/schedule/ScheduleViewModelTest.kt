package com.raulshma.jellyplay.feature.livetv.schedule

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.DvrTimer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

    // ── Date grouping details (locale-independent assertions) ────────────────

    private fun timer(id: String, startIso: String?, programName: String = id) = DvrTimer(
        id = id,
        programId = "p-$id",
        programName = programName,
        channelId = "c-$id",
        channelName = "Ch $id",
        startDate = startIso,
        endDate = null,
    )

    @Test
    fun timers_on_the_same_day_share_one_group_sorted_by_start_time() = runTest(mainDispatcher) {
        val late = timer("late", "2026-07-14T22:00:00Z")
        val early = timer("early", "2026-07-14T08:00:00Z")
        val nextDay = timer("next", "2026-07-15T09:00:00Z")
        // Deliberately unsorted input.
        coEvery { mediaRepository.getTimers(any(), any()) } returns
            Result.success(listOf(nextDay, late, early))

        viewModel.load()
        advanceUntilIdle()

        val groups = viewModel.uiState.value.upcomingGroups
        // Two distinct days → two groups, ordered by their earliest timer.
        assertEquals(2, groups.size)
        assertEquals(2, groups[0].timers.size)
        assertEquals(listOf("early", "late"), groups[0].timers.map { it.id })
        assertEquals(listOf("next"), groups[1].timers.map { it.id })
    }

    @Test
    fun timers_without_a_parseable_start_date_are_dropped_from_the_groups() = runTest(mainDispatcher) {
        val broken = timer("broken", "not-a-date")
        val good = timer("good", "2026-07-14T08:00:00Z")
        coEvery { mediaRepository.getTimers(any(), any()) } returns
            Result.success(listOf(broken, good))

        viewModel.load()
        advanceUntilIdle()

        val groups = viewModel.uiState.value.upcomingGroups
        assertEquals(1, groups.size)
        assertEquals(listOf("good"), groups.single().timers.map { it.id })
    }

    @Test
    fun load_populates_active_recordings() = runTest(mainDispatcher) {
        val active = listOf(
            com.raulshma.jellyplay.core.model.LiveTvRecording(id = "rec-1", name = "Live Rec"),
        )
        coEvery { mediaRepository.getRecordings(any(), any()) } returns Result.success(active)

        viewModel.load()
        advanceUntilIdle()

        assertEquals(active, viewModel.uiState.value.activeRecordings)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // ── Timer detail sheet ───────────────────────────────────────────────────

    @Test
    fun showTimerDetail_and_dismissDetail_round_trip() = runTest(mainDispatcher) {
        val timer = timer("t1", "2026-07-14T10:00:00Z")
        viewModel.showTimerDetail(timer)
        assertEquals(timer, viewModel.uiState.value.selectedTimer)

        viewModel.dismissDetail()

        assertEquals(null, viewModel.uiState.value.selectedTimer)
    }

    // ── Cancel failure path ──────────────────────────────────────────────────

    @Test
    fun cancelTimer_failure_surfaces_the_error_and_keeps_the_sheet_open() = runTest(mainDispatcher) {
        coEvery { mediaRepository.cancelTimer("t1") } returns Result.failure(RuntimeException("denied"))
        val timer = timer("t1", "2026-07-14T10:00:00Z")
        viewModel.showTimerDetail(timer)

        viewModel.cancelTimer("t1")
        advanceUntilIdle()

        assertEquals("denied", viewModel.uiState.value.error)
        assertEquals(timer, viewModel.uiState.value.selectedTimer)
    }

    // ── Image url tag quirk ──────────────────────────────────────────────────

    @Test
    fun getImageUrl_without_a_tag_returns_empty_and_with_one_delegates() {
        every { imageUrlProvider.getImageUrl("r1") } returns "http://img/r1"

        assertEquals("", viewModel.getImageUrl("r1", null))
        assertEquals("http://img/r1", viewModel.getImageUrl("r1", "tag"))
        verify(exactly = 1) { imageUrlProvider.getImageUrl("r1") }
    }
}
