package com.raulshma.jellyplay.feature.player.video

import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// SavedStateHandle keys for surviving process death. The in-stream
// playback position, the item it belongs to, the server session id, and the
// epoch at which the position was last persisted are stored so playback
// resumes from the user's last seek rather than the original entry point, and
// so the eventual stop-report matches the start. The timestamp lets a restore
// reject a position that is too old to be a trustworthy "continue from here"
// (see STALE_POSITION_THRESHOLD_MS) — the primary defense is the nav-route
// strip, but a stale SavedStateHandle position is the last-resort signal that
// the player's in-memory state is gone and auto-resume would land mid-stream
// on an episode the user moved past via auto-advance.
private const val SAVED_KEY_ITEM_ID = "video_player.saved_item_id"
private const val SAVED_KEY_POSITION_MS = "video_player.saved_position_ms"
private const val SAVED_KEY_PLAY_SESSION_ID = "video_player.saved_play_session_id"
private const val SAVED_KEY_POSITION_PERSISTED_AT = "video_player.saved_position_persisted_at"

/** Minimum position delta (ms) between throttled process-death persists. */
private const val POSITION_PERSIST_MIN_INTERVAL_MS = 5_000L

/**
 * Quiet-period for coalescing the *offline-mirror* DB write during rapid
 * scrubbing: seekTo fires one per seek gesture, and the immediate
 * `recordProgress` launches would queue against Room's executor. Only the DB
 * mirror is coalesced — the position-store snapshot stays immediate so explicit
 * seek positions still survive process death. The position tick's throttled
 * mirror write (`persistPlaybackPosition(force=false)`) catches up within
 * seconds, so a dropped coalesced write is never lost for long.
 */
private const val SEEK_PROGRESS_COALESCE_MS = 500L

/**
 * A persisted position older than this is treated as stale on a process-death
 * restore and ignored: the user backgrounded the app long enough that
 * auto-resuming mid-stream (potentially on an episode they auto-advanced past)
 * is worse than landing on Home and continuing via the "Continue Watching" row.
 * Matches the ~1h threshold users report as the trigger; well above any real
 * short backgrounding (notification reply, brief app switch).
 */
private const val STALE_POSITION_THRESHOLD_MS = 60L * 60L * 1000L

/**
 * Pure resume-position resolver used by
 * [PlaybackSession.resolveStartTicksAfterProcessDeath] so the staleness +
 * "only advance forward" rules are unit-testable without a session.
 *
 * Rules:
 * - No persisted position (`savedPosMs <= 0`): keep the entry point.
 * - Persisted position too old (`persistedAtMs > 0` and older than
 *   [staleThresholdMs]): keep the entry point. A zero/missing timestamp is
 *   treated as fresh so a normal resume-from-background keeps working.
 * - Otherwise resume at the persisted position, but never below the deliberate
 *   entry point (auto-advance may have moved the user forward of the route's
 *   original ticks; rewinding would jump back unexpectedly).
 */
internal fun resolveResumeTicks(
    savedPosMs: Long,
    persistedAtMs: Long,
    nowMs: Long,
    entryPointTicks: Long,
    staleThresholdMs: Long = STALE_POSITION_THRESHOLD_MS,
): Long {
    if (savedPosMs <= 0L) return entryPointTicks
    if (persistedAtMs > 0L && nowMs - persistedAtMs > staleThresholdMs) return entryPointTicks
    val savedTicks = savedPosMs * 10_000
    return if (savedTicks > entryPointTicks) savedTicks else entryPointTicks
}

/**
 * One playback session's lifecycle — the "deep module" being extracted from
 * [VideoPlayerViewModel] (Stage B of the video-player refactor).
 *
 * Step B1a moved the session-scoped latches and bookkeeping fields. Step B1b
 * moved the initialize path: [initialize] owns the load sequence — latch
 * resets, the routing early-returns, single-flight [loadJob] tracking, and
 * WHEN the [SessionLoadPipeline] starts. Step B2 moved the reload/retry
 * paths ([retryWithEngine], [retryPlayback], [reloadForMode],
 * [reloadForStreamChange]) plus the [EngineEventCoordinator] — its
 * construction, re-arm and decision execution — so the engine-swap
 * choreography and the decision fan-out live here, surfacing outcomes as
 * [SessionEvent]s that the ViewModel (the single forwarder) maps into its
 * sinks. Step B3 moved the reporting + release surface: the stop-report
 * ([reportCurrentPlaybackStopped] + its dedup latch), the seek latches and
 * [getReportPositionMs], the seek/position persistence behind
 * [SessionPositionStore] ([seekPersisted], [persistPlaybackPosition],
 * [resolveStartTicksAfterProcessDeath], the play-session id restore), and the
 * release split — the session-owned teardown half runs FIRST, then the
 * ViewModel-owned half back-to-back via the
 * [SessionLifecycleHooks.releaseInternalsVmPart] hook, and [release] owns the
 * final stop-report + pending-seek join on the release scope. Step B4 moved
 * the cinema-intro sequencing ([beginCinemaMode] / [loadCinemaIntro] /
 * [advanceCinemaIntro] + the [cinemaIntroContext] latch — the uiState write
 * goes through the [setCinemaIntroState] seam and the post-intro recursion
 * is a plain internal call to [initialize]), the mini-player reclaim BODY
 * ([loadReclaimedEngine]; the gate stays a
 * [SessionLifecycleHooks.tryReclaimMiniPlayer] hook) and
 * [preSeedPlayhead] (display write via the [seedDisplayedPositionMs] seam).
 * Every ViewModel-bound slice stays behind [SessionLifecycleHooks] or a
 * constructor lambda; the session never touches the ui state.
 *
 * Construction contract:
 * - the ViewModel's [CoroutineScope] is INJECTED, never constructed here.
 *   Session-launched coroutines (e.g. the coalesced seek-mirror write tracked
 *   by [pendingSeekProgressJob]) keep launching on that scope — never on
 *   [releaseScope] and never on a session-internal scope cancelled in
 *   release(), because the onDispose teardown path joins the pending seek
 *   job and depends on those launch semantics;
 * - [PlayerSessionManager] and [PlaybackProgressReporter] are injected as
 *   already-constructed instances. The reporter keeps being built inside the
 *   ViewModel (its ui-state handle wiring stays VM-side by design) and is
 *   handed over here as an object;
 * - the [SessionLoadPipeline] is CONSTRUCTED in the ViewModel — its outputs
 *   and hooks own every ui-state touch — and injected here as an object: the
 *   session owns when a load starts, never how the pipeline reaches the ui
 *   state;
 * - the same no-ui-state rule applies to the B2–B4 additions: the
 *   media-session controller is injected as an already-constructed instance,
 *   the process-death position persistence is reached ONLY through the
 *   [SessionPositionStore] seam (the ViewModel keeps the handle solely to
 *   build the store), and every value the moved code used to read from (or
 *   write into) the ui state is supplied as a parameter or a getter/setter
 *   lambda owned by the VM (B4: the cinema intro uiState write through
 *   [setCinemaIntroState] and the playhead display write through
 *   [seedDisplayedPositionMs]).
 */
