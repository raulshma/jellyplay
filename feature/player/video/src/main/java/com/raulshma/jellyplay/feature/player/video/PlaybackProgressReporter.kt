package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackProgress
import com.raulshma.jellyplay.core.model.PlaybackStartInfo

import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
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
    private val getMediaEngine: () -> MediaEngine?,
    private val onAutoSkipIntro: () -> Unit,
    private val onAutoSkipOutro: () -> Unit,
) {
    private var positionJob: Job? = null
    private var progressJob: Job? = null
    private var lastIntroSkipPosition: Long = -1
    private var lastOutroSkipPosition: Long = -1

    fun startPositionTracking() {
        positionJob?.cancel()
        lastIntroSkipPosition = -1
        lastOutroSkipPosition = -1
        val engine = getMediaEngine() ?: return
        positionJob = viewModel.viewModelScope.launch {
            engine.positionFlow.collect { pos ->
                uiState.update { it.copy(
                    currentPosition = pos,
                    duration = engine.durationMs.coerceAtLeast(0L),
                    bufferedPosition = engine.bufferedPositionMs.value,
                    videoStats = engine.videoStats.value,
                ) }
                checkAutoSkip(pos)
            }
        }
    }

    private fun checkAutoSkip(currentPositionMs: Long) {
        val state = uiState.value

        // Auto-skip intro
        if (state.autoSkipIntro && state.isInIntro) {
            val endTicks = state.introSegmentEndTicks
            if (endTicks != null && lastIntroSkipPosition != endTicks) {
                lastIntroSkipPosition = endTicks
                onAutoSkipIntro()
            }
        }

        // Auto-skip outro/credits
        if (state.autoSkipOutro && state.isInCredits) {
            val endTicks = state.creditSegmentEndTicks
            if (endTicks != null && lastOutroSkipPosition != endTicks) {
                lastOutroSkipPosition = endTicks
                onAutoSkipOutro()
            }
        }
    }

    fun startProgressReporting() {
        progressJob?.cancel()
        progressJob = viewModel.viewModelScope.launch {
            while (true) {
                delay(10_000)
                val engine = getMediaEngine() ?: continue
                val itemId = getCurrentItemId() ?: continue
                val positionTicks = engine.currentPositionMs * 10_000
                val isPaused = !engine.isPlaying.value
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
        val engine = getMediaEngine()
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
