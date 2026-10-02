package com.raulshma.jellyplay.feature.player.video.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
 *  - [scopeProvider]: the scope the loop launches on, re-read by every
 *    [launch] — a lookup (the mpv track-refresh coalescer's shape), not a
 *    captured value, so an engine whose scope accessor self-heals after an
 *    internal release-as-reset inside `load()` never launches the loop onto a
 *    cancelled generation (a captured ref would die with the generation it
 *    pinned and freeze the flow's output mid-collection).
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
    private val scopeProvider: () -> CoroutineScope,
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
    /**
     * Prime ONE synchronous [onActive] read before the polling loop starts —
     * the audio managers' `startPositionTracking` contract: the first
     * position/duration publish must land BEFORE [launch] returns, not one
     * polling interval later. The former hand-rolled loops published on
     * their FIRST iteration — before any delay — and the regression this
     * prime closes surfaced when the desktop audio manager adopted the
     * ticker: without it, the first position publish waits up to the full
     * interval, and a skip in that window reports the previous item's stop
     * position against `duration == 0` (the transition stop-report fallback
     * `_duration.value * 10_000` read 0 and advance stop reports were
     * suppressed).
     *
     * Synchronous ON PURPOSE, via [CoroutineStart.UNDISPATCHED]: a
     * dispatched (loop-first) tick could race the very stop-report fallback
     * it feeds — the prime body is a plain read + flow write and never
     * suspends, so UNDISPATCHED runs it to completion on the CALLER's
     * thread inside `launch()`, and the loop's first `delay` then parks as
     * usual on the scope's dispatcher. Like the manual tick the audio
     * managers used to make, the prime is gated only by [isReady] — the
     * body's own playing gate decides whether anything is published, and
     * [onActive]'s play/edge suppression deliberately does NOT apply (a
     * paused body is the body's own no-op).
     *
     * OFF by default because the video adapters' `positionFlow` shells
     * already prime via their surrounding `callbackFlow`'s initial
     * `trySend(currentPositionMs)` — an unconditional prime body here would
     * double-publish the same first value down a conflated (non-deduping)
     * channel. Audio managers pass `true`; their tick bodies publish
     * through dedup-guarded state flows, so the prime is idempotent.
     */
    private val primeFirstTick: Boolean = false,
) {
    /**
     * The ticker's last observed play-state, seeded from the current state.
     * Used to emit on play↔pause edges even while paused (so the UI reflects
     * the final position immediately when playback stops).
     */
    private var lastPlayingState: Boolean = isCurrentlyPlaying()

    /**
     * Launches the ticker loop. Returns the [Job] for cancellation.
     *
     * With [primeFirstTick] enabled the launch is [CoroutineStart.UNDISPATCHED]
     * so the prime read runs synchronously on the caller's thread (see the
     * [primeFirstTick] KDoc); without it the launch is DEFAULT-started and
     * the first body read happens one polling interval after launch — the
     * engine-shaped behaviour the video `positionFlow` shells rely on.
     */
    fun launch(): Job = scopeProvider().launch(
        start = if (primeFirstTick) CoroutineStart.UNDISPATCHED else CoroutineStart.DEFAULT,
    ) {
        if (primeFirstTick && isReady?.invoke() != false) {
            // Prime read (moved here from the audio managers' manual
            // synchronous tick calls) — see [primeFirstTick]. Does NOT
            // update [lastPlayingState]: the constructor seeded it from the
            // same synchronous read moments earlier on this same thread, so
            // the loop's edge detection starts from the same value either way.
            onActive()
        }
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