internal class PlaybackSession(
    val scope: CoroutineScope,
    val playerSessionManager: PlayerSessionManager,
    val progressReporter: PlaybackProgressReporter,
    private val sessionLoadPipeline: SessionLoadPipeline,
    private val hooks: SessionLifecycleHooks,
    private val mediaSessionController: MediaSessionController,
    private val playbackStore: PlaybackStore,
    private val adaptiveBitrateManager: AdaptiveBitrateManager,
    /** Server playback telemetry (the session-owned Stop reports). */
    private val playbackRepository: PlaybackRepository,
    /** Offline-mirror writes for the seek-coalesced + throttled position persists. */
    private val offlinePlaybackFacade: OfflinePlaybackFacade,
    /** Media-detail fetch for the mini-player reclaim body (since B4). */
    private val mediaRepository: MediaRepository,
    /**
     * The uiState `cinemaIntroState` write seam (since B4): every write the
     * cinema sequencing needs flows through this VM-supplied setter — the
     * session never touches the ui state. Nullable so the same seam both
     * shows ([loadCinemaIntro]) and clears ([advanceCinemaIntro]) the intro.
     */
    private val setCinemaIntroState: (CinemaIntroUiState?) -> Unit,
    /**
     * Writes the VM-owned high-frequency position display flow — the write
     * seam behind [preSeedPlayhead] (since B4).
     */
    private val seedDisplayedPositionMs: (Long) -> Unit,
    /** The process-death resume-position persistence (SavedStateHandle behind a seam). */
    private val positionStore: SessionPositionStore,
    /** Current in-memory streaming quality (the VM's ui-prefs mirror). */
    private val getStreamingQuality: () -> StreamingQuality,
    /** Writes the in-memory playback-mode mirror (ui state stays VM-owned). */
    private val setUiPlaybackMode: (PlaybackMode) -> Unit,
    /** Incognito gate for the session-owned stop-report. */
    private val getIncognitoModeEnabled: () -> Boolean,
    /** Feeds the track-selection helper's pending stream indices before a stream-change reload. */
    private val setPendingStreams: (subtitleStreamIndex: Int?, audioStreamIndex: Int?) -> Unit,
    /** Synchronous playback-mode read feeding the coordinator's fallback latch policy. */
    private val getPlaybackMode: () -> PlaybackMode,
    /** Localized FORCE_DIRECT_PLAY fallback notice for [SessionEvent.InformUser]. */
    private val directPlayFallbackNotice: (String) -> String,
    /** Pass-out protection hours; values <= 0 disable the poller. */
    private val passOutHours: Flow<Int>,
    /**
     * Invoked after a disposed coordinator was re-created: the VM restarts
     * its engine-mirror collectors (play/buffering ui-state writes) against
     * the new [engineEventCoordinator] instance.
     */
    private val onEngineEventCoordinatorRearmed: () -> Unit,
) {

    /**
     * Direct alias of the session manager's session flow — the SAME
     * [StateFlow] instance, no re-publish and no stateIn, so dispatch
     * ordering relative to [engineFlow] collectors is unchanged from when the
     * ViewModel collected the manager directly.
     */
    val sessionState: StateFlow<PlayerSessionState> = playerSessionManager.sessionState

    /** Direct alias of the session manager's engine flow — same instance, no re-publish. */
    val engineFlow: StateFlow<MediaEngine?> = playerSessionManager.engineFlow

    private val _events = MutableSharedFlow<SessionEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Session-level outcomes (errors to surface, user notices, end of
     * playback, close/pass-out requests) emitted by the decision fan-out and
     * the reload paths. The ViewModel is the single forwarder: one collector
     * maps each event into its existing sinks. `tryEmit`-only — a
     * mid-teardown emission never suspends (same contract as the
     * coordinator's decision stream).
     */
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    // ── Engine-event orchestration ──────────────────────────────────────────
    // The coordinator owns the engine-event *policies* (guarded play/buffering
    // mirrors, the FORCE_DIRECT_PLAY → transcode one-shot fallback latch, the
    // initial-buffering watchdog, subtitle toasts, pass-out protection) and
    // emits [EngineDecision]s; the session owns the coordinator's lifecycle
    // (construction, re-arm on re-initialization) and executes the decisions.
    // A `var` because the VM's performRelease() disposes it and the
    // Activity-scoped VM is reused across media: every load re-arms it via
    // [ensureEngineEventCoordinatorActive].

    private fun createEngineEventCoordinator() = EngineEventCoordinator(
        scope = scope,
        engineFlow = playerSessionManager.engineFlow,
        getPlaybackMode = getPlaybackMode,
        directPlayFallbackNotice = directPlayFallbackNotice,
        passOutHours = passOutHours,
    )

    /**
     * The live coordinator. Exposed so the VM can drive the pieces that stay
     * VM-owned: the mirror collectors ([isPlaying]/[isBuffering] ui-state
     * writes), the latch resets ([EngineEventCoordinator.onNewItem] /
     * [EngineEventCoordinator.onPlaybackModeChanged]), the interaction clock
     * ([EngineEventCoordinator.onUserInteraction]) and the teardown-time
     * [EngineEventCoordinator.dispose]. Only this class reassigns it.
     */
    internal var engineEventCoordinator: EngineEventCoordinator = createEngineEventCoordinator()
        private set

    /** Fan-out collector for the coordinator's decision stream. */
    private var engineDecisionJob: Job? = null

    init {
        startEngineDecisionFanOut()
    }

    /**
     * Starts (or restarts, after a dispose/re-arm cycle) the decision
     * executor. The mirror collectors stay VM-side — they write the ui state,
     * which this class never touches.
     */
    private fun startEngineDecisionFanOut() {
        engineDecisionJob?.cancel()
        val coordinator = engineEventCoordinator
        engineDecisionJob = scope.launch {
            coordinator.decisions.collect { decision ->
                executeEngineDecision(decision)
            }
        }
    }

    /**
     * Re-creates the engine-event coordinator if a previous VM
     * [com.raulshma.jellyplay.feature.player.video.VideoPlayerViewModel.release]
     * disposed it (the Activity-scoped VM is reused across media, so every
     * load must re-arm it exactly like the PiP transport). Also re-subscribes
     * the decision fan-out and pokes [onEngineEventCoordinatorRearmed] so the
     * VM re-arms its mirror collectors against the new instance.
     */
    private fun ensureEngineEventCoordinatorActive() {
        if (!engineEventCoordinator.disposed) return
        engineEventCoordinator = createEngineEventCoordinator()
        startEngineDecisionFanOut()
        onEngineEventCoordinatorRearmed()
    }

    /**
     * Executes one [EngineDecision]: what a decision *does* (reload
     * choreography, engine commands, store writes, user-visible outcomes via
     * [SessionEvent]). Idempotent after release — the decisions that touch
     * playback state are dropped rather than executing against a released
     * session (the mirrors and pure notices pass through, as before).
     */
    private fun executeEngineDecision(decision: EngineDecision) {
        when (decision) {
            is EngineDecision.ShowError -> {
                // EngineError is structured (retryable / Decoder / Drm /
                // Network / Source / Render / Unknown) — forward the
                // taxonomy's display message AND the structured retryability
                // verdict, so the dialog can offer same-engine retry
                // (Network/Render) vs. switch-engine (Decoder/Drm).
                if (released) return
                _events.tryEmit(
                    SessionEvent.ShowError(
                        error = decision.error.message,
                        retryable = decision.error.retryable,
                        clearBuffering = decision.clearBuffering,
                    )
                )
            }
            is EngineDecision.FallbackToTranscode -> {
                if (released) return
                launchFallbackToTranscode(
                    fromPositionMs = decision.fromPositionMs,
                    quality = getStreamingQuality(),
                )
            }
            EngineDecision.PlaybackEnded -> {
                if (!released) _events.tryEmit(SessionEvent.PlaybackEnded)
            }
            EngineDecision.PassOutPause -> {
                playerSessionManager.engine?.pause()
                _events.tryEmit(SessionEvent.PassOutPause)
            }
            is EngineDecision.InformUser -> _events.tryEmit(
                SessionEvent.InformUser(decision.message)
            )
        }
    }

    // @Volatile: set in the VM's release()/performRelease() (off Main) and
    // read in initialize's early-bail + decision guards.
    @Volatile
    internal var released: Boolean = false

    /**
     * Dedup guard for Stop reports. Two release paths can fire for the same
     * session — reportCurrentPlaybackStopped (transcode fallback,
     * end-of-item) and the final teardown in performRelease. Without this
     * guard the server receives a duplicate Stop for the same play-session
     * id, which can mark the item more-watched than reality and trigger
     * duplicate resume rows. Keyed by sessionId so a new load (new session)
     * clears the latch.
     */
    @Volatile
    internal var stopReportedForSession: String? = null

    /** Position (ms) of the last explicit seek; feeds getReportPositionMs. */
    internal var lastSeekPositionMs: Long? = null

    /** Wall clock of the last explicit seek; bounds the seek-latch's validity. */
    internal var lastSeekTimestamp: Long = 0L

    /**
     * Last position (ms) written to the process-death persistence; used to
     * throttle writes.
     */
    internal var lastPersistedPositionMs: Long = Long.MIN_VALUE

    /**
     * Locally-allocated UUID play-session id — the fallback used until (and
     * unless) the server issues its own id through the PlaybackInfo endpoint
     * (see [PlayerSessionState.playSessionId] on the session manager).
     */
    internal var playSessionId: String = java.util.UUID.randomUUID().toString()

    /**
     * Single-flight coalescing job for the offline-mirror DB write during
     * seek scrubbing; cancelled + relaunched per seek. Must keep launching on
     * the ViewModel-supplied [scope] — the teardown path joins this job after
     * cancelling the viewModelScope.
     */
    internal var pendingSeekProgressJob: Job? = null

    /**
     * In-flight media-load coroutine, so a new initialize call can cancel the
     * previous one before launching its own — prevents overlapping
     * network/teardown side effects when a SyncPlay load event races a user
     * navigation.
     */
    internal var loadJob: Job? = null

    /**
     * Scope for teardown work that must outlive the viewModelScope on clear()
     * (the final stop-report and the pending-seek join): IO dispatcher +
     * supervisor so one failing write cannot cancel the other. The VM cancels
     * it from onCleared.
     */
    internal val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Job returned from [initialize] when a hook early-returns before any
     * load was launched (remote "Play On" routing, same-item short-circuit):
     * callers get a uniformly typed, already-finished handle instead of a
     * dangling active job.
     */
    private val noLoadJob: Job = Job().also { it.complete() }

    /**
     * The load sequence previously inlined as the ViewModel's
     * `initializeInternal`, order preserved 1:1:
     *
     * 1. `released = false`;
     * 2. [SessionLifecycleHooks.rearmTransports] (PiP transport re-arm)
     *    followed by the session-owned engine-event coordinator re-arm
     *    ([ensureEngineEventCoordinatorActive]);
     * 3. [SessionLifecycleHooks.resetForNewItem] (autoplay reset,
     *    autoplay-cancelled clear, coordinator new-item latch, pending stream
     *    indices);
     * 4. seek-latch + Stop-dedup latch resets (steps 3–4 were interleaved with
     *    these pure field resets in the old body; the VM-side writes keep
     *    their relative order, the three field resets run here as one block);
     * 5. remote-routing early-return via
     *    [SessionLifecycleHooks.routeToRemotePlaySession];
     * 6. same-item short-circuit via [shouldShortCircuitSameItemReload];
     * 7. [SessionLifecycleHooks.wasInSyncPlay] (SyncPlay flag read) followed
     *    by the outgoing session's stop-report ([reportCurrentPlaybackStopped],
     *    session-side since B3, directly after the flag read);
     * 8. cancel any in-flight [loadJob];
     * 9. mini-player reclaim early-return: the GATE stays a VM hook
     *    ([SessionLifecycleHooks.tryReclaimMiniPlayer] — mini-player state
     *    knowledge), but the body ([loadReclaimedEngine]) is session-side
     *    since B4: veil lift via [SessionLifecycleHooks.onMiniPlayerReclaimed]
     *    at exactly its old position (synchronously before the body launch),
     *    then detail fetch → engine bind → media session → tracking restart →
     *    hydration via [SessionLifecycleHooks.hydrateReclaimedItem];
     * 10. [SessionLifecycleHooks.releaseMiniPlayerState];
     * 11. per-item teardown, split at B3 into two back-to-back halves (same
     *     synchronous call chain, no dispatch hop between them — an
     *     interleaved recomposition could flash the outgoing item's rebuilt
     *     stale title): the session-owned half ([releaseInternalsSessionPart]:
     *     in-flight load cancel, reporter jobs, media-session release, PSM
     *     release, seek latches) FIRST, then
     *     [SessionLifecycleHooks.releaseInternalsVmPart] (loading-veil raise +
     *     the VM-owned controller/ui-state teardown), followed at exactly its
     *     old position by the process-death play-session restore
     *     ([restoreOrAllocatePlaySessionId]);
     * 12. persistence-latch resets ([lastPersistedPositionMs],
     *     [pendingSeekProgressJob]);
     * 13. [SessionLifecycleHooks.clearTrickplay];
     * 14. [SessionLifecycleHooks.reattachSyncPlay] (conditional on step 7);
     * 15. start the [SessionLoadPipeline] and track it as [loadJob].
     */
    fun initialize(request: LoadRequest): Job {
        released = false
        hooks.rearmTransports()
        ensureEngineEventCoordinatorActive()
        hooks.resetForNewItem(
            subtitleStreamIndex = request.subtitleStreamIndex,
            audioStreamIndex = request.audioStreamIndex,
        )
        // New item = new session: drop the seek latch and clear the Stop
        // dedup latch so the upcoming session's Stop can be reported.
        lastSeekPositionMs = null
        lastSeekTimestamp = 0L
        stopReportedForSession = null

        // "Play On" routing: a connected Jellyfin remote session takes the
        // video instead of local playback. (Full rationale on the routing
        // hook.)
        if (hooks.routeToRemotePlaySession(request)) return noLoadJob

        if (shouldShortCircuitSameItemReload(request.itemId, request.startPositionTicks)) return noLoadJob

        val wasInSyncPlay = hooks.wasInSyncPlay()
        // Stop-report the outgoing session before anything is cancelled or
        // torn down — its old position, directly after the flag read (the
        // report moved session-side at B3; the hook is a pure flag read).
        reportCurrentPlaybackStopped()

        // Cancel any in-flight load before starting a new one. initialize
        // itself runs on Main.immediate so its synchronous prefix cannot
        // interleave with another call; but each call launches a long-lived
        // async load coroutine (media-detail fetch, engine load,
        // trickplay/segments/episodes). Two of those coroutines — e.g. a
        // SyncPlay `onLoadItem` event arriving while a user tap is also
        // loading — could interleave their network/teardown side effects
        // (double stop-reports, crossed engine binds). Tracking and cancelling
        // the previous load makes "latest load wins" deterministic without
        // changing the synchronous semantics of this function.
        loadJob?.cancel()

        hooks.tryReclaimMiniPlayer(request.itemId)?.let { reclaimed ->
            loadJob = loadReclaimedEngine(reclaimed, request.itemId)
            return loadJob!!
        }

        hooks.releaseMiniPlayerState()
        // Per-item teardown, split at B3: the session-owned half runs FIRST,
        // then the VM-owned half back-to-back from this same synchronous
        // chain. The play-session restore runs at exactly its old position
        // right after the teardown (it used to be the hook's return value).
        releaseInternalsSessionPart()
        hooks.releaseInternalsVmPart()
        playSessionId = restoreOrAllocatePlaySessionId(request.itemId)
        // The playhead is seeded by the load pipeline from the RESOLVED start
        // ticks (see onPlayheadSeeded) — seeding from the raw request ticks
        // missed the offline-mirror resume that resolution produces.
        lastPersistedPositionMs = Long.MIN_VALUE
        pendingSeekProgressJob?.cancel()
        pendingSeekProgressJob = null
        hooks.clearTrickplay()

        if (wasInSyncPlay) {
            hooks.reattachSyncPlay()
        }

        // The ordered load spine (SyncPlay reconcile → prefs projection →
        // cinema gate → offline-start resolution → loadMedia → per-item
        // hydration → media session + duration seed → trickplay → reports)
        // lives in [SessionLoadPipeline]; its stage order is pinned by
        // SessionLoadPipelineTest.
        val job = sessionLoadPipeline.start(scope = scope, request = request)
        loadJob = job
        return job
    }

    /**
     * Same-item short-circuit for [initialize]: re-selecting the item that is
     * already loaded in a live state (not ENDED/IDLE/ERROR) is a no-op —
     * unless a non-zero resume position was requested or playback has not
     * actually started yet, in which case the reload proceeds.
     */
    private fun shouldShortCircuitSameItemReload(itemId: String, startPositionTicks: Long): Boolean {
        if (playerSessionManager.sessionState.value.currentItemId != itemId) return false
        val engine = playerSessionManager.engine ?: return false
        val state = engine.playbackState.value
        if (state == EnginePlaybackState.ENDED ||
            state == EnginePlaybackState.IDLE ||
            state == EnginePlaybackState.ERROR
        ) {
            return false
        }
        if (startPositionTicks != 0L) return false
        return engine.currentPositionMs <= 0
    }

    /**
     * Re-resolves the current item against the (possibly changed)
     * [PlaybackMode]/[StreamingQuality] and swaps the engine onto the new
     * stream at the current position. [mode] and [quality] are supplied by
     * the VM wrapper from its ui-prefs mirror (this class never reads the ui
     * state). Surfaces a notice via [SessionEvent.InformUser] when switching
     * to a transcode since the brief re-buffer is otherwise surprising, and
     * auto-falls-back to transcode when a forced-direct-play request yields
     * no playable method.
     */
    suspend fun reloadForMode(mode: PlaybackMode, quality: StreamingQuality) {
        val pos = playerSessionManager.engine?.currentPositionMs ?: 0L

        // Stop-report the *current* server session before the swap: reloadPlayback
        // overwrites sessionState.playSessionId with the new server id, so without
        // this the previous session is never reported stopped (the server would
        // see start(idA) → progress(idB) → stop(idB), orphaning idA — the same
        // desync class the currentPlaySessionId resolver prevents elsewhere).
        reportCurrentPlaybackStopped()
        progressReporter.cancelJobs()

        val resolved = playerSessionManager.reloadPlayback(mode, quality, pos) ?: return
        afterEngineReloadRebuildSessionAndTracking()

        if (resolved.playMethod == PlayMethod.TRANSCODE) {
            _events.tryEmit(SessionEvent.InformUser("Switched to transcoded stream — re-buffering"))
        }
        if (mode == PlaybackMode.FORCE_DIRECT_PLAY &&
            resolved.playMethod != PlayMethod.DIRECT_PLAY
        ) {
            _events.tryEmit(
                SessionEvent.InformUser("Direct Play unavailable for this item — falling back to transcode")
            )
            launchFallbackToTranscode(
                fromPositionMs = playerSessionManager.engine?.currentPositionMs ?: pos,
                quality = quality,
            )
        }
    }

    /**
     * After a same-item engine reload ([reloadForMode], [retryWithEngine])
     * the previous engine — whose `positionFlow` the position-tracking job
     * was collecting — has been released, so the job goes silent. The media
     * session was also bound to the released engine's player. Rebuild both so
     * the seek bar, buffer bar, stats overlay, segment auto-skip and the
     * system media notification track the new engine. (Every other reload
     * path — initialize / cinema / retry — already does this; consolidating it
     * here keeps any future engine swap covered the same way.)
     */
    private fun afterEngineReloadRebuildSessionAndTracking() {
        val sessionState = playerSessionManager.sessionState.value
        mediaSessionController.createForItem(
            sessionState.currentItemId ?: "",
            sessionState.title,
            sessionState.subtitle,
        )
        progressReporter.startPositionTracking()
        progressReporter.startProgressReporting()
    }

    private fun launchFallbackToTranscode(fromPositionMs: Long, quality: StreamingQuality) {
        setUiPlaybackMode(PlaybackMode.FORCE_TRANSCODE)
        scope.launch {
            playbackStore.setPlaybackMode(PlaybackMode.FORCE_TRANSCODE)
            reportCurrentPlaybackStopped()
            progressReporter.cancelJobs()
            playerSessionManager.reloadPlayback(
                PlaybackMode.FORCE_TRANSCODE,
                quality,
                fromPositionMs,
            )
            afterEngineReloadRebuildSessionAndTracking()
        }
    }

    /**
     * Retry playback on a different engine after a fatal error.
     * [playbackSpeed] and [streamingQuality] are supplied by the VM wrapper
     * from its ui-state mirror; the error-dialog clear that used to precede
     * the engine swap stays VM-side (a synchronous ui-state write).
     */
    fun retryWithEngine(
        playerType: PlayerType,
        playbackSpeed: Float,
        streamingQuality: StreamingQuality,
    ) {
        val currentPos = playerSessionManager.engine?.currentPositionMs ?: 0L
        val maxBitrate = adaptiveBitrateManager.resolveMaxBitrate(streamingQuality)?.toInt()
        progressReporter.cancelJobs()
        mediaSessionController.release()
        scope.launch {
            playbackStore.setPreferredPlayer(playerType)
            playerSessionManager.reloadWithEngine(playerType, currentPos, playbackSpeed, maxBitrate)
            afterEngineReloadRebuildSessionAndTracking()
        }
    }

    /**
     * Same-engine retry for recoverable errors (Network, Render, or the
     * buffering watchdog timeout). Reloads the current engine at the current
     * position, mirroring [retryWithEngine] without changing engine.
     * [preferredPlayerType] selects the engine (the ui-state mirror of the
     * last-chosen engine, supplied by the VM wrapper).
     */
    fun retryPlayback(
        playbackSpeed: Float,
        streamingQuality: StreamingQuality,
        preferredPlayerType: PlayerType,
    ) {
        val currentPos = playerSessionManager.engine?.currentPositionMs ?: 0L
        val maxBitrate = adaptiveBitrateManager.resolveMaxBitrate(streamingQuality)?.toInt()
        progressReporter.cancelJobs()
        mediaSessionController.release()
        scope.launch {
            playerSessionManager.reloadWithEngine(
                preferredPlayerType,
                currentPos,
                playbackSpeed,
                maxBitrate,
            )
            afterEngineReloadRebuildSessionAndTracking()
        }
    }

    /**
     * Reload playback for the current item at the current position with a new
     * audio/subtitle stream index. Used when the user picks a server-origin
     * audio or subtitle track during transcoded playback — mpv cannot switch
     * audio in-place on an HLS manifest, and embedded subs aren't in the
     * transcode, so the server must re-issue the stream with the chosen index.
     */
    fun reloadForStreamChange(audioStreamIndex: Int?, subtitleStreamIndex: Int?) {
        if (playerSessionManager.engine == null) return
        val positionMs = getReportPositionMs()
        scope.launch {
            setPendingStreams(subtitleStreamIndex, audioStreamIndex)
            playerSessionManager.reloadForStreamChange(audioStreamIndex, subtitleStreamIndex, positionMs)
        }
    }

    // ── Mini-player reclaim (body moved from the VM at B4) ──────────────────

    /**
     * Load coroutine behind the mini-player-reclaim routing early-return in
     * [initialize]: binds the already-playing reclaimed engine to the session
     * manager and rebuilds its session bookkeeping (media session, tracking,
     * segments, episodes). Playback is continuous — no load screen, no
     * [SessionLoadPipeline] run (the engine never reloads).
     *
     * The GATE ([SessionLifecycleHooks.tryReclaimMiniPlayer]) stays a VM hook;
     * the two uiState/controller-bound slices of the old body stay VM-side at
     * exactly their old positions: the loading-veil lift
     * ([SessionLifecycleHooks.onMiniPlayerReclaimed], synchronously before the
     * body launch) and the post-bind hydration
     * ([SessionLifecycleHooks.hydrateReclaimedItem]).
     */
    internal fun loadReclaimedEngine(
        reclaimed: MediaEngine,
        itemId: String,
    ): Job {
        // Reclaim promotes an already-playing mini-player engine to
        // fullscreen — playback is continuous, so no load screen.
        hooks.onMiniPlayerReclaimed()
        return scope.launch {
            val detailResult = mediaRepository.getMediaDetail(itemId)
            val detail = detailResult.getOrNull()
            if (detail != null) {
                playerSessionManager.bindReclaimedEngine(reclaimed, itemId, detail)
                val sessionState = playerSessionManager.sessionState.value
                mediaSessionController.createForItem(
                    itemId,
                    sessionState.title,
                    sessionState.subtitle,
                )
                progressReporter.startPositionTracking()
                progressReporter.startProgressReporting()
                hooks.hydrateReclaimedItem(itemId, detail)
            }
        }
    }

    // ── Cinema Mode pre-roll sequencing (moved from the VM at B4) ────────────

    /**
     * Active Cinema Mode pre-roll context. Non-null only between the moment
     * intros are queued ([beginCinemaMode]) and the moment the main feature
     * begins loading. Captures the original [initialize] arguments so the main
     * feature can be resumed once all intros have been consumed (or skipped).
     */
    internal data class CinemaIntroContext(
        val mainItemId: String,
        val mainMediaSourceId: String?,
        val mainStartPositionTicks: Long,
        val mainSubtitleStreamIndex: Int?,
        val mainAudioStreamIndex: Int?,
        val intros: List<MediaItem>,
        val currentIndex: Int,
    )

    // @Volatile: written by the session load launch (beginCinemaMode, reached
    // through the pipeline's beginCinemaMode hook) + advanceCinemaIntro, read
    // from the VM (handlePlaybackEnded / skipIntro / the reporter's
    // end-of-media callback / the setVideoEffects per-item persist gate), and
    // cleared by the VM-part teardown at exactly its old slot — see
    // [SessionLifecycleHooks.releaseInternalsVmPart].
    @Volatile
    internal var cinemaIntroContext: CinemaIntroContext? = null

    /**
     * Cinema Mode take-over: queues the pre-roll [intros] and loads the first
     * one. Invoked by the ViewModel's `beginCinemaMode` pipeline hook — the
     * session owns the whole sequencing (context + loads + advance) since B4.
     */
    internal fun beginCinemaMode(intros: List<MediaItem>, request: LoadRequest) {
        cinemaIntroContext = CinemaIntroContext(
            mainItemId = request.itemId,
            mainMediaSourceId = request.mediaSourceId,
            mainStartPositionTicks = request.startPositionTicks,
            mainSubtitleStreamIndex = request.subtitleStreamIndex,
            mainAudioStreamIndex = request.audioStreamIndex,
            intros = intros,
            currentIndex = 0,
        )
        loadCinemaIntro(intros.first())
    }

    private fun loadCinemaIntro(intro: MediaItem) {
        val context = cinemaIntroContext ?: return
        scope.launch {
            setCinemaIntroState(
                CinemaIntroUiState(
                    title = intro.name.ifBlank { "Intro" },
                    currentIndex = context.currentIndex + 1,
                    totalCount = context.intros.size,
                )
            )
            // Pre-roll intros are not part of the user's library history — skip
            // server-side playback reporting and segment/next-episode/trickplay
            // bookkeeping for them.
            playerSessionManager.loadMedia(intro.id, null, 0L)
            mediaSessionController.createForItem(
                intro.id,
                playerSessionManager.sessionState.value.title,
                playerSessionManager.sessionState.value.subtitle,
            )
            progressReporter.startPositionTracking()
        }
    }

    /**
     * Advance to the next pre-roll intro, or — once all intros are exhausted —
     * resume normal playback of the main feature. Idempotent: callers may invoke
     * this on either an end-of-playback callback or an explicit "skip" tap.
     */
    internal fun advanceCinemaIntro() {
        val context = cinemaIntroContext ?: return
        val nextIndex = context.currentIndex + 1
        if (nextIndex < context.intros.size) {
            cinemaIntroContext = context.copy(currentIndex = nextIndex)
            loadCinemaIntro(context.intros[nextIndex])
            return
        }
        // Out of intros — restore the main feature. Clear cinema state first so
        // the recursive initialize call cannot re-enter cinema mode.
        cinemaIntroContext = null
        setCinemaIntroState(null)
        progressReporter.cancelJobs()
        initialize(
            LoadRequest(
                itemId = context.mainItemId,
                mediaSourceId = context.mainMediaSourceId,
                startPositionTicks = context.mainStartPositionTicks,
                allowCinemaMode = false,
                subtitleStreamIndex = context.mainSubtitleStreamIndex,
                audioStreamIndex = context.mainAudioStreamIndex,
            )
        )
    }

    // ── Reporting + position persistence (moved from the VM at B3) ───────────

    /**
     * Single resolved playback-session id for the session-owned reports and
     * persists. The server issues its own id via the `PlaybackInfo` endpoint
     * (stored in [PlayerSessionState.playSessionId]); [playSessionId] is the
     * locally-allocated UUID fallback. Routing every report and the
     * process-death persist through this resolver guarantees a single value
     * is used for the whole session lifecycle (the VM keeps an identical
     * resolver for the pieces it still owns: the reporter's session-id getter
     * and the start-report hook).
     */
    private val currentPlaySessionId: String
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
            val timeSinceSeek = System.currentTimeMillis() - seekTime
            if (timeSinceSeek < 3000L) {
                return seekPos
            }
        }
        return enginePos
    }

    /**
     * Stop-reports the *current* server playback session (skip on incognito,
     * dedup through [stopReportedForSession] so the two paths that can fire
     * for one session — this one and the final teardown in [release] — never
     * double-report). Both write sites (here and in [release]) moved together
     * from the VM at B3.
     */
    fun reportCurrentPlaybackStopped() {
        if (getIncognitoModeEnabled()) return
        val itemId = playerSessionManager.sessionState.value.currentItemId ?: return
        val sessionId = currentPlaySessionId
        if (sessionId == stopReportedForSession) return
        val positionTicks = getReportPositionMs() * 10_000
        if (positionTicks > 0) {
            stopReportedForSession = sessionId
            scope.launch {
                playbackRepository.reportPlaybackStopped(itemId, sessionId, positionTicks)
                // No manual cache invalidation (plan 08): the end-of-item
                // auto-advance path marks the episode played, which evicts
                // inside the repository; a same-item reload re-reads through
                // the provider; detail-screen re-entry force re-resolves.
            }
        }
    }

    /**
     * The persist half of the ViewModel's `seekTo`: records the seek latches
     * (feeding [getReportPositionMs]) and, when an item is loaded, snapshots
     * the seek position into the process-death store immediately (explicit
     * seeks are the most important position to survive process death — no
     * waiting for the throttle) and schedules the coalesced offline-mirror
     * write. The display write and the engine command stay VM-side.
     */
    fun seekPersisted(positionMs: Long) {
        lastSeekPositionMs = positionMs
        lastSeekTimestamp = System.currentTimeMillis()
        val itemId = playerSessionManager.sessionState.value.currentItemId ?: return
        lastPersistedPositionMs = positionMs
        positionStore.persist(itemId, positionMs, currentPlaySessionId, System.currentTimeMillis())
        // The DB mirror is coalesced: rapid scrubbing no longer queues one
        // recordProgress per seek. The store snapshot above is already
        // immediate, and the throttled tick mirror catches up regardless.
        val durationMs = playerSessionManager.engine?.durationMs ?: 0L
        scheduleCoalescedSeekProgress(itemId, positionMs, durationMs)
    }

    /**
     * Persists the current playback position so it survives process death.
     * Throttled to at most one write per [POSITION_PERSIST_MIN_INTERVAL_MS]
     * unless [force] (e.g. an explicit seek). Also stashes the server session
     * id so the post-restore stop-report pairs with the original start-report.
     */
    fun persistPlaybackPosition(positionMs: Long, force: Boolean) {
        if (!force && kotlin.math.abs(positionMs - lastPersistedPositionMs) < POSITION_PERSIST_MIN_INTERVAL_MS) return
        val itemId = playerSessionManager.sessionState.value.currentItemId ?: return
        lastPersistedPositionMs = positionMs
        positionStore.persist(itemId, positionMs, currentPlaySessionId, System.currentTimeMillis())
        // Mirror progress into the offline store so downloads render watched /
        // resume state while offline. No-op for non-downloaded items.
        val durationMs = playerSessionManager.engine?.durationMs ?: 0L
        val positionTicks = positionMs * 10_000L // ms → ticks
        val percentage = if (durationMs > 0L) {
            (positionMs.toDouble() / durationMs.toDouble() * 100.0).coerceIn(0.0, 100.0)
        } else 0.0
        scope.launch {
            offlinePlaybackFacade.recordProgress(itemId, positionTicks, percentage, isPlayed = false)
        }
    }

    /**
     * Coalesces the offline-mirror DB write during seek scrubbing: cancels any
     * in-flight pending write and schedules a fresh one [SEEK_PROGRESS_COALESCE_MS]
     * later, so rapid seeks emit at most one `recordProgress` per quiet window.
     * The position-store snapshot is already written synchronously by
     * [seekPersisted], and the throttled position tick
     * (`persistPlaybackPosition(force=false)`) re-writes the mirror every
     * [POSITION_PERSIST_MIN_INTERVAL_MS], so a dropped coalesced write is
     * recovered within seconds.
     *
     * Keeps launching on the ViewModel-supplied [scope] (NOT [releaseScope]):
     * the teardown path joins this job after cancelling the viewModelScope.
     */
    private fun scheduleCoalescedSeekProgress(itemId: String, positionMs: Long, durationMs: Long) {
        pendingSeekProgressJob?.cancel()
        pendingSeekProgressJob = scope.launch {
            delay(SEEK_PROGRESS_COALESCE_MS)
            val positionTicks = positionMs * 10_000L // ms → ticks
            val percentage = if (durationMs > 0L) {
                (positionMs.toDouble() / durationMs.toDouble() * 100.0).coerceIn(0.0, 100.0)
            } else 0.0
            offlinePlaybackFacade.recordProgress(itemId, positionTicks, percentage, isPlayed = false)
        }
    }

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
            nowMs = System.currentTimeMillis(),
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
     * actually begins. (Moved from the VM at B4.)
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
    private fun restoreOrAllocatePlaySessionId(itemId: String): String {
        val restoredSessionId = positionStore.savedPlaySessionId()
        val savedItemId = positionStore.savedItemId()
        return if (savedItemId == itemId && !restoredSessionId.isNullOrEmpty()) {
            restoredSessionId
        } else {
            java.util.UUID.randomUUID().toString()
        }
    }

    // ── Release (moved from the VM at B3) ────────────────────────────────────

    /**
     * The session-owned half of the old `releaseInternals` body. Runs FIRST
     * on both teardown paths — the per-item re-initialization (see
     * [initialize]) and the full release (see [release]) — immediately
     * followed by the VM-owned half
     * ([SessionLifecycleHooks.releaseInternalsVmPart]) from the same
     * synchronous call chain.
     */
    private fun releaseInternalsSessionPart() {
        loadJob?.cancel()
        loadJob = null
        progressReporter.cancelJobs()
        mediaSessionController.release()
        playerSessionManager.release()
        // New item / released session: drop the seek latch.
        lastSeekPositionMs = null
        lastSeekTimestamp = 0L
    }

    /**
     * Full session teardown — the session-owned half of the old VM
     * `performRelease` body, moved wholesale at B3:
     *
     * 1. snapshot the stop-report inputs BEFORE any teardown statement runs
     *    ([releaseInternalsSessionPart] calls PSM release, which clears the
     *    session state these values read; the VM-side preamble that used to
     *    sit between the old snapshot site and the teardown touches none of
     *    these values);
     * 2. [releaseInternalsSessionPart] + the VM teardown callback
     *    ([vmTeardownAfterInternals] runs the VM's post-internals release
     *    steps: PiP transport reset, cast consumer release, engine-controller
     *    clear) — the order of the old `performRelease` tail is preserved;
     * 3. flush a pending coalesced seek-mirror write (joined on the release
     *    scope so it survives the viewModelScope cancellation on clear());
     * 4. the final Stop report, deduped through [stopReportedForSession].
     *
     * The `released` flag stays with the caller (the VM's `release()` guards
     * on it before calling here); the engine-event coordinator dispose also
     * stays VM-side at exactly its old position in the sequence.
     */
    fun release(vmTeardownAfterInternals: () -> Unit) {
        val itemId = playerSessionManager.sessionState.value.currentItemId
        val sessionId = currentPlaySessionId
        val positionTicks = getReportPositionMs() * 10_000

        releaseInternalsSessionPart()
        hooks.releaseInternalsVmPart()
        vmTeardownAfterInternals()

        // Belt-and-suspenders: flush a pending coalesced seek-mirror write so the
        // offline store doesn't lag the final position on release. The write is
        // moved onto the release scope (IO + NonCancellable) so it survives the
        // viewModelScope being cancelled on clear().
        val pendingSeek = pendingSeekProgressJob
        if (pendingSeek != null && itemId != null) {
            releaseScope.launch(NonCancellable) {
                pendingSeek.join()
            }
        }
        // Skip the second Stop if reportCurrentPlaybackStopped already
        // sent one for this session — duplicate Stop reports confuse the
        // server's resume/progress bookkeeping.
        if (itemId != null && positionTicks > 0 && sessionId != stopReportedForSession) {
            stopReportedForSession = sessionId
            releaseScope.launch(NonCancellable) {
                runCatching {
                    withTimeout(5_000) {
                        playbackRepository.reportPlaybackStopped(
                            itemId = itemId,
                            sessionId = sessionId,
                            positionTicks = positionTicks,
                        )
                    }
                }
                // No manual cache invalidation here (plan 08): the detail
                // screen's re-entry freshness comes from the provider's forced
                // re-resolve (requestRevalidate) and the auto-advance path
                // already evicts via markPlayed inside the repository — the
                // old invalidateUserDataCaches call duplicated both.
            }
        }
    }

    /**
     * Cancels [releaseScope] — the teardown work that must outlive the
     * viewModelScope (final stop-report, pending-seek join). Called by the
     * VM's `onCleared` AFTER its `release()`, preserving the same
     * cancel-after-release ordering the VM used when it owned the scope.
     */
    fun onOwnerCleared() {
        releaseScope.cancel()
    }
}

