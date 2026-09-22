package com.raulshma.jellyplay.core.data.remote

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.playback.AudioPlayerEngine
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.playback.AudioQueueManager
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.core.model.remote.GeneralCommand
import com.raulshma.jellyplay.core.model.remote.PlayRequest
import com.raulshma.jellyplay.core.model.remote.PlaybackDomain
import com.raulshma.jellyplay.core.model.remote.PlaystateCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Desktop twin of androidMain's `AndroidVideoRemoteControlDispatcher`
 * (jvmShared receiver extraction): routes remote
 * "Play" / "Playstate" / "GeneralCommand" messages to the currently-bound
 * video engine — on desktop the per-session MpvDesktopEngine registered in
 * the commonMain [ActivePlayerController] by the player screen — and emits
 * the open-player navigation on a fresh "Play".
 *
 * Marshals to [Dispatchers.Main] (the AWT event thread under Compose
 * Desktop): DesktopAudioQueueManager-style main-thread guards apply to the
 * desktop engine surfaces too, and the receiver delivers on
 * [Dispatchers.Default].
 */
class DesktopVideoRemoteControlDispatcher(
    private val activePlayerController: ActivePlayerController,
    private val remoteNavigationBridge: RemoteNavigationBridge,
) : VideoRemoteControlDispatcher {

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
        Log.d(TAG, "Video play requested: itemId=$firstId pos=${request.startPositionTicks} sub=${request.subtitleStreamIndex} audio=${request.audioStreamIndex}")
    }

    override suspend fun handlePlaystate(command: PlaystateCommand) {
        withContext(Dispatchers.Main.immediate) {
            val engine = activePlayerController.engine ?: run {
                Log.d(TAG, "No active video engine for playstate ${command::class.simpleName}")
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
                    Log.d(TAG, "SetVolume pct=$pct mute=${command.mute}")
                }
                GeneralCommand.VolumeUp -> engine?.increaseVolume(0.05f)
                GeneralCommand.VolumeDown -> engine?.decreaseVolume(0.05f)
                GeneralCommand.Mute -> engine?.setMuted(true)
                GeneralCommand.Unmute -> engine?.setMuted(false)
                GeneralCommand.ToggleMute -> {
                    val e = engine ?: return@withContext
                    if (e.volume == 0f) e.setMuted(false) else e.setMuted(true)
                }
                is GeneralCommand.SetAudioStreamIndex -> {
                    engine?.selectTrack(type = TrackType.AUDIO, index = command.index)
                    Log.d(TAG, "SetAudioStreamIndex=${command.index}")
                }
                is GeneralCommand.SetSubtitleStreamIndex -> {
                    engine?.selectTrack(type = TrackType.SUBTITLE, index = command.index)
                    Log.d(TAG, "SetSubtitleStreamIndex=${command.index}")
                }
                is GeneralCommand.SetRepeatMode,
                is GeneralCommand.SetShuffleQueue,
                is GeneralCommand.SetPlaybackOrder -> {
                    // Queue controls are not applicable to single-item video playback.
                }
                is GeneralCommand.SetMaxStreamingBitrate -> {
                    engine?.setMaxVideoBitrate(command.bitrate)
                    Log.d(TAG, "SetMaxStreamingBitrate=${command.bitrate}")
                }
                GeneralCommand.ToggleFullscreen -> {
                    // Remote fullscreen toggling is not wired on desktop v1;
                    // the window's F11/title-bar controls own placement.
                }
                // Nav-ladder + DisplayContent + TakeScreenshot never reach a
                // dispatcher (the receiver routes them UI-side before
                // dispatch) — exhaustiveness backstop only.
                GeneralCommand.Back,
                GeneralCommand.Select,
                GeneralCommand.MoveUp,
                GeneralCommand.MoveDown,
                GeneralCommand.MoveLeft,
                GeneralCommand.MoveRight,
                GeneralCommand.GoHome,
                GeneralCommand.GoToSettings,
                GeneralCommand.GoToSearch,
                GeneralCommand.ToggleContextMenu,
                is GeneralCommand.DisplayContent,
                GeneralCommand.TakeScreenshot,
                is GeneralCommand.DisplayMessage,
                is GeneralCommand.Unknown -> Unit
            }
        }
    }

    companion object {
        private const val TAG = "DesktopVideoRemoteCtrl"
    }
}

/**
 * Desktop twin of androidMain's `AndroidAudioRemoteControlDispatcher`:
 * drives the shared-contract desktop audio core — [AudioQueueManager] for
 * queue mutations and [AudioPlayerEngine] for transport (both implemented by
 * the one DesktopAudioQueueManager single; passed as two ports so this class
 * names only the contracts).
 *
 * Divergence from the Android twin (declared): the desktop audio core
 * exposes no volume/mute surface, so the volume-family general commands are
 * logged no-ops here — desktop audio volume follows the system mixer.
 */
