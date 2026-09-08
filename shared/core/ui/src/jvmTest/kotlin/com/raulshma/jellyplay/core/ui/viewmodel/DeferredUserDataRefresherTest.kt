package com.raulshma.jellyplay.core.ui.viewmodel

import com.raulshma.jellyplay.core.model.UserDataChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the [DeferredUserDataRefresher] contract itself (the host ViewModels'
 * suites pin their own wiring): arm-on-change while off-screen, one
 * regeneration per stale period on the next activation, no mid-scroll
 * regeneration while the screen is active, WS-burst collapsing, the
 * trigger-bump pager shape, and the silent-failure [DeferredUserDataRefresher.rearm]
 * retry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeferredUserDataRefresherTest {

    private fun change(itemId: String) = UserDataChange(userId = "user-1", itemIds = listOf(itemId))

    /** Unconfined: the init collector subscribes eagerly, so tryEmit delivers synchronously. */
    private fun refresherScope() = CoroutineScope(UnconfinedTestDispatcher())

    @Test
    fun `change while off-screen refreshes once on next activation`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var refreshes = 0
        val refresher = DeferredUserDataRefresher(changes, refresherScope()) { refreshes++ }

        refresher.onScreenActiveChanged(true)
        assertEquals(0, refreshes, "activation with no pending change must not refresh")

        changes.tryEmit(change("item-1"))
        refresher.onScreenActiveChanged(true)
        assertEquals(1, refreshes)
    }

    @Test
    fun `change while on-screen arms but never regenerates mid-screen`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var refreshes = 0
        val refresher = DeferredUserDataRefresher(changes, refresherScope()) { refreshes++ }

        refresher.onScreenActiveChanged(true)
        changes.tryEmit(change("item-1"))
        assertEquals(0, refreshes, "an on-screen change must not swap the pager mid-scroll")

        refresher.onScreenActiveChanged(false)
        refresher.onScreenActiveChanged(true)
        assertEquals(1, refreshes, "the armed change heals on the next re-entry")
    }

    @Test
    fun `burst of changes collapses into one refresh`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var refreshes = 0
        val refresher = DeferredUserDataRefresher(changes, refresherScope()) { refreshes++ }

        changes.tryEmit(change("item-1"))
        changes.tryEmit(change("item-2"))
        changes.tryEmit(change("item-3"))
        refresher.onScreenActiveChanged(true)
        assertEquals(1, refreshes)

        refresher.onScreenActiveChanged(true)
        assertEquals(1, refreshes, "the pending flag is one-shot per stale period")
    }

    @Test
    fun `deactivation alone never consumes the pending flag`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var refreshes = 0
        val refresher = DeferredUserDataRefresher(changes, refresherScope()) { refreshes++ }

        changes.tryEmit(change("item-1"))
        refresher.onScreenActiveChanged(false)
        refresher.onScreenActiveChanged(false)
        refresher.onScreenActiveChanged(true)
        assertEquals(1, refreshes)
    }

    @Test
    fun `rearm retries the refresh on the next activation after a silent failure`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var refreshes = 0
        val refresher = DeferredUserDataRefresher(changes, refresherScope()) { refreshes++ }

        changes.tryEmit(change("item-1"))
        refresher.onScreenActiveChanged(true)
        assertEquals(1, refreshes)
        // The regeneration the host started failed without surfacing an error
        // (stale-while-revalidate): without the re-arm the consumed flag would
        // report the change healed and no later activation would retry.
        refresher.rearm()

        refresher.onScreenActiveChanged(true)
        assertEquals(2, refreshes)
    }

    @Test
    fun `rearm without a prior pending change is inert until an activation consumes it`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        var refreshes = 0
        val refresher = DeferredUserDataRefresher(changes, refresherScope()) { refreshes++ }

        refresher.rearm()
        refresher.onScreenActiveChanged(true)
        assertEquals(1, refreshes)
    }

    @Test
    fun `trigger constructor bumps the generation once per stale period`() = runTest {
        val changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)
        val trigger = StateFlowHandle(MutableStateFlow(0))
        val refresher = DeferredUserDataRefresher(changes, refresherScope(), trigger)

        changes.tryEmit(change("item-1"))
        changes.tryEmit(change("item-2"))
        refresher.onScreenActiveChanged(true)
        assertEquals(1, trigger.value, "a burst bumps the pager generation exactly once")
    }
}
