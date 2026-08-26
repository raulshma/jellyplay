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
    private val onActive: () -> Unit,
) {
    /**
     * The ticker's last observed play-state, seeded from the current state.
     * Used to emit on play↔pause edges even while paused (so the UI reflects
     * the final position immediately when playback stops).
     */
    private var lastPlayingState: Boolean = isCurrentlyPlaying()

    /** Launches the ticker loop. Returns the [Job] for cancellation. */
    fun launch(): Job = scope.launch {
        while (isActive) {
            if (!isCurrentlyPlaying()) {
                // Bounded wait — see [POSITION_PAUSED_RECHECK_MS].
                withTimeoutOrNull(POSITION_PAUSED_RECHECK_MS) {
                    isPlayingFlow.first { it }
                }
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
