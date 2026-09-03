package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the invariants of [PinLockoutState] — the PIN rate-limit snapshot the
 * lock screen renders after each attempt:
 *
 *  - [PinLockoutState.isLockedOut] is true exactly when
 *    [PinLockoutState.lockoutUntilEpochMs] is strictly positive; `0L` is the
 *    canonical "not locked out" sentinel, and negative values (clock skew)
 *    also read as unlocked.
 *  - [PinLockoutState.NOT_LOCKED] is the zero-attempt / zero-deadline state.
 *  - The state is a passive value object: no derived field depends on wall
 *    clock, so an expired lockout stays "locked" until the holder replaces it.
 */
class PinLockoutStateTest {

    @Test
    fun `zero deadline means not locked out`() {
        assertFalse(PinLockoutState(failedAttempts = 5, lockoutUntilEpochMs = 0L).isLockedOut)
    }

    @Test
    fun `positive deadline means locked out`() {
        assertTrue(PinLockoutState(failedAttempts = 3, lockoutUntilEpochMs = 1L).isLockedOut)
        assertTrue(
            PinLockoutState(failedAttempts = 3, lockoutUntilEpochMs = Long.MAX_VALUE).isLockedOut,
        )
    }

    @Test
    fun `negative deadline reads as unlocked`() {
        assertFalse(PinLockoutState(failedAttempts = 3, lockoutUntilEpochMs = -1L).isLockedOut)
    }

    @Test
    fun `NOT_LOCKED is the zeroed state`() {
        assertEquals(0, PinLockoutState.NOT_LOCKED.failedAttempts)
        assertEquals(0L, PinLockoutState.NOT_LOCKED.lockoutUntilEpochMs)
        assertFalse(PinLockoutState.NOT_LOCKED.isLockedOut)
    }

    @Test
    fun `failed attempts do not influence lockout on their own`() {
        // Many failures with no deadline set is still unlocked — the deadline
        // is the single source of truth.
        assertFalse(PinLockoutState(failedAttempts = Int.MAX_VALUE, lockoutUntilEpochMs = 0L).isLockedOut)
    }
}
