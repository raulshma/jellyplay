package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.PlaybackProgress
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Periodic Jellyfin playback-progress reporting for a LOCAL session: every
 * [PROGRESS_REPORT_INTERVAL_MS] a [PlaybackProgress] row rides the current
 * play session id, paused positions dedupe, and stop reports rotate the
 * session id SYNCHRONOUSLY.
 *
 * Promotion from androidMain (both managers now share this one class): the
 * reporting semantics were platform-neutral server bookkeeping whose only
 * Android-specific member was `exoPlayerProvider: () -> ExoPlayer?` —
 * replaced by two plain lambdas, [positionMsProvider] and [isPlayingProvider]
 * (null position = no live player this cycle; the 10 kHz tick math
 * `ms * 10_000` stays HERE, in exactly one place). Android adapts at
 * construction with ExoPlayer-backed lambdas; the desktop manager
 * constructs it with engine-backed ones and its former three mirror
 * functions (startProgressReporting / reportStopped / reportStoppedCurrent)
 * were deleted in its favour. `java.util.UUID` became the stdlib
 * multiplatform [Uuid] (also a v4 string — identical session-id shape).
 *
 * ## Invariants
 *  - CADENCE: the loop reports at [reportIntervalMs] (production default
 *    [PROGRESS_REPORT_INTERVAL_MS]; injectable only so host suites can pin
 *    cadence behaviour on virtual time — callers leave the default).
 *  - PAUSED DEDUP: while paused at an unmoving position exactly ONE paused
 *    row ever flushes ([lastPausedPositionTicks]); any position change while
 *    paused (seek) reports again; a playing row always resets the dedup.
 *  - REMOTE GATE: [start] is a no-op while [remoteSessionActive] returns
 *    true — the remote session owns server reporting then.
 *  - STOP ORDERING ([reportStopped]): the stop network call is LAUNCHED on
 *    [scope], NEVER awaited, while the play session id rotates
 *    SYNCHRONOUSLY before [reportStopped] returns — so the start-report
 *    that follows a transition always carries the fresh id; a deferred
 *    rotation could let it race onto the session the stop just used.
 *  - TEARDOWN ([stopAndCancel]): cancels the loop AND performs the final
 *    stop report + rotation both managers previously hand-rolled in their
 *    ~35-line `stopAndRelease` tails. Unlike [reportStopped] it rotates the
 *    session id even when no stop report fires (no item / zero position) —
 *    the hand-rolled tails rotated unconditionally. MUST be called while
 *    the caller's engine is still live: it snapshots the final item, session
 *    and position through the constructor providers, so calling it after
 *    the engine reference is dropped loses the position (provider → null →
 *    no stop report).
 *  - [cancel] stops the loop only — no report, no rotation.
 */
@OptIn(ExperimentalUuidApi::class)
class AudioProgressReporter(
    private val scope: CoroutineScope,
    private val playbackRepository: PlaybackRepository,
    private val remoteSessionActive: () -> Boolean,
    private val positionMsProvider: () -> Long?,
    private val isPlayingProvider: () -> Boolean,
    private val itemIdProvider: () -> String?,
    private val playSessionIdProvider: () -> String,
    private val playSessionIdSetter: (String) -> Unit,
    private val reportIntervalMs: Long = PROGRESS_REPORT_INTERVAL_MS,
) {
    private var progressJob: Job? = null
    private var lastPausedPositionTicks: Long = -1L

    fun start() {
        progressJob?.cancel()
        lastPausedPositionTicks = -1L
        if (remoteSessionActive()) return
        progressJob = scope.launch {
            while (true) {
                delay(reportIntervalMs)
                val positionMs = positionMsProvider() ?: continue
                val itemId = itemIdProvider() ?: continue
                val positionTicks = positionMs * 10_000
                val isPaused = !isPlayingProvider()
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
        val finalPos = positionTicks ?: ((positionMsProvider() ?: 0L) * 10_000)
        if (finalPos > 0) {
            scope.launch {
                playbackRepository.reportPlaybackStopped(finalItemId, finalSessionId, finalPos)
            }
        }
        playSessionIdSetter(Uuid.random().toString())
    }

    /**
     * Teardown entry point: the former caller-owned stop tail of both
     * managers' `stopAndRelease` (~35 duplicated lines each). Cancels the
     * reporting loop, then — snapshotting through the constructor providers
     * while the caller's engine is still live — launches the final stop
     * report (fire-and-forget on [scope], exactly like [reportStopped]) and
     * rotates the play session id SYNCHRONOUSLY. The rotation happens even
     * when no report fires (null item id or zero position), matching the
     * hand-rolled tails [reportStopped]'s early return does not.
     */
    fun stopAndCancel() {
        cancel()
        val finalItemId = itemIdProvider()
        val finalSessionId = playSessionIdProvider()
        val finalPos = (positionMsProvider() ?: 0L) * 10_000
        if (finalItemId != null && finalPos > 0) {
            scope.launch {
                playbackRepository.reportPlaybackStopped(finalItemId, finalSessionId, finalPos)
            }
        }
        playSessionIdSetter(Uuid.random().toString())
    }

    fun cancel() {
        progressJob?.cancel()
    }

    companion object {
        /** Production report cadence (Jellyfin's conventional 10 s interval). */
        const val PROGRESS_REPORT_INTERVAL_MS = 10_000L
    }
}
