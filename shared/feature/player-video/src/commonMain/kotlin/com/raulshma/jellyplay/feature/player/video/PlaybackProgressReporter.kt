package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.PeriodicProgressReportLoop
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.ui.viewmodel.StateFlowHandle

import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.SegmentCalculator
import com.raulshma.jellyplay.feature.player.video.engine.SegmentCalculatorInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Distance from the (fallback-aware — the engines' `durationMs` already folds
 * `serverDurationMs` in via `resolveDurationMs`) duration within which a
 * position counts as "at the end" for the stalled-finish detector.
 */
internal const val STALLED_FINISH_END_WINDOW_MS = 2_000L

/**
 * How long the position must fail to advance while inside the end window —
 * and while playing, not paused and not in error — before the item is treated
 * as finished at the stall point (malformed files whose demuxer
 * stalls at ~99.5 % complete properly instead of hanging).
 */
internal const val STALLED_FINISH_NO_ADVANCE_MS = 10_000L

/**
 * Pure stalled-finish predicate: a position that sits inside the
 * last [STALLED_FINISH_END_WINDOW_MS] of the runtime, has not advanced for at
 * least [STALLED_FINISH_NO_ADVANCE_MS], while the engine claims to be playing
 * (not paused) and is not in a terminal/erroneous state (IDLE: nothing
 * loaded; ENDED: the genuine-EOF path owns that; ERROR: the error latch
 * owns that) is a demuxer stall at the end — treat it as finished.
 * BUFFERING counts: a starved cache is exactly what an end-of-file stall
 * looks like from the outside.
 */
