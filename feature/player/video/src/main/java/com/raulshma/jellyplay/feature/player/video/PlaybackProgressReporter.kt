package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackProgress
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.ui.viewmodel.StateFlowHandle

import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class PlaybackProgressReporter(
    private val playbackRepository: PlaybackRepository,
    private val viewModel: ViewModel,
    private val uiState: StateFlowHandle<VideoPlayerUiState>,
    private val getCurrentItemId: () -> String?,
    private val getPlaySessionId: () -> String,
    private val getResolvedPlayMethod: () -> PlayMethod,
    private val getMediaEngine: () -> MediaEngine?,
    private val onAutoSkip: (MediaSegment) -> Unit,
) {
    private var positionJob: Job? = null
    private var progressJob: Job? = null
    private val autoSkippedSegments = mutableSetOf<String>()

    fun startPositionTracking() {
        positionJob?.cancel()
        autoSkippedSegments.clear()
        val engine = getMediaEngine() ?: return
        positionJob = viewModel.viewModelScope.launch {
            var lastPos = Long.MIN_VALUE
            var lastDur = Long.MIN_VALUE
            engine.positionFlow.collect { pos ->
                val dur = engine.durationMs.coerceAtLeast(0L)
                val buffered = engine.bufferedPositionMs.value
                val stats = engine.videoStats.value
                if (pos != lastPos || dur != lastDur) {
                    lastPos = pos
                    lastDur = dur
                    uiState.update { state ->
                        state.copy(
                            currentPosition = pos,
                            duration = dur,
                            bufferedPosition = buffered,
                            videoStats = stats,
                        )
                    }
                }
                checkAutoSkip(pos)
            }
        }
    }

    private fun checkAutoSkip(currentPositionMs: Long) {
        val state = uiState.value
        val seg = state.activeSegment ?: return
        val behavior = state.behaviorForType(seg.type)
        if (behavior != SegmentBehavior.AUTO_SKIP) return
        if (seg.id in autoSkippedSegments) return
        autoSkippedSegments.add(seg.id)
        onAutoSkip(seg)
    }

    fun startProgressReporting() {
        progressJob?.cancel()
        progressJob = viewModel.viewModelScope.launch {
            while (true) {
                delay(10_000)
                val engine = getMediaEngine() ?: break
                val itemId = getCurrentItemId() ?: break
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