class DesktopAudioRemoteControlDispatcher(
    private val audioQueueManager: AudioQueueManager,
    private val audioPlayerEngine: AudioPlayerEngine,
    private val mediaRepository: MediaRepository,
    private val remoteNavigationBridge: RemoteNavigationBridge,
) : AudioRemoteControlDispatcher {

    override val domain: PlaybackDomain = PlaybackDomain.AUDIO

    override suspend fun play(request: PlayRequest) {
        withContext(Dispatchers.Main.immediate) {
            val firstId = request.itemIds.firstOrNull() ?: return@withContext
            // Build a queue for multi-item Play, then open the player.
            if (request.itemIds.size > 1) {
                val items = buildQueueItems(request.itemIds)
                if (items.isNotEmpty()) {
                    val startIndex = request.startIndex.coerceIn(0, items.lastIndex)
                    audioQueueManager.playQueue(items, startIndex)
                }
            } else {
                audioPlayerEngine.play(firstId)
            }
            remoteNavigationBridge.request(NavigationTarget.OpenAudioPlayer(firstId))
            Log.d(TAG, "Audio play requested: itemId=$firstId pos=${request.startPositionTicks} queueSize=${request.itemIds.size}")
        }
    }

    override suspend fun handlePlaystate(command: PlaystateCommand) {
        withContext(Dispatchers.Main.immediate) {
            when (command) {
                PlaystateCommand.Stop -> {
                    // Jellyfin's "Stop" closes the player on the controlling
                    // device — tear the audio core down (not just pause) so
                    // the now-playing state clears and the server gets the
                    // final stop report.
                    audioPlayerEngine.stopAndRelease()
                    remoteNavigationBridge.request(NavigationTarget.ClosePlayer)
                }
                PlaystateCommand.Pause -> audioPlayerEngine.pause()
                PlaystateCommand.Unpause -> {
                    // AudioPlayerEngine's only play-shaped member from a
                    // paused state is the toggle — guarded so a racing
                    // Unpause never pauses.
                    if (!audioPlayerEngine.isPlaying.value) audioPlayerEngine.togglePlayPause()
                }
                PlaystateCommand.PlayPause -> audioPlayerEngine.togglePlayPause()
                is PlaystateCommand.Seek -> audioPlayerEngine.seekTo(command.positionTicks / 10_000L)
                PlaystateCommand.NextTrack -> audioQueueManager.skipToNext()
                PlaystateCommand.PreviousTrack -> audioQueueManager.skipToPrevious()
                PlaystateCommand.Rewind -> audioPlayerEngine.seekTo(
                    (audioPlayerEngine.currentPosition.value - 10_000L).coerceAtLeast(0L)
                )
                PlaystateCommand.FastForward -> audioPlayerEngine.seekTo(
                    audioPlayerEngine.currentPosition.value + 10_000L
                )
            }
        }
    }

    override suspend fun handleGeneral(command: GeneralCommand) {
        withContext(Dispatchers.Main.immediate) {
            when (command) {
                is GeneralCommand.SetRepeatMode -> {
                    when (command.mode) {
                        "RepeatOne" -> audioQueueManager.setRepeatMode(2)
                        "RepeatAll" -> audioQueueManager.setRepeatMode(1)
                        else -> audioQueueManager.setRepeatMode(0)
                    }
                }
                is GeneralCommand.SetShuffleQueue -> audioQueueManager.setShuffleMode(command.shuffle)
                is GeneralCommand.SetPlaybackOrder -> {
                    val shuffle = command.order.equals("Shuffle", ignoreCase = true) ||
                        command.order.equals("Random", ignoreCase = true)
                    audioQueueManager.setShuffleMode(shuffle)
                }
                // The desktop audio core owns no volume/mute scalar (system
                // mixer territory) — declared no-op, see class KDoc.
                is GeneralCommand.SetVolume,
                GeneralCommand.VolumeUp,
                GeneralCommand.VolumeDown,
                GeneralCommand.Mute,
                GeneralCommand.Unmute,
                GeneralCommand.ToggleMute -> {
                    Log.d(TAG, "Audio volume command ${command::class.simpleName} — no desktop audio volume surface")
                }
                is GeneralCommand.SetAudioStreamIndex,
                is GeneralCommand.SetSubtitleStreamIndex -> {
                    // Audio tracks don't expose stream indices via this protocol.
                }
                // Nav-ladder + DisplayContent + TakeScreenshot never reach a
                // dispatcher (the receiver routes them UI-side before
                // dispatch) — exhaustiveness backstop only.
                GeneralCommand.Back,
                GeneralCommand.Select,
                GeneralCommand.MoveUp,
                GeneralCommand.MoveDown,
                GeneralCommand.MoveLeft,
                GeneralCommand.MoveRight,
                GeneralCommand.GoHome,
                GeneralCommand.GoToSettings,
                GeneralCommand.GoToSearch,
                GeneralCommand.ToggleContextMenu,
                is GeneralCommand.DisplayContent,
                GeneralCommand.TakeScreenshot,
                is GeneralCommand.SetMaxStreamingBitrate,
                GeneralCommand.ToggleFullscreen,
                is GeneralCommand.DisplayMessage,
                is GeneralCommand.Unknown -> Unit
            }
        }
    }

    private suspend fun buildQueueItems(itemIds: List<String>): List<AudioQueueItem> = coroutineScope {
        itemIds.map { id ->
            async {
                mediaRepository.getMediaDetail(id).getOrNull()?.let { detail ->
                    AudioQueueItem(
                        id = id,
                        name = detail.item.name,
                        artist = detail.item.albumArtist ?: detail.item.artistItems.firstOrNull()?.name ?: "",
                        album = detail.item.album,
                        imageUrl = null,
                        mediaSourceId = detail.mediaSources.firstOrNull()?.id,
                        durationMs = detail.item.runTimeTicks?.div(10_000L) ?: 0L,
                        normalizationGain = detail.item.normalizationGain,
                    )
                }
            }
        }.awaitAll().filterNotNull()
    }

    companion object {
        private const val TAG = "DesktopAudioRemoteCtrl"
    }
}
