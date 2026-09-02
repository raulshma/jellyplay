package com.raulshma.jellyplay.core.data.remote

import android.util.Log
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.core.model.remote.GeneralCommand
import com.raulshma.jellyplay.core.model.remote.PlayRequest
import com.raulshma.jellyplay.core.model.remote.PlaybackDomain
import com.raulshma.jellyplay.core.model.remote.PlaystateCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Routes remote "Play" / "Playstate" / "GeneralCommand" messages to the
 * currently-bound video engine, and emits a navigation request to open the
 * full-screen player when a fresh "Play" comes in.
 *
 * All engine calls are marshalled to the main thread via
 * [withContext] because the [RemoteControlReceiver] runs on
 * [Dispatchers.Default] and ExoPlayer rejects player mutations from any
 * other thread (`IllegalStateException: Player is accessed on the wrong
 * thread`).
 */
class VideoRemoteControlDispatcher(
    private val activePlayerController: ActivePlayerController,
    private val remoteNavigationBridge: RemoteNavigationBridge,
) : RemoteControlDispatcher {

    override val domain: PlaybackDomain = PlaybackDomain.VIDEO

    override suspend fun play(request: PlayRequest) {
        val firstId = request.itemIds.firstOrNull() ?: return
        remoteNavigationBridge.request(
            NavigationTarget.OpenVideoPlayer(
                itemId = firstId,
                mediaSourceId = request.mediaSourceId,
                startPositionTicks = request.startPositionTicks,
                audioStreamIndex = request.audioStreamIndex,
                subtitleStreamIndex = request.subtitleStreamIndex,
            )
        )
        if (com.raulshma.jellyplay.core.data.BuildConfig.DEBUG) {
            Log.d(TAG, "Video play requested: itemId=$firstId pos=${request.startPositionTicks} sub=${request.subtitleStreamIndex} audio=${request.audioStreamIndex}")
        }
    }

    override suspend fun handlePlaystate(command: PlaystateCommand) {
        withContext(Dispatchers.Main.immediate) {
            val engine = activePlayerController.engine ?: run {
                if (com.raulshma.jellyplay.core.data.BuildConfig.DEBUG) {
                    Log.d(TAG, "No active video engine for playstate ${command::class.simpleName}")
                }
                return@withContext
            }
            when (command) {
                PlaystateCommand.Stop -> {
                    // Jellyfin's "Stop" closes the player on the controlling
                    // device — pause + navigate away + halt the engine.
                    engine.stop()
                    remoteNavigationBridge.request(NavigationTarget.ClosePlayer)
                }
                PlaystateCommand.Pause -> engine.pause()
                PlaystateCommand.Unpause -> engine.play()
                PlaystateCommand.PlayPause -> if (engine.isPlaying.value) engine.pause() else engine.play()
                is PlaystateCommand.Seek -> engine.seekTo(command.positionTicks / 10_000L)
                PlaystateCommand.NextTrack,
                PlaystateCommand.PreviousTrack,
                PlaystateCommand.Rewind,
                PlaystateCommand.FastForward -> {
                    // No-op for single-item video playback.
                }
            }
        }
    }

    override suspend fun handleGeneral(command: GeneralCommand) {
        withContext(Dispatchers.Main.immediate) {
            val engine = activePlayerController.engine
            when (command) {
                is GeneralCommand.SetVolume -> {
                    val pct = command.volume0to100.coerceIn(0, 100) / 100f
                    engine?.setVolume(pct)
                    if (command.mute == true) {
                        engine?.setMuted(true)
                    }
                    if (com.raulshma.jellyplay.core.data.BuildConfig.DEBUG) {
                        Log.d(TAG, "SetVolume pct=$pct mute=${command.mute}")
                    }
                }
                GeneralCommand.VolumeUp -> {
                    engine?.increaseVolume(0.05f)
                }
                GeneralCommand.VolumeDown -> {
                    engine?.decreaseVolume(0.05f)
                }
                GeneralCommand.Mute -> engine?.setMuted(true)
                GeneralCommand.Unmute -> engine?.setMuted(false)
                GeneralCommand.ToggleMute -> {
                    val e = engine ?: return@withContext
                    if (e.volume == 0f) e.setMuted(false) else e.setMuted(true)
                }
                is GeneralCommand.SetAudioStreamIndex -> {
                    engine?.selectTrack(type = TrackType.AUDIO, index = command.index)
                    if (com.raulshma.jellyplay.core.data.BuildConfig.DEBUG) {
                        Log.d(TAG, "SetAudioStreamIndex=${command.index}")
                    }
                }
                is GeneralCommand.SetSubtitleStreamIndex -> {
                    engine?.selectTrack(type = TrackType.SUBTITLE, index = command.index)
                    if (com.raulshma.jellyplay.core.data.BuildConfig.DEBUG) {
                        Log.d(TAG, "SetSubtitleStreamIndex=${command.index}")
                    }
                }
                is GeneralCommand.SetRepeatMode,
                is GeneralCommand.SetShuffleQueue,
                is GeneralCommand.SetPlaybackOrder -> {
                    // Queue controls are not applicable to single-item video playback.
                }
                is GeneralCommand.SetMaxStreamingBitrate -> {
                    engine?.setMaxVideoBitrate(command.bitrate)
                    if (com.raulshma.jellyplay.core.data.BuildConfig.DEBUG) {
                        Log.d(TAG, "SetMaxStreamingBitrate=${command.bitrate}")
                    }
                }
                GeneralCommand.ToggleFullscreen -> {
                    // TV is always fullscreen; the phone player is itself fullscreen.
                }
                is GeneralCommand.DisplayMessage -> {
                    if (com.raulshma.jellyplay.core.data.BuildConfig.DEBUG) {
                        Log.d(TAG, "DisplayMessage: ${command.header} ${command.text}")
                    }
                }
                is GeneralCommand.Unknown -> {
                    if (com.raulshma.jellyplay.core.data.BuildConfig.DEBUG) {
                        Log.d(TAG, "Unhandled general command: ${command.name}")
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "VideoRemoteCtrl"
    }
}
