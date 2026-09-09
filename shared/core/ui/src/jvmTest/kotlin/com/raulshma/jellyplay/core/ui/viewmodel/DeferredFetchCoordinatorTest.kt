package com.raulshma.jellyplay.core.ui.viewmodel

import com.raulshma.jellyplay.core.model.UserDataChange
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the [DeferredFetchCoordinator] contract itself (the host ViewModels'
 * suites pin their wiring): loud-failure re-arm, exception-as-failure,
 * cancellation never re-arming, the skip re-arm while a load is in flight,
 * and the conservative re-arm when a loud load cancels an in-flight silent
 * regeneration.
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

    @Test
    fun `loud failure re-arms the silent retry on the next activation`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var silentCalls = 0
        val coordinator = DeferredFetchCoordinator(
            userDataChanges = changes,
            scope = coordinatorScope(),
            silentFetch = { silentCalls++; true },
        )

        coordinator.load { false }
        assertEquals(0, silentCalls)

        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(1, silentCalls, "a failed loud load must retry silently on re-entry")
    }

    @Test
    fun `loud success does not re-arm`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var silentCalls = 0
        val coordinator = DeferredFetchCoordinator(
            userDataChanges = changes,
            scope = coordinatorScope(),
            silentFetch = { silentCalls++; true },
        )

        coordinator.load { true }

        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(0, silentCalls)
    }

    @Test
    fun `non-cancellation exception from the loud fetch re-arms instead of escaping`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var silentCalls = 0
        val coordinator = DeferredFetchCoordinator(
            userDataChanges = changes,
            scope = coordinatorScope(),
            silentFetch = { silentCalls++; true },
        )

        // With the unconfined scope an escaping exception would surface in
        // the uncaught handler and fail the test; reaching the assertion at
        // all pins the swallow.
        coordinator.load { throw IllegalStateException("boom") }

        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(1, silentCalls, "an escaping loud exception must count as failure and re-arm")
    }

    @Test
    fun `non-cancellation exception from the silent fetch re-arms for the next activation`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var silentCalls = 0
        val coordinator = DeferredFetchCoordinator(
            userDataChanges = changes,
            scope = coordinatorScope(),
            silentFetch = {
                silentCalls++
                if (silentCalls == 1) throw IllegalStateException("boom") else true
            },
        )

        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(1, silentCalls)

        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(2, silentCalls, "an escaping silent exception must retry on the next activation")
    }

    @Test
    fun `cancelling an in-flight loud load never re-arms`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var silentCalls = 0
        val firstLoad = CompletableDeferred<Boolean>()
        val coordinator = DeferredFetchCoordinator(
            userDataChanges = changes,
            scope = coordinatorScope(),
            silentFetch = { silentCalls++; true },
        )

        coordinator.load { firstLoad.await() }
        // The second loud load cancels the suspended first; the cancelling
        // load is itself the regeneration, so nothing may arm.
        coordinator.load { true }

        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(0, silentCalls, "a cancelled loud load must not arm a silent retry")
    }

    @Test
    fun `loud load cancelling an in-flight silent fetch re-arms conservatively`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var silentCalls = 0
        val never = CompletableDeferred<Boolean>()
        val coordinator = DeferredFetchCoordinator(
            userDataChanges = changes,
            scope = coordinatorScope(),
            silentFetch = {
                silentCalls++
                if (silentCalls == 1) never.await() else true
            },
        )

        changes.tryEmit(change("item-1"))
        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(1, silentCalls, "the silent regeneration is in flight, suspended")

        // The loud load cancels the silent pass mid-flight. The cancelling
        // load only regenerates the same data if it targets the same
        // subject; when it doesn't (a VM reused for a new id), only the
        // re-armed flag can heal — so the cancel must re-arm.
        coordinator.load { true }

        coordinator.deferredRefresher.onScreenActiveChanged(true)
        assertEquals(2, silentCalls, "cancelling an in-flight silent fetch must re-arm")
    }

    @Test
    fun `silent refresh skipped behind an in-flight load re-arms`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var silentCalls = 0
        var loudCalls = 0
        val loudGate = CompletableDeferred<Boolean>()
        val coordinator = DeferredFetchCoordinator(
            userDataChanges = changes,
            scope = coordinatorScope(),
            silentFetch = { silentCalls++; true },
        )

        coordinator.load { loudCalls++; loudGate.await() }

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
}
