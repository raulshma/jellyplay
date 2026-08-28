package com.raulshma.jellyplay.shell

import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-scoped holder for the PIN/biometric lock flag (wave 20E) — the single
 * source of truth for **"is the app unlocked right now"**.
 *
 * Before wave 20E this flag lived as a compose-local `mutableStateOf` field on
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
 *  - It stores ONLY the unlocked flag. "Is a gate configured at all" (PIN
 *    and/or biometric enabled in [SecuritySlice]) stays derived from
 *    preferences at each read site — same as MainActivity always did — via
 *    [AppLockRedirect.isGateConfigured].
 *  - No timers, no persistence: the flag resets to locked (`false`) on process
 *    death, and re-locks only through the existing call sites that used to
 *    flip the compose-local (PIN/biometric unlock in MainActivity, the
 *    auto-lock-on-resume timeout in MainActivity.onResume).
 *
 * Registered in `androidAppModule`; both hosts resolve it through Koin like
 * the other shell infrastructure.
 */
class AppLockState {

    private val _unlocked = MutableStateFlow(false)

    /** `true` once the user cleared MainActivity's [com.raulshma.jellyplay.core.ui.components.AuthChallengeScreen]; `false` while locked (and on cold start). */
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    /** Marks the app unlocked — called from MainActivity's PIN-success / biometric-success paths. */
    fun unlock() {
        _unlocked.value = true
    }

    /** Marks the app locked — called from MainActivity's auto-lock-on-resume timeout. */
    fun lock() {
        _unlocked.value = false
    }
}

/**
 * Pure decision helpers for the app PIN/biometric gate (wave 20E) — kept as a
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
}
