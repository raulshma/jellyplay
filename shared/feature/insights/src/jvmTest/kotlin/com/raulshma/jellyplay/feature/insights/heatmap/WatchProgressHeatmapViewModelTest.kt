package com.raulshma.jellyplay.feature.insights.heatmap

import com.raulshma.jellyplay.core.data.repository.DailyWatchActivity
import com.raulshma.jellyplay.core.data.repository.HeatmapFilter
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.WatchHistoryRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlaybackReportingDetail
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Watch Progress heatmap ViewModel coverage (requests/downloads conveyor test
 * style, no legacy suite existed): the min-activity-date ISO-prefix parse, the
 * streak walk (gap boundaries + the today anchor), the playback-reporting
 * plugin gate, day selection with media-detail resolution (success, failure
 * fallback, and the cross-selection cache), share event toggling, and the
 * load/refresh error paths.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchProgressHeatmapViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (requests conveyor port pattern). The VM's
    // init does real work on the main dispatcher, so setMain must precede
    // construction.
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var watchHistoryRepository: WatchHistoryRepository
    private lateinit var mediaRepository: MediaRepository
    private lateinit var playbackRepository: PlaybackRepository

    /** Backing flow behind WatchHistoryRepository.playbackReportingStatus. */
    private lateinit var playbackReportingStatus: MutableStateFlow<PlaybackReportingStatus>

    /** Every getDailyActivity invocation, most-recent-last, for param asserts. */
    private val dailyActivityCalls = mutableListOf<Pair<Int, HeatmapFilter>>()

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
        coEvery { watchHistoryRepository.getItemsForDay(any(), any()) } returns emptyList()
        every { playbackRepository.getImageUrl(any(), any(), any()) } returns "http://img"
        stubDailyActivity { emptyList() }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Routes every getDailyActivity call into [dailyActivityCalls]. */
    private fun stubDailyActivity(response: () -> List<DailyWatchActivity>) {
        coEvery {
            watchHistoryRepository.getDailyActivity(any(), any())
        } answers {
            dailyActivityCalls += arg<Int>(0) to arg<HeatmapFilter>(1)
            response()
        }
    }

    private fun newViewModel(): WatchProgressHeatmapViewModel = WatchProgressHeatmapViewModel(
        watchHistoryRepository = watchHistoryRepository,
        mediaRepository = mediaRepository,
        playbackRepository = playbackRepository,
    )

    private fun activity(date: LocalDate, value: Long) =
        DailyWatchActivity(date = date.toString(), value = value)

    // ── init: plugin status + min-date parse ─────────────────────────────

    @Test
    fun init_refreshes_playback_reporting_status() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { watchHistoryRepository.refreshPlaybackReportingStatus() }
        assertTrue(viewModel.uiState.value.isPluginAvailable)
    }

    @Test
    fun plugin_status_gate_follows_the_repository_flow() = runTest(mainDispatcher) {
        playbackReportingStatus.value = PlaybackReportingStatus.UNAVAILABLE

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPluginAvailable)
    }

    @Test
    fun init_parses_minimum_activity_date_from_the_iso_prefix() = runTest(mainDispatcher) {
        coEvery { watchHistoryRepository.getMinimumActivityDate() } returns "2023-05-14T10:30:00Z"

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(LocalDate.of(2023, 5, 14), viewModel.uiState.value.minActivityDate)
    }

    @Test
    fun init_degrades_unparseable_minimum_activity_date_to_null() = runTest(mainDispatcher) {
        coEvery { watchHistoryRepository.getMinimumActivityDate() } returns "not-a-date"

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.minActivityDate)
    }

    @Test
    fun init_without_minimum_activity_date_stays_null() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.minActivityDate)
    }

    // ── streak walk ───────────────────────────────────────────────────────

    @Test
    fun streaks_walk_gaps_and_anchor_on_today() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        // Two runs: today + yesterday (current streak 2) and a 5-day run a
        // month back (longest streak 5), separated by a >1 day gap.
        val oldRun = (0..4).map { today.minusDays(30L - it) }
        val dates = oldRun + listOf(today.minusDays(1), today)
        stubDailyActivity { dates.map { activity(it, 60L) } }

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(
            com.raulshma.jellyplay.core.data.repository.StreakInfo(
                currentStreak = 2,
                longestStreak = 5,
                totalActiveDays = 7,
            ),
            viewModel.uiState.value.streakInfo,
        )
    }

    @Test
    fun streaks_gap_boundary_breaks_run_at_exactly_one_missed_day() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        // consecutive pair, one-day gap, another pair → longest run = 2.
        val base = today.minusDays(10)
        val dates = listOf(base, base.plusDays(1), base.plusDays(3), base.plusDays(4))
        stubDailyActivity { dates.map { activity(it, 60L) } }

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.streakInfo.longestStreak)
        assertEquals(4, viewModel.uiState.value.streakInfo.totalActiveDays)
        // Today is not among the dates → current streak is anchored at 0.
        assertEquals(0, viewModel.uiState.value.streakInfo.currentStreak)
    }

    @Test
    fun streaks_count_yesterday_anchored_run_but_not_today() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        // [yesterday-2 .. yesterday] with today inactive: HEAD's walk starts
        // at today and stops immediately — yesterday's run never counts.
        val dates = listOf(today.minusDays(3), today.minusDays(2), today.minusDays(1))
        stubDailyActivity { dates.map { activity(it, 60L) } }

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.streakInfo.currentStreak)
        assertEquals(3, viewModel.uiState.value.streakInfo.longestStreak)
    }

    @Test
    fun streaks_ignore_zero_value_days() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        // Zero-value rows exist in the payload but never count as active.
        val dates = listOf(today.minusDays(2), today.minusDays(1), today)
        stubDailyActivity { dates.map { activity(it, 0L) } }

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(
            com.raulshma.jellyplay.core.data.repository.StreakInfo(0, 0, 0),
            viewModel.uiState.value.streakInfo,
        )
    }

    @Test
    fun streaks_short_circuit_on_empty_activity() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(
            com.raulshma.jellyplay.core.data.repository.StreakInfo(0, 0, 0),
            viewModel.uiState.value.streakInfo,
        )
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // ── day selection + media-detail resolution ──────────────────────────

    private fun session(itemId: String, name: String, durationMin: Long = 30) = PlaybackReportingDetail(
        itemId = itemId,
        name = name,
        duration = durationMin * 60_000_000_000L,
    )

    @Test
    fun selectDay_resolves_media_details_and_builds_selected_day_info() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        coEvery { watchHistoryRepository.getItemsForDay(any(), any()) } returns listOf(session("a", "Session A"))
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns Result.success(
            MediaDetail(item = MediaItem(id = "a", name = "Movie A", mediaType = MediaType.MOVIE)),
        )

        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.onEvent(HeatmapEvent.SelectDay(today))
        advanceUntilIdle()

        val selected = viewModel.uiState.value.selectedDay
        assertNotNull(selected)
        assertEquals(today, selected.date)
        assertEquals(1, selected.sessions.size)
        val resolved = selected.resolvedItems["a"]
        assertNotNull(resolved)
        assertEquals("Movie A", resolved.name)
        assertEquals(MediaType.MOVIE, resolved.mediaType)
        assertEquals("http://img", resolved.imageUrl)
        // The date label is locale-formatted (JVM default) — only pin shape.
        assertTrue(selected.dateLabel.isNotBlank())
    }

    @Test
    fun selectDay_media_detail_failure_falls_back_to_no_resolution() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        coEvery { watchHistoryRepository.getItemsForDay(any(), any()) } returns listOf(session("a", "Session A"))
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns Result.failure(RuntimeException("gone"))

        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.onEvent(HeatmapEvent.SelectDay(today))
        advanceUntilIdle()

        val selected = viewModel.uiState.value.selectedDay
        assertNotNull(selected)
        // HEAD's null-path: failed lookups simply never enter the cache — the
        // row still renders off the session's own name.
        assertTrue(selected.resolvedItems.isEmpty())
        assertEquals(listOf("Session A"), selected.sessions.map { it.name })
    }

    @Test
    fun selectDay_reuses_resolved_items_across_selections() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        coEvery { watchHistoryRepository.getItemsForDay(any(), any()) } returns listOf(session("a", "Session A"))
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns Result.success(
            MediaDetail(item = MediaItem(id = "a", name = "Movie A", mediaType = MediaType.MOVIE)),
        )

        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.onEvent(HeatmapEvent.SelectDay(today))
        advanceUntilIdle()
        viewModel.onEvent(HeatmapEvent.DismissDayDetail)
        viewModel.onEvent(HeatmapEvent.SelectDay(today.minusDays(1)))
        advanceUntilIdle()

        // The second selection hit the VM's cache — one detail fetch total.
        coVerify(exactly = 1) { mediaRepository.getMediaDetail("a", any()) }
        assertNotNull(viewModel.uiState.value.selectedDay?.resolvedItems?.get("a"))
    }

    @Test
    fun selectDay_null_clears_the_selection() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        coEvery { watchHistoryRepository.getItemsForDay(any(), any()) } returns listOf(session("a", "Session A"))
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns Result.failure(RuntimeException("gone"))

        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.onEvent(HeatmapEvent.SelectDay(today))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.selectedDay)

        viewModel.onEvent(HeatmapEvent.SelectDay(null))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedDay)
    }

    // ── share events ──────────────────────────────────────────────────────

    @Test
    fun share_events_toggle_the_one_shot_flag() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.shareRequested)

        viewModel.onEvent(HeatmapEvent.RequestShare)
        assertTrue(viewModel.uiState.value.shareRequested)

        viewModel.onEvent(HeatmapEvent.ShareConsumed)
        assertFalse(viewModel.uiState.value.shareRequested)
    }

    // ── year/filter/refresh loads ─────────────────────────────────────────

    @Test
    fun init_and_setYear_forward_the_year_to_the_loader() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        val currentYear = LocalDate.now().year
        assertEquals(currentYear to HeatmapFilter.ALL, dailyActivityCalls.first())

        viewModel.onEvent(HeatmapEvent.SetYear(currentYear - 1))
        advanceUntilIdle()

        assertEquals(currentYear - 1 to HeatmapFilter.ALL, dailyActivityCalls.last())
        assertEquals(currentYear - 1, viewModel.uiState.value.year)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun setFilter_forwards_the_filter_and_clears_the_selected_day() = runTest(mainDispatcher) {
        val today = LocalDate.now()
        coEvery { watchHistoryRepository.getItemsForDay(any(), any()) } returns listOf(session("a", "Session A"))
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns Result.failure(RuntimeException("gone"))

        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.onEvent(HeatmapEvent.SelectDay(today))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.selectedDay)

        viewModel.onEvent(HeatmapEvent.SetFilter(HeatmapFilter.VIDEO))
        advanceUntilIdle()

        assertEquals(HeatmapFilter.VIDEO, viewModel.uiState.value.filter)
        assertNull(viewModel.uiState.value.selectedDay)
        assertEquals(LocalDate.now().year to HeatmapFilter.VIDEO, dailyActivityCalls.last())
    }

    @Test
    fun load_failure_surfaces_error_then_refresh_recovers() = runTest(mainDispatcher) {
        stubDailyActivity { throw RuntimeException("offline") }

        val viewModel = newViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("offline", state.error)
        assertFalse(state.isLoading)

        stubDailyActivity { listOf(activity(LocalDate.now(), 60L)) }
        viewModel.refresh()
        advanceUntilIdle()

        val recovered = viewModel.uiState.value
        assertNull(recovered.error)
        assertFalse(recovered.isLoading)
        assertEquals(1, recovered.dailyActivities.size)
    }

    @Test
    fun refresh_reloads_with_the_current_year_and_filter() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        val callsBeforeRefresh = dailyActivityCalls.size

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(callsBeforeRefresh + 1, dailyActivityCalls.size)
    }
}
