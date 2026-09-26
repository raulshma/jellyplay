package com.raulshma.jellyplay.core.data.remote

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton registry that holds a reference to the currently-bound video
 * [RemotePlayableEngine] so non-Compose layers (e.g. [RemoteControlReceiver]
 * running off the WebSocket) can drive playback without holding a
 * [androidx.lifecycle.ViewModel].
 *
 * Promoted from androidMain to commonMain with the desktop receiver port:
 * desktop's video player binds its per-session mpv engine through
 * the same registry (feature/player-video's jvmMain adapter), so remote
 * playstate/volume/screenshot commands reach the desktop engine too. The
 * class is pure coroutines — nothing platform-specific was ever in it.
 *
 * Audio playback is intentionally not tracked here — the audio engine is
 * already a singleton managed by the platform audio core
 * (Android: AudioPlaybackManager; desktop: DesktopAudioQueueManager).
 */
class ActivePlayerController() {

    private val _activeEngine = MutableStateFlow<RemotePlayableEngine?>(null)
    val activeEngine: StateFlow<RemotePlayableEngine?> = _activeEngine.asStateFlow()

    /**
     * Remote screenshot requests ("TakeScreenshot"). The receiver
     * emits here while a video engine is bound; the mounted player screen
     * collects [screenshotRequests] and runs the SAME frame-capture path as
     * its overflow-menu action (PixelCopy on Android, the engine's mpv
     * screenshot command on desktop). Fire-and-forget with a buffer of one:
     * a burst coalesces — there is nothing useful about capturing twice
     * within one frame.
     */
    private val _screenshotRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val screenshotRequests: SharedFlow<Unit> = _screenshotRequests.asSharedFlow()

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

    /** Emits one [screenshotRequests] event (remote "TakeScreenshot"). */
    fun requestScreenshot() {
        _screenshotRequests.tryEmit(Unit)
    }
}