/**
 * ViewModel-bound slices of [PlaybackSession.initialize], in the exact order
 * the session calls them (see [PlaybackSession.initialize] for the numbered
 * sequence). The session owns the sequence; the VM keeps owning everything
 * that touches its controllers, the ui state and the repositories (the
 * SavedStateHandle is reached only through the [SessionPositionStore] the VM
 * builds). This member set stayed durable across steps B2–B3 —
 * implementations shrank as behaviors moved into the session, the call sites
 * did not change — until B4 collapsed the reclaim pair:
 * `loadReclaimedEngine` died (the body is session-side now), its two
 * VM-owned slices surviving as [onMiniPlayerReclaimed] and
 * [hydrateReclaimedItem] at exactly their old positions.
 */
internal interface SessionLifecycleHooks {
    /**
     * Re-arms the VM-owned PiP transport bridge for a new load: release()
     * nulled it and `init` does not re-run on this Activity-scoped, reused
     * VM. The engine-event coordinator re-arm used to run here too — as of
     * B2 it is session-owned and [PlaybackSession.initialize] performs it
     * directly after this hook.
     */
    fun rearmTransports()

    /**
     * Synchronous-prefix resets for the new item — all direct writes, none
     * pipelined: the autoplay controller reset, the autoplay-cancelled clear,
     * the coordinator's new-item fallback-latch reset, and the pending
     * audio/subtitle stream indices for the track selection helper.
     */
    fun resetForNewItem(subtitleStreamIndex: Int?, audioStreamIndex: Int?)

