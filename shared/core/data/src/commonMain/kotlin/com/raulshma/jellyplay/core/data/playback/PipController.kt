package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * THE Picture-in-Picture port: the one member set every player feature
 * (`player-video`, `player-live`) drives on the PiP state owner, promoted
 * here from the two module-local interface copies it used to be forked into
 * (one per player, each with its own androidMain adapter wrapping this
 * module's process singleton). `PipAction`/`PipTransport` travel with it —
 * the verbatim-duplicated twins are gone with the forks.
 *
 * The production impl is the androidMain **`AndroidPipController`**
 * singleton (this module) — the SAME instance the host PlayerActivity
 * injects, so a player ViewModel's writes and the Activity's collectors
 * observe one state; it also keeps the Android-typed extras the Activity
 * needs (Rational aspect, Rect source hint, `notifyPipDismissed` /
 * `setPipMode` / `shouldAutoEnterPip` / `autoExitPip`) which deliberately do
 * NOT ride this common port. Desktop binds a no-op.
 *
 * `pipDismissed` / `consumeAutoExitPip` / `clearPipDismissed` are one-shot
 * LATCHES on a process singleton (issue #145): the Activity's auto-exit
 * collector translates `requestAutoExitPip` into `notifyPipDismissed`, the
 * player VM reacts (pause + teardown + close) and re-clears the latch, and
 * every fresh load defensively clears both latches before the session
 * starts — a flag left set by an abnormally torn-down previous session must
 * never greet the next load.
 */
interface PipController {

    /** Whether the player is currently in a system PiP window. */
    val isInPipMode: StateFlow<Boolean>

    /** One-shot latch set when the PiP window must close (auto-exit path). */
    val pipDismissed: StateFlow<Boolean>

    /**
     * Remote-action bridge armed by the player ViewModel (re-armed on every
     * load — the Activity dispatches PiP remote actions through it).
     */
    var pipTransport: PipTransport?

    /** Whether a "next episode" action is available on the PiP window. */
    var pipHasNext: Boolean

    /** Mirror of the play state so the Activity renders the correct PiP icon. */
    fun setPlaying(playing: Boolean)

    /** Locks/unlocks the player controls (screen-lock overlay). */
    fun setControlsLocked(locked: Boolean)

    /** Arms/disarms auto-enter-on-dismiss PiP for the current engine. */
    fun requestAutoEnterPip(shouldEnter: Boolean)

    /** Requests the system to exit the PiP window (used on end/error). */
    fun requestAutoExitPip()

    /**
     * Defensively resets the auto-exit latch before a fresh load: the
     * controller is a process singleton whose one-shot flags outlive the
     * Activity (issue #145).
     */
    fun consumeAutoExitPip()

    /** Clears the [pipDismissed] latch after the screen handled it. */
    fun clearPipDismissed()

    /**
     * Pushes the media's aspect ratio (`width to height`) so the PiP window
     * matches the content instead of letterboxing to 16:9; null resets it.
     */
    fun setPipAspectRatio(aspect: Pair<Int, Int>?)

    /**
     * Forwards the video surface's window bounds as the PiP source-rect hint.
     */
    fun updatePipSourceRect(left: Int, top: Int, right: Int, bottom: Int)

    /** Clears all PiP state (full teardown). */
    fun reset()
}

/**
 * Transport bridge used by PiP remote actions. Implemented by the player
 * ViewModel and registered on [PipController] so the Activity can dispatch
 * play/pause/skip/next without a core→feature dependency.
 */
fun interface PipTransport {
    /** Dispatched when the user taps a PiP remote action. */
    fun handle(action: PipAction)
}

/** The set of PiP remote actions exposed on the PiP window. */
enum class PipAction {
    PLAY,
    PAUSE,
    SKIP_FORWARD,
    SKIP_BACKWARD,
    NEXT,
}
