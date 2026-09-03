package com.raulshma.jellyplay.feature.calendar

import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrMediaType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.onDay
import kotlinx.datetime.yearMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Month-navigation and transient-state gaps in [UpcomingCalendarViewModel] NOT
 * pinned by [UpcomingCalendarViewModelTest] (which covers changeMonth/goToDate
 * windows, the feature gate and the enrichment):
 *
 * 1. [UpcomingCalendarViewModel.goToToday] — the third nav entry was untested:
 *    - already on the current month → no-op (no collector restart, no refresh);
 *    - on another month → resets the window, restarts the collector on the
 *      current month and refreshes.
 * 2. A month swap clears a previously surfaced refresh error (both
 *    [UpcomingCalendarViewModel.changeMonth] and [UpcomingCalendarViewModel.goToDate]
 *    copy `error = null` — the flag tests never observe the error path).
 * 3. The stale-emission guard in the month collector: items arriving after the
 *    visible month moved on are dropped, not mirrored into state.
 * 4. [UpcomingCalendarViewModel.setFilter] keeps items intact (pure state).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpcomingCalendarViewModelMonthNavGapsTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (UpcomingCalendarViewModelTest pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var arr: ArrRepository
    private lateinit var seerr: SeerrRepository
    private lateinit var experimentalStore: ExperimentalStore
    private lateinit var experimentalSlice: MutableStateFlow<ExperimentalSlice>

    /** Backing flow behind ArrRepository.calendar; reseated per window. */
    private lateinit var calendarFlow: MutableStateFlow<List<ArrCalendarItem>>
    private val calendarWindows = mutableListOf<Pair<LocalDate, LocalDate>>()
    private val refreshCalls = mutableListOf<Pair<LocalDate, LocalDate>>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        arr = mockk()
        seerr = mockk(relaxed = true)
        experimentalStore = mockk()
        experimentalSlice = MutableStateFlow(ExperimentalSlice())
        every { experimentalStore.experimental } returns experimentalSlice
        calendarFlow = MutableStateFlow(emptyList())
        every { arr.calendar(any(), any()) } answers {
            calendarWindows += firstArg<LocalDate>() to secondArg<LocalDate>()
            calendarFlow
        }
        coEvery { arr.refreshCalendar(any(), any()) } answers {
            refreshCalls += firstArg<LocalDate>() to secondArg<LocalDate>()
            Result.success(Unit)
        }
        // The enrichment watcher must never fire during these tests: no items
        // carry a tmdbId, and even if they did the seerr mock is relaxed.
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun enableDirectArr() {
        experimentalSlice.value = ExperimentalSlice(
            enabledExperimentalFeatures = setOf(ExperimentalFeature.DIRECT_ARR_INTEGRATION),
        )
    }

    private fun newViewModel() = UpcomingCalendarViewModel(
        arrRepository = arr,
        seerrRepository = seerr,
        experimentalStore = experimentalStore,
    )

    private fun item(title: String) = ArrCalendarItem(
        tmdbId = null,
        title = title,
        mediaType = ArrMediaType.MOVIE,
        airDateUtc = "2026-07-14T00:00:00Z",
    )

    private fun windowOf(month: YearMonth) = month.onDay(1) to month.lastDay

    @Test
    fun goToToday_onTheCurrentMonth_isANoop() = runTest {
        enableDirectArr()
        val vm = newViewModel()
        advanceUntilIdle()
        val windowsBefore = calendarWindows.size
        val refreshesBefore = refreshCalls.size

        vm.goToToday()
        advanceUntilIdle()

        assertEquals(windowsBefore, calendarWindows.size, "no collector restart on the current month")
        assertEquals(refreshesBefore, refreshCalls.size, "no refresh on the current month")
        assertEquals(CalendarFilter.ALL, vm.state.value.filter)
    }

    @Test
    fun goToToday_fromAnotherMonth_resetsTheWindowAndRefreshes() = runTest {
        enableDirectArr()
        val vm = newViewModel()
        advanceUntilIdle()
        vm.changeMonth(-1)
        advanceUntilIdle()
        val currentMonth = today().yearMonth
        assertTrue(vm.state.value.visibleMonth != currentMonth)

        vm.goToToday()
        advanceUntilIdle()

        assertEquals(currentMonth, vm.state.value.visibleMonth)
        assertEquals(windowOf(currentMonth), calendarWindows.last(), "collector restarted on the current month")
        assertEquals(windowOf(currentMonth), refreshCalls.last(), "refresh ran for the current month")
    }

    @Test
    fun changeMonth_clearsAPreviouslySurfacedRefreshError() = runTest {
        enableDirectArr()
        coEvery { arr.refreshCalendar(any(), any()) } coAnswers {
            refreshCalls += firstArg<LocalDate>() to secondArg<LocalDate>()
            Result.failure(RuntimeException("boom"))
        }
        val vm = newViewModel()
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        assertEquals("boom", vm.state.value.error)

        // The swap clears the stale error SYNCHRONOUSLY, before the new
        // month's refresh launch runs.
        vm.changeMonth(1)
        assertNull(vm.state.value.error, "the month swap must clear the stale error immediately")
        assertTrue(vm.state.value.items.isEmpty())

        // The new month's refresh then runs (and fails again with the stub).
        advanceUntilIdle()
        assertEquals("boom", vm.state.value.error)
    }

    @Test
    fun collector_dropsEmissionsFromAStaleMonthWindow() = runTest {
        enableDirectArr()
        // A fresh flow per calendar() call, each starting EMPTY so only
        // explicit emissions can populate state: each collector window gets
        // its own flow, so a late emission into the OLD flow can only travel
        // through the OLD collector.
        val flows = mutableListOf<MutableStateFlow<List<ArrCalendarItem>>>()
        every { arr.calendar(any(), any()) } answers {
            calendarWindows += firstArg<LocalDate>() to secondArg<LocalDate>()
            MutableStateFlow<List<ArrCalendarItem>>(emptyList())
                .also { flows += it }
        }
        val vm = newViewModel()
        advanceUntilIdle()
        assertEquals(1, flows.size)
        flows[0].value = listOf(item("month row"))
        advanceUntilIdle()
        assertTrue(vm.state.value.items.isNotEmpty(), "the current window's emission lands")

        // Navigate away: collector 1 is cancelled and a new one attaches to a
        // fresh flow for the new window.
        vm.changeMonth(1)
        advanceUntilIdle()
        assertEquals(2, flows.size)
        assertTrue(vm.state.value.items.isEmpty())

        // A late emission into the OLD month's flow must never land.
        flows[0].value = listOf(item("late arrival from the old month"))
        advanceUntilIdle()

        assertTrue(
            vm.state.value.items.isEmpty(),
            "a stale-window emission must not land after the month changed",
        )
    }

    @Test
    fun setFilter_keepsTheLoadedItemsIntact() = runTest {
        enableDirectArr()
        val vm = newViewModel()
        advanceUntilIdle()
        calendarFlow.value = listOf(item("kept"))
        advanceUntilIdle()
        assertTrue(vm.state.value.items.isNotEmpty())

        vm.setFilter(CalendarFilter.MOVIES)

        assertEquals(CalendarFilter.MOVIES, vm.state.value.filter)
        assertTrue(vm.state.value.items.isNotEmpty(), "the pure filter switch never touches the item list")
        assertFalse(vm.state.value.isLoading)
    }
}