    /**
     * "Play On" routing early-return: when a Jellyfin remote session is
     * connected, sends the video there instead of playing locally. Returns
     * true when the load was routed away and initialization is complete.
     */
    fun routeToRemotePlaySession(request: LoadRequest): Boolean

    /**
     * Reclaims the mini-player's live engine when it is playing exactly
     * [itemId], so the fullscreen load can bind it instead of reloading.
     * Null when there is nothing to reclaim. The GATE only — since B4 the
     * reclaim BODY (detail fetch, engine bind, media session, tracking
     * restart) is session-side ([PlaybackSession.loadReclaimedEngine]); the
     * gate stays here because the mini-player state it reads is VM-owned
     * lifecycle knowledge.
     */
    fun tryReclaimMiniPlayer(itemId: String): MediaEngine?

    /**
     * Lowers the loading veil when the reclaim routing takes over — playback
     * is continuous, no load screen. The uiState write stays VM-owned; the
     * session's [PlaybackSession.loadReclaimedEngine] invokes this
     * synchronously BEFORE launching the body, at exactly the old position
     * of the veil write (it used to be the first statement of the old
     * `loadReclaimedEngine` hook).
     */
    fun onMiniPlayerReclaimed()

    /**
     * Post-bind hydration for a reclaimed mini-player engine: the segments,
     * adjacent-episodes and series-episodes fetches whose uiState writes and
     * VM collaborators keep them VM-owned. Called by the session's reclaim
     * body at exactly the old position (after the engine bind, media session
     * and tracking restart). Replaced the old `loadReclaimedEngine` hook's
     * tail at B4.
     */
    fun hydrateReclaimedItem(itemId: String, detail: MediaDetail)

