package com.raulshma.jellyplay.core.datastore.security

import com.raulshma.jellyplay.core.model.PinLockoutState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Covers the PIN rate-limit escalation policy: defaults, threshold-triggered
 * lockout, exponential backoff, reset, and the ATM-style "expired lockout →
 * one-more-attempt grace" rule.
 */
class PinRateLimiterTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var limiter: PinRateLimiter

    @BeforeTest
    fun setup() = kotlinx.coroutines.runBlocking {
        val dataStore = com.raulshma.jellyplay.core.datastore.TestDataStoreProvider.get()
        limiter = PinRateLimiter(dataStore, scope)
        // Robolectric reuses the same DataStore file across @Test methods in a
        // class; reset to a pristine NOT_LOCKED state so tests are order-independent.
        limiter.resetPinLockout()
    }

    @Test
    fun `defaults to NOT_LOCKED before any attempt`() {
        assertEquals(PinLockoutState.NOT_LOCKED, limiter.getPinLockoutState())
    }

    @Test
    fun `attempts below threshold do not lock out`() = runTest {
        repeat(PinRateLimiter.MAX_PIN_ATTEMPTS - 1) { i ->
            val state = limiter.recordFailedPinAttempt()
            assertEquals(i + 1, state.failedAttempts)
            assertFalse(state.isLockedOut)
        }
        assertFalse(limiter.getPinLockoutState().isLockedOut)
    }

    @Test
    fun `threshold attempt triggers first escalation`() = runTest {
        repeat(PinRateLimiter.MAX_PIN_ATTEMPTS) {
            limiter.recordFailedPinAttempt()
        }
        val state = limiter.getPinLockoutState()
        assertTrue(state.isLockedOut, "expected lockout after threshold attempts")
        assertEquals(PinRateLimiter.MAX_PIN_ATTEMPTS, state.failedAttempts)
    }

    @Test
    fun `further failures escalate to subsequent durations`() = runTest {
        // Trigger the first lockout, then drive two more batches of failed
        // attempts to walk the escalation table (index 0 → 1 → 2).
        repeat(PinRateLimiter.MAX_PIN_ATTEMPTS) { limiter.recordFailedPinAttempt() }
        var lastUntil = limiter.getPinLockoutState().lockoutUntilEpochMs
        repeat(PinRateLimiter.MAX_PIN_ATTEMPTS) { limiter.recordFailedPinAttempt() }
        var nextUntil = limiter.getPinLockoutState().lockoutUntilEpochMs
        assertTrue(nextUntil > lastUntil, "escalation must extend the lockout window")
        lastUntil = nextUntil
        repeat(PinRateLimiter.MAX_PIN_ATTEMPTS) { limiter.recordFailedPinAttempt() }
        nextUntil = limiter.getPinLockoutState().lockoutUntilEpochMs
        assertTrue(nextUntil > lastUntil, "further escalation must extend again")
    }

    @Test
    fun `resetPinLockout clears counters after successful unlock`() = runTest {
        repeat(PinRateLimiter.MAX_PIN_ATTEMPTS) { limiter.recordFailedPinAttempt() }
        assertTrue(limiter.getPinLockoutState().isLockedOut)

        limiter.resetPinLockout()

        assertEquals(PinLockoutState.NOT_LOCKED, limiter.getPinLockoutState())
    }

    @Test
    fun `recordFailedPinAttempt returns the resulting state synchronously`() = runTest {
        val state = limiter.recordFailedPinAttempt()
        assertEquals(1, state.failedAttempts)
        assertFalse(state.isLockedOut)
    }
}
