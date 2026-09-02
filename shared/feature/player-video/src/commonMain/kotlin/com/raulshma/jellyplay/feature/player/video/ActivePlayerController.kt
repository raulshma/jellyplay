package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine

/**
 * Active-engine registry seam for the video player (wave 8C): the member set
 * the commonMain [VideoPlayerViewModel] calls on the legacy `core:data`
 * `ActivePlayerController` singleton so remote-control paths (WebSocket-driven
 * RemoteControlReceiver) can drive playback without a ViewModel reference.
 *
 * [RemotePlayableEngine] is the shared player-contract interface
 * [MediaEngine][com.raulshma.jellyplay.feature.player.video.engine.MediaEngine]
 * extends, so the commonMain ViewModel binds engines type-safely. The
 * androidMain adapter ([AndroidActivePlayerController], module androidMain)
 * wraps the Hilt-owned legacy singleton; the jvmMain actual is a no-op stub.
 */
interface ActivePlayerController {

    /** The currently-bound engine, or null. */
    val engine: RemotePlayableEngine?

    /** Registers the currently-bound engine. */
    fun bindEngine(engine: RemotePlayableEngine)

    /** Clears the registration (full teardown path). */
    fun clearEngine()
}
