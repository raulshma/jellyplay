package com.raulshma.jellyplay.core.ui.components

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-logic tests for [InlineConfirmState]'s arm/confirm/reset lifecycle and
 * its auto-reset window — no Compose UI, so they run on the JVM without a
 * device. The countdown rides the [runTest] virtual-time scheduler: the state's
 * scope is the TestScope itself, whose launched jobs the advance functions
 * drive (backgroundScope children are not executed by the scheduler here).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InlineConfirmStateTest {

    @Test
    fun state_startsDisarmed() = runTest {
        val state = InlineConfirmState(DEFAULT_INLINE_CONFIRM_TIMEOUT_MS, this)
        assertFalse(state.isConfirming)
    }

    @Test
    fun confirm_withinWindow_firesActionAndDisarms() = runTest {
        val state = InlineConfirmState(DEFAULT_INLINE_CONFIRM_TIMEOUT_MS, this)
        var ran = 0
        state.arm()
        assertTrue(state.isConfirming)

        advanceTimeBy(DEFAULT_INLINE_CONFIRM_TIMEOUT_MS / 2)
        assertTrue(state.isConfirming, "must stay armed inside the window")
        state.confirm { ran++ }

        assertFalse(state.isConfirming, "confirm disarms before the action runs")
        assertEquals(1, ran)
        // Past the original window: no timeout reset can double-fire or re-disarm.
        advanceUntilIdle()
        assertEquals(1, ran)
        assertFalse(state.isConfirming)
    }

    @Test
    fun arm_timeout_resetsWithoutFiring() = runTest {
        val state = InlineConfirmState(DEFAULT_INLINE_CONFIRM_TIMEOUT_MS, this)
        var ran = 0
        state.arm()
        assertTrue(state.isConfirming)

        advanceTimeBy(DEFAULT_INLINE_CONFIRM_TIMEOUT_MS)
        advanceUntilIdle()
        assertFalse(state.isConfirming, "timeout must auto-reset")

        // Confirming after the timeout is a no-op — the window has lapsed.
        state.confirm { ran++ }
        assertEquals(0, ran)
    }

    @Test
    fun rearm_getsAFullFreshWindow() = runTest {
        val state = InlineConfirmState(DEFAULT_INLINE_CONFIRM_TIMEOUT_MS, this)
        state.arm()
        advanceTimeBy(DEFAULT_INLINE_CONFIRM_TIMEOUT_MS)
        advanceUntilIdle()
        assertFalse(state.isConfirming)

        // A stale countdown from the first arm must not cut the second short.
        state.arm()
        advanceTimeBy(DEFAULT_INLINE_CONFIRM_TIMEOUT_MS / 2)
        assertTrue(state.isConfirming, "second arm must own a full window")
        advanceTimeBy(DEFAULT_INLINE_CONFIRM_TIMEOUT_MS / 2)
        advanceUntilIdle()
        assertFalse(state.isConfirming)
    }

    @Test
    fun reset_disarmsEarlyWithoutFiring() = runTest {
        val state = InlineConfirmState(DEFAULT_INLINE_CONFIRM_TIMEOUT_MS, this)
        var ran = 0
        state.arm()
        state.reset()
        assertFalse(state.isConfirming)

        advanceUntilIdle()
        state.confirm { ran++ }
        assertEquals(0, ran, "reset must reap the countdown and leave the window lapsed")
    }

    @Test
    fun confirm_whileUnarmed_isNoOp() = runTest {
        val state = InlineConfirmState(DEFAULT_INLINE_CONFIRM_TIMEOUT_MS, this)
        var ran = 0
        state.confirm { ran++ }
        assertFalse(state.isConfirming, "confirm must never arm")
        assertEquals(0, ran)
    }
}
