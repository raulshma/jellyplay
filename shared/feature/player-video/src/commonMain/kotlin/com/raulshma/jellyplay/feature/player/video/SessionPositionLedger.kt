package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.isWatchedPercentage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The session's position ledger — the position/report family carved out of
 * [PlaybackSession] as a module constructed inside the session root (the
 * composition-root stance and the cycles-dissolved-by-ownership map of the
 * session survive intact; this class is never injected from outside).
 *
 * Owns, verbatim from the session body:
 *  - the play-session id resolution ([currentPlaySessionId],
 *    [restoreOrAllocatePlaySessionId]) — one id per session lifecycle for
 *    every report and persist;
 *  - the seek latch + report position ([getReportPositionMs]) — the last
 *    explicit seek while still fresh (< 3 s), else the engine position;
 *  - the stop-report pair ([reportPlaybackStart],
 *    [reportCurrentPlaybackStopped], [finalStopReport]) with the
 *    incognito gate and the [stopReportedForSession] dedup;
 *  - the process-death persistence ([seekPersisted],
 *    [persistPlaybackPosition], [resolveStartTicksAfterProcessDeath],
 *    [preSeedPlayhead]) — the wall-clock throttle, the store snapshot and
 *    the coalesced offline-mirror write;
 *  - the per-item reset hooks the session's initialize/release chain calls
 *    in its pinned order.
 */
