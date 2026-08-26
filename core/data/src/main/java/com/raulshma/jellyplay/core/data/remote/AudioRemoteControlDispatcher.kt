package com.raulshma.jellyplay.core.data.remote

import android.util.Log
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.repository.MediaRepository
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
 * Routes remote "Play" / "Playstate" / "GeneralCommand" messages to
 * [AudioPlaybackManager] (which owns the singleton audio engine).
 *
 * [handlePlaystate] and [handleGeneral] marshal to the main thread because
 * the underlying ExoPlayer instance must only be touched from the
 * application looper and the receiver delivers these events on
 * [Dispatchers.Default].
 */
class AudioRemoteControlDispatcher(
    private val audioPlaybackManager: AudioPlaybackManager,
    private val mediaRepository: MediaRepository,
    private val remoteNavigationBridge: RemoteNavigationBridge,
) : RemoteControlDispatcher {

    override val domain: PlaybackDomain = PlaybackDomain.AUDIO

    override suspend fun play(request: PlayRequest) {
        withContext(Dispatchers.Main.immediate) {
            val firstId = request.itemIds.firstOrNull() ?: return@withContext
            // Build a queue for multi-item Play, then open the player.
            if (request.itemIds.size > 1) {
                val items = buildQueueItems(request.itemIds)
                if (items.isNotEmpty()) {
                    val startIndex = request.startIndex.coerceIn(0, items.lastIndex)
                    audioPlaybackManager.playQueue(items, startIndex)
                }
            } else {
                audioPlaybackManager.play(firstId)
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
                    // device. We need to actually tear down the audio engine
                    // (not just pause) so the mini-player dismisses and the
                    // server gets a final `playbackStopped` report.
                    audioPlaybackManager.stopAndRelease()
                    remoteNavigationBridge.request(NavigationTarget.ClosePlayer)
                }
                PlaystateCommand.Pause -> audioPlaybackManager.pause()
                PlaystateCommand.Unpause -> audioPlaybackManager.resume()
                PlaystateCommand.PlayPause -> audioPlaybackManager.togglePlayPause()
                is PlaystateCommand.Seek -> audioPlaybackManager.seekTo(command.positionTicks / 10_000L)
                PlaystateCommand.NextTrack -> audioPlaybackManager.skipToNext()
                PlaystateCommand.PreviousTrack -> audioPlaybackManager.skipToPrevious()
                PlaystateCommand.Rewind -> audioPlaybackManager.seekTo(
                    (audioPlaybackManager.currentPosition.value - 10_000L).coerceAtLeast(0L)
                )
                PlaystateCommand.FastForward -> audioPlaybackManager.seekTo(
                    audioPlaybackManager.currentPosition.value + 10_000L
                )
            }
        }
    }

    override suspend fun handleGeneral(command: GeneralCommand) {
        withContext(Dispatchers.Main.immediate) {
            when (command) {
                is GeneralCommand.SetVolume -> {
                    val pct = command.volume0to100.coerceIn(0, 100) / 100f
                    audioPlaybackManager.setVolume(pct)
                    command.mute?.let { audioPlaybackManager.setMuted(it) }
                    Log.d(TAG, "SetVolume pct=$pct mute=${command.mute}")
                }
                GeneralCommand.VolumeUp -> audioPlaybackManager.increaseVolume()
                GeneralCommand.VolumeDown -> audioPlaybackManager.decreaseVolume()
                GeneralCommand.Mute -> audioPlaybackManager.setMuted(true)
                GeneralCommand.Unmute -> audioPlaybackManager.setMuted(false)
                GeneralCommand.ToggleMute -> audioPlaybackManager.toggleMute()
                is GeneralCommand.SetRepeatMode -> {
                    when (command.mode) {
                        "RepeatOne" -> audioPlaybackManager.setRepeatMode(2)
                        "RepeatAll" -> audioPlaybackManager.setRepeatMode(1)
                        else -> audioPlaybackManager.setRepeatMode(0)
                    }
                }
                is GeneralCommand.SetShuffleQueue -> audioPlaybackManager.setShuffleMode(command.shuffle)
                is GeneralCommand.SetPlaybackOrder -> {
                    val shuffle = command.order.equals("Shuffle", ignoreCase = true) ||
                        command.order.equals("Random", ignoreCase = true)
                    audioPlaybackManager.setShuffleMode(shuffle)
                }
                is GeneralCommand.SetAudioStreamIndex,
                is GeneralCommand.SetSubtitleStreamIndex -> {
                    // Audio books / tracks don't expose stream indices via this protocol.
                }
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
        private const val TAG = "AudioRemoteCtrl"
    }
}
