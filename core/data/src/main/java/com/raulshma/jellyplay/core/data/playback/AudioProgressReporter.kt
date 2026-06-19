package com.raulshma.jellyplay.core.data.playback

import androidx.media3.exoplayer.ExoPlayer
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.PlaybackProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class AudioProgressReporter(
    private val scope: CoroutineScope,
    private val playbackRepository: PlaybackRepository,
    private val remoteSessionActive: () -> Boolean,
    private val exoPlayerProvider: () -> ExoPlayer?,
    private val itemIdProvider: () -> String?,
    private val playSessionIdProvider: () -> String,
    private val playSessionIdSetter: (String) -> Unit,
) {
    private var progressJob: Job? = null

    fun start() {
        progressJob?.cancel()
        if (remoteSessionActive()) return
        progressJob = scope.launch {
            while (true) {
                delay(10_000)
                val player = exoPlayerProvider() ?: continue
                val itemId = itemIdProvider() ?: continue
                playbackRepository.reportPlaybackProgress(
                    PlaybackProgress(
                        itemId = itemId,
                        sessionId = playSessionIdProvider(),
                        positionTicks = player.currentPosition * 10_000,
                        isPaused = !player.isPlaying,
                    )
                )
            }
        }
    }

    fun reportStopped() {
        val player = exoPlayerProvider() ?: return
        val itemId = itemIdProvider() ?: return
        val sid = playSessionIdProvider()
        val pos = player.currentPosition * 10_000
        if (pos > 0) {
            scope.launch {
                playbackRepository.reportPlaybackStopped(itemId, sid, pos)
            }
        }
        playSessionIdSetter(UUID.randomUUID().toString())
    }

    fun cancel() {
        progressJob?.cancel()
    }
}