internal class SessionPositionLedger(
    private val scope: CoroutineScope,
    private val releaseScope: CoroutineScope,
    private val clock: () -> Long,
    private val positionStore: SessionPositionStore,
    private val playbackRepository: PlaybackRepository,
    private val offlinePlaybackFacade: OfflinePlaybackFacade,
    private val progressReporter: PlaybackProgressReporter,
    private val playerSessionManager: PlayerSessionManager,
    private val getIncognitoModeEnabled: () -> Boolean,
    private val seedDisplayedPositionMs: (Long) -> Unit,
) {

    /**
     * Dedup guard for Stop reports. Two release paths can fire for the same
     * session — reportCurrentPlaybackStopped (transcode fallback,
     * end-of-item) and the final teardown report — without this guard the
     * server receives a duplicate Stop for the same play-session id, which
     * can mark the item more-watched than reality and trigger duplicate
     * resume rows. Keyed by sessionId so a new load (new session) clears the
     * latch.
     */
    @Volatile
    internal var stopReportedForSession: String? = null

    /** Position (ms) of the last explicit seek; feeds [getReportPositionMs]. */
    internal var lastSeekPositionMs: Long? = null

    /** Wall clock of the last explicit seek; bounds the seek-latch's validity. */
    internal var lastSeekTimestamp: Long = 0L

    /**
     * Last position (ms) written to the process-death persistence; feeds the
     * stop-report fallback when the engine reports 0 after STATE_ENDED.
     */
    internal var lastPersistedPositionMs: Long = Long.MIN_VALUE

    /**
     * Wall clock of the last process-death persist; the throttle key for
     * [persistPlaybackPosition]. A wall-clock gate (not a position delta)
     * keeps the write cadence fixed at
     * [POSITION_PERSIST_MIN_WALL_CLOCK_INTERVAL_MS] regardless of playback speed —
     * a position delta made 2× speed halve the interval between writes.
     */
    internal var lastPersistedAtMs: Long = 0L

    /**
     * Locally-allocated UUID play-session id — the fallback used until (and
     * unless) the server issues its own id through the PlaybackInfo endpoint
     * (see [PlayerSessionState.playSessionId] on the session manager).
     */
    internal var playSessionId: String = java.util.UUID.randomUUID().toString()

    /** The pending coalesced seek-mirror write, if a quiet window is running. */
    private var seekProgressJob: Job? = null

    /**
     * Single resolved playback-session id for the session-owned reports and
     * persists. The server issues its own id via the `PlaybackInfo` endpoint
     * (stored in [PlayerSessionState.playSessionId]); [playSessionId] is the
     * locally-allocated UUID fallback. Routing every report and the
     * process-death persist through this resolver guarantees a single value
     * is used for the whole session lifecycle.
     */
    val currentPlaySessionId: String
        get() = playerSessionManager.sessionState.value.playSessionId ?: playSessionId

    /**
     * The position a report should carry: the last explicit seek while it is
     * still fresh (< 3 s), otherwise the engine's current position. A seek
     * followed by an immediate teardown would otherwise report the engine's
     * not-yet-caught-up position.
     */
    fun getReportPositionMs(): Long {
        val enginePos = playerSessionManager.engine?.currentPositionMs ?: 0L
        val seekPos = lastSeekPositionMs
        val seekTime = lastSeekTimestamp
        if (seekPos != null && seekTime > 0L) {
            val timeSinceSeek = clock() - seekTime
            if (timeSinceSeek < 3000L) {
                return seekPos
            }
        }
        return enginePos
    }

    /**
     * Stop-reports the *current* server playback session (skip on incognito,
     * dedup through [stopReportedForSession] so the two paths that can fire
     * for one session — this one and the final teardown report — never
     * double-report; the reporter's stalled-finish stop for the same session
     * dedups through [PlaybackProgressReporter.hasReportedStopFor] the same
     * way). The report carries `failed = true` when the reporter's error
     * latch is held: an error-aborted session must not trip the server's own
     * "≥X % = played" rule — the actual spoiler-protection fix.
     */
    fun reportCurrentPlaybackStopped() {
        if (getIncognitoModeEnabled()) return
        val itemId = playerSessionManager.sessionState.value.currentItemId ?: return
        val sessionId = currentPlaySessionId
        if (sessionId == stopReportedForSession) return
        if (progressReporter.hasReportedStopFor(sessionId)) {
            // The reporter already stop-reported this session at the FULL
            // duration (stalled-finish): latch the dedup and skip the
            // duplicate, which would only downgrade the position.
            stopReportedForSession = sessionId
            return
        }
        val failed = progressReporter.isErrorLatched()
        val positionMs = getReportPositionMs().takeIf { it > 0L }
            // Some engines report 0 right after STATE_ENDED; falling back to the
            // last persisted position keeps the stop telemetry (and with it the
            // server's chance to resolve played-ness / final resume position)
            // instead of silently dropping the stop entirely. The > 0
            // guard skips the "never persisted" sentinel (Long.MIN_VALUE),
            // whose ×10_000 overflow would feed garbage ticks into the check
            // below instead of cleanly skipping the report.
            ?: lastPersistedPositionMs.takeIf { it > 0L }
            ?: 0L
        val positionTicks = positionMs * 10_000
        if (positionTicks > 0) {
            stopReportedForSession = sessionId
            scope.launch {
                playbackRepository.reportPlaybackStopped(itemId, sessionId, positionTicks, failed = failed)
                // No manual cache invalidation: the end-of-item
                // auto-advance path marks the episode played, which evicts
                // inside the repository; a same-item reload re-reads through
                // the provider; detail-screen re-entry force re-resolves.
            }
        }
    }

    /**
     * The server start report, incognito-gated: incognito never reaches the
     * server (the same invariant [reportCurrentPlaybackStopped] enforces).
     * The play-session id resolves through [currentPlaySessionId] — the same
     * single-value resolver this ledger's stop reports and persists use.
     * Reached through the load spine's `reportPlaybackStart` hook at stage
     * 10, directly before position/progress tracking starts.
     */
    suspend fun reportPlaybackStart(itemId: String, source: MediaSource?, playMethod: PlayMethod) {
        if (getIncognitoModeEnabled()) return
        playbackRepository.reportPlaybackStart(
            PlaybackStartInfo(
                itemId = itemId,
                sessionId = currentPlaySessionId,
                mediaSourceId = source?.id,
                playMethod = playMethod,
            )
        )
    }

    /**
     * The persist half of the seek funnel: records the seek latches
     * (feeding [getReportPositionMs]) and, when an item is loaded, snapshots
     * the seek position into the process-death store immediately (explicit
     * seeks are the most important position to survive process death — no
     * waiting for the throttle) and schedules the coalesced offline-mirror
     * write. The display write and the engine command stay caller-side.
     */
    fun seekPersisted(positionMs: Long) {
        lastSeekPositionMs = positionMs
        val now = clock()
        lastSeekTimestamp = now
        val itemId = playerSessionManager.sessionState.value.currentItemId ?: return
        lastPersistedPositionMs = positionMs
        // The seek just persisted the store; restart the tick throttle's
        // wall-clock window so post-seek ticks inside the window don't
        // immediately re-persist.
        lastPersistedAtMs = now
        positionStore.persist(itemId, positionMs, currentPlaySessionId, now)
        // The DB mirror is coalesced: rapid scrubbing does not queue one
        // recordProgress per seek. The store snapshot above is already
        // immediate, and the throttled tick mirror catches up regardless.
        val durationMs = playerSessionManager.engine?.durationMs ?: 0L
        scheduleCoalescedSeekProgress(itemId, positionMs, durationMs)
    }

    /**
     * Persists the current playback position so it survives process death.
     * Throttled to at most one write per [POSITION_PERSIST_MIN_WALL_CLOCK_INTERVAL_MS]
     * of WALL CLOCK unless [force] (e.g. an explicit seek) — keying on
     * wall clock rather than a position delta keeps the write cadence fixed
     * regardless of playback speed (a delta gate wrote every 2.5 s at 2×);
     * the accepted trade-off is that crash-resume granularity at >1× speed
     * is ≤5 s wall-clock (coarser in content terms). Also stashes the server
     * session id so the post-restore stop-report pairs with the original
     * start-report.
     */
    fun persistPlaybackPosition(positionMs: Long, force: Boolean) {
        val now = clock()
        if (!force && now - lastPersistedAtMs < POSITION_PERSIST_MIN_WALL_CLOCK_INTERVAL_MS) return
        val itemId = playerSessionManager.sessionState.value.currentItemId ?: return
        lastPersistedPositionMs = positionMs
        lastPersistedAtMs = now
        positionStore.persist(itemId, positionMs, currentPlaySessionId, now)
        // Mirror progress into the offline store so downloads render watched /
        // resume state while offline. No-op for non-downloaded items.
        val durationMs = playerSessionManager.engine?.durationMs ?: 0L
        val positionTicks = positionMs * 10_000L // ms → ticks
        val percentage = mirrorPlayedPercentage(positionMs, durationMs)
        scope.launch {
            offlinePlaybackFacade.recordProgress(
                itemId,
                positionTicks,
                percentage,
                isPlayed = mirrorIsPlayed(percentage),
            )
        }
    }

    /**
     * Coalesces the offline-mirror DB write during seek scrubbing: cancels any
     * in-flight pending write and schedules a fresh one [SEEK_PROGRESS_COALESCE_MS]
     * later, so rapid seeks emit at most one `recordProgress` per quiet window.
     * The position-store snapshot is already written synchronously by
     * [seekPersisted], and the throttled position tick
     * (`persistPlaybackPosition(force=false)`) re-writes the mirror every
     * [POSITION_PERSIST_MIN_WALL_CLOCK_INTERVAL_MS], so a dropped coalesced write is
     * recovered within seconds.
     *
     * Keeps launching on the owner-supplied [scope] (NOT [releaseScope]):
     * the teardown path joins this job after cancelling the viewModelScope.
     */
    private fun scheduleCoalescedSeekProgress(itemId: String, positionMs: Long, durationMs: Long) {
        seekProgressJob?.cancel()
        seekProgressJob = scope.launch {
            delay(SEEK_PROGRESS_COALESCE_MS)
            val positionTicks = positionMs * 10_000L // ms → ticks
            val percentage = mirrorPlayedPercentage(positionMs, durationMs)
            offlinePlaybackFacade.recordProgress(
                itemId,
                positionTicks,
                percentage,
                isPlayed = mirrorIsPlayed(percentage),
            )
        }
    }

    /**
     * Percentage of runtime played for the offline-mirror write — the shared
     * math of [persistPlaybackPosition] and the coalesced seek write above.
     */
    private fun mirrorPlayedPercentage(positionMs: Long, durationMs: Long): Double =
        if (durationMs > 0L) {
            (positionMs.toDouble() / durationMs.toDouble() * 100.0).coerceIn(0.0, 100.0)
        } else 0.0

    /**
     * The offline-mirror `isPlayed` value for a progress write: never write
     * `false` over a mirror row that has already crossed the watched
     * threshold — once playback passes 95%, the row must read as watched, or
     * a tick racing the threshold callback would downgrade the very fact sync
     * relies on.
     */
    private fun mirrorIsPlayed(percentage: Double): Boolean = isWatchedPercentage(percentage)

    /**
     * After process death the Navigation 3 route still carries the *original*
     * entry-point ticks, but the user's in-stream seeks were tracked only in
     * the position store. If we have a persisted position for [itemId] that
     * is beyond the entry point we resume from there. A fresh navigation (new
     * entry) has an empty store, so this is a no-op outside the
     * process-death-restore path.
     *
     * Staleness guard: a position persisted more than
     * [STALE_POSITION_THRESHOLD_MS] ago is ignored. The primary defense
     * against stale auto-resume is the nav-route strip in
     * `rememberNavigationState` (a stripped route never mounts the player at
     * all, so this method never runs). This guard covers any restore path
     * that escapes the strip. A missing/zero timestamp (positions persisted
     * before this field existed, or a non-process-death re-entry) is treated
     * as fresh so the normal resume-from-background path keeps working.
     */
    fun resolveStartTicksAfterProcessDeath(itemId: String, startPositionTicks: Long): Long {
        val savedItemId = positionStore.savedItemId() ?: return startPositionTicks
        if (savedItemId != itemId) return startPositionTicks
        val savedPosMs = positionStore.savedPositionMs() ?: return startPositionTicks
        val persistedAt = positionStore.savedPersistedAtMs() ?: 0L
        return resolveResumeTicks(
            savedPosMs = savedPosMs,
            persistedAtMs = persistedAt,
            nowMs = clock(),
            entryPointTicks = startPositionTicks,
            staleThresholdMs = STALE_POSITION_THRESHOLD_MS,
        )
    }

    /**
     * Pre-seeds the playhead with the resolved start position so the seek bar
     * reflects where playback will resume the instant the new item opens —
     * instead of staying at 0 until the engine emits its first position tick
     * while playing (which for MPV + slow buffering can take 20-30s, and with
     * duration == 0 the bar renders its empty branch anyway). Reached through
     * the pipeline's `onPlayheadSeeded` output with the RESOLVED ticks
     * (explicit request ticks or the offline-mirror resume they resolve to);
     * mirrors `seekTo`'s synchronous display write. Display-only: written
     * directly through the [seedDisplayedPositionMs] seam, not via the
     * progress reporter, so it reports nothing to the server before playback
     * actually begins.
     */
    fun preSeedPlayhead(startPositionTicks: Long) {
        if (startPositionTicks > 0) {
            seedDisplayedPositionMs(startPositionTicks / 10_000)
        }
    }

    /**
     * Restores the server play-session id after process death (when this is
     * the same item) so the eventual stop-report pairs with the start-report
     * instead of orphaning it; otherwise allocates a fresh session id.
     */
    fun restoreOrAllocatePlaySessionId(itemId: String): String {
        val restoredSessionId = positionStore.savedPlaySessionId()
        val savedItemId = positionStore.savedItemId()
        return if (savedItemId == itemId && !restoredSessionId.isNullOrEmpty()) {
            restoredSessionId
        } else {
            java.util.UUID.randomUUID().toString()
        }
    }

    /**
     * New item = new session: initialize's early reset — drop the seek latch
     * and clear the Stop dedup latch so the upcoming session's Stop can be
     * reported.
     */
    fun resetForNewItem() {
        lastSeekPositionMs = null
        lastSeekTimestamp = 0L
        stopReportedForSession = null
    }

    /** Drop the seek latch only — releaseInternalsSessionPart's reset. */
    fun resetSeekLatch() {
        lastSeekPositionMs = null
        lastSeekTimestamp = 0L
    }

    /**
     * The post-teardown persistence reset of initialize: the never-persisted
     * sentinel back in place, the throttle window re-armed and any pending
     * coalesced seek-mirror write dropped (a same-item reload re-seeds from
     * the resolved ticks).
     */
    fun resetPersistenceWindow() {
        lastPersistedPositionMs = Long.MIN_VALUE
        lastPersistedAtMs = 0L
        seekProgressJob?.cancel()
        seekProgressJob = null
    }

    /**
     * The stop-report inputs snapshot the session's release() takes BEFORE
     * any teardown statement runs — releaseInternalsSessionPart calls the
     * session-manager release, which clears the session state these values
     * read; nothing between the snapshot and the teardown touches these
     * values.
     */
    class TeardownStopInputs(
        val itemId: String?,
        val sessionId: String,
        val positionTicks: Long,
        val failed: Boolean,
    )

    /** The pre-teardown snapshot — see [TeardownStopInputs]. */
    fun snapshotTeardownInputs(): TeardownStopInputs =
        TeardownStopInputs(
            itemId = playerSessionManager.sessionState.value.currentItemId,
            sessionId = currentPlaySessionId,
            positionTicks = getReportPositionMs() * 10_000,
            failed = progressReporter.isErrorLatched(),
        )

    /**
     * Belt-and-suspenders: flush a pending coalesced seek-mirror write so the
     * offline store doesn't lag the final position on release. The write is
     * moved onto the release scope (IO + NonCancellable) so it survives the
     * viewModelScope being cancelled on clear().
     */
    fun flushPendingSeekOnRelease() {
        val pendingSeek = seekProgressJob ?: return
        releaseScope.launch(NonCancellable) {
            pendingSeek.join()
        }
    }

    /**
     * The final teardown Stop report, deduped through [stopReportedForSession].
     * Skip the second Stop if reportCurrentPlaybackStopped already sent one
     * for this session — duplicate Stop reports confuse the server's
     * resume/progress bookkeeping. The reporter's stalled-finish stop (sent
     * at the FULL duration) dedups the same way: a teardown stop at the
     * stalled position would only downgrade the position the server already
     * resolved.
     */
    fun finalStopReport(inputs: TeardownStopInputs) {
        val itemId = inputs.itemId
        val sessionId = inputs.sessionId
        val positionTicks = inputs.positionTicks
        val failed = inputs.failed
        if (itemId != null && positionTicks > 0 && sessionId != stopReportedForSession) {
            stopReportedForSession = sessionId
            if (!progressReporter.hasReportedStopFor(sessionId)) {
                releaseScope.launch(NonCancellable) {
                    // withTimeoutOrNull, not withTimeout: the timeout is an
                    // expected give-up (best-effort final report), not a
                    // failure — a rethrown TimeoutCancellationException would
                    // escape this handler-less scope. Real cancellation still
                    // propagates through the rethrowing variant.
                    runCatchingRethrowingCancellation {
                        withTimeoutOrNull(5_000) {
                            playbackRepository.reportPlaybackStopped(
                                itemId = itemId,
                                sessionId = sessionId,
                                positionTicks = positionTicks,
                                failed = failed,
                            )
                        }
                    }
                    // No manual cache invalidation here: the detail
                    // screen's re-entry freshness comes from the provider's forced
                    // re-resolve (requestRevalidate) and the auto-advance path
                    // already evicts via markPlayed inside the repository — the
                    // old invalidateUserDataCaches call duplicated both.
                }
            }
        }
    }

    private companion object {
        /** Minimum wall-clock interval (ms) between throttled process-death persists. */
        const val POSITION_PERSIST_MIN_WALL_CLOCK_INTERVAL_MS = 5_000L

        /**
         * Quiet-period for coalescing the *offline-mirror* DB write during rapid
         * scrubbing: seekTo fires one per seek gesture, and the immediate
         * `recordProgress` launches would queue against Room's executor. Only the DB
         * mirror is coalesced — the position-store snapshot stays immediate so explicit
         * seek positions still survive process death. The position tick's throttled
         * mirror write (`persistPlaybackPosition(force=false)`) catches up within
         * seconds, so a dropped coalesced write is never lost for long.
         */
        const val SEEK_PROGRESS_COALESCE_MS = 500L
    }
}