    /** Releases the mini-player state when its engine was not reclaimed. */
    fun releaseMiniPlayerState()

    /**
     * The ViewModel-owned part of the per-item teardown (the old
     * `releaseInternals` body's VM half), prefixed by the loading-veil raise.
     * Called back-to-back AFTER the session-owned teardown half — the session
     * cancels the in-flight load, reporter jobs, media session and PSM, and
     * clears the seek latches directly before this hook — from the same
     * synchronous call chain (no dispatch hop). Also invoked by
     * [PlaybackSession.release] on full teardown, where the veil raise is a
     * same-value write (the ui-state rebuild constructs a fresh state whose
     * `isInitializing` default is already true). The play-session id restore
     * that this hook used to return is session-side since B3.
     */
    fun releaseInternalsVmPart()

    /** Clears the trickplay cache for the outgoing item. */
    fun clearTrickplay()

    /** Re-attaches the SyncPlay bridge to the (surviving) group session. */
    fun reattachSyncPlay()

    /**
     * Reads whether playback was in a SyncPlay session before this load
     * (feeding the conditional [reattachSyncPlay] call). A pure flag read
     * since B3 — the outgoing session's stop-report that used to run here is
     * session-owned ([PlaybackSession.initialize] fires
     * [PlaybackSession.reportCurrentPlaybackStopped] directly after this
     * read, at exactly its old position).
     */
    fun wasInSyncPlay(): Boolean
}

