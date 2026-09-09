package com.raulshma.jellyplay.core.ui.viewmodel

import com.raulshma.jellyplay.core.model.UserDataChange
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pins the [DeferredFetchCoordinator] contract itself (the host ViewModels'
 * suites pin their projections): the publish policy it owns (synchronous
 * loud Loading, loud Error keeping the last value, silent serve-stale,
 * silent heal without a loading flash, [DeferredFetchCoordinator.updateValue]
 * patches), the single-flight/re-arm table (loud failure re-arm,
 * exception-as-failure, cancellation never re-arming, the skip re-arm while
 * a load is in flight, the conservative re-arm when a loud load cancels an
 * in-flight silent regeneration) and the identity decision table the detail
 * hosts used to hand-roll (same-id re-entry no-op, reload-after-failure,
 * silent heal, force propagation).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeferredFetchCoordinatorTest {

    private fun change(itemId: String) = UserDataChange(userId = "user-1", itemIds = listOf(itemId))

    /**
     * Unconfined: fetch bodies run eagerly to their first suspension point,
     * so [DeferredFetchCoordinator.load] and silent activations complete
     * synchronously and assertions need no virtual-time plumbing.
     */
    private fun coordinatorScope() = CoroutineScope(UnconfinedTestDispatcher())

    /**
     * Records every (id, force) pair the fetch body was invoked with;
     * [failure] non-null makes it throw that instead of returning [result]
     * (the failure protocol: throwing, not returning a flag).
     */
    private class FetchLog {
        val calls = mutableListOf<Pair<String, Boolean>>()
        var result = "value"
        var failure: Exception? = null

        fun fetch(id: String, force: Boolean): String {
            calls += id to force
            failure?.let { throw it }
            return result
        }
    }

    private fun coordinator(
        changes: MutableSharedFlow<UserDataChange>,
        fetch: suspend (String, Boolean) -> String,
        scope: CoroutineScope = coordinatorScope(),
    ) = DeferredFetchCoordinator<String, String>(
        userDataChanges = changes,
        scope = scope,
        fetch = fetch,
    )

    /**
     * Records every distinct state the coordinator published, in order — a
     * StateFlow dedupes equal consecutive values, so a loud start over the
     * initial (loading) state emits nothing until the fetch resolves.
     */
    private fun TestScope.recordStates(
        states: MutableList<DeferredFetchState<String>>,
        coordinator: DeferredFetchCoordinator<String, String>,
    ) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.state.collect { states += it }
        }
    }

    // ── Publish policy (the loud/silent transitions the hosts used to own) ──

    @Test
    fun `a loud load starts on Loading and resolves to Success`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val gate = CompletableDeferred<Boolean>()
        val coordinator = coordinator(changes, fetch = { _, _ -> gate.await().let { "value" } })

        // The loading phase is published synchronously inside load — the
        // screen reads it the moment load returns, before the fetch resolves.
        coordinator.load("item-1")
        assertEquals(
            DeferredFetchState<String>(value = null, isLoading = true),
            coordinator.state.value,
            "the accepted loud load must be in its loading phase before the fetch completes",
        )

        gate.complete(true)
        assertEquals(
            DeferredFetchState(value = "value", isLoading = false),
            coordinator.state.value,
            "the loud success publishes the value and clears the phase",
        )
    }

    @Test
    fun `a loud failure publishes the error and keeps the last value`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        val coordinator = coordinator(changes, log::fetch)
        coordinator.load("item-1")

        log.result = "fresh"
        log.failure = IllegalStateException("boom")
        coordinator.load("item-1", force = true)

        val state = coordinator.state.value
        assertEquals("boom", state.error?.message, "a loud failure surfaces its exception")
        assertEquals("value", state.value, "a loud failure keeps the last value behind the error (serve-stale)")
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `a loud start clears the previous error synchronously`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val gate = CompletableDeferred<Boolean>()
        var first = true
        val coordinator = coordinator(changes, fetch = { _, _ ->
            if (first) {
                first = false
                throw IllegalStateException("boom")
            }
            gate.await().let { "value" }
        })
        coordinator.load("item-1")
        assertEquals("boom", coordinator.state.value.error?.message)

        // Retry from the error screen: the stale error must be gone the
        // moment load returns, with the loading phase in its place — a
        // stranded error would pin the error screen over the retry.
        coordinator.load("item-1", force = true)
        assertNull(coordinator.state.value.error)
        assertEquals(true, coordinator.state.value.isLoading)

        gate.complete(true)
        assertEquals("value", coordinator.state.value.value)
    }

    @Test
    fun `a silent failure publishes nothing at all`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        val coordinator = coordinator(changes, log::fetch)
        coordinator.load("item-1")
        val before = coordinator.state.value

        // The silent regeneration throws: value, loading phase and error
        // must all stay exactly as they were — serve-stale-while-revalidate.
        log.failure = IllegalStateException("silent blip")
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)

        assertSame(before, coordinator.state.value, "a failed silent regeneration must not touch the state")
    }

    @Test
    fun `a silent success over a failed loud load heals without a loading flash`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        val failure = IllegalStateException("boom")
        val coordinator = coordinator(changes, log::fetch)
        val states = mutableListOf<DeferredFetchState<String>>()
        recordStates(states, coordinator)

        log.failure = failure
        coordinator.load("item-1")
        assertEquals("boom", coordinator.state.value.error?.message)

        log.failure = null
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)

        assertEquals(
            listOf(
                // Initial + loud start deduped into one loading value.
                DeferredFetchState<String>(value = null, isLoading = true),
                DeferredFetchState(value = null, isLoading = false, error = failure), // loud failure
                DeferredFetchState(value = "value", isLoading = false), // silent heal
            ),
            states,
        )
        assertNull(coordinator.state.value.error, "the silent success cleared the loud error")
        assertEquals(true, states.drop(1).none { it.isLoading }, "the heal must never flash a loading phase")
    }

    @Test
    fun `a silent success over healthy content publishes quietly`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        val coordinator = coordinator(changes, log::fetch)
        coordinator.load("item-1")

        log.result = "value-2"
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)

        val state = coordinator.state.value
        assertEquals("value-2", state.value, "the silent regeneration publishes fresh content")
        assertEquals(false, state.isLoading, "the silent path never publishes a loading phase")
        assertNull(state.error, "a silent refresh over healthy content heals nothing")
    }

    @Test
    fun `updateValue patches the shown value without touching the load lifecycle`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        val coordinator = coordinator(changes, log::fetch)

        // Before the first successful fetch there is nothing to patch.
        coordinator.updateValue { it + "!" }
        assertNull(coordinator.state.value.value)

        coordinator.load("item-1")
        coordinator.updateValue { it + "!" }
        val state = coordinator.state.value
        assertEquals("value!", state.value, "the optimistic patch lands on the shown aggregate")
        assertEquals(false, state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `a patch behind an in-flight loud reload lands on the kept value and the completing load overwrites it`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var gate = CompletableDeferred<Boolean>()
        var result = "fresh"
        val coordinator = coordinator(changes, fetch = { _, _ -> gate.await().let { result } })

        coordinator.load("item-1")
        gate.complete(true)
        assertEquals("fresh", coordinator.state.value.value)

        // A forced loud reload parks on a fresh gate; the kept aggregate
        // stays on screen behind its spinner, so an optimistic user-data
        // flip must still land on it — and the completing load's aggregate
        // (server truth) overwrites the patch, not vice versa.
        gate = CompletableDeferred()
        result = "refetched"
        coordinator.load("item-1", force = true)
        assertTrue(coordinator.state.value.isLoading, "the reload is in its loading phase")

        coordinator.updateValue { it + "!" }
        assertEquals(
            "fresh!",
            coordinator.state.value.value,
            "the patch reaches the kept value behind the spinner",
        )

        gate.complete(true)
        assertEquals(
            DeferredFetchState(value = "refetched", isLoading = false),
            coordinator.state.value,
            "the completing load's aggregate overwrites the patch",
        )
    }

    // ── Single-flight / re-arm table ─────────────────────────────────────────

    @Test
    fun `loud failure re-arms the silent retry on the next activation`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog().apply { failure = IllegalStateException("boom") }
        val coordinator = coordinator(changes, log::fetch)

        coordinator.load("item-1")
        assertEquals(listOf("item-1"), log.calls.map { it.first })

        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(
            listOf("item-1" to false, "item-1" to true),
            log.calls,
            "a failed loud load must retry silently (forced) on re-entry",
        )
    }

    @Test
    fun `loud success does not re-arm`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        val coordinator = coordinator(changes, log::fetch)

        coordinator.load("item-1")

        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(1, log.calls.size)
    }

    @Test
    fun `non-cancellation exception from the loud fetch re-arms instead of escaping`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var fetchCalls = 0
        val coordinator = coordinator(changes, fetch = { _, _ ->
            fetchCalls++
            if (fetchCalls == 1) throw IllegalStateException("boom") else "value"
        })

        // With the unconfined scope an escaping exception would surface in
        // the uncaught handler and fail the test; reaching the assertion at
        // all pins the swallow.
        coordinator.load("item-1")

        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(2, fetchCalls, "an escaping loud exception must count as failure and re-arm")
    }

    @Test
    fun `non-cancellation exception from the silent fetch re-arms for the next activation`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var silentCalls = 0
        val coordinator = coordinator(changes, fetch = { _, force ->
            if (force) {
                silentCalls++
                if (silentCalls == 1) throw IllegalStateException("boom") else "value"
            } else {
                "value"
            }
        })

        coordinator.load("item-1")
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(1, silentCalls)

        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(2, silentCalls, "an escaping silent exception must retry on the next activation")
    }

    @Test
    fun `a cancelled fetch never publishes a failure`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val never = CompletableDeferred<Boolean>()
        var silentStarted = false
        val coordinator = coordinator(changes, fetch = { _, force ->
            if (force && !silentStarted) {
                silentStarted = true
                never.await().let { "value" } // the silent regeneration parks
            } else {
                "value"
            }
        })

        coordinator.load("item-1")
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        // The loud load cancels the suspended silent pass mid-flight; the
        // cancellation is the regeneration itself, never a failure to report.
        coordinator.load("item-1", force = true)

        assertNull(coordinator.state.value.error, "cancellation must stay masked from the published state")
        assertEquals("value", coordinator.state.value.value)
    }

    @Test
    fun `cancelling an in-flight loud load never re-arms`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val firstLoadGate = CompletableDeferred<Boolean>()
        var fetchCalls = 0
        val coordinator = coordinator(changes, fetch = { _, _ ->
            fetchCalls++
            if (fetchCalls == 1) {
                // The first loud load parks; the second (forced) cancels it.
                firstLoadGate.await()
            }
            "value"
        })

        coordinator.load("item-1")
        coordinator.load("item-1", force = true)

        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(
            2,
            fetchCalls,
            "a cancelled loud load must not arm a silent retry (a third fetch)",
        )
    }

    @Test
    fun `loud load cancelling an in-flight silent fetch re-arms conservatively`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val forces = mutableListOf<Boolean>()
        val never = CompletableDeferred<Boolean>()
        val coordinator = coordinator(changes, fetch = { _, force ->
            forces += force
            if (forces.size == 2) never.await().let { "value" } // the silent regeneration parks
            "value"
        })

        coordinator.load("item-1")
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(listOf(false, true), forces, "the silent regeneration is in flight, suspended")

        // The loud load cancels the silent pass mid-flight. The cancelling
        // load only regenerates the same data if it targets the same
        // subject; when it doesn't (a VM reused for a new id), only the
        // re-armed flag can heal — so the cancel must re-arm.
        coordinator.load("item-1", force = true)
        assertEquals(listOf(false, true, true), forces, "the forced loud load replaced the silent pass")

        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(
            listOf(false, true, true, true),
            forces,
            "cancelling an in-flight silent fetch must re-arm (a fourth, silent fetch fired)",
        )
    }

    @Test
    fun `silent refresh skipped behind an in-flight load re-arms`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var silentCalls = 0
        var loudCalls = 0
        val loudGate = CompletableDeferred<Boolean>()
        val coordinator = coordinator(changes, fetch = { _, force ->
            if (force) {
                silentCalls++
                "value"
            } else {
                loudCalls++
                loudGate.await().let { "value" }
            }
        })

        coordinator.load("item-1")

        // The change lands while the loud load is suspended: the silent
        // twin must skip (single flight) but re-arm — the in-flight load
        // may be fetching pre-change data.
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(0, silentCalls, "the silent twin must not race the in-flight load")

        loudGate.complete(true)
        assertEquals(1, loudCalls)

        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(1, silentCalls, "the skipped refresh must retry once the load lands")
    }

    @Test
    fun `silent regeneration with nothing loaded is a no-op success`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        val coordinator = coordinator(changes, log::fetch)
        val states = mutableListOf<DeferredFetchState<String>>()
        recordStates(states, coordinator)

        // The change lands before any load: re-entry must not fetch (there
        // is nothing showing to regenerate) and must not re-arm either —
        // the hosts' old `currentXId?.let { … } ?: true` no-op success.
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(0, log.calls.size)
        assertEquals(1, states.size, "nothing was published")

        // The first real load still runs untouched.
        coordinator.load("item-1")
        assertEquals(listOf("item-1" to false), log.calls)
    }

    // ── Identity decision table (the absorbed re-entry guard) ────────────────

    @Test
    fun `a loud load for the already-showing id is a re-entry no-op`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        val coordinator = coordinator(changes, log::fetch)
        val states = mutableListOf<DeferredFetchState<String>>()
        recordStates(states, coordinator)

        coordinator.load("item-1")
        val afterFirstLoad = coordinator.state.value

        coordinator.load("item-1")

        assertEquals(1, log.calls.size, "the second loud load for the showing id must not fetch")
        assertSame(
            afterFirstLoad,
            coordinator.state.value,
            "the no-op guard must not publish a second loud loading phase",
        )
    }

    @Test
    fun `a failed loud load re-arms the guard so re-entry reloads`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog().apply { failure = IllegalStateException("boom") }
        val coordinator = coordinator(changes, log::fetch)

        coordinator.load("item-1")
        log.failure = null

        coordinator.load("item-1")
        assertEquals(2, log.calls.size, "re-entry after a failed loud load must reload")
    }

    @Test
    fun `a different id always loads even after a success`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        val coordinator = coordinator(changes, log::fetch)

        coordinator.load("item-1")
        coordinator.load("item-2")

        assertEquals(
            listOf("item-1", "item-2"),
            log.calls.map { it.first },
            "a VM reused for a new id must loud-load it (the guard keys on identity)",
        )
    }

    @Test
    fun `force bypasses the guard for the same id`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        val coordinator = coordinator(changes, log::fetch)

        coordinator.load("item-1")
        coordinator.load("item-1", force = true)

        assertEquals(2, log.calls.size)
    }

    @Test
    fun `a silent success heals the guard so re-entry no-ops`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        val coordinator = coordinator(changes, log::fetch)

        // Loud failure, then the re-armed silent regeneration succeeds: the
        // guard must heal — re-entry no-ops over healed content. The
        // unconfined scope runs the silent fetch synchronously inside the
        // activation, so the state flips before it.
        log.failure = IllegalStateException("boom")
        coordinator.load("item-1")
        log.failure = null
        coordinator.deferredRefresher.onScreenActiveChanged(true)

        coordinator.load("item-1")
        assertEquals(
            listOf("item-1" to false, "item-1" to true),
            log.calls,
            "the silent heal closed the guard — re-entry must no-op",
        )
    }

    @Test
    fun `a silent failure leaves the guard as the last completed fetch set it`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        val coordinator = coordinator(changes, log::fetch)

        // Loud success → silent failure: the stale-but-whole content still
        // counts as showing successfully; re-entry no-ops.
        coordinator.load("item-1")
        log.failure = IllegalStateException("blip")
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        coordinator.load("item-1")
        assertEquals(
            listOf("item-1" to false, "item-1" to true),
            log.calls,
            "a silent failure must not flash-reload content that is still showing",
        )

        // Loud failure → silent failure: still failed; re-entry reloads.
        val changes2 = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log2 = FetchLog().apply { failure = IllegalStateException("boom") }
        val coordinator2 = coordinator(changes2, log2::fetch)
        coordinator2.load("item-1")
        changes2.tryEmit(change("item-1"))
        coordinator2.deferredRefresher.onScreenActiveChanged(true)
        coordinator2.load("item-1")
        assertEquals(3, log2.calls.size, "a silent failure must not heal a failed loud load")
    }

    @Test
    fun `the guard no-op leaves an in-flight silent regeneration running`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        val silentGate = CompletableDeferred<Boolean>()
        val coordinator = coordinator(changes, fetch = { id, force ->
            log.calls += id to force
            if (force) silentGate.await().let { "value" } else "value"
        })

        coordinator.load("item-1")
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(
            listOf("item-1" to true),
            log.calls.drop(1),
            "the silent regeneration is in flight",
        )

        // Re-entry for the showing id: the guard no-ops and must NOT cancel
        // the in-flight regeneration (it is the refresh the flag armed for).
        coordinator.load("item-1")
        silentGate.complete(true)

        assertEquals(
            listOf("item-1" to false, "item-1" to true),
            log.calls,
            "exactly one loud and one silent fetch — the no-op cancelled nothing",
        )
    }

    // ── Fetch invocation protocol ────────────────────────────────────────────

    @Test
    fun `fetch receives the id and the force flag per path`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        val coordinator = coordinator(changes, log::fetch)

        coordinator.load("item-1")
        coordinator.load("item-1", force = true)
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)

        assertEquals(
            listOf("item-1" to false, "item-1" to true, "item-1" to true),
            log.calls,
            "loud carries the load's force flag; the silent regeneration is always forced",
        )
    }

    @Test
    fun `a silent throw stays quiet while a loud throw publishes its error`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var silentThrew = false
        val coordinator = coordinator(changes, fetch = { _, force ->
            if (force && !silentThrew) {
                silentThrew = true
                throw IllegalStateException("silent boom")
            } else {
                "value"
            }
        })

        coordinator.load("item-1")
        val before = coordinator.state.value

        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertSame(before, coordinator.state.value, "a silent throw publishes nothing")

        val changes2 = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val coordinator2 = coordinator(changes2, fetch = { _, _ ->
            throw IllegalStateException("loud boom")
        })
        coordinator2.load("item-1")
        assertEquals(
            "loud boom",
            coordinator2.state.value.error?.message,
            "a loud throw publishes its exception in the state",
        )
    }

    @Test
    fun `a non-Exception throwable rethrows instead of becoming a fetch failure`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val escaped = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, e -> escaped += e }
        var fetchCalls = 0
        val coordinator = coordinator(
            changes,
            // SupervisorJob + handler so the rethrown Error lands in the
            // captured list instead of killing the test process.
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher() + handler),
            fetch = { _, _ ->
                fetchCalls++
                throw AssertionError("fatal") // an Error, not an Exception
            },
        )

        coordinator.load("item-1")

        assertEquals(1, escaped.size, "an Error must escape the coordinator, not count as a fetch failure")
        assertNull(coordinator.state.value.error, "an Error never reaches the published state")

        // The rethrow also skips the failure re-arm: no quiet refetch fires.
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(1, fetchCalls, "an escaped Error must not arm a silent retry")
    }
}
