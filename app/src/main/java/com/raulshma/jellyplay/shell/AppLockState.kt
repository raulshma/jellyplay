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
 *    needs: the monotonic-clock stamp of the most recent backgrounding
 *    ([onBackgrounded]; hosts stamp with `SystemClock.elapsedRealtime()`, so
 *    the measurement is immune to wall-clock jumps and includes device
 *    sleep). "Is a gate configured at all" (PIN and/or biometric enabled in
 *    [SecuritySlice]) stays derived from preferences at each read site —
 *    same as MainActivity always did — via
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
     * Monotonic-clock stamp (milliseconds; `SystemClock.elapsedRealtime` at
     * the hosts) of the most recent backgrounding, fed by `onPause` of BOTH
     * hosts (MainActivity, and PlayerActivity — which can stay the task's
     * top activity across a backgrounding now that the launcher root is
     * `singleTop`). `0L` is the "no prior background" sentinel — the same
     * convention the former MainActivity `backgroundedAt` field used, so a
     * resume without a background can never mistake the clock's zero for a
     * stamp that elapsed eons ago.
     */
    private var backgroundedAtMs = 0L

    /**
     * True while a backgrounding stamp awaits the next [onResumed] fold.
     * Consulted by the pause/PiP-entry recorders (through
     * [onBackgroundedIfNonePending]) so a later transition inside one absence
     * cannot reset the clock, and by the lock gate to fail closed when the
     * persisted policy cannot be read in time. The stamp's value stays
     * private — only its presence is observable.
     */
    internal val hasPendingBackground: Boolean
        get() = backgroundedAtMs > 0L

    /**
     * Marks the app unlocked — called from the PIN-success / biometric-success
     * paths (PinGateController, composed by MainActivity's gate). A successful
     * authentication is also the RESOLUTION of any absence whose stamp is
     * still pending: the fail-closed timeout relock and PlayerActivity's
     * delivery redirect both leave the stamp behind under the challenge they
     * raise, and a later fold measuring that dead absence would relock even a
     * momentary backgrounding past the timer — so unlock consumes it, and the
     * next return measures a fresh absence.
     */
    fun unlock() {
        _unlocked.value = true
        backgroundedAtMs = 0L
    }

    /** Marks the app locked — driven by [onResumed]'s auto-lock fold (and directly testable). */
    fun lock() {
        _unlocked.value = false
    }

    /**
     * Records the app backgrounding at [nowMs] unconditionally. Both hosts'
     * `onPause` recorders go through [onBackgroundedIfNonePending] — a later
     * transition inside one absence must not reset the clock — so this
     * primitive stays for stamping the first transition of an absence and
     * for direct test setup. Replaces the former Activity-local
     * `backgroundedAt` var so the timer state lives beside the fold that
     * consumes it.
     */
    fun onBackgrounded(nowMs: Long) {
        backgroundedAtMs = nowMs
    }

    /**
     * Records the app backgrounding at [nowMs] only when no stamp is already
     * pending: the later of two transitions inside one absence must not reset
     * the clock the first one started. Used by PlayerActivity's PiP entry (on
     * most OEMs the pause-first ordering already stamped that same
     * transition), by its `onPause` (a pause that follows a PiP-entry stamp —
     * e.g. the dismissal-time pause of a session that stayed RESUMED through
     * PiP — is not a new absence), and by MainActivity's `onPause` (the shell
     * can pause inside an absence the player stamped: when the docked player
     * expands, overwriting the PiP-entry stamp with "now" would reset the
     * clock the expand fold is about to measure). The stamp survives to be
     * applied by the next genuine return.
     */
    fun onBackgroundedIfNonePending(nowMs: Long) {
        if (!hasPendingBackground) onBackgrounded(nowMs)
    }

    /**
     * Folds the auto-lock-on-resume policy (the exact expression formerly
     * inline in MainActivity.onResume) and re-locks when it fires. The
     * background stamp is consumed unconditionally — mirrors the old
     * `backgroundedAt = 0L` reset on every resume that followed a background,
     * so a later resume can never relock off a stale stamp.
     *
     * Both hosts run this fold through [AppLockReturn.foldReturnPolicy] at
     * their return-to-app moments — MainActivity in `onResume`, PlayerActivity
     * at the returns that skip the shell (launcher relaunch onto a live
     * player, notification taps, PiP expands) once the activity is genuinely
     * back in the foreground (`onResume` outside PiP, or a PiP exit that
     * lands already RESUMED). Intent delivery (`onCreate`/`onNewIntent`)
     * deliberately does NOT fold: it can precede the actual resume, and
     * consuming the stamp there would measure the absence too early.
     *
     * @return `true` when this return re-locked the app (used by tests to pin
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
 * [shouldRedirect] is consumed by PlayerActivity's return-to-player gate
 * (`onCreate`/`onNewIntent`/`onResume`/PiP-exit): when a gate is configured
 * AND the app is locked, the dedicated playback host redirects to MainActivity
 * (whose compose gate then renders the lock screen) instead of composing any
 * player UI.
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

    /** What MainActivity's composition renders while the app lock is consulted. */
    enum class ShellGate {
        /** Locked, and the PERSISTED security slice has not loaded (or is stalled) — block. */
        Blocked,

        /** Locked with a configured gate — render the PIN/biometric challenge. */
        LockScreen,

        /** Unlocked, or locked with no gate configured (no lock screen to show). */
        Browse,
    }

    /**
     * The shell gate decision for MainActivity's composition. [persisted] is
     * the PERSISTED security slice read at the current locking transition
     * ([AppLockReturn.persistedSecurity] on the activity side); `null` means
     * that read has not landed yet.
     *
     * Locked + unloaded → [ShellGate.Blocked] (fail CLOSED): the preferences
     * pipeline's seed is all-defaults ("no gate configured"), so deciding
     * from it would render browse content — or, with a stalled read, leave it
     * reachable — until the first real emission. Deciding from the persisted
     * slice instead keeps browse blocked until hydration resolves, then shows
     * the challenge exactly when the persisted configuration requires it.
     */
    fun shellGate(pinUnlocked: Boolean, persisted: SecuritySlice?): ShellGate = when {
        pinUnlocked -> ShellGate.Browse
        persisted == null -> ShellGate.Blocked
        isGateConfigured(persisted.pinLockEnabled, persisted.biometricLockEnabled) ->
            ShellGate.LockScreen
        else -> ShellGate.Browse
    }

    /**
     * Whether the shell's loaded gate snapshot must be dropped and re-read
     * before it may decide again. The compose gate keys its persisted read on
     * [AppLockState.unlocked] AND on the projected security fields: a
     * PIN/biometric toggle or PIN change in Settings moves the projection
     * without moving `pinUnlocked`, so a snapshot loaded at the previous lock
     * keeps [shellGate] (and the challenge's `pinHash`) on the pre-edit
     * configuration for as long as the activity lives. `true` means
     * "invalidate": no snapshot loaded yet, or any gate-relevant field
     * disagrees with the projection — the caller blanks the gate (so
     * [shellGate] returns [ShellGate.Blocked], fail closed) and re-reads the
     * PERSISTED slice. The projection only triggers the re-read; it is never
     * the decision input (its seed is all-defaults until DataStore hydrates).
     */
    fun gateSnapshotStale(
        loaded: SecuritySlice?,
        projectedPinLockEnabled: Boolean,
        projectedBiometricLockEnabled: Boolean,
        projectedPinHash: String?,
    ): Boolean = loaded == null ||
        loaded.pinLockEnabled != projectedPinLockEnabled ||
        loaded.biometricLockEnabled != projectedBiometricLockEnabled ||
        loaded.pinHash != projectedPinHash
}
