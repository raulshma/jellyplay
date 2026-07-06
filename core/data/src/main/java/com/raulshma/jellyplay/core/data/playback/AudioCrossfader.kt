package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioCrossfader(
    private val scope: CoroutineScope,
    private val context: Context,
    private val effectsProcessor: AudioEffectsProcessor,
    private val mediaRepository: MediaRepository,
    private val downloadRepository: DownloadRepository,
    private val playbackRepository: PlaybackRepository,
    private val repeatModeProvider: () -> Int,
    private val crossfadeDurationMsProvider: () -> Long,
    private val isCrossfadingProvider: () -> Boolean,
    private val isCrossfadingSetter: (Boolean) -> Unit,
    private val exoPlayerProvider: () -> ExoPlayer?,
    private val queueSizeProvider: () -> Int,
    private val onGetNextItem: (Int) -> AudioQueueItem?,
    private val speedProvider: () -> Float,
    private val audioBufferProvider: () -> Pair<Int, Int>,
    private val onCrossfadeTransition: suspend (secondary: ExoPlayer, nextIndex: Int, nextItem: AudioQueueItem) -> Unit,
    private val detachPrimaryListener: (ExoPlayer) -> Unit,
) {
    private var crossfadePlayer: ExoPlayer? = null
    private var crossfadeJob: Job? = null

    fun cancel() {
        crossfadeJob?.cancel()
        crossfadeJob = null
        isCrossfadingSetter(false)
        crossfadePlayer?.let { player ->
            player.stop()
            player.release()
        }
        crossfadePlayer = null
        exoPlayerProvider()?.volume = 1.0f
    }

    fun maybeStart() {
        val crossfadeMs = crossfadeDurationMsProvider()
        if (crossfadeMs <= 0L || repeatModeProvider() == 2) return

        val player = exoPlayerProvider() ?: return
        val duration = player.duration
        val position = player.currentPosition
        val timeRemaining = duration - position

        if (timeRemaining <= crossfadeMs && timeRemaining > 0) {
            val nextIndex = player.currentMediaItemIndex + 1
            if (nextIndex >= queueSizeProvider() && repeatModeProvider() < 1) return
            prepareAndCrossfade(nextIndex, crossfadeMs)
        }
    }

    fun setPlaybackSpeed(value: Float) {
        crossfadePlayer?.setPlaybackSpeed(value)
    }

    fun setVolume(pct: Float) {
        crossfadePlayer?.volume = pct
    }

    private fun createCrossfadePlayer(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): androidx.media3.exoplayer.audio.AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(effectsProcessor.crossfadeReplayGainProcessor))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }

        val (minBufferMs, maxBufferMs) = audioBufferProvider()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                1_000,
                3_000
            )
            .setTargetBufferBytes(-1)
            .build()

        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }

    private fun prepareAndCrossfade(targetIndex: Int, crossfadeMs: Long) {
        if (isCrossfadingProvider()) return

        val actualIndex = if (targetIndex >= queueSizeProvider()) {
            if (repeatModeProvider() >= 1) 0 else return
        } else {
            targetIndex
        }

        val nextItem = onGetNextItem(actualIndex) ?: return
        isCrossfadingSetter(true)

        crossfadeJob = scope.launch {
            val detail = mediaRepository.getMediaDetail(nextItem.id)
            detail.onSuccess { d ->
                val source = d.mediaSources.firstOrNull()
                val localDownload = downloadRepository.getDownloadByMediaItemId(nextItem.id)
                val file = localDownload?.let { dl ->
                    java.io.File(dl.downloadPath).takeIf { f -> f.exists() }
                }
                val url = if (localDownload != null && file != null &&
                    localDownload.status == com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED
                ) {
                    Uri.fromFile(file).toString()
                } else {
                    playbackRepository.getStreamUrl(nextItem.id, source?.id ?: "", 0L)
                }

                val cfPlayer = createCrossfadePlayer()
                crossfadePlayer = cfPlayer

                val artUri = Uri.parse(playbackRepository.getImageUrl(nextItem.id, maxWidth = 600))
                val mediaItem = MediaItem.Builder()
                    .setMediaId(nextItem.id)
                    .setUri(url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(d.item.name)
                            .setArtist(d.item.albumArtist ?: d.item.artistItems.firstOrNull()?.name ?: "")
                            .setAlbumTitle(d.item.album ?: "")
                            .setArtworkUri(artUri)
                            .build()
                    )
                    .build()

                cfPlayer.setMediaItem(mediaItem)
                cfPlayer.prepare()

                val speed = speedProvider()
                cfPlayer.setPlaybackSpeed(speed)

                cfPlayer.playWhenReady = true
                cfPlayer.play()

                performVolumeCrossfade(crossfadeMs, actualIndex, nextItem)
            }.onFailure {
                // A transient failure (e.g. network error fetching the next
                // item's detail) must release the crossfade flag so that the
                // next attempt is not permanently blocked.
                isCrossfadingSetter(false)
            }
        }
    }

    private suspend fun performVolumeCrossfade(
        crossfadeMs: Long,
        nextIndex: Int,
        nextItem: AudioQueueItem,
    ) {
        val primary = exoPlayerProvider() ?: return
        val secondary = crossfadePlayer ?: return

        val targetVolume = if (effectsProcessor.nightModeEnabled.value) effectsProcessor.nightModeVolumeForStrength else 1.0f

        val steps = 30
        val stepDelay = crossfadeMs / steps

        for (i in 1..steps) {
            if (!scope.isActive || !isCrossfadingProvider()) {
                // Crossfade was cancelled (cancel already restored
                // the primary player's volume and released the secondary) or
                // the scope is no longer active. Do NOT touch `secondary`
                // here — it may already be released by cancel().
                return
            }

            val progress = i.toFloat() / steps
            primary.volume = targetVolume * (1.0f - progress)
            secondary.volume = targetVolume * progress

            delay(stepDelay)
        }

        primary.volume = 0.0f
        secondary.volume = 1.0f

        // Detach the AudioManager's shared playerListener BEFORE stop/release.
        // ExoPlayer.release() internally clears listeners so it's not a hard
        // leak — but between primary.release() and the subsequent
        // secondary.addListener(playerListener) in onCrossfadeTransition, any
        // queued callback draining on the application looper could otherwise
        // let playerListener observe events from a dead primary and mutate
        // _currentPosition/state from a stale source.
        detachPrimaryListener(primary)
        primary.stop()
        primary.release()

        crossfadePlayer = null

        onCrossfadeTransition(secondary, nextIndex, nextItem)
    }
}
