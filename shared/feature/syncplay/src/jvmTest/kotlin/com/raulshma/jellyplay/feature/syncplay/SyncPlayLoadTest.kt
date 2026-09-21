package com.raulshma.jellyplay.feature.syncplay

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
 * Coverage for the syncplay feature's one load ladder ([loadInto])
 * — the arms [SyncPlayViewModel.loadGroups] and
 * [SyncPlayViewModel.joinGroup] fold onto. Pins the dispatch contract the
 * folded sites rely on: start raises before the fetch and clears the stale
 * error, exactly one arm fires, the `suspend` success arm completes before
 * the site's final settle, and the flavour start may raise more than one
 * flag. The VM-level arms (groups projection, message fallbacks, auto-join)
 * are pinned by [SyncPlayViewModelTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncPlayLoadTest {

    /** Mirrors the group-list UiState fields the ladders touch. */
    private data class FakeSyncPlayState(
        val groups: List<String> = emptyList(),
        val isLoading: Boolean = false,
        val isJoining: Boolean = false,
        val isInGroup: Boolean = false,
        val error: String? = null,
    )

    private class FakeSyncPlayScreen(initial: FakeSyncPlayState = FakeSyncPlayState()) {
        var state: FakeSyncPlayState = initial
            private set

        fun update(transform: (FakeSyncPlayState) -> FakeSyncPlayState) {
            state = transform(state)
        }
    }

    @Test
    fun start_raises_the_flags_and_clears_the_error_before_the_fetch() = runTest {
        val screen = FakeSyncPlayScreen(FakeSyncPlayState(error = "old failure"))
        val events = mutableListOf<String>()
        var stateAtFetch: FakeSyncPlayState? = null

        loadInto(
            start = {
                events += "start"
                screen.update { it.copy(isJoining = true, isLoading = true, error = null) }
            },
            fetch = {
                events += "fetch"
                stateAtFetch = screen.state
                Result.success(Unit)
            },
            onSuccess = {
                events += "onSuccess"
                screen.update { it.copy(isInGroup = true) }
            },
            onFailure = { events += "onFailure" },
        )
        screen.update { it.copy(isJoining = false, isLoading = false) }

        assertEquals(listOf("start", "fetch", "onSuccess"), events)
        // The flavour start raised BOTH flags and the stale error is gone
        // while the fetch is in flight.
        assertEquals(FakeSyncPlayState(isLoading = true, isJoining = true), stateAtFetch)
        // The arms never settle the flags — the site's ONE final update does.
        assertEquals(FakeSyncPlayState(isInGroup = true), screen.state)
    }

    @Test
    fun failure_dispatches_only_the_failure_arm() = runTest {
        val screen = FakeSyncPlayScreen()
        val events = mutableListOf<String>()

        loadInto(
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
        assertFalse(screen.state.isInGroup)
    }

    @Test
    fun the_suspend_success_arm_completes_before_the_sites_final_settle() = runTest {
        val screen = FakeSyncPlayScreen()
        val armGate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val job = launch {
            loadInto(
                start = { screen.update { it.copy(isLoading = true, error = null) } },
                fetch = { Result.success(listOf("g1")) },
                onSuccess = { groups ->
                    armGate.await() // the arm's suspend follow-up (loadCurrentGroup shape)
                    events += "onSuccess"
                    screen.update { it.copy(groups = groups) }
                },
                onFailure = { events += "onFailure" },
            )
            events += "settle"
            screen.update { it.copy(isLoading = false) }
        }
        runCurrent() // the ladder parks inside the suspend success arm

        // In flight: flag still up (the arm has not published yet), nothing settled.
        assertTrue(screen.state.isLoading)
        assertEquals(emptyList<String>(), screen.state.groups)

        armGate.complete(Unit)
        job.join()
        assertEquals(listOf("onSuccess", "settle"), events)
        assertEquals(listOf("g1"), screen.state.groups)
        assertFalse(screen.state.isLoading)
    }
}
