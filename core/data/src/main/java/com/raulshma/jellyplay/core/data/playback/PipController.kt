package com.raulshma.jellyplay.core.data.playback

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
        _isInPipMode.value = false
        _shouldAutoEnterPip.value = false
        _pipDismissed.value = false
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
