package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

/**
 * Pins the shared player-chrome timing policies both player screens consume
 * (the VOD `VideoPlayerScreen` and the live `LivePlayerScreen` cite this ONE
 * implementation — moved verbatim from player-video's `PlayerScreenPolicies`
 * with the candidate-C4 dedup).
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
}
