package com.raulshma.jellyplay.shell

import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-scoped holder for the PIN/biometric lock flag — the single
 * source of truth for **"is the app unlocked right now"**.
 *
 * This flag used to live as a compose-local `mutableStateOf` field on
 * `MainActivity`, which meant only MainActivity's own gate could read it. The
 * media notification's content intent, however, opens `PlayerActivity` **by
 * class name** (`MediaSessionController`'s session-activity PendingIntent) —
 * bypassing MainActivity entirely — so with a lock configured and the app
 * locked, tapping the notification reached full playback with no challenge.
 * Hoisting the flag into a Koin single lets PlayerActivity enforce the same
 * gate (see its `redirectToLockGateIfNeeded`).
 *
 * The holder is deliberately dumb:
 *
 *  - It stores the unlocked flag plus the bare minimum the auto-lock fold
 *    needs: the wall-clock stamp of the most recent backgrounding
 *    ([onBackgrounded]). "Is a gate configured at all" (PIN and/or biometric
 *    enabled in [SecuritySlice]) stays derived from preferences at each read
 *    site — same as MainActivity always did — via
 *    [AppLockRedirect.isGateConfigured].
 *  - No timers, no persistence: the flag resets to locked (`false`) on process
 *    death, and re-locks only through the existing call sites that used to
 *    flip the compose-local (PIN/biometric unlock in MainActivity, the
 *    auto-lock-on-resume timeout — formerly inline in MainActivity.onResume,
 *    now [onResumed] folding through [AppLockRedirect.shouldRelock]).
 *
 *    One deliberate delta vs the old compose-local (documented HERE because
 *    MainActivity's comment points at this KDoc): the unlocked flag now
 *    SURVIVES activity recreate() (e.g. the pre-T per-app locale change)
 *    instead of re-locking mid-session; process death still resets it.
 *
 * Registered in `androidAppModule`; both hosts resolve it through Koin like
 * the other shell infrastructure.
 */
class AppLockState {

    private val _unlocked = MutableStateFlow(false)

    /** `true` once the user cleared MainActivity's [com.raulshma.jellyplay.core.ui.components.AuthChallengeScreen]; `false` while locked (and on cold start). */
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    /**
     * Wall-clock stamp (milliseconds) of the most recent backgrounding, fed by
     * MainActivity.onPause. `0L` is the "no prior background" sentinel — the
     * same convention the former MainActivity `backgroundedAt` field used, so
     * a resume without a background can never mistake the epoch for a stamp
     * that elapsed eons ago.
     */
    private var backgroundedAtMs = 0L

    /** Marks the app unlocked — called from MainActivity's PIN-success / biometric-success paths. */
    fun unlock() {
        _unlocked.value = true
    }

    /** Marks the app locked — driven by [onResumed]'s auto-lock fold (and directly testable). */
    fun lock() {
        _unlocked.value = false
    }

    /**
     * Records the app backgrounding at [nowMs] (MainActivity.onPause).
     * Replaces the former Activity-local `backgroundedAt` var so the timer
     * state lives beside the fold that consumes it.
     */
    fun onBackgrounded(nowMs: Long) {
        backgroundedAtMs = nowMs
    }

    /**
     * Folds the auto-lock-on-resume policy (the exact expression formerly
     * inline in MainActivity.onResume) and re-locks when it fires. The
     * background stamp is consumed unconditionally — mirrors the old
     * `backgroundedAt = 0L` reset on every resume that followed a background,
     * so a later resume can never relock off a stale stamp.
     *
     * @return `true` when this resume re-locked the app (used by tests to pin
     *   the no-double-transition semantics).
     */
    fun onResumed(nowMs: Long, gateConfigured: Boolean, autoLockTimerMs: Long): Boolean {
        val stamp = backgroundedAtMs
        backgroundedAtMs = 0L
        val relock = AppLockRedirect.shouldRelock(
            gateConfigured = gateConfigured,
            autoLockTimerMs = autoLockTimerMs,
            backgroundedAtMs = stamp,
            nowMs = nowMs,
        )
        if (relock) {
            lock()
        }
        return relock
    }
}

/**
 * Pure decision helpers for the app PIN/biometric gate — kept as a
 * standalone object so the predicate and the redirect rule are unit-testable
 * on the JVM without any Android types.
 *
 * [shouldRedirect] is consumed by PlayerActivity's `onCreate`/`onNewIntent`
 * gate: when a gate is configured AND the app is locked, the dedicated
 * playback host redirects to MainActivity (whose compose gate then renders
 * the lock screen) instead of composing any player UI.
 */
object AppLockRedirect {

    /**
     * Whether any app-lock gate is configured. The exact predicate
     * MainActivity's compose gate has always used (`pinLockEnabled ||
     * biometricLockEnabled`), shared with PlayerActivity so the two hosts can
     * never drift. Takes the two booleans (rather than a preference type) so
     * both callers can feed it from whichever projection they hold
     * (`MainPreferences` in MainActivity, `SecuritySlice` in PlayerActivity).
     */
    fun isGateConfigured(pinLockEnabled: Boolean, biometricLockEnabled: Boolean): Boolean =
        pinLockEnabled || biometricLockEnabled

    /**
     * Whether a host that is NOT the lock screen (PlayerActivity) must
     * redirect to MainActivity instead of showing its own UI: only when a
     * gate is configured and the app is locked. With no gate configured the
     * flag is irrelevant — never redirect.
     */
    fun shouldRedirect(gateConfigured: Boolean, unlocked: Boolean): Boolean =
        gateConfigured && !unlocked

    /**
     * Whether a resume after backgrounding must re-lock the app — the exact
     * fold formerly inline in MainActivity.onResume, extracted here so the
     * auto-lock policy is unit-testable like the rest of the lock family.
     * Semantics preserved verbatim:
     *
     *  - [backgroundedAtMs] `0L` means "no prior background" — never relock
     *    (without this arm, `nowMs - 0` would always exceed the timer and
     *    relock every resume).
     *  - A disabled timer (`0L`) or no configured gate — never relock.
     *  - The timer fires on `nowMs - backgroundedAtMs >= autoLockTimerMs`, so
     *    elapsed == timer relocks and elapsed == timer - 1 does not.
     *
     * Consumed by [AppLockState.onResumed], which also consumes the stamp.
     */
    fun shouldRelock(
        gateConfigured: Boolean,
        autoLockTimerMs: Long,
        backgroundedAtMs: Long,
        nowMs: Long,
    ): Boolean =
        backgroundedAtMs > 0L &&
            gateConfigured &&
            autoLockTimerMs > 0L &&
            (nowMs - backgroundedAtMs) >= autoLockTimerMs
}
