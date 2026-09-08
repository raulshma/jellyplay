package com.raulshma.jellyplay.feature.admin

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
 * Coverage for the admin feature's one load ladder ([AdminLoad.load]) — the
 * `isLoading = true, error = null` suspend-guard choreography folded from the
 * admin ViewModels (Dashboard, Devices, Logs, Plugin Detail, Plugins, User
 * Statistics, User Statistics Detail, Scheduled Tasks, Users, and the
 * androidMain Plugin Config). Pins the dispatch contract the folded sites
 * rely on: start raises before the fetch, exactly one arm fires, both arms
 * settle, and the fetch suspends the ladder.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminLoadTest {

    /** Mirrors the admin UiState fields the ladders touch. */
    private data class FakeAdminState(
        val items: List<String> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val error: String? = null,
    )

    /** The composeState/StateFlowHandle write idiom the ViewModels use. */
    private class FakeAdminScreen(initial: FakeAdminState = FakeAdminState()) {
        var state: FakeAdminState = initial
            private set

        fun update(transform: (FakeAdminState) -> FakeAdminState) {
            state = transform(state)
        }
    }

    // ── Canonical per-arm ladder (Devices/Plugin Detail/Users shape) ─────────

    @Test
    fun start_flips_the_flag_on_entry_and_the_success_arm_settles() = runTest {
        val screen = FakeAdminScreen(FakeAdminState(items = listOf("stale")))
        val events = mutableListOf<String>()
        var loadingAtFetch: Boolean? = null

        AdminLoad.load(
            start = {
                events += "start"
                screen.update { it.copy(isLoading = true, error = null) }
            },
            fetch = {
                events += "fetch"
                loadingAtFetch = screen.state.isLoading
                Result.success(listOf("fresh"))
            },
            onSuccess = { items ->
                events += "onSuccess"
                screen.update { it.copy(items = items, isLoading = false) }
            },
            onFailure = { _ ->
                events += "onFailure"
                screen.update { it.copy(isLoading = false) }
            },
        )

        // start raised the flag BEFORE the fetch ran, and only the success arm fired.
        assertEquals(listOf("start", "fetch", "onSuccess"), events)
        assertEquals(true, loadingAtFetch)
        // The success arm settled the flag and published the items.
        assertEquals(FakeAdminState(items = listOf("fresh")), screen.state)
    }

    @Test
    fun failure_runs_only_the_failure_arm_and_preserves_prior_items() = runTest {
        val screen = FakeAdminScreen(FakeAdminState(items = listOf("prior")))
        val events = mutableListOf<String>()

        AdminLoad.load(
            start = {
                events += "start"
                screen.update { it.copy(isLoading = true, error = null) }
            },
            fetch = {
                events += "fetch"
                Result.failure<List<String>>(RuntimeException("boom"))
            },
            onSuccess = { _ ->
                events += "onSuccess"
                screen.update { it.copy(isLoading = false) }
            },
            onFailure = { e ->
                events += "onFailure"
                screen.update { it.copy(error = e.message, isLoading = false) }
            },
        )

        // The failure arm saw the exception and settled the flag; the success
        // arm never ran, so the previously-loaded items survive the error.
        assertEquals(listOf("start", "fetch", "onFailure"), events)
        assertEquals("boom", screen.state.error)
        assertFalse(screen.state.isLoading)
        assertEquals(listOf("prior"), screen.state.items)
    }

    @Test
    fun start_clears_a_stale_error_before_the_fetch() = runTest {
        val screen = FakeAdminScreen(FakeAdminState(error = "old failure"))
        val errorBeforeLoad: String? = screen.state.error

        AdminLoad.load(
            start = { screen.update { it.copy(isLoading = true, error = null) } },
            fetch = {
                // The ladder's whole point: the previous error is gone while
                // the new fetch is in flight.
                assertEquals(null, screen.state.error)
                Result.success(emptyList<String>())
            },
            onSuccess = { screen.update { it.copy(isLoading = false) } },
            onFailure = { screen.update { it.copy(isLoading = false) } },
        )

        assertEquals("old failure", errorBeforeLoad)
        assertEquals(null, screen.state.error)
    }

    // ── Final-update settle variant (Plugins/Scheduled Tasks/Plugin Config) ──

    @Test
    fun the_final_update_settle_lands_after_the_arms_and_needs_no_arm_settle() = runTest {
        val screen = FakeAdminScreen(FakeAdminState(items = listOf("stale")))

        // The VM's legacy shape: the arms publish items/error only, the flag
        // settles once AFTER the ladder. The folded ladder awaits its fetch,
        // so the flag now covers the fetch (the declared timing unification).
        val loadingMidFlight = CompletableDeferred<Boolean>()
        val settleAfterLadder = CompletableDeferred<Unit>()
        val job = launch {
            AdminLoad.load(
                start = { screen.update { it.copy(isLoading = true, error = null) } },
                fetch = {
                    loadingMidFlight.complete(screen.state.isLoading)
                    Result.success(listOf("fresh"))
                },
                onSuccess = { items -> screen.update { it.copy(items = items) } },
                onFailure = { e -> screen.update { it.copy(error = e.message) } },
            )
            screen.update { it.copy(isLoading = false) }
            settleAfterLadder.complete(Unit)
        }
        job.join()

        // The flag was still up while the fetch ran, and the final update (not
        // an arm) dropped it after the arms had published their payload.
        assertEquals(true, loadingMidFlight.getCompleted())
        assertTrue(settleAfterLadder.isCompleted)
        assertEquals(FakeAdminState(items = listOf("fresh")), screen.state)
    }

    @Test
    fun the_final_update_settle_also_drops_the_flag_on_failure() = runTest {
        val screen = FakeAdminScreen()

        launch {
            AdminLoad.load(
                start = { screen.update { it.copy(isLoading = true, error = null) } },
                fetch = { Result.failure<List<String>>(RuntimeException("offline")) },
                onSuccess = { items -> screen.update { it.copy(items = items) } },
                onFailure = { e -> screen.update { it.copy(error = e.message) } },
            )
            screen.update { it.copy(isLoading = false) }
        }.join()

        assertEquals("offline", screen.state.error)
        assertFalse(screen.state.isLoading)
    }

    // ── Flavour-start variants (Users' refresh, Stats Detail's page > 0) ─────

    @Test
    fun the_flavour_start_raises_only_its_own_flag_and_skips_the_error_clear() = runTest {
        // Users' pull-to-refresh raises isRefreshing instead of isLoading and
        // deliberately does NOT clear a shown error; Stats Detail's page > 0
        // raises isLoadingMore the same way. Both ride the start closure.
        val refreshScreen = FakeAdminScreen(FakeAdminState(error = "kept"))
        AdminLoad.load(
            start = { refreshScreen.update { it.copy(isRefreshing = true) } },
            fetch = { Result.success(listOf("users")) },
            onSuccess = { users ->
                refreshScreen.update { it.copy(items = users, isLoading = false, isRefreshing = false, error = null) }
            },
            onFailure = { refreshScreen.update { it.copy(isLoading = false, isRefreshing = false) } },
        )
        assertEquals(FakeAdminState(items = listOf("users")), refreshScreen.state)

        val pagedScreen = FakeAdminScreen(FakeAdminState(items = listOf("page0")))
        AdminLoad.load(
            start = { pagedScreen.update { it.copy(isLoadingMore = true) } },
            fetch = { Result.success(listOf("page1")) },
            onSuccess = { page ->
                pagedScreen.update { it.copy(items = pagedScreen.state.items + page, isLoading = false, isLoadingMore = false) }
            },
            onFailure = { e ->
                pagedScreen.update { it.copy(error = e.message, isLoading = false, isLoadingMore = false) }
            },
        )
        assertEquals(listOf("page0", "page1"), pagedScreen.state.items)
        assertFalse(pagedScreen.state.isLoadingMore)
    }

    // ── Suspend + dispatch contract ──────────────────────────────────────────

    @Test
    fun the_fetch_suspends_the_ladder_until_it_completes() = runTest {
        val screen = FakeAdminScreen()
        val fetchGate = CompletableDeferred<Unit>()
        val loadJob = launch {
            AdminLoad.load(
                start = { screen.update { it.copy(isLoading = true, error = null) } },
                fetch = {
                    fetchGate.await()
                    Result.success(listOf("late"))
                },
                onSuccess = { items -> screen.update { it.copy(items = items, isLoading = false) } },
                onFailure = { screen.update { it.copy(isLoading = false) } },
            )
        }
        runCurrent() // runs the ladder up to the parked fetch

        // In flight: flag up, nothing published, neither arm run.
        assertTrue(screen.state.isLoading)
        assertEquals(emptyList<String>(), screen.state.items)

        fetchGate.complete(Unit)
        loadJob.join()
        assertEquals(FakeAdminState(items = listOf("late")), screen.state)
    }

    @Test
    fun exactly_one_arm_fires_per_ladder() = runTest {
        val ran = mutableListOf<String>()

        AdminLoad.load(
            start = { },
            fetch = { Result.success("summary") },
            onSuccess = { ran += "success arm" },
            onFailure = { ran += "failure arm" },
        )
        AdminLoad.load(
            start = { },
            fetch = { runCatching { error("no summary") } },
            onSuccess = { ran += "success arm" },
            onFailure = { ran += "failure arm" },
        )

        // A success fetch dispatches only the success arm, a failure fetch
        // only the failure arm — the dispatch is never re-decided downstream.
        assertEquals(listOf("success arm", "failure arm"), ran)
    }
}
