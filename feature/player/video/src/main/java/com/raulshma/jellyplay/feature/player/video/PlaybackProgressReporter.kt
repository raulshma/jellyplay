package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackProgress
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.feature.player.video.engine.ExoPlayerEngine
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine
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
    private val getPlayerEngine: () -> PlayerEngine?,
) {
    private var positionJob: Job? = null
    private var progressJob: Job? = null

    fun startPositionTracking() {
        positionJob?.cancel()
        val engine = getPlayerEngine() ?: return
        if (engine is ExoPlayerEngine) {
            positionJob = viewModel.viewModelScope.launch {
                engine.positionFlow().collect { pos ->
                    uiState.update { it.copy(
                        currentPosition = pos,
                        duration = engine.durationMs.coerceAtLeast(0L),
                    ) }
                }
            }
        } else {
            positionJob = viewModel.viewModelScope.launch {
                while (true) {
                    val eng = getPlayerEngine()
                    if (eng != null) {
                        uiState.update { it.copy(
                            currentPosition = eng.currentPositionMs,
                            duration = eng.durationMs.coerceAtLeast(0L),
                        ) }
                    }
                    delay(250)
                }
            }
        }
    }

    fun startProgressReporting() {
        progressJob?.cancel()
        progressJob = viewModel.viewModelScope.launch {
            while (true) {
                delay(10_000)
                val engine = getPlayerEngine() ?: continue
                val itemId = getCurrentItemId() ?: continue
                val positionTicks = engine.currentPositionMs * 10_000
                val isPaused = !engine.isPlaying
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
        val positionTicks = engine?.currentPositionMs?.let { it * 10_000 } ?: 0L
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
