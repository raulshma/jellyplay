package com.raulshma.jellyplay.feature.player.video.engine

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the shared polling-ticker loop. The ticker must:
 *  - call [onActive] every polling tick while playing,
 *  - skip work while paused but still emit on a play↔pause edge,
 *  - honour polling-interval changes even while paused (the bounded
 *    [POSITION_PAUSED_RECHECK_MS] wait this helper centralises).
 *
 * Each test builds its own [TestScope] on an isolated scheduler so a leaked
 * coroutine in one test cannot surface as an `UncaughtExceptionsBeforeTest` in
 * the next. The ticker's `delay`/`withTimeoutOrNull` calls resolve against the
 * scheduler's virtual clock; `advanceTimeBy` + `runCurrent` drives the loop
 * deterministically before the launched [Job] is cancelled and joined.
 */
class EnginePositionTickerTest {

    private fun newScope(): TestScope = TestScope(StandardTestDispatcher())

    private fun ticker(
        scope: TestScope,
        isPlayingFlow: MutableStateFlow<Boolean>,
        pollingInterval: MutableStateFlow<Long>,
        isCurrentlyPlaying: () -> Boolean = { isPlayingFlow.value },
        onActive: () -> Unit,
    ): EnginePositionTicker = EnginePositionTicker(
        scope = scope,
        pollingIntervalMs = pollingInterval,
        isPlayingFlow = isPlayingFlow,
        isCurrentlyPlaying = isCurrentlyPlaying,
        onActive = onActive,
    )

    @Test
    fun onActive_firesWhenPlaying() {
        val scope = newScope()
        val isPlaying = MutableStateFlow(true)
        val interval = MutableStateFlow(100L)
        var calls = 0
        val job = ticker(scope, isPlaying, interval) { calls++ }.launch()

        scope.advanceTimeBy(interval.value + 1)
        scope.runCurrent()
        job.cancel(); scope.runCurrent()
        assertTrue("expected at least one onActive call, got $calls", calls >= 1)
    }

    @Test
    fun onActive_repeatsAtPollingIntervalWhilePlaying() {
        val scope = newScope()
        val isPlaying = MutableStateFlow(true)
        val interval = MutableStateFlow(100L)
        var calls = 0
        val job = ticker(scope, isPlaying, interval) { calls++ }.launch()

        scope.advanceTimeBy(interval.value * 5 + 1)
        scope.runCurrent()
        job.cancel(); scope.runCurrent()
        assertTrue("expected multiple onActive calls, got $calls", calls >= 3)
    }

    @Test
    fun onActive_doesNotFireWhilePausedWithNoEdge() {
        val scope = newScope()
        val isPlaying = MutableStateFlow(false)
        val interval = MutableStateFlow(100L)
        var calls = 0
        val job = ticker(scope, isPlaying, interval) { calls++ }.launch()

        scope.advanceTimeBy(POSITION_PAUSED_RECHECK_MS * 4 + interval.value * 4)
        scope.runCurrent()
        job.cancel(); scope.runCurrent()
        assertEquals("onActive should not fire while paused with no edge", 0, calls)
    }

    @Test
    fun falseToTrueSyncReadback_emitsOnEdge() {
        val scope = newScope()
        val isPlaying = MutableStateFlow(false)
        val interval = MutableStateFlow(100L)
        var syncPlaying = false
        var calls = 0
        val job = ticker(scope, isPlaying, interval, isCurrentlyPlaying = { syncPlaying }) { calls++ }.launch()

        syncPlaying = true
        scope.advanceTimeBy(interval.value + 1)
        scope.runCurrent()
        job.cancel(); scope.runCurrent()
        assertTrue("onActive must fire on false→true edge, got $calls", calls >= 1)
    }

    @Test
    fun playToPause_emitsOnEdge() {
        val scope = newScope()
        val isPlaying = MutableStateFlow(true)
        val interval = MutableStateFlow(100L)
        var calls = 0
        val job = ticker(scope, isPlaying, interval) { calls++ }.launch()

        scope.advanceTimeBy(interval.value * 3 + 1)
        scope.runCurrent()
        val playingCalls = calls

        isPlaying.value = false
        scope.advanceTimeBy(interval.value + 1)
        scope.runCurrent()
        val afterPause = calls
        job.cancel(); scope.runCurrent()
        assertTrue("expected an edge emission on pause, before=$playingCalls after=$afterPause",
            afterPause > playingCalls)
    }

    @Test
    fun longerPollingInterval_throttlesTicks() {
        val scope = newScope()
        val isPlaying = MutableStateFlow(true)
        val interval = MutableStateFlow(100L)
        var calls = 0
        val job = ticker(scope, isPlaying, interval) { calls++ }.launch()

        scope.advanceTimeBy(100L + 1)
        scope.runCurrent()
        val at100 = calls

        interval.value = 1_000L
        scope.advanceTimeBy(100L)
        scope.runCurrent()
        val atShort = calls
        job.cancel(); scope.runCurrent()
        assertTrue("new interval should throttle ticks, at100=$at100 atShort=$atShort",
            atShort - at100 <= 2)
    }

    @Test
    fun launch_returnsCancellableJob() {
        val scope = newScope()
        val isPlaying = MutableStateFlow(false)
        val interval = MutableStateFlow(100L)
        val job = ticker(scope, isPlaying, interval) { }.launch()
        assertTrue("ticker job must be active right after launch", job.isActive)
        job.cancel(); scope.runCurrent()
        assertTrue(job.isCancelled)
    }
}
