package com.raulshma.jellyplay.feature.player.live

import kotlinx.coroutines.flow.StateFlow

/**
 * PiP control seam for the live player (wave 19C): the member set
 * [LiveTvPlayerViewModel] actually drives so live playback mounted in the
 * app's dedicated PlayerActivity host can enter system Picture-in-Picture —
 * the same host VOD plays in. Shape mirrors the wave-8C `player-video`
 * seam, but trimmed to live's usage (no pipHasNext: live has no "next
 * episode") and owned by this module so player-live keeps no dependency on
 * `player-video` (the only shared→shared feature edge so far is
 * subtitle-tester's androidMain-only one).
 *
 * The androidMain adapter (`AndroidPipController`, module androidMain) wraps
 * the legacy `core:data` `PipController` singleton — the same instance the
 * app's PlayerActivity injects, so VM writes and Activity reads observe one
 * state. Pure-data [PipAction]/[PipTransport] mirror the legacy declarations
 * one-to-one so the ViewModel keeps the exact `when` dispatch shape the VOD
 * ViewModel uses.
 *
 * The jvm target needs no actual: the seam is a nullable ctor dep of
 * [LiveTvPlayerViewModel] (the `LivePlayerAudio` pattern) defaulting to null,
 * and the live screen never composes on desktop — `playerLiveModule`'s
 * desktop registration stays documented-latent, exactly as it already is for
 * the audio/renderer seams.
 */
interface PipController {

    /** Whether the player is currently in a system PiP window. */
    val isInPipMode: StateFlow<Boolean>

    /**
     * Remote-action bridge armed by the ViewModel (re-armed on every engine
     * creation — the Activity dispatches PiP remote actions through it).
     */
    var pipTransport: PipTransport?

    /** Mirror of the play state so the Activity renders the correct PiP icon. */
    fun setPlaying(playing: Boolean)

    /** Arms/disarms auto-enter-on-dismiss PiP for the current engine. */
    fun requestAutoEnterPip(shouldEnter: Boolean)

    /** Requests the system to exit the PiP window (used on end/error). */
    fun requestAutoExitPip()

    /**
     * Pushes the media's aspect ratio (`width to height`) so the PiP window
     * matches the content instead of letterboxing to 16:9; null resets it.
     */
    fun setPipAspectRatio(aspect: Pair<Int, Int>?)

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
