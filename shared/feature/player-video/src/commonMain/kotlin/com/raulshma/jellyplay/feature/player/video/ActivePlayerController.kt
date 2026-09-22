package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine
import kotlinx.coroutines.flow.SharedFlow

/**
 * Active-engine registry seam for the video player: the member set
 * the commonMain [VideoPlayerViewModel] calls on the legacy `core:data`
 * `ActivePlayerController` singleton so remote-control paths (WebSocket-driven
 * RemoteControlReceiver) can drive playback without a ViewModel reference.
 *
 * [RemotePlayableEngine] is the shared player-contract interface
 * [MediaEngine][com.raulshma.jellyplay.feature.player.video.engine.MediaEngine]
 * extends, so the commonMain ViewModel binds engines type-safely. The
 * androidMain adapter ([AndroidActivePlayerController], module androidMain)
 * wraps the Hilt-owned legacy singleton; the jvmMain actual is a no-op stub.
 *
 * [screenshotRequests] carries remote "TakeScreenshot" commands to the
 * mounted player screen — the SAME frame-capture path the overflow-menu
 * screenshot button drives.
 */
interface ActivePlayerController {

    /** The currently-bound engine, or null. */
    val engine: RemotePlayableEngine?

    /**
     * Remote screenshot requests: the receiver emits while an engine
     * is bound; the mounted [VideoPlayerScreen] collects this and runs its
     * screenshot action. Fire-and-forget SharedFlow — no replay.
     */
    val screenshotRequests: SharedFlow<Unit>

    /** Registers the currently-bound engine. */
    fun bindEngine(engine: RemotePlayableEngine)

    /** Clears the registration (full teardown path). */
    fun clearEngine()
}
