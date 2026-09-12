package com.raulshma.jellyplay.core.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton registry that holds a reference to the currently-bound video
 * [RemotePlayableEngine] so non-Compose layers (e.g. [RemoteControlReceiver]
 * running off the WebSocket) can drive playback without holding a
 * [androidx.lifecycle.ViewModel].
 *
 * Audio playback is intentionally not tracked here — the audio engine is
 * already a [Singleton] managed by the AudioPlaybackManager.
 */
class ActivePlayerController() {

    private val _activeEngine = MutableStateFlow<RemotePlayableEngine?>(null)
    val activeEngine: StateFlow<RemotePlayableEngine?> = _activeEngine.asStateFlow()

    /**
     * Register the currently-bound engine. Called by the video player ViewModel
     * when it spins up an engine and cleared in `onCleared` / before navigation
     * away.
     */
    fun bindEngine(engine: RemotePlayableEngine) {
        _activeEngine.value = engine
    }

    fun unbindEngine(engine: RemotePlayableEngine) {
        if (_activeEngine.value === engine) {
            _activeEngine.value = null
        }
    }

    fun clearEngine() {
        _activeEngine.value = null
    }

    val engine: RemotePlayableEngine? get() = _activeEngine.value
}
