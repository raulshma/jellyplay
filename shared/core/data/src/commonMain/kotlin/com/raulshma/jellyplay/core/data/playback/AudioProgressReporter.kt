package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Periodic Jellyfin playback-progress reporting for a LOCAL session: every
 * 10 s a [PlaybackProgress] row rides the current play session id, paused
 * positions dedupe, and stop reports rotate the session id SYNCHRONOUSLY.
 *
 * The reporting loop itself is [PeriodicProgressReportLoop], the core that
 * player-video's reporter shares — the CADENCE and PAUSED DEDUP invariants
 * (and their virtual-time pins) live THERE, once. What remains here is the
 * local-session bookkeeping the loop core deliberately does not own:
 *
 * Promotion from androidMain (both managers now share this one class): the
 * reporting semantics were platform-neutral server bookkeeping whose only
 * Android-specific member was `exoPlayerProvider: () -> ExoPlayer?` —
 * replaced by two plain lambdas, [positionMsProvider] and [isPlayingProvider]
 * (null position = no live player this cycle). Android adapts at
 * construction with ExoPlayer-backed lambdas; the desktop manager
 * constructs it with engine-backed ones and its former three mirror
 * functions (startProgressReporting / reportStopped / reportStoppedCurrent)
 * were deleted in its favour. `java.util.UUID` became the stdlib
 * multiplatform [Uuid] (also a v4 string — identical session-id shape).
 *
 * ## Invariants
 *  - REMOTE GATE: [start] is a no-op while [remoteSessionActive] returns
 *    true — the remote session owns server reporting then. Wired as the
 *    loop core's START GATE (a gated start still cancels any prior loop and
 *    re-seeds the dedup before returning).
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
    private val loop = PeriodicProgressReportLoop(
        scope = scope,
        playbackRepository = playbackRepository,
        positionMsProvider = positionMsProvider,
        isPlayingProvider = isPlayingProvider,
        itemIdProvider = itemIdProvider,
        sessionIdProvider = playSessionIdProvider,
        reportIntervalMs = reportIntervalMs,
        gateStart = remoteSessionActive,
    )

    fun start() = loop.start()

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

    fun cancel() = loop.cancel()

    companion object {
        /** Production report cadence (Jellyfin's conventional 10 s interval). */
        const val PROGRESS_REPORT_INTERVAL_MS = PeriodicProgressReportLoop.PROGRESS_REPORT_INTERVAL_MS
    }
}
