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
    private var lastPausedPositionTicks: Long = -1L

    fun start() {
        progressJob?.cancel()
        lastPausedPositionTicks = -1L
        if (remoteSessionActive()) return
        progressJob = scope.launch {
            while (true) {
                delay(10_000)
                val player = exoPlayerProvider() ?: continue
                val itemId = itemIdProvider() ?: continue
                val positionTicks = player.currentPosition * 10_000
                val isPaused = !player.isPlaying
                if (isPaused && positionTicks == lastPausedPositionTicks) continue
                if (isPaused) lastPausedPositionTicks = positionTicks else lastPausedPositionTicks = -1L
                playbackRepository.reportPlaybackProgress(
                    PlaybackProgress(
                        itemId = itemId,
                        sessionId = playSessionIdProvider(),
                        positionTicks = positionTicks,
                        isPaused = isPaused,
                    )
                )
            }
        }
    }

    fun reportStopped(
        itemId: String? = null,
        sessionId: String? = null,
        positionTicks: Long? = null
    ) {
        val finalItemId = itemId ?: itemIdProvider() ?: return
        val finalSessionId = sessionId ?: playSessionIdProvider()
        val finalPos = positionTicks ?: (exoPlayerProvider()?.currentPosition?.let { it * 10_000 } ?: 0L)
        if (finalPos > 0) {
            scope.launch {
                playbackRepository.reportPlaybackStopped(finalItemId, finalSessionId, finalPos)
            }
        }
        playSessionIdSetter(UUID.randomUUID().toString())
    }

    fun cancel() {
        progressJob?.cancel()
    }
}
