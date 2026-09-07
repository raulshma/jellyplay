package com.raulshma.jellyplay

/**
 * Pure PiP ordering machine for [PlayerActivity] — the three-callback
 * protocol (`onPipModeChanged(false)` / `onResume` / `onStop`), the auto-enter
 * guards and the param-builder folds, extracted verbatim from the Activity so
 * the OEM-ordering rules are executable and JVM-testable. No Android
 * framework types cross this file: lifecycle phases, aspect ratios and source
 * rects arrive as plain enums/ints, and every event returns a [Decision]
 * (commands in, sealed actions out — the BackExitConfirmation precedent).
 * The `justExitedPip` flag stays at the call site; each [Decision] carries its
 * new value so the Activity's `onStop`/resume plumbing is a thin feed.
 *
 * ## The dismiss/expand protocol (the Activity's OEM-ordering comments — now
 * the spec)
 *
 * Leaving PiP fires `onPictureInPictureModeChanged(false)` for BOTH
 * expand-to-fullscreen and dismiss; the two are distinguished by the
 * activity's lifecycle state at callback time ([Phase]):
 *
 *  - **Expand**: the activity resumes, so state is >= RESUMED here (and
 *    onResume follows). Nothing arms; onResume clears `justExitedPip` for a
 *    genuine expand.
 *
 *  - **Dismiss (close icon / swipe-away)**: on some OEMs onStop fires BEFORE
 *    this callback (observed: onStop at isInPipMode=true, screenOff=false,
 *    justExitedPip=false — so onStop's dismiss arm misses — then this
 *    callback at state=CREATED). When state < STARTED the activity is already
 *    past onStop and will not resume, so finish here directly. This drives
 *    onDestroy → onDispose → viewModel.release() (engine stop + playback-stop
 *    report) — the same teardown as back-close.
 *
 *  - **Dismiss with background audio enabled** (screen interactive): only the
 *    PiP window closes. The activity stays alive (stopped, behind the
 *    revealed MainActivity) so its ViewModel, engine and media session are
 *    never torn down — playback keeps running exactly like the
 *    fullscreen→home minimise path. If the keyguard or a screen-off lands
 *    inside the dismissal transition, fall back to the finish path — that is
 *    issue #145's lock-dismiss territory where the dismiss machinery must
 *    stay deterministic.
 *
 * When state is >= STARTED but < RESUMED, the callback arms `justExitedPip`
 * (only when the dismissal should tear down; a keep-alive dismissal leaves it
 * clear so onStop skips finish) so a later onStop can still finish — the
 * ordering where `onPipModeChanged(false)` fires BEFORE onStop. onStop is the
 * discharge point: an armed flag finishes (unless a keyguard landing flipped
 * the keep-alive decision between the two callbacks), and an unarmed stop
 * while in PiP pauses only for screen-lock/keyguard so audio doesn't leak
 * with background audio OFF (by onStop the keyguard / non-interactive flags
 * have settled — during PiP the activity is already PAUSED, so onPause can't
 * reliably see the screen-off state; `onActivityPause` is itself a no-op when
 * background audio is ON, so a plain minimise while in PiP keeps playing).
 *
 * ## Auto-enter divergences (modelled explicitly, NOT unified)
 *
 * The three auto-enter predicates genuinely differ today and stay as three
 * named functions:
 *
 *  - [userLeaveAutoEnter] (`onUserLeaveHint`) gates on should-auto-enter +
 *    controls lock + screen/keyguard. It does NOT consult `isPlaying`.
 *  - [topResumedLossAutoEnter] (the `onTopResumedActivityChanged` fallback
 *    for OEMs/API levels where onUserLeaveHint is not reliably fired for
 *    gesture "slide up to home") ADDS `isPlaying` — that is the hand-copied
 *    divergence, kept: the fallback must not steal the screen for a paused
 *    session.
 *  - [systemAutoEnterEnabled] (the `setAutoEnterEnabled` pre-arm) checks
 *    should-auto-enter + isPlaying + screen/keyguard but deliberately NOT
 *    the controls lock — the system-side flag has never gated on it.
 *
 * All three share the screen/keyguard term for the same reason (issue #145):
 * several OEMs fire these callbacks for the power button too — entering PiP
 * behind the keyguard arms the dismiss/finish machinery while nothing can
 * observe it, and unlock then lands on the browse UI with the player gone.
 */
internal object PipLifecyclePolicy {

