package com.raulshma.jellyplay.core.data.remote

import com.raulshma.jellyplay.core.model.remote.GeneralCommand
import com.raulshma.jellyplay.core.model.remote.PlayRequest
import com.raulshma.jellyplay.core.model.remote.PlaystateCommand

/**
 * Strategy for routing a remote control request to the appropriate engine(s).
 *
 * One implementation per playback domain (video, audio) and one for UI-only
 * side effects (toast, preferences). The [RemoteControlReceiver] picks a
 * dispatcher based on the request's [com.raulshma.jellyplay.core.model.remote.PlaybackDomain].
 *
 * The control methods are `suspend` so implementations can safely switch to
 * the engine's owning thread (e.g. main for ExoPlayer) before issuing player
 * commands. The [RemoteControlReceiver] runs on
 * [kotlinx.coroutines.Dispatchers.Default] and would otherwise trip
 * [IllegalStateException] when calling into strict engines.
 */
interface RemoteControlDispatcher {

    /**
     * The domain this dispatcher is responsible for. Used by
     * [RemoteControlReceiver] to pick the right dispatcher.
     */
    val domain: com.raulshma.jellyplay.core.model.remote.PlaybackDomain

    suspend fun play(request: PlayRequest)

    suspend fun handlePlaystate(command: PlaystateCommand)

    suspend fun handleGeneral(command: GeneralCommand)
}
