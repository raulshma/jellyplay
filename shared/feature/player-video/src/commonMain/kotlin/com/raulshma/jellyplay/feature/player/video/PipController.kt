package com.raulshma.jellyplay.feature.player.video

import kotlinx.coroutines.flow.StateFlow

/**
 * PiP control seam for the video player (wave 8C): the member set the
 * commonMain [VideoPlayerViewModel], [PlayerSessionManager] and the screen
 * actually use on the legacy `core:data` `PipController` singleton. Pure-data
 * [PipAction]/[PipTransport] mirror the legacy declarations one-to-one so the
 * ViewModel body keeps the exact `when` dispatch it had.
 *
 * The androidMain adapter ([AndroidPipController], module androidMain) wraps
 * the legacy Hilt-owned singleton (the same instance the app's PlayerActivity
 * injects — both sides observe one state), mapping [PipTransport] wrappers
 * and `(width, height)` aspect pairs onto `android.util.Rational` /
 * `android.graphics.Rect`. The jvmMain actual is a no-op stub.
 */
interface PipController {

    /** Whether the player is currently in a system PiP window. */
    val isInPipMode: StateFlow<Boolean>

    /** One-shot latch set when the user dismisses the PiP window. */
    val pipDismissed: StateFlow<Boolean>

    /**
     * Remote-action bridge armed by the ViewModel (re-armed on every load —
     * the Activity dispatches PiP remote actions through it).
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
 * Transport bridge used by PiP remote actions (common twin of the legacy
 * core:data `PipTransport`; the androidMain adapter wraps one in the other).
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