    /**
     * Coarse lifecycle phase at callback time — exactly the distinctions the
     * dismiss fold makes (`isAtLeast(STARTED)` / `isAtLeast(RESUMED)`).
     */
    enum class Phase {
        /** Below STARTED — already past onStop, will not resume. */
        BELOW_STARTED,

        /** >= STARTED but < RESUMED — the arm-justExitedPip window. */
        STARTED_NOT_RESUMED,

        /** >= RESUMED — a genuine expand. */
        RESUMED,
    }

    /** What the host must execute for one event. */
    sealed interface Action {
        /** Nothing to do. */
        data object None : Action

        /** `finish()` — the back-close teardown path. */
        data object Finish : Action

        /** `PlayerLifecycleManager.onActivityPause()`. */
        data object Pause : Action

        /** `PlayerLifecycleManager.onActivityResume()`. */
        data object Resume : Action

        /** `enterPipMode()`. */
        data object EnterPip : Action
    }

    /** The [Action] for one event plus the new value of `justExitedPip`. */
    data class Decision(val action: Action, val justExitedPip: Boolean)

    // ── PiP mode changes ────────────────────────────────────────────────────

    /**
     * PiP window left — the expand-vs-dismiss fold documented in the class
     * KDoc. [keepPlayerAlive] is the host's fresh
     * `backgroundAudioEnabled && !screenOffOrLocked` read; [isFinishing]
     * guards a double finish (the lock-gate redirect may already be tearing
     * the activity down).
     */
    fun pipExited(
        phase: Phase,
        keepPlayerAlive: Boolean,
        isFinishing: Boolean,
        justExitedPip: Boolean,
    ): Decision = when (phase) {
        // Dismiss where onStop already ran (some OEMs): finish directly — the
        // activity will never resume. Background audio keeps the player alive
        // instead (only the window closes).
        Phase.BELOW_STARTED -> Decision(
            action = if (!keepPlayerAlive && !isFinishing) Action.Finish else Action.None,
            justExitedPip = false,
        )
        // The arm window: a tearing-down dismissal arms justExitedPip so the
        // following onStop can finish; a keep-alive dismissal leaves it clear.
        Phase.STARTED_NOT_RESUMED -> Decision(Action.None, !keepPlayerAlive)
        // Genuine expand — nothing arms here; onResume does the clear.
        Phase.RESUMED -> Decision(Action.None, justExitedPip)
    }

    // ── Resume paths ────────────────────────────────────────────────────────

    /**
     * A genuine foreground resume: clears the dismiss arm and resumes the
     * player lifecycle. (The lock-gate re-check stays at the call site.)
     */
    fun onResume(justExitedPip: Boolean): Decision =
        Decision(Action.Resume, justExitedPip = false)

    // ── Stop / pause ────────────────────────────────────────────────────────

    /**
     * onStop — the discharge point of the protocol, per the class KDoc: an
     * armed `justExitedPip` finishes (re-checked against [keepPlayerAlive] so
     * a keyguard landing between the callback and this stop keeps the player
     * alive); an unarmed stop while in PiP pauses only for screen-lock or
     * keyguard. The branches are exclusive exactly as the inline original's
     * `if / else if` was — an armed stop never also pauses.
     */
    fun onStop(
        inPip: Boolean,
        screenOffOrLocked: Boolean,
        keepPlayerAlive: Boolean,
        isFinishing: Boolean,
        justExitedPip: Boolean,
    ): Decision = when {
        justExitedPip -> Decision(
            action = if (!keepPlayerAlive && !isFinishing) Action.Finish else Action.None,
            justExitedPip = false,
        )
        inPip && screenOffOrLocked -> Decision(Action.Pause, justExitedPip)
        else -> Decision(Action.None, justExitedPip)
    }

    /**
     * onPause — pause unless the activity is minimising into a PiP window
     * with an interactive screen (that minimise keeps playing; screen-lock
     * still pauses so audio doesn't leak with background audio OFF).
     */
    fun onPause(inPip: Boolean, screenOffOrLocked: Boolean): Action =
        if (!inPip || screenOffOrLocked) Action.Pause else Action.None

    // ── Auto-enter events ───────────────────────────────────────────────────

    /**
     * `onUserLeaveHint` — home gesture / power button. Uses
     * [userLeaveAutoEnter] (no isPlaying term — see the class KDoc).
     */
    fun onUserLeaveHint(
        shouldAutoEnter: Boolean,
        controlsLocked: Boolean,
        screenOffOrLocked: Boolean,
    ): Action =
        if (userLeaveAutoEnter(shouldAutoEnter, controlsLocked, screenOffOrLocked)) {
            Action.EnterPip
        } else {
            Action.None
        }

