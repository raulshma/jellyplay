package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

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
 * sinks. Every ViewModel-bound slice stays behind [SessionLifecycleHooks] or
 * a constructor lambda. Release, seek persistence and stop-reporting still
 * live on the ViewModel until steps B3–B4.
 *
 * Construction contract:
 * - the ViewModel's [CoroutineScope] is INJECTED, never constructed here.
 *   Session-launched coroutines (e.g. the coalesced seek-mirror write tracked
 *   by [pendingSeekProgressJob]) keep launching on that scope — never on
 *   [releaseScope] and never on a session-internal scope cancelled in a
 *   future release(), because the onDispose teardown path joins the pending
 *   seek job and depends on those launch semantics;
 * - [PlayerSessionManager] and [PlaybackProgressReporter] are injected as
 *   already-constructed instances. The reporter keeps being built inside the
 *   ViewModel (its ui-state handle wiring stays VM-side by design) and is
 *   handed over here as an object;
 * - the [SessionLoadPipeline] is CONSTRUCTED in the ViewModel — its outputs
 *   and hooks own every ui-state touch — and injected here as an object: the
 *   session owns when a load starts, never how the pipeline reaches the ui
 *   state;
 * - the same no-ui-state rule applies to the B2 additions: the media-session
 *   controller is injected as an already-constructed instance, and every
 *   value the moved code used to read from (or write into) the ui state is
 *   supplied as a parameter or a getter/setter lambda owned by the VM.
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
    /** Current in-memory streaming quality (the VM's ui-prefs mirror). */
    private val getStreamingQuality: () -> StreamingQuality,
    /** Writes the in-memory playback-mode mirror (ui state stays VM-owned). */
    private val setUiPlaybackMode: (PlaybackMode) -> Unit,
    /**
     * The VM's stop-report for the *current* session (incognito gate, session
     * id resolution, dedup latch). The report body itself moves session-side
     * at B3; the reload/decision paths only need to trigger it.
     */
    private val reportCurrentPlaybackStopped: () -> Unit,
    /** Position resolver for reload anchoring (seek latch + engine pos). Moves session-side at B3. */
    private val getReportPositionMs: () -> Long,
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
                setUiPlaybackMode(PlaybackMode.FORCE_TRANSCODE)
                scope.launch {
                    playbackStore.setPlaybackMode(PlaybackMode.FORCE_TRANSCODE)
                    reportCurrentPlaybackStopped()
                    progressReporter.cancelJobs()
                    playerSessionManager.reloadPlayback(
                        PlaybackMode.FORCE_TRANSCODE,
                        getStreamingQuality(),
                        decision.fromPositionMs,
                    )
                    afterEngineReloadRebuildSessionAndTracking()
                }
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
     * 7. [SessionLifecycleHooks.wasInSyncPlay] (SyncPlay flag read + the
     *    outgoing session's stop-report);
     * 8. cancel any in-flight [loadJob];
     * 9. mini-player reclaim early-return ([SessionLifecycleHooks.tryReclaimMiniPlayer]
     *    + [SessionLifecycleHooks.loadReclaimedEngine]);
     * 10. [SessionLifecycleHooks.releaseMiniPlayerState];
     * 11. [SessionLifecycleHooks.releaseInternalsVmPart] (loading veil raise,
     *     per-item teardown, play-session id restore/allocation);
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
            loadJob = hooks.loadReclaimedEngine(reclaimed, request.itemId)
            return loadJob!!
        }

        hooks.releaseMiniPlayerState()
        // The loading veil raise, per-item state teardown and the
        // process-death play-session restore all run inside this hook; it
        // returns the restored-or-fresh session id for [playSessionId] so the
        // assignment happens at exactly its old position in the sequence.
        playSessionId = hooks.releaseInternalsVmPart(request.itemId)
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
            setUiPlaybackMode(PlaybackMode.FORCE_TRANSCODE)
            scope.launch {
                playbackStore.setPlaybackMode(PlaybackMode.FORCE_TRANSCODE)
                reportCurrentPlaybackStopped()
                progressReporter.cancelJobs()
                playerSessionManager.reloadPlayback(
                    PlaybackMode.FORCE_TRANSCODE, quality,
                    playerSessionManager.engine?.currentPositionMs ?: pos,
                )
                afterEngineReloadRebuildSessionAndTracking()
            }
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
}

/**
 * ViewModel-bound slices of [PlaybackSession.initialize], in the exact order
 * the session calls them (see [PlaybackSession.initialize] for the numbered
 * sequence). The session owns the sequence; the VM keeps owning everything
 * that touches its controllers, the ui state, the repositories and the
 * SavedStateHandle. This member set is deliberately durable across steps
 * B2–B4 — implementations shrink as behaviors move into the session, the
 * call sites do not change.
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
     * Null when there is nothing to reclaim.
     */
    fun tryReclaimMiniPlayer(itemId: String): MediaEngine?

    /**
     * Load coroutine behind the mini-player-reclaim routing early-return:
     * binds the already-playing reclaimed engine to the session manager and
     * rebuilds its session bookkeeping (media session, tracking, segments,
     * episodes). Also lowers the loading veil — playback is continuous, no
     * load screen.
     */
    fun loadReclaimedEngine(engine: MediaEngine, itemId: String): Job

    /** Releases the mini-player state when its engine was not reclaimed. */
    fun releaseMiniPlayerState()

    /**
     * The ViewModel-owned part of the per-item teardown (today's
     * `releaseInternals` body) run during re-initialization, prefixed by the
     * loading-veil raise. Returns the restored-or-fresh play-session id (the
     * SavedStateHandle-backed restore moves behind SessionPositionStore at
     * B3, at which point this return disappears). This hook dies at B3 when
     * the release split lands.
     */
    fun releaseInternalsVmPart(itemId: String): String

    /** Clears the trickplay cache for the outgoing item. */
    fun clearTrickplay()

    /** Re-attaches the SyncPlay bridge to the (surviving) group session. */
    fun reattachSyncPlay()

    /**
     * Reads whether playback was in a SyncPlay session before this load
     * (feeding the conditional [reattachSyncPlay] call), then stop-reports
     * the outgoing session — its old position, directly after the flag read.
     * The stop-report itself moves session-side at B3.
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
 * B1a declares the type only; the SavedStateHandle-backed implementation and
 * its wiring (seekTo's persist half, persistPlaybackPosition, the
 * process-death restore) move behind it at B3.
 */
interface SessionPositionStore {
    fun persist(itemId: String, positionMs: Long, playSessionId: String, nowMs: Long)
    fun savedItemId(): String?
    fun savedPositionMs(): Long?
    fun savedPersistedAtMs(): Long?
    fun savedPlaySessionId(): String?
}
