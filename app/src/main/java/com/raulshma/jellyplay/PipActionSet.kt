package com.raulshma.jellyplay

import com.raulshma.jellyplay.core.data.playback.PipAction

/**
 * Pure decision half of [PlayerActivity]'s PiP remote-action apparatus — the
 * recorded deferred "PiP action apparatus" design, landed now that PiP churn
 * resumed. Two tables that used to live Activity-inline with nothing pinning
 * their sync:
 *
 *  - the **action-set fold** ([actionSpecs]) — which actions render, in what
 *    order, given `isPlaying` / `pipHasNext`: the fixed skip-back →
 *    play-or-pause → skip-forward spine, the play/pause fork (icon AND title
 *    swap together), and the next-episode tail gated on [PipAction.NEXT]'s
 *    `hasNext` term;
 *  - the **id codec** ([idFor] / [actionForId]) — the wire ids that flow
 *    through the broadcast extra AND the PendingIntent request codes, so the
 *    encode (render) and decode (receiver) sides cannot drift.
 *
 * The Activity keeps only Android wiring: `RemoteAction`/`Icon`/`PendingIntent`
 * construction over each [ActionSpec] ([PlayerActivity.pipRemoteAction]) and
 * the receiver registration. Like [PipLifecyclePolicy], no Android framework
 * *types* cross this file — icon drawables and title strings travel as
 * resource ids (`Int`), resolved by the Activity.
 *
 * Protocol constants ([PIP_ACTION_BROADCAST], [PIP_ACTION_EXTRA], the id
 * table) moved verbatim from PlayerActivity's companion; their values are
 * wire-stable — outstanding PendingIntents and in-flight broadcasts reference
 * them across app updates, so they must never be renumbered or renamed.
 */
internal object PipActionSet {

    /** Broadcast action every PiP remote-action PendingIntent fires. */
    const val PIP_ACTION_BROADCAST = "com.raulshma.jellyplay.PIP_ACTION"

    /** Intent extra carrying the fired action's wire id. */
    const val PIP_ACTION_EXTRA = "pip_action_id"

    // ── Wire id table ───────────────────────────────────────────────────────

    const val PIP_ACTION_PLAY = 1
    const val PIP_ACTION_PAUSE = 2
    const val PIP_ACTION_SKIP_FORWARD = 3
    const val PIP_ACTION_SKIP_BACK = 4
    const val PIP_ACTION_NEXT = 5

    /**
     * One rendered remote action: the transport [PipAction] to dispatch, its
     * icon drawable resource, and its title string resource (the content
     * description too — the Activity passes the resolved title to both).
     */
    data class ActionSpec(val action: PipAction, val iconRes: Int, val titleRes: Int)

    /**
     * The action-set fold: skip-back → play/pause (icon+title fork on
     * [isPlaying]) → skip-forward, plus next ONLY when [hasNext] (the
     * `pipHasNext` gate — a hidden next button with no next episode would
     * dead-tap). Order is presentation-stable; ids come from [idFor].
     */
    fun actionSpecs(isPlaying: Boolean, hasNext: Boolean): List<ActionSpec> = buildList {
        add(ActionSpec(PipAction.SKIP_BACKWARD, android.R.drawable.ic_media_rew, R.string.pip_rewind))
        add(
            if (isPlaying) {
                ActionSpec(PipAction.PAUSE, android.R.drawable.ic_media_pause, R.string.media_pause)
            } else {
                ActionSpec(PipAction.PLAY, android.R.drawable.ic_media_play, R.string.media_play)
            },
        )
        add(ActionSpec(PipAction.SKIP_FORWARD, android.R.drawable.ic_media_ff, R.string.pip_forward))
        if (hasNext) {
            add(ActionSpec(PipAction.NEXT, android.R.drawable.ic_media_next, R.string.pip_next))
        }
    }

    // ── Id codec ────────────────────────────────────────────────────────────

    /**
     * Encodes a transport action as its wire id — the broadcast extra value
     * and the PendingIntent request code (distinct request codes per action,
     * so re-registering one action's PendingIntent never clobbers another's).
     */
    fun idFor(action: PipAction): Int = when (action) {
        PipAction.PLAY -> PIP_ACTION_PLAY
        PipAction.PAUSE -> PIP_ACTION_PAUSE
        PipAction.SKIP_FORWARD -> PIP_ACTION_SKIP_FORWARD
        PipAction.SKIP_BACKWARD -> PIP_ACTION_SKIP_BACK
        PipAction.NEXT -> PIP_ACTION_NEXT
    }

    /**
     * Decodes a received broadcast id back to its transport action — the
     * receiver-side twin of [idFor]. Unknown ids (including the receiver's
     * `getIntExtra(..., -1)` default and any future/foreign value) decode to
     * null, which the Activity drops.
     */
    fun actionForId(id: Int): PipAction? = when (id) {
        PIP_ACTION_PLAY -> PipAction.PLAY
        PIP_ACTION_PAUSE -> PipAction.PAUSE
        PIP_ACTION_SKIP_FORWARD -> PipAction.SKIP_FORWARD
        PIP_ACTION_SKIP_BACK -> PipAction.SKIP_BACKWARD
        PIP_ACTION_NEXT -> PipAction.NEXT
        else -> null
    }
}
