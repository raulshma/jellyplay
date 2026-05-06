package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackProgress
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine
import androidx.media3.exoplayer.ExoPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class PlaybackProgressReporter(
    private val playbackRepository: PlaybackRepository,
    private val viewModel: ViewModel,
    private val uiState: MutableStateFlow<VideoPlayerUiState>,
    private val getCurrentItemId: () -> String?,
    private val getPlaySessionId: () -> String,
    private val getResolvedPlayMethod: () -> PlayMethod,
    private val getExoPlayer: () -> ExoPlayer?,
    private val getPlayerEngine: () -> PlayerEngine?,
) {
    private var positionJob: Job? = null
    private var progressJob: Job? = null

    fun startPositionTracking() {
        positionJob?.cancel()
        positionJob = viewModel.viewModelScope.launch {
            while (true) {
                val engine = getPlayerEngine()
                if (engine != null) {
                    uiState.update { it.copy(
                        currentPosition = engine.currentPositionMs,
                        duration = engine.durationMs.coerceAtLeast(0L),
                    ) }
                } else {
                    getExoPlayer()?.let { player ->
                        uiState.update { it.copy(
                            currentPosition = player.currentPosition,
                            duration = player.duration.coerceAtLeast(0L),
                        ) }
                    }
                }
                delay(250)
            }
        }
    }

    fun startProgressReporting() {
        progressJob?.cancel()
        progressJob = viewModel.viewModelScope.launch {
            while (true) {
                delay(10_000)
                val itemId = getCurrentItemId() ?: continue
                val engine = getPlayerEngine()
                val positionTicks: Long
                val isPaused: Boolean
                if (engine != null) {
                    positionTicks = engine.currentPositionMs * 10_000
                    isPaused = !engine.isPlaying
                } else {
                    val player = getExoPlayer() ?: continue
                    positionTicks = player.currentPosition * 10_000
                    isPaused = !player.isPlaying
                }
                playbackRepository.reportPlaybackProgress(
                    PlaybackProgress(
                        itemId = itemId,
                        sessionId = getPlaySessionId(),
                        positionTicks = positionTicks,
                        isPaused = isPaused,
                        playMethod = getResolvedPlayMethod(),
                    )
                )
            }
        }
    }

    suspend fun reportStart(itemId: String, sessionId: String, mediaSourceId: String?, playMethod: PlayMethod) {
        playbackRepository.reportPlaybackStart(
            PlaybackStartInfo(
                itemId = itemId,
                sessionId = sessionId,
                mediaSourceId = mediaSourceId,
                playMethod = playMethod,
            )
        )
    }

    fun reportStopAndRelease(
        itemId: String?,
        sessionId: String,
    ) {
        val engine = getPlayerEngine()
        val player = getExoPlayer()
        val positionTicks = when {
            engine != null -> engine.currentPositionMs * 10_000
            player != null -> player.currentPosition * 10_000
            else -> 0L
        }
        cancelJobs()
        if (itemId != null && positionTicks > 0) {
            viewModel.viewModelScope.launch {
                playbackRepository.reportPlaybackStopped(
                    itemId = itemId,
                    sessionId = sessionId,
                    positionTicks = positionTicks,
                )
            }
        }
    }

    fun cancelJobs() {
        progressJob?.cancel()
        positionJob?.cancel()
    }
}
