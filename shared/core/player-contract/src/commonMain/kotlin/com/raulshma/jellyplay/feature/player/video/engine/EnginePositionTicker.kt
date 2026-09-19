package com.raulshma.jellyplay.feature.player.video.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How often the position ticker re-checks whether playback resumed while
 * paused. Historically each engine's `positionFlow` waited on
 * `_isPlaying.first { it }` with no timeout, which suspended forever while
 * paused — so polling-interval and video-stats-config changes were ignored
 * until playback resumed, and buffer/stats froze. Re-checking every few
 * seconds lets config be honoured even while paused. Centralised here so the
 * fix lives in exactly one place rather than being copy-pasted across three
 * engine implementations.
 */
const val POSITION_PAUSED_RECHECK_MS = 2_500L

/** First wait of the not-ready backoff (see [EnginePositionTicker.isReady]). */
const val POSITION_NOT_READY_MIN_WAIT_MS = 250L

/** Cap of the not-ready backoff — defaults to the paused re-check cadence. */
const val POSITION_NOT_READY_MAX_WAIT_MS = POSITION_PAUSED_RECHECK_MS

/**
 * Shared polling-ticker loop used by every [MediaEngine] implementation's
 * `positionFlow`.
 *
 * The three backends (ExoPlayer / libmpv / LibVLC) previously each carried a
 * near-identical copy of this loop: a `while(isActive)` ticker, the bounded
 * paused-wait, a `delay(pollingIntervalMs)`, and a
 * play-state edge-detection that suppresses redundant work while paused. The
 * subtle concurrency reasoning was triplicated; this helper centralises it.
 *
 * Each engine injects its engine-specific readbacks via lambdas:
 *  - [isCurrentlyPlaying]: the synchronous "are we playing right now" read
 *    (ExoPlayer reads `Player.isPlaying`; MPV/VLC read their `_isPlaying` value).
 *  - [onActive]: work to run on a tick where playback is active or just
 *    changed — typically pushing the position + buffered position downstream
 *    and, conditionally, refreshing stats.
 *
 * The paused-wait wakes on the engine's [isPlayingFlow] (so a resume is
 * detected immediately) but is bounded by [POSITION_PAUSED_RECHECK_MS] so
 * config changes are still honoured while paused.
 *
 * The caller still owns the surrounding `callbackFlow` (its initial
 * `trySend`, any engine-specific listener wiring such as ExoPlayer's
 * `Player.Listener` for discontinuities, and the `awaitClose` cancellation).
 *
 * Lives in the player-contract module so both production engines
 * (`shared/feature/player-video` via `ReloadablePlayerEngine`) and test
 * doubles over the [MediaEngine] contract share one implementation — see
 * `CONTEXT.md` "feature/player/core (the engine-agnostic
 * `MediaEngine` contract and engine-shared machinery)".
 */
class EnginePositionTicker(
    private val scope: CoroutineScope,
    private val pollingIntervalMs: StateFlow<Long>,
    private val isPlayingFlow: StateFlow<Boolean>,
    private val isCurrentlyPlaying: () -> Boolean,
    private val onActive: suspend () -> Unit,
    /**
     * Optional readiness gate for consumers whose underlying player is
     * created lazily (the audio manager's ExoPlayer is null between
     * sessions). When non-null and returning false, the loop backs off
     * exponentially from [notReadyInitialWaitMs] to [notReadyMaxWaitMs]
     * instead of waking at the poll rate; the first ready read resets the
     * backoff. Null (the default) keeps the engine-shaped behaviour where a
     * player always exists.
     */
    private val isReady: (() -> Boolean)? = null,
    private val notReadyInitialWaitMs: Long = POSITION_NOT_READY_MIN_WAIT_MS,
    private val notReadyMaxWaitMs: Long = POSITION_NOT_READY_MAX_WAIT_MS,
) {
    /**
     * The ticker's last observed play-state, seeded from the current state.
     * Used to emit on play↔pause edges even while paused (so the UI reflects
     * the final position immediately when playback stops).
     */
    private var lastPlayingState: Boolean = isCurrentlyPlaying()

    /** Launches the ticker loop. Returns the [Job] for cancellation. */
    fun launch(): Job = scope.launch {
        var notReadyWaitMs = notReadyInitialWaitMs
        while (isActive) {
            if (isReady?.invoke() == false) {
                // Not ready — back off exponentially so a player-less stretch
                // doesn't wake the loop at the poll rate.
                delay(notReadyWaitMs)
                notReadyWaitMs = (notReadyWaitMs * 2).coerceAtMost(notReadyMaxWaitMs)
                continue
            }
            notReadyWaitMs = notReadyInitialWaitMs
            if (!isCurrentlyPlaying()) {
                // Bounded wait — see [POSITION_PAUSED_RECHECK_MS].
                val resumed = withTimeoutOrNull(POSITION_PAUSED_RECHECK_MS) {
                    isPlayingFlow.first { it }
                }
                // Timed out and still paused: loop straight back into the
                // bounded wait instead of also paying the interval delay, so a
                // paused session cycles at POSITION_PAUSED_RECHECK_MS and a
                // resume isn't held off by a stale interval sleep. A
                // flow-driven resume falls through to the playing path below.
                if (resumed == null && !isCurrentlyPlaying()) continue
            }
            delay(pollingIntervalMs.value)
            val currentlyPlaying = isCurrentlyPlaying()
            // Only do work when playing, or on a play↔pause edge — avoids
            // churning identical positions every paused wake.
            if (currentlyPlaying || currentlyPlaying != lastPlayingState) {
                onActive()
            }
            lastPlayingState = currentlyPlaying
        }
    }
}