/**
 * Event surface a [PlaybackSession] exposes to the ViewModel: the VM stays
 * the single forwarder, mapping each event into its existing sinks (the
 * close-player channel, the uiState error fields, the user-message bus, the
 * pass-out event channel). As of B2 the decision fan-out and the reload
 * paths emit these; later steps extend the emission sites as more behaviors
 * move in.
 */
sealed interface SessionEvent {
    /**
     * A playback error to surface in the player's error dialog. [error] and
     * [retryable] carry the structured engine-error taxonomy's display
     * message and retry verdict; [clearBuffering] is `true` for the start-up
     * watchdog timeout, which must also lift the stuck buffering spinner.
     */
    data class ShowError(
        val error: String,
        val retryable: Boolean,
        val clearBuffering: Boolean = false,
    ) : SessionEvent

    /** A transient informational message for the user. */
    data class InformUser(val message: String) : SessionEvent

    /** Media playback reached its end (autoplay/close policy stays VM-side). */
    data object PlaybackEnded : SessionEvent

    /** The session asks the player screen to close. */
    data object ClosePlayerRequested : SessionEvent

    /** Pass-out protection triggered a pause. */
    data object PassOutPause : SessionEvent
}

/**
 * Narrow persistence seam for the session's resume position: the four
 * SavedStateHandle keys (item id, position, play-session id, persisted-at
 * epoch) behind read accessors, so the session can persist and restore a
 * process-death resume position without touching the handle type.
 *
 * Live since B3: [SavedStateHandlePositionStore] is the production
 * implementation, constructed by the ViewModel (which keeps the handle as a
 * constructor parameter solely to build the store) and injected into
 * [PlaybackSession].
 */
