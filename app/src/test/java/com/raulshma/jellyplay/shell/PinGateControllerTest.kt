package com.raulshma.jellyplay.shell

import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.model.PinLockoutState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the PIN-unlock command fold [PinGateController] owns for MainActivity's
 * gate:
 *
 *  - the click-time lockout re-check short-circuits BEFORE verification, so a
 *    lockout that raced past the compose-time read rejects the attempt without
 *    ever touching the (deliberately slow) verifier;
 *  - the Nth wrong PIN records the escalating lockout and reports the exact
 *    remaining window off the injected clock;
 *  - a correct PIN lands the unlock AND the limiter reset by the time
 *    [PinGateController.submit] hands the outcome back — the point the UI
 *    observes the unlock;
 *  - the empty-PIN shortcut unlocks instantly, touching neither limiter nor
 *    verifier;
 *  - [PinGateController.lockoutMessage] keeps the former in-activity
 *    formatter's exact bucket boundaries (seconds round up; 60s → minutes,
 *    3600s → hours, 86400s → hours-only).
 */
class PinGateControllerTest {

    private val nowMs = 1_000_000L

    private fun lockedLimiter(untilEpochMs: Long): PinRateLimiter = mockk {
        every { getPinLockoutState() } returns
            PinLockoutState(failedAttempts = PinRateLimiter.MAX_PIN_ATTEMPTS, lockoutUntilEpochMs = untilEpochMs)
        coEvery { recordFailedPinAttempt() } returns PinLockoutState.NOT_LOCKED
        coEvery { resetPinLockout() } returns Unit
    }

    private fun openLimiter(): PinRateLimiter = mockk {
        every { getPinLockoutState() } returns PinLockoutState.NOT_LOCKED
        coEvery { recordFailedPinAttempt() } returns PinLockoutState.NOT_LOCKED
        coEvery { resetPinLockout() } returns Unit
    }

    private fun controller(
        limiter: PinRateLimiter,
        verify: suspend (String) -> Boolean = { false },
        state: AppLockState = AppLockState(),
    ): PinGateController = PinGateController(
        rateLimiter = limiter,
        appLockState = state,
        verifyPin = verify,
        nowMs = { nowMs },
    )

    // ── click-time lockout re-check ────────────────────────────────────────

    @Test
    fun `a lockout raced past the compose-time check rejects the attempt without verifying`() = runTest {
        val limiter = lockedLimiter(untilEpochMs = nowMs + 30_000L)
        var verifyCalls = 0
        val state = AppLockState()
        val gate = controller(limiter, verify = { verifyCalls++; true }, state = state)

        val outcome = gate.submit("1234")

        // The verifier would have accepted this PIN — the limiter wins anyway.
        assertEquals(30_000L, (outcome as PinGateController.PinSubmitOutcome.LockedOut).remainingMs)
        assertEquals(0, verifyCalls)
        assertFalse(state.unlocked.value)
    }

    @Test
    fun `the click-time re-check accepts an attempt once the lockout deadline has passed`() = runTest {
        val limiter = lockedLimiter(untilEpochMs = nowMs) // deadline == now → expired
        val state = AppLockState()
        val gate = controller(limiter, verify = { true }, state = state)

        val outcome = gate.submit("1234")

        assertEquals(PinGateController.PinSubmitOutcome.Unlocked, outcome)
        assertTrue(state.unlocked.value)
        coVerify(exactly = 1) { limiter.resetPinLockout() }
    }

    // ── failure accounting ─────────────────────────────────────────────────

    @Test
    fun `the Nth wrong pin records the lockout and returns the remaining window`() = runTest {
        val limiter = openLimiter()
        coEvery { limiter.recordFailedPinAttempt() } returns
            PinLockoutState(failedAttempts = PinRateLimiter.MAX_PIN_ATTEMPTS, lockoutUntilEpochMs = nowMs + 60_000L)
        val state = AppLockState()
        val gate = controller(limiter, verify = { false }, state = state)

        val outcome = gate.submit("0000")

        assertEquals(60_000L, (outcome as PinGateController.PinSubmitOutcome.LockedOut).remainingMs)
        coVerify(exactly = 1) { limiter.recordFailedPinAttempt() }
        coVerify(exactly = 0) { limiter.resetPinLockout() }
        assertFalse(state.unlocked.value)
    }

