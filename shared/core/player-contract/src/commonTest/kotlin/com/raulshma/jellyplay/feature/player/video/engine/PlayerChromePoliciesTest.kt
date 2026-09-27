package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

/**
 * Pins the shared player-chrome policies both player screens consume
 * (the VOD `VideoPlayerScreen` and the live `LivePlayerScreen` cite this ONE
 * implementation — moved verbatim from player-video's `PlayerScreenPolicies`
 * with the candidate-C4 dedup; the play-state mirror is the hosts' shared
 * `isPlaying` collector with its same-value guard and sink fan-out).
 */
class PlayerChromePoliciesTest {

    @Test
    fun controlsAutoHideTimeout_touchFormsUseTheBaseTimeout() {
        assertEquals(5_000L, controlsAutoHideTimeoutMs(baseTimeoutMs = 5_000L, isTv = false))
    }

    @Test
    fun controlsAutoHideTimeout_tvDoublesTheBaseTimeout() {
        assertEquals(10_000L, controlsAutoHideTimeoutMs(baseTimeoutMs = 5_000L, isTv = true))
    }

    @Test
    fun controlsAutoHideTimeout_zeroStaysZeroOnTv() {
        assertEquals(0L, controlsAutoHideTimeoutMs(baseTimeoutMs = 0L, isTv = true))
    }

    @Test
    fun liveWindowRefreshLoop_ticksAtTheSharedCadenceWhileActive() = runTest {
        val ticks = mutableListOf<Int>()
        var active = true
        launch {
            liveWindowRefreshLoop(active = { active }, onTick = { ticks += ticks.size })
        }
        // Ticks land at the 500ms boundaries: 0/500/1000/1500 within 1600ms.
        testScheduler.advanceTimeBy(1_600L)
        testScheduler.runCurrent()
        assertEquals(listOf(0, 1, 2, 3), ticks)

        // Deactivate: the loop exits at the next gate check — no further ticks.
        active = false
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()
        assertEquals(listOf(0, 1, 2, 3), ticks)
    }

    @Test
    fun liveWindowRefreshLoop_neverTicksWhenInactiveAtLaunch() = runTest {
        val ticks = mutableListOf<Int>()
        launch {
            liveWindowRefreshLoop(active = { false }, onTick = { ticks += 1 })
        }
        testScheduler.advanceTimeBy(5_000L)
        testScheduler.runCurrent()
        assertEquals(emptyList(), ticks)
    }

    @Test
    fun mirrorPlaying_fansOutEachDistinctValueToEverySink() = runTest {
        val sinkA = mutableListOf<Boolean>()
        val sinkB = mutableListOf<Boolean>()
        val job = mirrorPlaying(
            listOf(true, false, true).asFlow(),
            { sinkA += it },
            { sinkB += it },
        )
        job.join()
        assertEquals(listOf(true, false, true), sinkA)
        assertEquals(listOf(true, false, true), sinkB)
    }

    @Test
    fun mirrorPlaying_swallowsSameValueRepeatsBeforeAnySinkRuns() = runTest {
        val sinkA = mutableListOf<Boolean>()
        val sinkB = mutableListOf<Boolean>()
        // asFlow replays EVERY emission — including the consecutive same-values
        // a StateFlow source dedupes upstream — exactly the redundant
        // emissions the guard exists to swallow.
        val job = mirrorPlaying(
            listOf(true, true, false, false, true).asFlow(),
            { sinkA += it },
            { sinkB += it },
        )
        job.join()
        assertEquals(listOf(true, false, true), sinkA)
        assertEquals(listOf(true, false, true), sinkB)
    }

    @Test
    fun mirrorPlaying_freshMirrorAlwaysDeliversItsFirstValue() = runTest {
        val sink = mutableListOf<Boolean>()
        // A re-armed mirror has no prior value: its first delivery fans out
        // even when the sinks already hold the same state — the VOD host's
        // uiState sink keeps its own same-value check for exactly that replay.
        val job = mirrorPlaying(
            flowOf(false),
            { sink += it },
        )
        job.join()
        assertEquals(listOf(false), sink)
    }

    @Test
    fun mirrorPlaying_cancellingTheJobStopsTheFanOut() = runTest {
        val sink = mutableListOf<Boolean>()
        val job = mirrorPlaying(
            flow {
                emit(true)
                awaitCancellation()
            },
            { sink += it },
        )
        testScheduler.runCurrent()
        assertEquals(listOf(true), sink)
        job.cancel()
        testScheduler.runCurrent()
        assertFalse(job.isActive)
        assertEquals(listOf(true), sink)
    }
}
