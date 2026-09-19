package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The ONE periodic playback-progress loop, shared by both local players:
 * every [reportIntervalMs] it snapshots (position, play state, item, session)
 * through caller-supplied lambdas, applies the paused-position dedup, and
 * flushes a [PlaybackProgress] row through [playbackRepository].
 * [AudioProgressReporter] (local audio sessions; adds stop-report/session-id
 * rotation bookkeeping) and player-video's `PlaybackProgressReporter` (video;
 * keeps its position-tracking/auto-skip/watched-threshold half) are thin
 * shells over this core, and the loop invariants below are documented — and
 * pinned by PeriodicProgressReportLoopTest — HERE, once.
 *
 * ## Invariants
 *  - CADENCE: cycles fire every [reportIntervalMs] (production default
 *    [PROGRESS_REPORT_INTERVAL_MS], Jellyfin's conventional 10 s; injectable
 *    only so host suites can pin cadence behaviour on virtual time — callers
 *    leave the default).
 *  - PAUSED DEDUP: while paused at an unmoving position exactly ONE paused
 *    row ever flushes ([lastPausedPositionTicks]); any position change while
 *    paused (seek) reports again; a playing row always resets the dedup.
 *    [start] re-seeds the dedup, so a restart at the same paused position
 *    reports again.
 *  - START GATE: when [gateStart] returns true, [start] is a no-op — it still
 *    cancels any prior loop and re-seeds the dedup, but launches nothing
 *    (audio passes its remote-session check: the remote session owns server
 *    reporting then; video leaves the default).
 *  - CYCLE GATE: when [cycleGate] returns true the cycle is skipped right
 *    after its delay, before any snapshot — dedup state untouched (video
 *    passes its incognito check; audio leaves the default).
 *  - SKIPS: a null position ([positionMsProvider] — no live engine/player
 *    this cycle) or a null item id skips the cycle without flushing.
 *  - TICK MATH: the 10 kHz conversion `positionMs * 10_000` lives here, in
 *    exactly one place.
 *  - [cancel] stops the loop only — no report, no other side effects.
 */
class PeriodicProgressReportLoop(
    private val scope: CoroutineScope,
    private val playbackRepository: PlaybackRepository,
    private val positionMsProvider: () -> Long?,
    private val isPlayingProvider: () -> Boolean,
    private val itemIdProvider: () -> String?,
    private val sessionIdProvider: () -> String,
    private val reportIntervalMs: Long = PROGRESS_REPORT_INTERVAL_MS,
    private val gateStart: () -> Boolean = { false },
    private val cycleGate: () -> Boolean = { false },
    private val playMethodProvider: () -> PlayMethod = { PlayMethod.DIRECT_PLAY },
) {
    private var job: Job? = null
    private var lastPausedPositionTicks: Long = -1L

    fun start() {
        job?.cancel()
        lastPausedPositionTicks = -1L
        if (gateStart()) return
        job = scope.launch {
            while (true) {
                delay(reportIntervalMs)
                if (cycleGate()) continue
                val positionMs = positionMsProvider() ?: continue
                val itemId = itemIdProvider() ?: continue
                val positionTicks = positionMs * 10_000
                val isPaused = !isPlayingProvider()
                if (isPaused && positionTicks == lastPausedPositionTicks) continue
                if (isPaused) lastPausedPositionTicks = positionTicks else lastPausedPositionTicks = -1L
                playbackRepository.reportPlaybackProgress(
                    PlaybackProgress(
                        itemId = itemId,
                        sessionId = sessionIdProvider(),
                        positionTicks = positionTicks,
                        isPaused = isPaused,
                        playMethod = playMethodProvider(),
                    )
                )
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    companion object {
        /** Production report cadence (Jellyfin's conventional 10 s interval). */
        const val PROGRESS_REPORT_INTERVAL_MS = 10_000L
    }
}
