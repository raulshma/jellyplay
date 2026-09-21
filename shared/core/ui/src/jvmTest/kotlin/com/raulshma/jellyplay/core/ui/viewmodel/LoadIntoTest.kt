package com.raulshma.jellyplay.core.ui.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins for [loadInto], the ONE load ladder the feature ViewModels route
 * through (folded from the five per-feature slices — the `LiveTvLoad` /
 * `RequestsLoad` / `CalendarLoad` / `SyncPlayLoad` / `AdminLoad` shapes).
 * Pins what the helper OWNS: the guard sequence (start raises before the
 * fetch, exactly one arm fires per Result, the fetch suspends the ladder),
 * the settle modes the call sites declare (per-arm settle, final-update
 * settle, suspend arms completing before the site settles), the returned
 * Result leg gate, non-suspend arms riding the suspend signature, and — the
 * invariant every folded site relies on — cancellation transparency: a
 * cancelled fetch propagates and NO arm runs, so no settle can mask it.
 * The per-site settle choreography stays pinned by the feature suites
 * (`LiveTvLoadTest`, `RequestsLoadTest`, `CalendarLoadTest`,
 * `SyncPlayLoadTest`, `AdminLoadTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoadIntoTest {

    /** Mirrors the UiState fields a folded ladder touches. */
    private data class FakeState(
        val items: List<String> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
    )

    /** The StateFlowHandle.update / composeState idiom the ViewModels write through. */
    private class FakeScreen(initial: FakeState = FakeState()) {
        var state: FakeState = initial
            private set

        fun update(transform: (FakeState) -> FakeState) {
            state = transform(state)
        }
    }

    // ── Guard sequence ───────────────────────────────────────────────────────

    @Test
    fun start_raises_before_the_fetch_and_exactly_one_arm_fires() = runTest {
        val screen = FakeScreen(FakeState(error = "stale"))
        val events = mutableListOf<String>()
        var stateAtFetch: FakeState? = null

        val result = loadInto(
            start = {
                events += "start"
                screen.update { it.copy(isLoading = true, error = null) }
            },
            fetch = {
                events += "fetch"
                stateAtFetch = screen.state
                Result.success(listOf("fresh"))
            },
            onSuccess = { items ->
                events += "onSuccess"
                // Per-arm settle (the canonical shape): the arm drops the flag.
                screen.update { it.copy(items = items, isLoading = false) }
            },
            onFailure = { events += "onFailure" },
        )

        // start ran first (flag up, stale error already cleared while the
        // fetch was in flight), then exactly one arm.
        assertEquals(listOf("start", "fetch", "onSuccess"), events)
        assertEquals(FakeState(isLoading = true), stateAtFetch)
        assertEquals(FakeState(items = listOf("fresh")), screen.state)
        // The fetch's own Result is returned AFTER its arm ran.
        assertTrue(result.isSuccess)
        assertEquals(listOf("fresh"), result.getOrNull())
    }

    @Test
    fun a_failure_result_dispatches_only_the_failure_arm() = runTest {
        val screen = FakeScreen(FakeState(items = listOf("prior")))
        val events = mutableListOf<String>()

        val result = loadInto(
            start = { screen.update { it.copy(isLoading = true, error = null) } },
            fetch = { Result.failure<List<String>>(RuntimeException("boom")) },
            onSuccess = { events += "onSuccess" },
            onFailure = { e ->
                events += "onFailure"
                screen.update { it.copy(error = e.message, isLoading = false) }
            },
        )

        assertEquals(listOf("onFailure"), events)
        assertEquals("boom", screen.state.error)
        assertFalse(screen.state.isLoading)
        // The success arm never ran, so the prior payload survives the error.
        assertEquals(listOf("prior"), screen.state.items)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun the_fetch_suspends_the_ladder_until_it_completes() = runTest {
        val screen = FakeScreen()
        val fetchGate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val job = launch {
            loadInto(
                start = { screen.update { it.copy(isLoading = true, error = null) } },
                fetch = {
                    fetchGate.await()
                    Result.success(listOf("late"))
                },
                onSuccess = { events += "onSuccess" },
                onFailure = { events += "onFailure" },
            )
            events += "after-ladder"
        }
        runCurrent() // the ladder parks inside the fetch

        assertTrue(screen.state.isLoading)
        assertTrue(events.isEmpty()) // neither arm runs before the fetch resolves

        fetchGate.complete(Unit)
        job.join()
        assertEquals(listOf("onSuccess", "after-ladder"), events)
    }

    // ── Settle modes the call sites declare ──────────────────────────────────

    @Test
    fun the_final_update_settle_lands_after_the_ladder_with_untouched_arms() = runTest {
        // The Calendar/Plugins/ScheduledTasks shape: arms publish payload/error
        // only; the site settles the flag once AFTER the ladder returns — the
        // folded ladder awaits its fetch, so the flag covers the call.
        val screen = FakeScreen()
        val job = launch {
            loadInto(
                start = { screen.update { it.copy(isLoading = true, error = null) } },
                fetch = { Result.success(listOf("row")) },
                onSuccess = { rows -> screen.update { it.copy(items = rows) } },
                onFailure = { e -> screen.update { it.copy(error = e.message) } },
            )
            screen.update { it.copy(isLoading = false) }
        }
        job.join()

        assertEquals(FakeState(items = listOf("row")), screen.state)
    }

    @Test
    fun a_suspend_arm_runs_to_completion_before_the_site_settles() = runTest {
        // The SyncPlay join shape: the success arm continues with suspend
        // follow-ups; the site's final settle waits for the whole ladder.
        val screen = FakeScreen()
        val armGate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val job = launch {
            loadInto(
                start = { screen.update { it.copy(isLoading = true, error = null) } },
                fetch = { Result.success(listOf("g1")) },
                onSuccess = { groups ->
                    armGate.await()
                    events += "onSuccess"
                    screen.update { it.copy(items = groups) }
                },
                onFailure = { events += "onFailure" },
            )
            events += "settle"
            screen.update { it.copy(isLoading = false) }
        }
        runCurrent() // the ladder parks inside the suspend success arm

        assertTrue(screen.state.isLoading) // nothing settled while the arm runs
        assertEquals(emptyList<String>(), screen.state.items)

        armGate.complete(Unit)
        job.join()
        assertEquals(listOf("onSuccess", "settle"), events)
        assertEquals(listOf("g1"), screen.state.items)
        assertFalse(screen.state.isLoading)
    }

    @Test
    fun non_suspend_arms_and_function_references_ride_the_suspend_signature() = runTest {
        // The Plugins/ScheduledTasks shape: arms passed as plain function
        // references (a (T) -> Unit is a suspend (T) -> Unit).
        val ran = mutableListOf<String>()
        fun apply(value: String) {
            ran += "apply:$value"
        }

        val success = loadInto(
            start = { },
            fetch = { Result.success("summary") },
            onSuccess = ::apply,
            onFailure = { ran += "log" },
        )
        val failure = loadInto(
            start = { },
            fetch = { Result.failure<String>(RuntimeException("offline")) },
            onSuccess = ::apply,
            onFailure = { ran += "log" },
        )

        assertEquals(listOf("apply:summary", "log"), ran)
        assertTrue(success.isSuccess)
        assertTrue(failure.isFailure)
    }

    // ── Cancellation transparency ────────────────────────────────────────────

    @Test
    fun a_cancelled_fetch_propagates_and_no_arm_runs_or_settles() = runTest {
        val screen = FakeScreen()
        val events = mutableListOf<String>()
        val fetchGate = CompletableDeferred<Unit>()
        val job = launch {
            loadInto(
                start = { screen.update { it.copy(isLoading = true, error = null) } },
                fetch = {
                    try {
                        fetchGate.await()
                    } catch (e: CancellationException) {
                        events += "fetch-cancelled" // observable, then rethrown
                        throw e
                    }
                    Result.success(listOf("x"))
                },
                onSuccess = { events += "onSuccess" },
                onFailure = { events += "onFailure" },
            )
            events += "settled"
            screen.update { it.copy(isLoading = false) }
        }
        runCurrent() // the ladder parks inside the fetch

        job.cancel() // cancel while the fetch is in flight
        fetchGate.complete(Unit)
        runCurrent()

        // The ladder is cancellation-transparent: the fetch's cancellation
        // propagated, so NO arm ran, nothing settled, and the post-ladder
        // settle never executed — the flag stays exactly as start raised it.
        assertEquals(listOf("fetch-cancelled"), events)
        assertTrue(screen.state.isLoading)
        assertNull(screen.state.error)
    }
}
