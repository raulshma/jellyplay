package com.raulshma.jellyplay.screensaver

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dim-scrim timing state, driven on TestScope virtual time: the scrim
 * arms after exactly [DreamDimState]`dimAfterMs` of slideshow runtime, and a
 * 0 delay is off (never activates).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DreamDimStateTest {

    @Test
    fun `activates only after the dim delay elapses`() = runTest {
        val state = DreamDimState(30_000L)
        val job = launch { state.run() }
        advanceTimeBy(29_999)
        assertFalse(state.isActive)
        advanceTimeBy(1)
        // The delay's resumption lands exactly at t = 30_000 — advanceTimeBy
        // stops there without running it, so drain the current instant first.
        runCurrent()
        assertTrue(state.isActive)
        job.cancel()
    }

    @Test
    fun `zero delay is off and never activates`() = runTest {
        val state = DreamDimState(0L)
        val job = launch { state.run() }
        advanceTimeBy(60_000)
        assertFalse(state.isActive)
        job.cancel()
    }
}