internal fun shouldTreatAsStalledFinish(
    positionMs: Long,
    durationMs: Long,
    stalledForMs: Long,
    isPlaying: Boolean,
    playbackState: EnginePlaybackState,
): Boolean =
    durationMs > 0L &&
        positionMs >= durationMs - STALLED_FINISH_END_WINDOW_MS &&
        stalledForMs >= STALLED_FINISH_NO_ADVANCE_MS &&
        isPlaying &&
        (playbackState == EnginePlaybackState.READY || playbackState == EnginePlaybackState.BUFFERING)

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
    /**
     * Wall clock (ms) for the stalled-finish detector. Injectable so tests
     * advance the 10 s no-advance window virtually instead of sleeping.
     */
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    private var positionJob: Job? = null
    private val autoSkippedSegments = mutableSetOf<String>()
    private var endedNoNextTriggered = false
    private var watchedThresholdTriggered = false
    private var cachedDurationMs: Long = 0L
    private var cachedState: VideoPlayerUiState? = null
    private var cachedSegmentInput: SegmentCalculatorInput? = null

    /**
     * Error latch: set the moment the engine enters ERROR or an
     * [com.raulshma.jellyplay.feature.player.video.engine.EngineDecision.ShowError]
     * fired for the current item (the session forwards those via
     * [onEngineError]); cleared only when a load actually succeeds and
     * restarts tracking ([startPositionTracking] — the funnel every retry /
     * transcode-fallback / new-item path goes through). While latched the
     * watched-threshold callback stays suppressed for the remainder of the
     * item, so a decode error at 99 % cannot mark the episode watched, and
     * the session's teardown stop-report carries `failed = true` so the
     * server skips its own "≥X % = played" rule on the aborted session.
     *
     * @Volatile: written from the position tick / the session's decision
     * fan-out (main), read by [PlaybackSession.release] off-main.
     */
    @Volatile
    private var errorLatch = false

    /** One-shot latch for the stalled-finish completion. */
    private var stalledFinishTriggered = false

    /** Last position seen inside the end window; -1 while outside it. */
    private var stalledLastPositionMs = -1L

    /** [nowProvider] reading at which [stalledLastPositionMs] was first seen. */
    private var stalledLastAdvancedAtMs = 0L

    /**
     * Session id the stalled-finish path already stop-reported (at the FULL
     * duration). [PlaybackSession] consults [hasReportedStopFor] so its own
     * teardown stop for the same session is deduped away instead of
     * downgrading the server-side position below the reported end.
     *
     * @Volatile: written from the tick, read by the session's teardown paths.
     */
    @Volatile
    private var stalledStopReportedSessionId: String? = null

    fun startPositionTracking() {
        positionJob?.cancel()
        autoSkippedSegments.clear()
        endedNoNextTriggered = false
        watchedThresholdTriggered = false
        cachedDurationMs = 0L
        cachedState = null
        cachedSegmentInput = null
        // A successful (re)load is the recovery point that re-arms the
        // watched-threshold callback after an error — the retry /
        // transcode-fallback / next-item paths all restart tracking here
        // (only playback actually resuming clears the latch).
        errorLatch = false
        stalledFinishTriggered = false
        stalledLastPositionMs = -1L
        stalledStopReportedSessionId = null
        val engine = getMediaEngine() ?: return
        positionJob = scope.launch {
            var lastPos = Long.MIN_VALUE
            var lastDur = Long.MIN_VALUE
            engine.positionFlow.collect { pos ->
                if (engine.playbackState.value == EnginePlaybackState.ERROR) {
                    errorLatch = true
                }
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
                checkStalledFinish(pos, dur, engine)
                if (!watchedThresholdTriggered && !errorLatch && dur > 0) {
                    val progressPercent = (pos.toFloat() / dur.toFloat()) * 100f
                    if (progressPercent >= 95f) {
                        markWatchedThreshold()
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

    // ── Error latch, genuine-EOF, stalled-finish ─────────────────────

    /**
     * The session forwards every engine-error surface it sees — the
     * [com.raulshma.jellyplay.feature.player.video.engine.EngineDecision.ShowError]
     * decisions (engine errors plus the initial-buffering watchdog) — so the
     * latch catches error paths the tick's own `playbackState` read could
     * miss while the tracking job is cancelled between load attempts.
     */
    fun onEngineError() {
        errorLatch = true
    }

    /**
     * Genuine-EOF completion: an engine ENDED (not an error) counts
     * as watched even below the 95 % threshold — short or malformed items
     * that reach their true end must not stay unwatched (the shim's
     * `_finished_at_eof`). Reached from the session's
     * `EngineDecision.PlaybackEnded` fan-out.
     */
    fun onGenuineEof() {
        if (errorLatch) return
        markWatchedThreshold()
    }

    /** Read seam for the session's teardown stop-report (`failed` flag). */
    fun isErrorLatched(): Boolean = errorLatch

    /**
     * Whether the stalled-finish path already stop-reported [sessionId] (at
     * the full duration) — the session's own stop for that session would be
     * a duplicate and is suppressed.
     */
    fun hasReportedStopFor(sessionId: String): Boolean =
        stalledStopReportedSessionId == sessionId

    /** The single fire-point of the watched-threshold callback. */
    private fun markWatchedThreshold() {
        if (watchedThresholdTriggered) return
        watchedThresholdTriggered = true
        getCurrentItemId()?.let { onWatchedThresholdReached(it) }
    }

    /**
     * Stalled-finish detection, run on every position tick: once the
     * position is inside the last [STALLED_FINISH_END_WINDOW_MS] of the
     * runtime and has not advanced for [STALLED_FINISH_NO_ADVANCE_MS] while
     * the engine keeps claiming to play, the demuxer is treated as stalled at
     * the end — fire the threshold (below 95 % too) and stop-report at the
     * FULL duration so the server resolves played-ness. Guarded against the
     * error latch (an errored engine near the end must complete nothing) and
     * one-shot per item.
     */
    private fun checkStalledFinish(positionMs: Long, durationMs: Long, engine: MediaEngine) {
        if (stalledFinishTriggered) return
        if (durationMs <= 0L || positionMs < durationMs - STALLED_FINISH_END_WINDOW_MS) {
            // Outside the end window: reset the baseline so a later re-entry
            // (seek back into the tail) restarts the 10 s no-advance window.
            stalledLastPositionMs = -1L
            return
        }
        val now = nowProvider()
        if (positionMs != stalledLastPositionMs) {
            stalledLastPositionMs = positionMs
            stalledLastAdvancedAtMs = now
            return
        }
        if (errorLatch) return
        if (!shouldTreatAsStalledFinish(
                positionMs = positionMs,
                durationMs = durationMs,
                stalledForMs = now - stalledLastAdvancedAtMs,
                isPlaying = engine.isPlaying.value,
                playbackState = engine.playbackState.value,
            )
        ) {
            return
        }
        stalledFinishTriggered = true
        markWatchedThreshold()
        // Incognito keeps the local-only threshold mark (the VM callback
        // handles that mode itself) but never reaches the server — the same
        // invariant reportCurrentPlaybackStopped enforces.
        if (getIncognitoModeEnabled()) return
        val itemId = getCurrentItemId() ?: return
        val sessionId = getPlaySessionId()
        stalledStopReportedSessionId = sessionId
        scope.launch {
            playbackRepository.reportPlaybackStopped(itemId, sessionId, durationMs * 10_000L)
        }
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
        stalledFinishTriggered = false
        stalledLastPositionMs = -1L
        // errorLatch is deliberately NOT cleared here: it records that an
        // error occurred during THIS item (feeding the teardown stop's
        // `failed` flag); only a successful load restart
        // ([startPositionTracking]) re-arms it.
    }
}
