package com.raulshma.jellyplay.feature.requests

import com.raulshma.jellyplay.core.ui.viewmodel.loadInto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Coverage for the requests feature's one load ladder ([loadInto])
 * — the arms [RequestsViewModel.loadRequests] folds onto. Pins the dispatch
 * contract the folded site relies on: start raises before the fetch and
 * clears the stale error, exactly one arm fires, neither arm runs before
 * the fetch resolves, and — the requests site's declared variant — BOTH
 * arms settle `isLoading` per-arm (no single final settle; the site's
 * enrichment fan-out is launched by the success arm, not awaited in it —
 * the arms stay non-suspend). The VM-level arms (paging projection,
 * enrichment merge) are pinned by [RequestsViewModelPagingInterleaveTest]/
 * [RequestsViewModelEnrichMergeTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestsLoadTest {

    /** Mirrors the requests-list UiState fields the ladder touches. */
    private data class FakeRequestsState(
        val requests: List<String> = emptyList(),
        val totalResults: Int = 0,
        val totalPages: Int = 0,
        val isLoading: Boolean = false,
        val error: String? = null,
    )

    private class FakeRequestsScreen(initial: FakeRequestsState = FakeRequestsState()) {
        var state: FakeRequestsState = initial
            private set

        fun update(transform: (FakeRequestsState) -> FakeRequestsState) {
            state = transform(state)
        }
    }

    @Test
    fun start_raises_the_flag_and_clears_the_error_before_the_fetch() = runTest {
        val screen = FakeRequestsScreen(FakeRequestsState(error = "old failure"))
        val events = mutableListOf<String>()
        var stateAtFetch: FakeRequestsState? = null

        loadInto(
            start = {
                events += "start"
                screen.update { it.copy(isLoading = true, error = null) }
            },
            fetch = {
                events += "fetch"
                stateAtFetch = screen.state
                Result.success(Unit)
            },
            onSuccess = {
                events += "onSuccess"
                // The per-arm settle the requests site declares.
                screen.update { it.copy(isLoading = false) }
            },
            onFailure = { events += "onFailure" },
        )

        assertEquals(listOf("start", "fetch", "onSuccess"), events)
        // The flag was up and the stale error gone while the fetch was in
        // flight; the success arm settled the flag itself (no final update).
        assertEquals(FakeRequestsState(isLoading = true), stateAtFetch)
        assertEquals(FakeRequestsState(), screen.state)
    }

    @Test
    fun failure_dispatches_only_the_failure_arm_and_settles_per_arm() = runTest {
        val screen = FakeRequestsScreen(FakeRequestsState(isLoading = true))
        val events = mutableListOf<String>()

        loadInto(
            start = { screen.update { it.copy(isLoading = true, error = null) } },
            fetch = { Result.failure<Unit>(RuntimeException("boom")) },
            onSuccess = { events += "onSuccess" },
            onFailure = { e ->
                events += "onFailure"
                screen.update { it.copy(isLoading = false, error = e.message) }
            },
        )

        assertEquals(listOf("onFailure"), events)
        assertEquals("boom", screen.state.error)
        assertFalse(screen.state.isLoading)
        assertEquals(emptyList<String>(), screen.state.requests)
    }

    @Test
    fun neither_arm_runs_until_the_fetch_resolves() = runTest {
        val screen = FakeRequestsScreen()
        val fetchGate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val job = launch {
            loadInto(
                start = { screen.update { it.copy(isLoading = true, error = null) } },
                fetch = {
                    fetchGate.await() // the repository round-trip in flight
                    Result.success(listOf("r1"))
                },
                onSuccess = { page ->
                    events += "onSuccess"
                    screen.update { it.copy(requests = page, isLoading = false) }
                },
                onFailure = { events += "onFailure" },
            )
            events += "ladder-done"
        }
        runCurrent() // the ladder parks inside the fetch

        // In flight: flag up, error cleared, and NEITHER arm has fired —
        // the site's in-flight guard reads that flag, so an arm firing
        // early would let a guarded re-entry race the settle.
        assertTrue(screen.state.isLoading)
        assertTrue(events.isEmpty())

        fetchGate.complete(Unit)
        job.join()
        assertEquals(listOf("onSuccess", "ladder-done"), events)
        assertEquals(listOf("r1"), screen.state.requests)
        assertFalse(screen.state.isLoading)
    }
}