interface SessionPositionStore {
    fun persist(itemId: String, positionMs: Long, playSessionId: String, nowMs: Long)
    fun savedItemId(): String?
    fun savedPositionMs(): Long?
    fun savedPersistedAtMs(): Long?
    fun savedPlaySessionId(): String?
}

/**
 * Production [SessionPositionStore]: a thin wrapper over the ViewModel's
 * [SavedStateHandle]. Key names and the write order (item id, position,
 * play-session id, persisted-at) are byte-for-byte the ones the ViewModel
 * used before the store seam existed, so a process-death restore across an
 * app upgrade keeps resolving.
 */
internal class SavedStateHandlePositionStore(
    private val handle: SavedStateHandle,
) : SessionPositionStore {
    override fun persist(itemId: String, positionMs: Long, playSessionId: String, nowMs: Long) {
        handle[SAVED_KEY_ITEM_ID] = itemId
        handle[SAVED_KEY_POSITION_MS] = positionMs
        handle[SAVED_KEY_PLAY_SESSION_ID] = playSessionId
        handle[SAVED_KEY_POSITION_PERSISTED_AT] = nowMs
    }

    override fun savedItemId(): String? = handle[SAVED_KEY_ITEM_ID]

    override fun savedPositionMs(): Long? = handle[SAVED_KEY_POSITION_MS]

    override fun savedPersistedAtMs(): Long? = handle[SAVED_KEY_POSITION_PERSISTED_AT]

    override fun savedPlaySessionId(): String? = handle[SAVED_KEY_PLAY_SESSION_ID]
}
