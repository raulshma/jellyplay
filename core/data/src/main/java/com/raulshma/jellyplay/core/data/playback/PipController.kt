package com.raulshma.jellyplay.core.data.playback

import android.util.Rational
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deep module owning Picture-in-Picture state and the remote-action transport.
 *
 * Split out of `PlayerLifecycleManager` so the Activity↔engine lifecycle bridge
 * and the PiP window state are two distinct modules, each deep on its own
 * responsibility. The Activity observes `isInPipMode` / `shouldAutoEnterPip` /
 * `pipDismissed` / `isPlaying` and dispatches `PipAction`s through `pipTransport`;
 * the ViewModel registers the transport and mirrors play state + hasNext.
 */
@Singleton
class PipController @Inject constructor() {

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    private val _shouldAutoEnterPip = MutableStateFlow(false)
    val shouldAutoEnterPip: StateFlow<Boolean> = _shouldAutoEnterPip.asStateFlow()

    /**
     * Set to `true` when PiP is dismissed (user swiped it away).
     * Uses StateFlow so the value survives lifecycle STOPPED→STARTED transitions.
     * The UI layer must call [clearPipDismissed] after handling the event.
     */
    private val _pipDismissed = MutableStateFlow(false)
    val pipDismissed: StateFlow<Boolean> = _pipDismissed.asStateFlow()

    /**
     * Transport bridge for PiP remote actions. Registered by the player
     * ViewModel when playback starts so the Activity can dispatch
     * play/pause/skip/next without a core→feature dependency. Cleared on [reset].
     */
    @Volatile
    var pipTransport: PipTransport? = null

    /** The current play state, mirrored so PiP can toggle its play/pause icon. */
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** Whether a "next" action is available (e.g. next episode in a series). */
    @Volatile
    var pipHasNext: Boolean = false

    /**
     * Whether the player controls are currently locked (screen-lock overlay up).
     * Mirrored from the player UI so the host Activity can suppress PiP auto-entry
     * while locked (gates `onUserLeaveHint` on `!isControlsLocked`).
     */
    @Volatile
    var isControlsLocked: Boolean = false
        private set

    fun setControlsLocked(locked: Boolean) {
        isControlsLocked = locked
    }

    /**
     * The video's aspect ratio as a `Rational` (width:height), derived from the
     * server-reported [com.raulshma.jellyplay.core.model.MediaStream] width/height.
     * `null` until media streams are known; the Activity falls back to 16:9.
     * Surfed as a flow so the Activity can re-apply params when it changes while
     * already in PiP (resolution/track swap).
     */
    private val _pipAspectRatio = MutableStateFlow<Rational?>(null)
    val pipAspectRatio: StateFlow<Rational?> = _pipAspectRatio.asStateFlow()

    /**
     * Source-rect hint for the PiP enter animation: the window bounds of the
     * video surface in window coordinates. Transient (recomputed on every
     * layout), so a plain `@Volatile` read at apply time is sufficient — no
     * flow needed. `null` clears the hint.
     */
    @Volatile
    var pipSourceRect: android.graphics.Rect? = null
        private set

    /**
     * Set by the ViewModel when playback ends or errors in PiP so the Activity
     * reuses the existing dismiss path (pause + navigate back) instead of
     * leaving a dead PiP window up.
     */
    private val _autoExitPip = MutableStateFlow(false)
    val autoExitPip: StateFlow<Boolean> = _autoExitPip.asStateFlow()

    fun setPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    /** Mirrors the engine play state so PiP can reflect it in its action icons. */
    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun requestAutoEnterPip(shouldEnter: Boolean) {
        _shouldAutoEnterPip.value = shouldEnter
    }

    /** Updates the PiP aspect ratio; `null` clears it (Activity falls back to 16:9). */
    fun setPipAspectRatio(ratio: Rational?) {
        _pipAspectRatio.value = ratio
    }

    /** Updates the source-rect hint; `null` clears it. */
    fun updatePipSourceRect(rect: android.graphics.Rect?) {
        pipSourceRect = rect
    }

    /**
     * Requests an auto-exit from PiP. The Activity collector translates this
     * into [notifyPipDismissed] so the existing dismiss machinery (pause +
     * navigate back) handles the exit uniformly.
     */
    fun requestAutoExitPip() {
        _autoExitPip.value = true
    }

    fun consumeAutoExitPip() {
        _autoExitPip.value = false
    }

    fun notifyPipDismissed() {
        _pipDismissed.value = true
    }

    fun clearPipDismissed() {
        _pipDismissed.value = false
    }

    /** Clears all PiP state. Called when playback ends. */
    fun reset() {
        pipTransport = null
        _isPlaying.value = false
        pipHasNext = false
        isControlsLocked = false
        _isInPipMode.value = false
        _shouldAutoEnterPip.value = false
        _pipDismissed.value = false
        _pipAspectRatio.value = null
        pipSourceRect = null
        _autoExitPip.value = false
    }
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