    @Test
    fun `a wrong pin below the threshold reports incorrect without a lockout`() = runTest {
        val gate = controller(openLimiter())

        val outcome = gate.submit("9999")

        assertEquals(PinGateController.PinSubmitOutcome.Incorrect, outcome)
    }

    // ── success ordering ───────────────────────────────────────────────────

    @Test
    fun `a correct pin unlocks, clears the error surface, then resets the limiter`() = runTest {
        val limiter = openLimiter()
        val state = AppLockState()
        val gate = controller(limiter, verify = { it == "1234" }, state = state)
        val events = mutableListOf<String>()
        coEvery { limiter.resetPinLockout() } coAnswers { events += "reset"; Unit }

        val outcome = gate.submit("1234", onUnlocked = { events += "clear-error" })

        assertEquals(PinGateController.PinSubmitOutcome.Unlocked, outcome)
        assertTrue(state.unlocked.value)
        // The historical order: unlock → clear error → limiter reset, so the
        // stale "incorrect PIN" text never survives the (slow) reset write.
        assertEquals(listOf("clear-error", "reset"), events)
        coVerify(exactly = 0) { limiter.recordFailedPinAttempt() }
    }

    // ── empty-PIN shortcut ─────────────────────────────────────────────────

    @Test
    fun `an empty pin unlocks instantly without touching limiter or verifier`() = runTest {
        val limiter = openLimiter()
        var verifyCalls = 0
        val state = AppLockState()
        val gate = controller(limiter, verify = { verifyCalls++; true }, state = state)
        val cleared = mutableListOf<Boolean>()

        val outcome = gate.submit("", onUnlocked = { cleared += true })

        assertEquals(PinGateController.PinSubmitOutcome.Unlocked, outcome)
        assertTrue(state.unlocked.value)
        assertEquals(listOf(true), cleared)
        assertEquals(0, verifyCalls)
        verify(exactly = 0) { limiter.getPinLockoutState() }
        coVerify(exactly = 0) { limiter.recordFailedPinAttempt() }
        coVerify(exactly = 0) { limiter.resetPinLockout() }
    }

    // ── lockoutMessage boundaries (the former formatter's exact fold) ──────

    @Test
    fun `lockoutMessage buckets a non-positive remainder as already expired`() {
        assertEquals(PinGateController.LockoutMessage.Now, PinGateController.lockoutMessage(0L))
        assertEquals(PinGateController.LockoutMessage.Now, PinGateController.lockoutMessage(-1L))
    }

    @Test
    fun `lockoutMessage rounds seconds up and switches buckets at 60s`() {
        assertEquals(PinGateController.LockoutMessage.Seconds(1), PinGateController.lockoutMessage(1L))
        assertEquals(PinGateController.LockoutMessage.Seconds(59), PinGateController.lockoutMessage(59_000L))
        assertEquals(PinGateController.LockoutMessage.Minutes(1), PinGateController.lockoutMessage(59_999L))
        assertEquals(PinGateController.LockoutMessage.Minutes(1), PinGateController.lockoutMessage(60_000L))
    }

    @Test
    fun `lockoutMessage switches buckets at 3600s and 86400s`() {
        assertEquals(PinGateController.LockoutMessage.Minutes(59), PinGateController.lockoutMessage(3_599_000L))
        assertEquals(PinGateController.LockoutMessage.Hours(1), PinGateController.lockoutMessage(3_600_000L))
        assertEquals(
            PinGateController.LockoutMessage.HoursMinutes(23, 59),
            PinGateController.lockoutMessage(86_399_000L),
        )
        assertEquals(PinGateController.LockoutMessage.Hours(24), PinGateController.lockoutMessage(86_400_000L))
    }
}