    /**
     * `onTopResumedActivityChanged` — both its roles in one fold: regaining
     * top-resumed discharges an armed `justExitedPip` (the resume twin of
     * [onResume] for OEM orderings where expand lands here), and losing it
     * during playback is the reliability fallback for
     * [onUserLeaveHint] ([topResumedLossAutoEnter], gated on [apiSupportsAutoEnter]
     * = `Build.VERSION.SDK_INT >= S` at the call site).
     */
    fun onTopResumedChanged(
        isTopResumed: Boolean,
        inPip: Boolean,
        apiSupportsAutoEnter: Boolean,
        shouldAutoEnter: Boolean,
        isPlaying: Boolean,
        controlsLocked: Boolean,
        screenOffOrLocked: Boolean,
        justExitedPip: Boolean,
    ): Decision = when {
        isTopResumed && justExitedPip -> Decision(Action.Resume, justExitedPip = false)
        apiSupportsAutoEnter && topResumedLossAutoEnter(
            shouldAutoEnter = shouldAutoEnter,
            isPlaying = isPlaying,
            controlsLocked = controlsLocked,
            screenOffOrLocked = screenOffOrLocked,
        ) && !isTopResumed && !inPip -> Decision(Action.EnterPip, justExitedPip)
        else -> Decision(Action.None, justExitedPip)
    }

    // ── Auto-enter predicates (the three genuinely-different copies) ────────

    /** `onUserLeaveHint`'s guard — no isPlaying term. */
    fun userLeaveAutoEnter(
        shouldAutoEnter: Boolean,
        controlsLocked: Boolean,
        screenOffOrLocked: Boolean,
    ): Boolean = shouldAutoEnter && !controlsLocked && !screenOffOrLocked

    /**
     * The top-resumed-loss fallback's guard — [userLeaveAutoEnter] plus the
     * `isPlaying` term (the documented hand-copied divergence).
     */
    fun topResumedLossAutoEnter(
        shouldAutoEnter: Boolean,
        isPlaying: Boolean,
        controlsLocked: Boolean,
        screenOffOrLocked: Boolean,
    ): Boolean = shouldAutoEnter && isPlaying && !controlsLocked && !screenOffOrLocked

    /**
     * The `setAutoEnterEnabled` pre-arm value — should-auto-enter + isPlaying
     * + screen/keyguard, deliberately WITHOUT the controls-lock term (that
     * system-side flag never gated on it; locking the controls overlay
     * mid-playback must not disable system auto-enter).
     */
    fun systemAutoEnterEnabled(
        shouldAutoEnter: Boolean,
        isPlaying: Boolean,
        screenOffOrLocked: Boolean,
    ): Boolean = shouldAutoEnter && isPlaying && !screenOffOrLocked

    // ── Param-builder folds ─────────────────────────────────────────────────

    /**
     * Aspect-ratio clamp to the platform's supported window
     * (`100/239 … 239/100`), over numerator/denominator pairs so the fold
     * stays JVM-testable (the call site maps `android.util.Rational` in and
     * out — same values, same cross-multiplied comparison the platform's
     * `Rational.compareTo` performs for the positive ratios video dimensions
     * produce). In-range inputs pass through unrenormalised.
     */
    fun clampAspectRatio(numerator: Int, denominator: Int): Pair<Int, Int> {
        val minNum = 100L
        val minDen = 239L
        val maxNum = 239L
        val maxDen = 100L
        val lhs = numerator.toLong() * minDen
        val rhs = minNum * denominator
        if (denominator != 0 && lhs < rhs) return 100 to 239
        val lhsMax = numerator.toLong() * maxDen
        val rhsMax = maxNum * denominator
        if (denominator != 0 && lhsMax > rhsMax) return 239 to 100
        return numerator to denominator
    }

    /**
     * Source-rect-hint validity: non-degenerate, on-screen at origin, and —
     * unless the window size is not yet known (`<= 0`, e.g. before first
     * layout, where the platform skips the bounds check anyway) — inside the
     * window bounds. The call site maps `android.graphics.Rect` in.
     */
    fun isValidSourceRect(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        windowWidth: Int,
        windowHeight: Int,
    ): Boolean {
        if (right - left <= 0 || bottom - top <= 0) return false
        if (left < 0 || top < 0) return false
        return windowWidth <= 0 || windowHeight <= 0 ||
            (right <= windowWidth && bottom <= windowHeight)
    }
}
