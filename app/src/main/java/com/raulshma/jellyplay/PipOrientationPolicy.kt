package com.raulshma.jellyplay

import com.raulshma.jellyplay.feature.player.video.PlayerOrientationLock

/**
 * Pure PiP orientation machine for [PlayerActivity] — the dock/restore
 * protocol that keeps the player's semantic orientation intent across a
 * system-PiP round trip (issue #186: a landscape player expanded from PiP
 * stayed portrait with the rotate button unable to recover). Sibling of
 * [PipLifecyclePolicy] with the same pure-JVM contract — no Android framework
 * types cross this file, every event returns a [Decision] (commands in,
 * sealed actions out) — so the tested dismiss/expand `Decision` shape there
 * stays untouched; orientation gets its own state instead of hanging off it.
 *
 * The vocabulary is [PlayerOrientationLock] — the same platform-neutral enum
 * the player screen records through `PlayerWindowOps.recordOrientationIntent`
 * (the `PlayerOrientationHost` seam) — so the Activity never infers intent
 * from the current window request: `requestedOrientation` is an
 * implementation detail that can legitimately read UNSPECIFIED (cast, TV, or
 * a PiP entry landing inside the screen's 400 ms orientation settle).
 *
 * ## The dock/restore protocol
 *
 *  - **Entry** ([onEntered]) stashes the player's desired intent once and
 *    docks. Repeated `onPictureInPictureModeChanged(true)` callbacks (OEM
 *    re-entry, PiP resize, configuration ripple) return [Decision.None] —
 *    the first stash wins and the already-reset window request can never
 *    overwrite it.
 *  - **Expand** ([onExited] at [Phase.RESUMED]) is the only path that
 *    restores: the stash is re-applied to the window and the caller clears
 *    the dock state.
 *  - **The OEM ordering where the expand callback fires before onResume**
 *    ([Phase.STARTED_NOT_RESUMED]) keeps the stash; the following [onResumed]
 *    applies it. A keep-alive dismissal also lands here — the stash stays
 *    harmlessly until a later resume (the reopen-from-notification path
 *    restores the original orientation) or destroy.
 *  - **Dismiss** ([Phase.BELOW_STARTED]) clears without writing — no
 *    rotation flash on the revealed browse UI or the hidden player.
 *
 * The Activity stores the current semantic intent in `orientationIntent`;
 * the [State] here stores only the PiP stash/dock flag. The stash rides the
 * Activity's `onSaveInstanceState` (an immutable [State] reconstruction), so
 * an OEM recreation during the PiP transition still expands to the pre-PiP
 * orientation.
 */
internal object PipOrientationPolicy {

    /**
     * The PiP-side orientation state — the Activity's own field; nothing here
     * mutates. [stashedIntent] is the intent captured at PiP entry; null when
     * nothing was captured (no concrete intent existed anywhere).
     */
    data class State(
        val stashedIntent: PlayerOrientationLock? = null,
        val docked: Boolean = false,
    )

    /** What the host must execute for one event. */
    sealed interface Decision {
        /** Nothing to do (repeated callback, or nothing stashed). */
        data object None : Decision

        /** Write UNSPECIFIED while docked; [stash] is the intent to keep. */
        data class Dock(val stash: PlayerOrientationLock?) : Decision

        /** Re-apply [intent]; the caller clears the stash. */
        data class Restore(val intent: PlayerOrientationLock) : Decision

        /** Keep the stash for a later resume (OEM callback ordering). */
        data object Keep : Decision

        /** Dismiss path: drop the stash. */
        data object Clear : Decision
    }

    /**
     * Coarse lifecycle phase at `onPipModeChanged(false)` time — mirrors
     * [PipLifecyclePolicy.Phase]; orientation folds on the same distinctions
     * the dismiss/expand protocol makes there.
     */
    enum class Phase {
        /** Below STARTED — already past onStop, will not resume (a dismiss). */
        BELOW_STARTED,

        /** >= STARTED but < RESUMED — the early-expand / keep-alive window. */
        STARTED_NOT_RESUMED,

        /** >= RESUMED — a genuine expand. */
        RESUMED,
    }

    // ── PiP entry ───────────────────────────────────────────────────────────

    /**
     * PiP window confirmed — capture the intent and dock. [desiredIntent] is
     * the Activity's recorded semantic intent; [currentRequested] the current
     * window request, the fallback for an entry that raced the screen's
     * record (nothing recorded yet). UNSPECIFIED is never a stash — it means
     * "no concrete intent" on either source, and a UNSPECIFIED stash would
     * both restore nothing on expand and rot the create-in-PiP stash check.
     * Already docked → [Decision.None]: the repeat callbacks OEMs fire read
     * the already-reset window request and must not overwrite the first
     * stash.
     */
    fun onEntered(
        state: State,
        desiredIntent: PlayerOrientationLock?,
        currentRequested: PlayerOrientationLock?,
    ): Decision {
        if (state.docked) return Decision.None
        val stash = desiredIntent?.takeIf { it != PlayerOrientationLock.UNSPECIFIED }
            ?: currentRequested?.takeIf { it != PlayerOrientationLock.UNSPECIFIED }
        return Decision.Dock(stash)
    }

    // ── PiP exit ────────────────────────────────────────────────────────────

    /**
     * PiP window left — the expand-vs-dismiss fold on [phase] (the same
     * distinction `PipLifecyclePolicy.pipExited` makes at the call site):
     * RESUMED restores (or clears when nothing was stashed),
     * STARTED_NOT_RESUMED keeps the stash for the following [onResumed],
     * BELOW_STARTED clears without writing (the dismiss paths must not
     * rotate the revealed browse UI or the hidden player).
     */
    fun onExited(state: State, phase: Phase): Decision = when (phase) {
        Phase.RESUMED -> state.stashedIntent?.let { Decision.Restore(it) } ?: Decision.Clear
        Phase.STARTED_NOT_RESUMED -> Decision.Keep
        Phase.BELOW_STARTED -> Decision.Clear
    }

    // ── Resume paths ────────────────────────────────────────────────────────

    /**
     * The re-assert point for OEMs where `onPipModeChanged(false)` fires
     * before the activity reports RESUMED (the [Decision.Keep] ordering):
     * once the activity actually resumes OUT of PiP with a stash alive, the
     * stashed intent applies. A resume that is still in PiP — or a normal
     * foreground resume with nothing stashed — is a no-op.
     */
    fun onResumed(state: State, inPip: Boolean): Decision {
        if (inPip) return Decision.None
        return state.stashedIntent?.let { Decision.Restore(it) } ?: Decision.None
    }
}
