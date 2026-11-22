package com.raulshma.jellyplay.shell

import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The shared return-moment seam for the app lock: the bounded read of the
 * PERSISTED security slice plus the auto-lock fold both hosts run —
 * MainActivity in `onResume`, PlayerActivity through its return gate
 * (`redirectToLockGateIfNeeded`) at the returns that skip the shell. The
 * shell's compose gate also decides from this same read (MainActivity's
 * `gateSecurity`), so the persisted-slice read has ONE bound and one
 * fail-closed rule across every call site, not a hand-rolled second read.
 */
object AppLockReturn {

    /**
     * Upper bound for [persistedSecurity] — a pathological DataStore read
     * must never wedge a resume. On timeout the gate treats the gate
     * predicate as configured (fail CLOSED) and, on a genuine foreground
     * return with a pending background stamp, re-locks rather than letting
     * the stale unlocked flag skip the auto-lock policy.
     */
    const val READ_TIMEOUT_MS = 1_000L

    /**
     * Delay between retries of a timed-out [persistedSecurity] read in the
     * shell's gate decision. The resume folds run the read once (a blocked
     * resume must complete before any input is processed); the shell's gate
     * is the one caller that can retry, because the fail-closed Blocked
     * surface is what the user sees until the read answers — a transient
     * stall must not strand it for the life of the activity.
     */
    const val READ_RETRY_MS = 500L

    /**
     * The PERSISTED security slice, bounded by [READ_TIMEOUT_MS]; `null`
     * means the read timed out.
     *
     * The hosts must not decide the gate from a `StateFlow` seed: on a cold
     * process (or an activity recreated in a warm one) the Eagerly-collected
     * security upstream may not have emitted yet, and the seed's
     * all-defaults would read as "no gate configured" — consuming an
     * expired background stamp without relocking, or showing browse content
     * behind the lock screen. `firstPersistedSecurity()` suspends until
     * DataStore's initial read lands; warm-process callers return from the
     * cached snapshot without disk IO.
     */
    suspend fun persistedSecurity(store: SecurityStore): SecuritySlice? =
        withTimeoutOrNull(READ_TIMEOUT_MS) { store.firstPersistedSecurity() }

    /**
     * Whether a SHELL resume is a genuine foreground return. A resume that
     * lands beneath the player's still-DOCKED PiP window is not one (ADR 0018,
     * Decision 5): the shell becomes visible behind the floating player while
     * the PiP-entry stamp still represents an ongoing absence, so folding here
     * would consume the stamp and let a later expand past the auto-lock timer
     * skip the challenge. The stamp stays pending until the exit/expand fold
     * evaluates the original absence.
     *
     * The docked exclusion covers only the reveal WITHIN the dock session —
     * the PiP state alone cannot exempt both that reveal and a later return
     * from outside the app (home/recents with the window still floating):
     * [shellBackgroundedDuringDockedSession] carries the caller's own record
     * that its `onStop` ran while THE CURRENT dock session was up (same
     * [AndroidPipController.pipSession] id) — the task left the foreground
     * mid-session, so this resume re-entered the app and the PIN/timer policy
     * must fold exactly like a non-docked return. A record from an EARLIER
     * session (the window was dismissed and something re-docked while the
     * shell stayed backgrounded) must NOT match — the newer session's reveal
     * keeps its exemption. Docked with no matching record: exempt, per
     * Decision 5.
     */
    fun isGenuineShellReturn(
        playerDockedInPip: Boolean,
        shellBackgroundedDuringDockedSession: Boolean,
    ): Boolean = !playerDockedInPip || shellBackgroundedDuringDockedSession

    /**
     * Folds the shared auto-lock-on-return policy from [persisted] values.
     * With a slice, this is exactly [AppLockState.onResumed] fed by the same
     * gate configuration and timer the persisted file carries, measuring
     * against the monotonic [nowMs] the hosts stamp with. With `null` (a
     * timed-out read) the policy cannot be evaluated, so fail CLOSED: an
     * UNLOCKED holder with a pending background stamp re-locks (a return
     * whose absence may have outlasted the timer must not keep browsing on
     * the strength of the stale unlocked flag), and the stamp is left
     * pending for the challenge the relock raises — the successful
     * [AppLockState.unlock] that answers it consumes the stamp (the
     * authentication resolves the absence), so the next return measures a
     * fresh absence instead of re-locking off the dead one. A locked holder
     * is unaffected — it already lands on the lock screen.
     */
    fun foldReturnPolicy(lockState: AppLockState, persisted: SecuritySlice?, nowMs: Long) {
        if (persisted != null) {
            lockState.onResumed(
                nowMs = nowMs,
                gateConfigured = AppLockRedirect.isGateConfigured(
                    pinLockEnabled = persisted.pinLockEnabled,
                    biometricLockEnabled = persisted.biometricLockEnabled,
                ),
                autoLockTimerMs = persisted.autoLockTimerMs,
            )
        } else if (lockState.unlocked.value && lockState.hasPendingBackground) {
            lockState.lock()
        }
    }
}
