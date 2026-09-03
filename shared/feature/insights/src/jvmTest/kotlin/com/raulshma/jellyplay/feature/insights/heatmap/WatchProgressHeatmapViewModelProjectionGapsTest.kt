package com.raulshma.jellyplay.feature.insights.heatmap

import com.raulshma.jellyplay.core.data.repository.DailyWatchActivity
import com.raulshma.jellyplay.core.data.repository.HeatmapFilter
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.WatchHistoryRepository
import com.raulshma.jellyplay.core.model.PlaybackReportingDetail
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * Projection-detail gaps in [WatchProgressHeatmapViewModel] NOT pinned by
 * [WatchProgressHeatmapViewModelTest] (which asserts streak math, the plugin
 * gate and the resolution cache, but only shape-checks the day-header label
 * and never asserts the day-query or year-query parameters):
 *
 * 1. [WatchProgressHeatmapViewModel.selectDay] formats the header label with
 *    the exact `EEEE, MMMM d, yyyy` pattern (pinned to Locale.ENGLISH for
 *    determinism — the JVM path resolves the DEFAULT locale at runtime).
 * 2. The day query forwards the ISO date string AND the currently active
 *    [HeatmapFilter] to [WatchHistoryRepository.getItemsForDay].
 * 3. The loaded [DailyWatchActivity] list is mirrored into
 *    [WatchProgressHeatmapUiState.dailyActivities] verbatim.
 * 4. [HeatmapEvent.SetYear] retains the active filter in the follow-up load.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchProgressHeatmapViewModelProjectionGapsTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to this module (WatchProgressHeatmapViewModelTest pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var watchHistoryRepository: WatchHistoryRepository
    private lateinit var mediaRepository: MediaRepository
    private lateinit var playbackRepository: PlaybackRepository

    private lateinit var playbackReportingStatus: MutableStateFlow<PlaybackReportingStatus>

    /** (dateStr, filter) pairs passed to getItemsForDay, most-recent-last. */
    private val dayQueries = mutableListOf<Pair<String, HeatmapFilter>>()

    /** (year, filter) pairs passed to getDailyActivity, most-recent-last. */
    private val yearQueries = mutableListOf<Pair<Int, HeatmapFilter>>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        watchHistoryRepository = mockk()
        mediaRepository = mockk()
        playbackRepository = mockk()
        playbackReportingStatus = MutableStateFlow(PlaybackReportingStatus.AVAILABLE)
        every { watchHistoryRepository.playbackReportingStatus } returns playbackReportingStatus
        coEvery { watchHistoryRepository.refreshPlaybackReportingStatus() } just Runs
        coEvery { watchHistoryRepository.getMinimumActivityDate() } returns null
        coEvery {
            watchHistoryRepository.getItemsForDay(any(), any())
        } coAnswers {
            dayQueries += firstArg<String>() to secondArg<HeatmapFilter>()
            emptyList()
        }
        coEvery {
            watchHistoryRepository.getDailyActivity(any(), any())
        } coAnswers {
            yearQueries += firstArg<Int>() to secondArg<HeatmapFilter>()
            emptyList()
        }
        every { playbackRepository.getImageUrl(any(), any(), any()) } returns "http://img"
        // Day-selection resolution: a failed detail lookup simply skips the
        // cache (the label under test lives in SelectedDayInfo itself).
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns
            Result.failure(RuntimeException("no detail"))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(): WatchProgressHeatmapViewModel = WatchProgressHeatmapViewModel(
        watchHistoryRepository = watchHistoryRepository,
        mediaRepository = mediaRepository,
        playbackRepository = playbackRepository,
    )

    private fun session(itemId: String, name: String) = PlaybackReportingDetail(
        itemId = itemId,
        name = name,
        duration = 30L * 60_000_000_000L,
    )

    @Test
    fun selectDay_formatsTheHeaderWithTheEEEE_MMMM_d_yyyyPattern() = runTest(mainDispatcher) {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            coEvery { watchHistoryRepository.getItemsForDay(any(), any()) } returns
                listOf(session("a", "Session A"))
            val viewModel = newViewModel()
            advanceUntilIdle()

            viewModel.onEvent(HeatmapEvent.SelectDay(LocalDate.of(2026, 7, 14)))
            advanceUntilIdle()

            // 2026-07-14 is a Tuesday.
            assertEquals(
                "Tuesday, July 14, 2026",
                viewModel.uiState.value.selectedDay?.dateLabel,
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun selectDay_forwardsTheIsoDateAndTheActiveFilterToTheDayQuery() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        // Activate a non-default filter first: the day query must follow it.
        viewModel.onEvent(HeatmapEvent.SetFilter(HeatmapFilter.VIDEO))
        advanceUntilIdle()
        viewModel.onEvent(HeatmapEvent.SelectDay(LocalDate.of(2026, 3, 5)))
        advanceUntilIdle()

        assertEquals(
            listOf("2026-03-05" to HeatmapFilter.VIDEO),
            dayQueries,
        )
    }

    @Test
    fun loadedActivities_mirrorVerbatimIntoState() = runTest(mainDispatcher) {
        val activities = listOf(
            DailyWatchActivity(date = "2026-01-02", value = 120L),
            DailyWatchActivity(date = "2026-01-01", value = 45L),
        )
        coEvery { watchHistoryRepository.getDailyActivity(any(), any()) } returns activities

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(activities, viewModel.uiState.value.dailyActivities)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun setYear_retainsTheActiveFilterInTheFollowUpLoad() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HeatmapEvent.SetFilter(HeatmapFilter.MUSIC))
        advanceUntilIdle()
        viewModel.onEvent(HeatmapEvent.SetYear(2019))
        advanceUntilIdle()

        assertEquals(2019, viewModel.uiState.value.year)
        assertEquals(HeatmapFilter.MUSIC, viewModel.uiState.value.filter)
        // The year load kept the audio filter.
        assertEquals(2019 to HeatmapFilter.MUSIC, yearQueries.last())
    }
}
