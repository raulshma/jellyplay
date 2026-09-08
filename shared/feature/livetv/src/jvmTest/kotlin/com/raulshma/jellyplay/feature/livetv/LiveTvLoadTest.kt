package com.raulshma.jellyplay.feature.livetv

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
 * Coverage for the Live-TV feature's one load ladder ([LiveTvLoad.load]) —
 * the `isLoading = true, error = null` suspend-guard choreography folded from
 * the tab ViewModels (Channels, Series, Recordings, Programs, Channel Detail).
 * Pins the dispatch contract the folded sites rely on: start raises before the
 * fetch, exactly one arm fires, both arms settle, the fetch suspends the
 * ladder, and the fetch Result is returned to the caller after its arm ran
 * (Channel Detail's leg gating).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveTvLoadTest {

    /** Mirrors the tab UiState fields the ladders touch. */
    private data class FakeTabState(
        val items: List<String> = emptyList(),
        val isLoading: Boolean = false,
        val refreshing: Boolean = false,
        val error: String? = null,
    )

    /** The StateFlowHandle.update idiom the ViewModels write through. */
    private class FakeTab(initial: FakeTabState = FakeTabState()) {
        var state: FakeTabState = initial
            private set

        fun update(transform: (FakeTabState) -> FakeTabState) {
            state = transform(state)
        }
    }

    // ── Canonical per-arm ladder (Channels/Series shape) ─────────────────────

    @Test
    fun start_flips_the_flag_on_entry_and_the_success_arm_settles() = runTest {
        val tab = FakeTab(FakeTabState(items = listOf("stale")))
        val events = mutableListOf<String>()
        var loadingAtFetch: Boolean? = null

        val result = LiveTvLoad.load(
            start = {
                events += "start"
                tab.update { it.copy(isLoading = true, error = null) }
            },
            fetch = {
                events += "fetch"
                loadingAtFetch = tab.state.isLoading
                Result.success(listOf("fresh"))
            },
            onSuccess = { items ->
                events += "onSuccess"
                tab.update { it.copy(items = items, isLoading = false) }
            },
            onFailure = { _ ->
                events += "onFailure"
                tab.update { it.copy(isLoading = false) }
            },
        )

        // start raised the flag BEFORE the fetch ran, and only the success arm fired.
        assertEquals(listOf("start", "fetch", "onSuccess"), events)
        assertEquals(true, loadingAtFetch)
        // The success arm settled the flag and published the items.
        assertEquals(FakeTabState(items = listOf("fresh")), tab.state)
        assertTrue(result.isSuccess)
        assertEquals(listOf("fresh"), result.getOrNull())
    }

    @Test
    fun failure_runs_only_the_failure_arm_and_preserves_prior_items() = runTest {
        val tab = FakeTab(FakeTabState(items = listOf("prior")))
        val events = mutableListOf<String>()

        val result = LiveTvLoad.load(
            start = {
                events += "start"
                tab.update { it.copy(isLoading = true, error = null) }
            },
            fetch = {
                events += "fetch"
                Result.failure<List<String>>(RuntimeException("boom"))
            },
            onSuccess = { _ ->
                events += "onSuccess"
                tab.update { it.copy(isLoading = false) }
            },
            onFailure = { e ->
                events += "onFailure"
                tab.update { it.copy(error = e.message, isLoading = false) }
            },
        )

        // The failure arm saw the exception and settled the flag; the success
        // arm never ran, so the previously-loaded items survive the error.
        assertEquals(listOf("start", "fetch", "onFailure"), events)
        assertEquals("boom", tab.state.error)
        assertFalse(tab.state.isLoading)
        assertEquals(listOf("prior"), tab.state.items)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun start_clears_a_stale_error_before_the_fetch() = runTest {
        val tab = FakeTab(FakeTabState(error = "old failure"))
        val errorBeforeLoad: String? = tab.state.error

        LiveTvLoad.load(
            start = { tab.update { it.copy(isLoading = true, error = null) } },
            fetch = {
                // The ladder's whole point: the previous error is gone while
                // the new fetch is in flight.
                assertEquals(null, tab.state.error)
                Result.success(emptyList<String>())
            },
            onSuccess = { tab.update { it.copy(isLoading = false) } },
            onFailure = { tab.update { it.copy(isLoading = false) } },
        )

        assertEquals("old failure", errorBeforeLoad)
        assertEquals(null, tab.state.error)
    }

    // ── Single-settle variant (Recordings shape) ─────────────────────────────

    @Test
    fun the_failure_arm_may_also_reset_the_items_when_the_site_settles_that_way() = runTest {
        // Recordings' legacy settle ran unconditionally with getOrDefault — a
        // failure CLEARS the previous list instead of preserving it. The arms
        // stay at the call site precisely so this drift stays declarable.
        val tab = FakeTab(FakeTabState(items = listOf("prior")))

        LiveTvLoad.load(
            start = { tab.update { it.copy(isLoading = true, error = null) } },
            fetch = { Result.failure<List<String>>(RuntimeException("boom")) },
            onSuccess = { items -> tab.update { it.copy(items = items, isLoading = false) } },
            onFailure = { e ->
                tab.update { it.copy(items = emptyList(), error = e.message, isLoading = false) }
            },
        )

        assertEquals(FakeTabState(items = emptyList(), error = "boom"), tab.state)
    }

    // ── Flavour variant (Programs shape) ─────────────────────────────────────

    @Test
    fun the_flavour_variant_flips_only_its_own_flag_and_both_arms_clear_both() = runTest {
        // Full render raises isLoading; refreshing stays untouched; both arms
        // clear BOTH flags on settle (the Programs settle copies).
        val fullTab = FakeTab(FakeTabState(error = "stale"))
        LiveTvLoad.load(
            start = { fullTab.update { it.copy(isLoading = true, error = null) } },
            fetch = { Result.success(listOf("row")) },
            onSuccess = { rows ->
                fullTab.update { it.copy(items = rows, isLoading = false, refreshing = false) }
            },
            onFailure = { e ->
                fullTab.update { it.copy(error = e.message, isLoading = false, refreshing = false) }
            },
        )
        assertEquals(FakeTabState(items = listOf("row")), fullTab.state)

        // Throttled re-entry raises refreshing INSTEAD — observed mid-flight,
        // while the fetch is parked — and start still clears the error.
        val throttledTab = FakeTab(FakeTabState(error = "stale"))
        val settleGate = CompletableDeferred<Unit>()
        val loadJob = launch {
            LiveTvLoad.load(
                start = { throttledTab.update { it.copy(refreshing = true, error = null) } },
                fetch = {
                    settleGate.await()
                    Result.success(listOf("row"))
                },
                onSuccess = { rows ->
                    throttledTab.update { it.copy(items = rows, isLoading = false, refreshing = false) }
                },
                onFailure = { e ->
                    throttledTab.update { it.copy(error = e.message, isLoading = false, refreshing = false) }
                },
            )
        }
        runCurrent() // runs the ladder up to the parked fetch

        assertTrue(throttledTab.state.refreshing)
        assertFalse(throttledTab.state.isLoading)
        assertEquals(null, throttledTab.state.error)

        settleGate.complete(Unit)
        loadJob.join()
        assertEquals(FakeTabState(items = listOf("row")), throttledTab.state)
    }

    // ── Returned-Result contract (Channel Detail's leg gating) ────────────────

    @Test
    fun the_fetch_result_is_returned_after_its_arm_ran_so_a_failure_skips_the_next_leg() = runTest {
        val ran = mutableListOf<String>()

        val success = LiveTvLoad.load(
            start = { },
            fetch = { Result.success("meta") },
            onSuccess = { ran += "success arm" },
            onFailure = { ran += "failure arm" },
        )
        val failure = LiveTvLoad.load(
            start = { },
            fetch = { Result.failure<String>(RuntimeException("no channels")) },
            onSuccess = { ran += "success arm" },
            onFailure = { ran += "failure arm" },
        )

        // One arm per ladder, and the caller sees the same Result — the gate
        // that replaced the legacy `return@launch` on meta failure.
        assertEquals(listOf("success arm", "failure arm"), ran)
        assertTrue(success.isSuccess)
        assertEquals("meta", success.getOrNull())
        assertTrue(failure.isFailure)
    }
}
