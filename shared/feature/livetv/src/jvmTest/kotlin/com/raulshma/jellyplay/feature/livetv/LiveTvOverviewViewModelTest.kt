package com.raulshma.jellyplay.feature.livetv

import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.LiveTvRecording
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

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTvOverviewViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: LiveTvRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk()
        stubAllQueriesSucceed(recordings = 2, activeRecordings = 1, timers = 3, seriesTimers = 4)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Happy-path answer for all four badge queries with the given sizes. */
    private fun stubAllQueriesSucceed(recordings: Int, activeRecordings: Int, timers: Int, seriesTimers: Int) {
        coEvery { mediaRepository.getRecordings(limit = 1) } returns
            Result.success(List(recordings) { recording(it) })
        coEvery { mediaRepository.getRecordings(isInProgress = true) } returns
            Result.success(List(activeRecordings) { recording(it) })
        coEvery { mediaRepository.getTimers(isActive = false, isScheduled = true) } returns
            Result.success(List(timers) { timer(it) })
        coEvery { mediaRepository.getSeriesTimers() } returns
            Result.success(List(seriesTimers) { seriesTimer(it) })
    }

    private fun stubQueryFails(query: BadgeQuery) {
        when (query) {
            BadgeQuery.RECORDINGS ->
                coEvery { mediaRepository.getRecordings(limit = 1) } returns Result.failure(RuntimeException("offline"))
            BadgeQuery.ACTIVE_RECORDINGS ->
                coEvery { mediaRepository.getRecordings(isInProgress = true) } returns Result.failure(RuntimeException("offline"))
            BadgeQuery.UPCOMING ->
                coEvery { mediaRepository.getTimers(isActive = false, isScheduled = true) } returns Result.failure(RuntimeException("offline"))
            BadgeQuery.SERIES ->
                coEvery { mediaRepository.getSeriesTimers() } returns Result.failure(RuntimeException("offline"))
        }
    }

    private enum class BadgeQuery { RECORDINGS, ACTIVE_RECORDINGS, UPCOMING, SERIES }

    private fun newViewModel() = LiveTvOverviewViewModel(mediaRepository)

    private fun recording(id: Int) = LiveTvRecording(id = "rec-$id", name = "Recording $id")

    private fun timer(id: Int) = DvrTimer(
        id = "t$id",
        programId = "p$id",
        programName = "Program $id",
        channelId = "c$id",
        channelName = "Channel $id",
    )

    private fun seriesTimer(id: Int) = DvrSeriesTimer(id = "st$id", name = "Series $id")

    @Test
    fun init_refresh_queries_all_four_counts_in_parallel_and_populates_the_badges() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        // init{ refresh() } has not run yet — the seed state is all-zero.
        assertEquals(LiveTvBadges(), viewModel.badges.value)

        advanceUntilIdle()

        assertEquals(
            LiveTvBadges(recordings = 2, activeRecordings = 1, upcoming = 3, series = 4),
            viewModel.badges.value,
        )
        // Exact query shapes (Schedule tab reuses the same two of them).
        coVerify { mediaRepository.getRecordings(limit = 1) }
        coVerify { mediaRepository.getRecordings(isInProgress = true) }
        coVerify { mediaRepository.getTimers(isActive = false, isScheduled = true) }
        coVerify { mediaRepository.getSeriesTimers() }
    }

    @Test
    fun each_single_repo_failure_degrades_only_its_own_badge_to_zero() = runTest(mainDispatcher) {
        val expectedWhenHealthy = mapOf(
            BadgeQuery.RECORDINGS to 2,
            BadgeQuery.ACTIVE_RECORDINGS to 1,
            BadgeQuery.UPCOMING to 3,
            BadgeQuery.SERIES to 4,
        )

        for (failing in BadgeQuery.entries) {
            stubAllQueriesSucceed(recordings = 2, activeRecordings = 1, timers = 3, seriesTimers = 4)
            stubQueryFails(failing)

            val viewModel = newViewModel()
            advanceUntilIdle()

            val badges = viewModel.badges.value
            assertEquals(
                0,
                when (failing) {
                    BadgeQuery.RECORDINGS -> badges.recordings
                    BadgeQuery.ACTIVE_RECORDINGS -> badges.activeRecordings
                    BadgeQuery.UPCOMING -> badges.upcoming
                    BadgeQuery.SERIES -> badges.series
                },
                "Expected the ${failing.name} badge to degrade to 0 on repo failure",
            )
            // Every other badge keeps its count — getOrDefault(emptyList()) is per query.
            assertEquals(
                expectedWhenHealthy.filterKeys { it != failing },
                mapOf(
                    BadgeQuery.RECORDINGS to badges.recordings,
                    BadgeQuery.ACTIVE_RECORDINGS to badges.activeRecordings,
                    BadgeQuery.UPCOMING to badges.upcoming,
                    BadgeQuery.SERIES to badges.series,
                ).filterKeys { it != failing },
            )
        }
    }

    @Test
    fun all_repo_failures_degrade_every_badge_to_zero_instead_of_throwing() = runTest(mainDispatcher) {
        stubQueryFails(BadgeQuery.RECORDINGS)
        stubQueryFails(BadgeQuery.ACTIVE_RECORDINGS)
        stubQueryFails(BadgeQuery.UPCOMING)
        stubQueryFails(BadgeQuery.SERIES)

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(LiveTvBadges(), viewModel.badges.value)
    }

    @Test
    fun refresh_recomputes_every_badge_from_the_current_repo_data() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        assertEquals(
            LiveTvBadges(recordings = 2, activeRecordings = 1, upcoming = 3, series = 4),
            viewModel.badges.value,
        )

        stubAllQueriesSucceed(recordings = 0, activeRecordings = 7, timers = 0, seriesTimers = 9)
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(
            LiveTvBadges(recordings = 0, activeRecordings = 7, upcoming = 0, series = 9),
            viewModel.badges.value,
        )
    }
}
