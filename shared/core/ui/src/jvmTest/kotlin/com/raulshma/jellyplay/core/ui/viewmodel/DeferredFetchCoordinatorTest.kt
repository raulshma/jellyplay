package com.raulshma.jellyplay.core.ui.viewmodel

import com.raulshma.jellyplay.core.model.UserDataChange
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the [DeferredFetchCoordinator] contract itself (the host ViewModels'
 * suites pin their adapter wiring): the single-flight/re-arm table (loud
 * failure re-arm, exception-as-failure, cancellation never re-arming, the
 * skip re-arm while a load is in flight, the conservative re-arm when a
 * loud load cancels an in-flight silent regeneration) and the identity
 * decision table the detail hosts used to hand-roll (same-id re-entry
 * no-op, reload-after-failure, silent heal, mode/force propagation).
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

    /** Records every (id, mode, force) triple the fetch body was invoked with. */
    private class FetchLog {
        val calls = mutableListOf<Triple<String, FetchMode, Boolean>>()
        var result = true

        fun fetch(id: String, mode: FetchMode, force: Boolean): Boolean {
            calls += Triple(id, mode, force)
            return result
        }
    }

    // ── Single-flight / re-arm table ─────────────────────────────────────────

    @Test
    fun `loud failure re-arms the silent retry on the next activation`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog().apply { result = false }
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = log::fetch,
        )

        coordinator.load("item-1")
        assertEquals(listOf("item-1"), log.calls.map { it.first })

        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(
            listOf("item-1" to FetchMode.LOUD, "item-1" to FetchMode.SILENT),
            log.calls.map { it.first to it.second },
            "a failed loud load must retry silently on re-entry",
        )
    }

    @Test
    fun `loud success does not re-arm`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = log::fetch,
        )

        coordinator.load("item-1")

        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(1, log.calls.size)
    }

    @Test
    fun `non-cancellation exception from the loud fetch re-arms instead of escaping`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var fetchCalls = 0
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = { _, mode, _ ->
                fetchCalls++
                if (mode == FetchMode.LOUD) throw IllegalStateException("boom") else true
            },
        )

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
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = { _, mode, _ ->
                if (mode == FetchMode.SILENT) {
                    silentCalls++
                    if (silentCalls == 1) throw IllegalStateException("boom") else true
                } else {
                    true
                }
            },
        )

        coordinator.load("item-1")
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(1, silentCalls)

        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(2, silentCalls, "an escaping silent exception must retry on the next activation")
    }

    @Test
    fun `a cancelled fetch never reaches onLoudError`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var hookCalls = 0
        val never = CompletableDeferred<Boolean>()
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = { _, mode, _ -> if (mode == FetchMode.SILENT) never.await() else true },
            onLoudError = { _ -> hookCalls++ },
        )

        coordinator.load("item-1")
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        // The loud load cancels the suspended silent pass mid-flight; the
        // cancellation is the regeneration itself, never a failure to report.
        coordinator.load("item-1", force = true)

        assertEquals(0, hookCalls, "cancellation must stay masked from the error hook")
    }

    @Test
    fun `cancelling an in-flight loud load never re-arms`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var silentCalls = 0
        val firstLoadGate = CompletableDeferred<Boolean>()
        var firstLoudLoad = true
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = { _, mode, _ ->
                if (mode == FetchMode.SILENT) {
                    silentCalls++
                    true
                } else if (firstLoudLoad) {
                    // The first loud load parks; the second cancels it.
                    firstLoudLoad = false
                    firstLoadGate.await()
                } else {
                    true
                }
            },
        )

        coordinator.load("item-1")
        coordinator.load("item-1", force = true)

        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(0, silentCalls, "a cancelled loud load must not arm a silent retry")
    }

    @Test
    fun `loud load cancelling an in-flight silent fetch re-arms conservatively`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var silentCalls = 0
        val never = CompletableDeferred<Boolean>()
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = { _, mode, _ ->
                if (mode == FetchMode.SILENT) {
                    silentCalls++
                    if (silentCalls == 1) never.await() else true
                } else {
                    true
                }
            },
        )

        coordinator.load("item-1")
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(1, silentCalls, "the silent regeneration is in flight, suspended")

        // The loud load cancels the silent pass mid-flight. The cancelling
        // load only regenerates the same data if it targets the same
        // subject; when it doesn't (a VM reused for a new id), only the
        // re-armed flag can heal — so the cancel must re-arm.
        coordinator.load("item-1", force = true)

        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(2, silentCalls, "cancelling an in-flight silent fetch must re-arm")
    }

    @Test
    fun `silent refresh skipped behind an in-flight load re-arms`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var silentCalls = 0
        var loudCalls = 0
        val loudGate = CompletableDeferred<Boolean>()
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = { _, mode, _ ->
                if (mode == FetchMode.SILENT) {
                    silentCalls++
                    true
                } else {
                    loudCalls++
                    loudGate.await()
                }
            },
        )

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
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = log::fetch,
        )

        // The change lands before any load: re-entry must not fetch (there
        // is nothing showing to regenerate) and must not re-arm either —
        // the hosts' old `currentXId?.let { … } ?: true` no-op success.
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(0, log.calls.size)

        // The first real load still runs untouched.
        coordinator.load("item-1")
        assertEquals(listOf(Triple("item-1", FetchMode.LOUD, false)), log.calls)
    }

    // ── Identity decision table (the absorbed re-entry guard) ────────────────

    @Test
    fun `a loud load for the already-showing id is a re-entry no-op`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        var loudStarts = 0
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = log::fetch,
            onLoudStart = { loudStarts++ },
        )

        coordinator.load("item-1")
        coordinator.load("item-1")

        assertEquals(1, log.calls.size, "the second loud load for the showing id must not fetch")
        assertEquals(1, loudStarts, "the no-op guard must not publish the loud start a second time")
    }

    @Test
    fun `a failed loud load re-arms the guard so re-entry reloads`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog().apply { result = false }
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = log::fetch,
        )

        coordinator.load("item-1")
        log.result = true

        coordinator.load("item-1")
        assertEquals(2, log.calls.size, "re-entry after a failed loud load must reload")
    }

    @Test
    fun `a different id always loads even after a success`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = log::fetch,
        )

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
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = log::fetch,
        )

        coordinator.load("item-1")
        coordinator.load("item-1", force = true)

        assertEquals(2, log.calls.size)
    }

    @Test
    fun `a silent success heals the guard and fires onSilentHeal only over a failed loud load`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        var heals = 0
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = log::fetch,
            onSilentHeal = { heals++ },
        )

        // Loud failure, then the re-armed silent regeneration succeeds: the
        // guard must heal (re-entry no-ops over healed content) and the host
        // must learn so it can clear the loud failure's error surface. The
        // unconfined scope runs the silent fetch synchronously inside the
        // activation, so the result flag flips before it.
        log.result = false
        coordinator.load("item-1")
        log.result = true
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(1, heals)

        coordinator.load("item-1")
        assertEquals(
            listOf("item-1" to FetchMode.LOUD, "item-1" to FetchMode.SILENT),
            log.calls.map { it.first to it.second },
            "the silent heal closed the guard — re-entry must no-op",
        )
    }

    @Test
    fun `a silent success over a successful load never fires onSilentHeal`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        var heals = 0
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = log::fetch,
            onSilentHeal = { heals++ },
        )

        coordinator.load("item-1")
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)

        assertEquals(0, heals, "a silent refresh over healthy content heals nothing")
    }

    @Test
    fun `a silent failure leaves the guard as the last completed fetch set it`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = log::fetch,
        )

        // Loud success → silent failure: the stale-but-whole content still
        // counts as showing successfully; re-entry no-ops.
        coordinator.load("item-1")
        log.result = false
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        coordinator.load("item-1")
        assertEquals(
            listOf("item-1" to FetchMode.LOUD, "item-1" to FetchMode.SILENT),
            log.calls.map { it.first to it.second },
            "a silent failure must not flash-reload content that is still showing",
        )

        // Loud failure → silent failure: still failed; re-entry reloads.
        val changes2 = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log2 = FetchLog().apply { result = false }
        val coordinator2 = DeferredFetchCoordinator<String>(
            userDataChanges = changes2,
            scope = coordinatorScope(),
            fetch = log2::fetch,
        )
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
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = { id, mode, _ ->
                log.calls += Triple(id, mode, false)
                if (mode == FetchMode.SILENT) silentGate.await() else true
            },
        )

        coordinator.load("item-1")
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(
            listOf("item-1" to FetchMode.SILENT),
            log.calls.drop(1).map { it.first to it.second },
            "the silent regeneration is in flight",
        )

        // Re-entry for the showing id: the guard no-ops and must NOT cancel
        // the in-flight regeneration (it is the refresh the flag armed for).
        coordinator.load("item-1")
        silentGate.complete(true)

        assertEquals(
            listOf("item-1" to FetchMode.LOUD, "item-1" to FetchMode.SILENT),
            log.calls.map { it.first to it.second },
            "exactly one loud and one silent fetch — the no-op cancelled nothing",
        )
    }

    // ── Mode, force and hook propagation ─────────────────────────────────────

    @Test
    fun `fetch receives the id, the mode and the force flag per path`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val log = FetchLog()
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = log::fetch,
        )

        coordinator.load("item-1")
        coordinator.load("item-1", force = true)
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)

        assertEquals(
            listOf(
                Triple("item-1", FetchMode.LOUD, false),
                Triple("item-1", FetchMode.LOUD, true),
                Triple("item-1", FetchMode.SILENT, true),
            ),
            log.calls,
            "loud carries the load's force flag; the silent regeneration is always forced",
        )
    }

    @Test
    fun `onLoudStart fires synchronously when a loud load is accepted`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var loudStarts = 0
        val gate = CompletableDeferred<Boolean>()
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = { _, mode, _ -> if (mode == FetchMode.LOUD) gate.await() else true },
            onLoudStart = { loudStarts++ },
        )

        // Before the fetch can even start (it parks on the gate), the start
        // hook has already run — hosts publishing a Loading screen state
        // read it the moment load returns.
        coordinator.load("item-1", force = true)
        assertEquals(1, loudStarts)

        gate.complete(true)
        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(1, loudStarts, "the silent path never publishes the loud start")
    }

    @Test
    fun `a throwing loud fetch reports to onLoudError while a silent one stays quiet`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val reported = mutableListOf<String>()
        var silentCalls = 0
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            scope = coordinatorScope(),
            fetch = { _, mode, _ ->
                if (mode == FetchMode.SILENT) {
                    silentCalls++
                    if (silentCalls == 1) throw IllegalStateException("silent boom") else true
                } else {
                    throw IllegalStateException("loud boom")
                }
            },
            onLoudError = { e -> reported += e.message ?: "" },
        )

        coordinator.load("item-1")
        coordinator.deferredRefresher.onScreenActiveChanged(true)

        assertEquals(
            listOf("loud boom"),
            reported,
            "only the loud throw reaches the error hook — silent throws stay quiet",
        )
    }

    @Test
    fun `a non-Exception throwable rethrows instead of becoming a fetch failure`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val escaped = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, e -> escaped += e }
        var errorHookCalls = 0
        var fetchCalls = 0
        val coordinator = DeferredFetchCoordinator<String>(
            userDataChanges = changes,
            // SupervisorJob + handler so the rethrown Error lands in the
            // captured list instead of killing the test process.
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher() + handler),
            fetch = { _, _, _ ->
                fetchCalls++
                throw AssertionError("fatal") // an Error, not an Exception
            },
            onLoudError = { _ -> errorHookCalls++ },
        )

        coordinator.load("item-1")

        assertEquals(1, escaped.size, "an Error must escape the coordinator, not count as a fetch failure")
        assertEquals(0, errorHookCalls, "an Error never reaches the loud error hook")

        // The rethrow also skips the failure re-arm: no quiet refetch fires.
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(1, fetchCalls, "an escaped Error must not arm a silent retry")
    }
}
