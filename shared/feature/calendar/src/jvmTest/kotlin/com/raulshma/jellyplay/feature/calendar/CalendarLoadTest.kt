package com.raulshma.jellyplay.feature.calendar

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coverage for the calendar feature's one load ladder ([CalendarLoad.load])
 * — the arms [UpcomingCalendarViewModel.refresh] folds onto. Pins the
 * dispatch contract the folded site relies on: start raises before the
 * fetch and clears the stale error, exactly one arm fires, the site's
 * success arm is empty by declaration (items arrive through the month
 * collector), and the flag settles in ONE final update after the ladder.
 * The VM-level arms (failure message, settle, feature gate) are pinned by
 * [UpcomingCalendarViewModelTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarLoadTest {

    /** Mirrors the calendar UiState fields the ladder touches. */
    private data class FakeCalendarState(
        val isLoading: Boolean = false,
        val error: String? = null,
    )

    private class FakeCalendarScreen(initial: FakeCalendarState = FakeCalendarState()) {
        var state: FakeCalendarState = initial
            private set

        fun update(transform: (FakeCalendarState) -> FakeCalendarState) {
            state = transform(state)
        }
    }

    @Test
    fun start_raises_the_flag_and_clears_the_error_before_the_fetch() = runTest {
        val screen = FakeCalendarScreen(FakeCalendarState(error = "old failure"))
        val events = mutableListOf<String>()
        var stateAtFetch: FakeCalendarState? = null

        CalendarLoad.load(
            start = {
                events += "start"
                screen.update { it.copy(isLoading = true, error = null) }
            },
            fetch = {
                events += "fetch"
                stateAtFetch = screen.state
                Result.success(Unit)
            },
            onSuccess = { events += "onSuccess" },
            onFailure = { events += "onFailure" },
        )
        screen.update { it.copy(isLoading = false) }

        assertEquals(listOf("start", "fetch", "onSuccess"), events)
        // The previous error is gone while the new fetch is in flight.
        assertEquals(FakeCalendarState(isLoading = true), stateAtFetch)
        assertFalse(screen.state.isLoading)
    }

    @Test
    fun failure_dispatches_only_the_failure_arm() = runTest {
        val screen = FakeCalendarScreen()
        val events = mutableListOf<String>()

        CalendarLoad.load(
            start = { screen.update { it.copy(isLoading = true, error = null) } },
            fetch = { Result.failure<Unit>(RuntimeException("boom")) },
            onSuccess = { events += "onSuccess" },
            onFailure = { e ->
                events += "onFailure"
                screen.update { it.copy(error = e.message) }
            },
        )
        screen.update { it.copy(isLoading = false) }

        assertEquals(listOf("onFailure"), events)
        assertEquals("boom", screen.state.error)
        assertFalse(screen.state.isLoading)
    }

    @Test
    fun the_ladder_suspends_until_the_fetch_completes_and_neither_arm_runs_early() = runTest {
        val screen = FakeCalendarScreen()
        val fetchGate = CompletableDeferred<Unit>()
        val job = launch {
            CalendarLoad.load(
                start = { screen.update { it.copy(isLoading = true, error = null) } },
                fetch = {
                    fetchGate.await()
                    Result.success(Unit)
                },
                onSuccess = { },
                onFailure = { e -> screen.update { it.copy(error = e.message) } },
            )
            screen.update { it.copy(isLoading = false) }
        }
        runCurrent() // runs the ladder up to the parked fetch

        // In flight: flag up, error clear, no arm has touched the state.
        assertTrue(screen.state.isLoading)
        assertNull(screen.state.error)

        fetchGate.complete(Unit)
        job.join()
        assertFalse(screen.state.isLoading)
    }
}
