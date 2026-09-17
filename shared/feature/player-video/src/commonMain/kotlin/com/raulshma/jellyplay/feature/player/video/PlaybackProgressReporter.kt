package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.PeriodicProgressReportLoop
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.ui.viewmodel.StateFlowHandle

import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.SegmentCalculator
import com.raulshma.jellyplay.feature.player.video.engine.SegmentCalculatorInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class PlaybackProgressReporter(
    private val playbackRepository: PlaybackRepository,
    private val scope: CoroutineScope,
    private val uiState: StateFlowHandle<VideoPlayerUiState>,
    private val getCurrentItemId: () -> String?,
    private val getPlaySessionId: () -> String,
    private val getResolvedPlayMethod: () -> PlayMethod,
    private val getMediaEngine: () -> MediaEngine?,
    private val getIncognitoModeEnabled: () -> Boolean,
    private val onAutoSkip: (MediaSegment) -> Unit,
    private val onPlaybackEndedNoNext: () -> Unit,
    private val onWatchedThresholdReached: (String) -> Unit,
    private val onPositionPersisted: (positionMs: Long) -> Unit,
    /**
     * Receives every engine position tick (position, duration, buffered,
     * stats). The ViewModel routes these to dedicated high-frequency
      * StateFlows instead of the monolithic [uiState], so the screen
     * root stops recomposing at 4 Hz.
     */
    private val onEnginePositionUpdate: (positionMs: Long, durationMs: Long, bufferedPositionMs: Long, videoStats: EngineVideoStats) -> Unit,
) {
    private var positionJob: Job? = null
    private val autoSkippedSegments = mutableSetOf<String>()
    private var endedNoNextTriggered = false
    private var watchedThresholdTriggered = false
    private var cachedDurationMs: Long = 0L
    private var cachedState: VideoPlayerUiState? = null
    private var cachedSegmentInput: SegmentCalculatorInput? = null

    fun startPositionTracking() {
        positionJob?.cancel()
        autoSkippedSegments.clear()
        endedNoNextTriggered = false
        watchedThresholdTriggered = false
        cachedDurationMs = 0L
        cachedState = null
        cachedSegmentInput = null
        val engine = getMediaEngine() ?: return
        positionJob = scope.launch {
            var lastPos = Long.MIN_VALUE
            var lastDur = Long.MIN_VALUE
            engine.positionFlow.collect { pos ->
                var dur = cachedDurationMs
                if (dur <= 0L) {
                    dur = engine.durationMs.coerceAtLeast(0L)
                    cachedDurationMs = dur
                }
                val buffered = engine.bufferedPositionMs.value
                if (pos != lastPos || dur != lastDur) {
                    lastPos = pos
                    lastDur = dur
                    // Route the high-frequency display values to dedicated
                    // flows — NOT into uiState — so the screen root is
                    // not invalidated at 4 Hz. The segment auto-skip logic
                    // below operates on the raw `pos` directly, decoupled from
                    // uiState.currentPosition, so behavior is unchanged.
                    val stats = engine.videoStats.value
                    onEnginePositionUpdate(pos, dur, buffered, stats)
                    onPositionPersisted(pos)
                }
                checkAutoSkip(pos)
                checkEndedNoNext(pos, dur)
                if (!watchedThresholdTriggered && dur > 0) {
                    val progressPercent = (pos.toFloat() / dur.toFloat()) * 100f
                    if (progressPercent >= 95f) {
                        watchedThresholdTriggered = true
                        getCurrentItemId()?.let { onWatchedThresholdReached(it) }
                    }
                }
            }
        }
    }

    private fun checkEndedNoNext(currentPositionMs: Long, durationMs: Long) {
        if (endedNoNextTriggered) return
        if (durationMs <= 0L) return
        if (currentPositionMs < durationMs - 500L) return
        val state = uiState.value
        if (state.episodes.nextEpisode != null) return
        endedNoNextTriggered = true
        onPlaybackEndedNoNext()
    }

    private fun checkAutoSkip(currentPositionMs: Long) {
        // Compute the active segment from the raw tick position using the
        // position-explicit overload. This avoids copying the ~95-field
        // VideoPlayerUiState on every position tick (the highest-frequency
        // avoidable allocation on the playback path). Behaviour is identical
        // to the previous `uiState.value.copy(currentPosition = ...)` form:
        // the copy was only ever read, never emitted to a StateFlow.
        val state = uiState.value
        var input = cachedSegmentInput
        // uiState is a low-frequency stream (position/duration live on
        // dedicated ViewModel flows), so an instance-identity check rebuilds
        // the segment input only when a segment-relevant field can actually
        // have changed — the field list lives in [SegmentProjection], not
        // duplicated here as a hand-maintained invalidation condition.
        if (input == null || cachedState !== state) {
            input = state.toSegmentInput()
            cachedSegmentInput = input
            cachedState = state
        }
        val seg = SegmentCalculator.computeActiveSegment(input, currentPositionMs) ?: return
        val behavior = SegmentCalculator.behaviorForType(input, seg.type)
        if (behavior != SegmentBehavior.AUTO_SKIP) return
        if (seg.id in autoSkippedSegments) return
        autoSkippedSegments.add(seg.id)
        onAutoSkip(seg)
    }

    /**
     * The 10 s server-progress loop, delegated to the shared
     * [PeriodicProgressReportLoop] core (the audio reporter's loop): the
     * CADENCE and PAUSED DEDUP invariants and the `ms * 10_000` tick math
     * live THERE, once. This class keeps only the wiring — incognito mode as
     * the per-cycle CYCLE GATE (a gated cycle skips before any snapshot),
     * the engine as the position/play-state source and the resolved play
     * method on every row. The `?: true` play-state fallback is dead by
     * construction: with a null engine the position provider already skipped
     * the cycle, so the play state is only ever read with an engine present.
     */
    private val progressLoop = PeriodicProgressReportLoop(
        scope = scope,
        playbackRepository = playbackRepository,
        positionMsProvider = { getMediaEngine()?.currentPositionMs },
        isPlayingProvider = { getMediaEngine()?.isPlaying?.value ?: true },
        itemIdProvider = getCurrentItemId,
        sessionIdProvider = getPlaySessionId,
        cycleGate = getIncognitoModeEnabled,
        playMethodProvider = { getResolvedPlayMethod() },
    )

    fun startProgressReporting() = progressLoop.start()

    fun cancelJobs() {
        progressLoop.cancel()
        positionJob?.cancel()
        autoSkippedSegments.clear()
        endedNoNextTriggered = false
        watchedThresholdTriggered = false
    }
}
