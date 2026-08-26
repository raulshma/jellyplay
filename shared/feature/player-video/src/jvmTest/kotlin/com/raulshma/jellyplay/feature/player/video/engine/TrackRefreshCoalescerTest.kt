package com.raulshma.jellyplay.feature.player.video.engine

import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * Tests for [TrackRefreshCoalescer]. The coalescer collapses a burst of rapid
 * [request] calls into a single [onRefresh] invocation after the debounce
 * window — the contract that previously lived inline in MpvPlayerEngine and
 * caused the MPV playback ANR when each request in the select + sid/aid/
 * track-list observer cascade did its own main-thread getPropertyNode read.
 *
 * Each test builds its own [TestScope] on an isolated virtual-clock scheduler
 * (mirroring [EnginePositionTickerTest]) so a leaked coroutine in one test
 * cannot surface as an UncaughtExceptionsBeforeTest in the next.
 */
class TrackRefreshCoalescerTest {

    private fun newScope(): TestScope = TestScope(StandardTestDispatcher())

    private fun coalescer(
        scope: TestScope,
        debounceMs: Long = TRACK_REFRESH_DEBOUNCE_MS,
        onRefresh: () -> Unit,
    ): TrackRefreshCoalescer = TrackRefreshCoalescer(
        scopeProvider = { scope },
        onRefresh = onRefresh,
        debounceMs = debounceMs,
    )

    @Test
    fun singleRequest_firesOnceAfterDebounce() {
        val scope = newScope()
        var calls = 0
        val c = coalescer(scope) { calls++ }

        c.request()
        scope.runCurrent()
        assertEquals(0, calls, "no fire before debounce elapses")

        scope.advanceTimeBy(TRACK_REFRESH_DEBOUNCE_MS)
        scope.runCurrent()
        assertEquals(1, calls, "exactly one fire after debounce")
    }

    @Test
    fun burstOfRequests_firesOnceNotPerRequest() {
        val scope = newScope()
        var calls = 0
        val c = coalescer(scope) { calls++ }

        // Simulate the select + sid/aid/track-list observer cascade: several
        // requests land within the debounce window.
        repeat(5) { c.request() }
        scope.runCurrent()
        assertEquals(0, calls, "no fire while burst is ongoing")

        scope.advanceTimeBy(TRACK_REFRESH_DEBOUNCE_MS)
        scope.runCurrent()
        assertEquals(1, calls, "burst must collapse into a single fire, got $calls")
    }

    @Test
    fun requestAfterFire_startsFreshDebounce() {
        val scope = newScope()
        var calls = 0
        val c = coalescer(scope) { calls++ }

        c.request()
        scope.advanceTimeBy(TRACK_REFRESH_DEBOUNCE_MS)
        scope.runCurrent()
        assertEquals(1, calls, "first fire")

        // A later request must debounce again — it must not fire immediately
        // (would mean the debounce window was bypassed) nor be dropped.
        c.request()
        scope.runCurrent()
        assertEquals(1, calls, "no immediate re-fire")

        scope.advanceTimeBy(TRACK_REFRESH_DEBOUNCE_MS)
        scope.runCurrent()
        assertEquals(2, calls, "second fire after fresh debounce")
    }

    @Test
    fun staggeredRequests_rearmDeadlineEachTime() {
        val scope = newScope()
        var calls = 0
        val c = coalescer(scope, debounceMs = 100L) { calls++ }

        c.request()
        scope.advanceTimeBy(60) // before deadline
        scope.runCurrent()
        assertEquals(0, calls)

        c.request() // rearm: deadline pushed out another 100ms
        scope.advanceTimeBy(60) // 120ms total since first request
        scope.runCurrent()
        assertEquals(0, calls, "must not fire — deadline was rearmed")

        scope.advanceTimeBy(40) // 100ms since the rearm
        scope.runCurrent()
        assertEquals(1, calls, "fires once after rearmed deadline")
    }

    @Test
    fun cancel_dropsPendingFire() {
        val scope = newScope()
        var calls = 0
        val c = coalescer(scope) { calls++ }

        c.request()
        c.cancel()
        scope.advanceUntilIdle()
        scope.runCurrent()
        assertEquals(0, calls, "cancel must prevent the pending fire")
    }

    @Test
    fun scopeCancellation_dropsPendingFire() {
        val scope = newScope()
        var calls = 0
        val c = coalescer(scope) { calls++ }

        c.request()
        scope.cancel() // engine release cancels its scope
        scope.runCurrent()
        assertEquals(0, calls, "scope cancellation must drop the pending fire")
    }
}
