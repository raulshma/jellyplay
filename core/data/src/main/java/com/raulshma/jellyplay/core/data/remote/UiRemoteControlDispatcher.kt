package com.raulshma.jellyplay.core.data.remote

import android.util.Log
import com.raulshma.jellyplay.core.model.remote.GeneralCommand
import com.raulshma.jellyplay.core.model.remote.PlayRequest
import com.raulshma.jellyplay.core.model.remote.PlaybackDomain
import com.raulshma.jellyplay.core.model.remote.PlaystateCommand
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-cutting dispatcher for remote commands that aren't tied to a specific
 * playback engine — currently limited to logging fall-throughs.
 */
@Singleton
class UiRemoteControlDispatcher @Inject constructor() : RemoteControlDispatcher {

    override val domain: PlaybackDomain = PlaybackDomain.UNKNOWN

    override suspend fun play(request: PlayRequest) {
        // The UI dispatcher does not own a player. The video / audio dispatchers
        // emit the actual play request. Kept here for protocol completeness.
    }

    override suspend fun handlePlaystate(command: PlaystateCommand) {
        // No engine-agnostic playstate.
    }

    override suspend fun handleGeneral(command: GeneralCommand) {
        when (command) {
            is GeneralCommand.SetMaxStreamingBitrate -> {
                // The video dispatcher already applies this to the bound
                // engine. No persistent preference exists in this app for
                // max bitrate, so the per-engine call is the only effect.
                Log.d(TAG, "SetMaxStreamingBitrate=${command.bitrate} (applied at engine level)")
            }
            is GeneralCommand.DisplayMessage -> {
                // Surfaced by the app shell: MainViewModel collects
                // RemoteControlReceiver.displayMessages into the user message bus.
                Log.d(TAG, "DisplayMessage: ${command.header} | ${command.text}")
            }
            is GeneralCommand.Unknown -> Log.d(TAG, "Unhandled general command: ${command.name}")
            else -> Unit
        }
    }

    companion object {
        private const val TAG = "UiRemoteCtrl"
    }
}
