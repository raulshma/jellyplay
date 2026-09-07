package com.raulshma.jellyplay.shell

import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter

/**
 * The PIN-unlock command fold for the app lock gate: one [submit] per attempt
 * owns the whole sequence MainActivity's `onPinEntered` used to hand-copy —
 * the click-time lockout re-check (the user may have hit the rate limit since
 * the last composition read the limiter), the empty-PIN instant unlock, the
 * verification through [verifyPin] (production wires
 * `SecurityStore::verifyPinOffMainThread` so the PBKDF2 derivation stays off
 * the main thread), the failure accounting order (record the attempt, then
 * read back the escalated state to render its window) and the success
 * ordering (flip [AppLockState.unlocked], clear the gate's error surface via
 * [submit]'s `onUnlocked` hook, then clear the limiter).
 *
 * The controller holds no renderable state: the gate composable stays the
 * owner of its error/verifying fields and folds the returned
 * [PinSubmitOutcome] (plus [lockoutMessage]) into strings.
 *
 * @param rateLimiter the persisted attempt counter / lockout escalation state.
 * @param appLockState the app-scoped unlocked flag this controller flips on
 *   success — the same flag PlayerActivity's redirect gate reads.
 * @param verifyPin the credential-check seam; injected so JVM tests substitute
 *   an in-memory predicate.
 * @param nowMs wall-clock read for every lockout-remaining computation;
 *   injected so tests drive the clock instead of freezing
 *   [System.currentTimeMillis].
 */
class PinGateController(
    private val rateLimiter: PinRateLimiter,
    private val appLockState: AppLockState,
    private val verifyPin: suspend (String) -> Boolean,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    /** Result of one [submit] — exactly what the gate needs to render next. */
    sealed interface PinSubmitOutcome {

        /** The gate is open: the PIN verified (or the empty-PIN shortcut fired). */
        data object Unlocked : PinSubmitOutcome

        /** Wrong PIN; the limiter recorded the attempt (no lockout triggered yet). */
        data object Incorrect : PinSubmitOutcome

        /**
         * The attempt was rejected without verification: the limiter holds the
         * gate for another [remainingMs].
         */
        data class LockedOut(val remainingMs: Long) : PinSubmitOutcome
    }

    /**
     * Submits one PIN attempt. The click-time lockout re-check runs BEFORE
     * verification: a lockout triggered since the caller's compose-time read
     * short-circuits here, never feeding the (slow) verifier while locked out.
     * The success sequence keeps its historical order — unlock, clear the
     * gate's error state via [onUnlocked], THEN clear the limiter — so the
     * stale "incorrect PIN" text never survives the (slow) reset write.
     */
    suspend fun submit(
        pin: String,
        onUnlocked: () -> Unit = {},
    ): PinSubmitOutcome {
        // Empty input is the challenge screen's "no PIN to check" shortcut:
        // unlock immediately, touching neither the limiter nor the verifier.
        if (pin.isEmpty()) {
            appLockState.unlock()
            onUnlocked()
            return PinSubmitOutcome.Unlocked
        }
        val lockout = rateLimiter.getPinLockoutState()
        val now = nowMs()
        if (lockout.isLockedOut && lockout.lockoutUntilEpochMs > now) {
            return PinSubmitOutcome.LockedOut(lockout.lockoutUntilEpochMs - now)
        }
        return if (verifyPin(pin)) {
            appLockState.unlock()
            onUnlocked()
            rateLimiter.resetPinLockout()
            PinSubmitOutcome.Unlocked
        } else {
            val newState = rateLimiter.recordFailedPinAttempt()
            if (newState.isLockedOut) {
                PinSubmitOutcome.LockedOut(newState.lockoutUntilEpochMs - nowMs())
            } else {
                PinSubmitOutcome.Incorrect
            }
        }
    }

    companion object {

        /**
         * Pure fold of a remaining lockout duration into the message shapes
         * the `pin_lockout_*` string resources format. Seconds always round
         * up (the user must never see "0s"); a non-positive remainder means
         * the lockout has already expired. The string-side twin is
         * MainActivity's `LockoutMessage.resolve`.
         */
        fun lockoutMessage(remainingMs: Long): LockoutMessage {
            if (remainingMs <= 0L) return LockoutMessage.Now
            val seconds = (remainingMs + 999L) / 1000L // round up so we never show 0s
            return when {
                seconds < 60 -> LockoutMessage.Seconds(seconds)
                seconds < 3600 -> LockoutMessage.Minutes(seconds / 60)
                seconds < 86400 -> {
                    val h = seconds / 3600
                    val m = (seconds % 3600) / 60
                    if (m == 0L) LockoutMessage.Hours(h)
                    else LockoutMessage.HoursMinutes(h, m)
                }
                else -> LockoutMessage.Hours(seconds / 3600)
            }
        }
    }

    /**
     * The coarse buckets of [lockoutMessage], one per `pin_lockout_*` string
     * resource. Numbers stay [Long] so the resource format args are the same
     * values the former in-activity formatter passed.
     */
    sealed interface LockoutMessage {
        /** Lockout already expired — the no-args "try again" string. */
        data object Now : LockoutMessage
        data class Seconds(val seconds: Long) : LockoutMessage
        data class Minutes(val minutes: Long) : LockoutMessage
        data class Hours(val hours: Long) : LockoutMessage
        data class HoursMinutes(val hours: Long, val minutes: Long) : LockoutMessage
    }
}
