package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.TrickplayInfo
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayController
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayPreparation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The extracted [SessionHost] implementation (formerly an object literal
 * inlined in [VideoPlayerViewModel]): the ViewModel-bound halves of the
 * session stack — the load pipeline's uiState-shaped outputs
 * ([SessionLoadOutputs], first five members) and the initialize/release
 * lifecycle slices ([SessionLifecycleHooks]) — PLUS the session load's
 * [SessionLoadHooks] bundle ([loadHooks], previously built inline at the
 * [SessionLoadPipeline]'s construction site). The session owns the ORDER
 * (latch resets, routing early-returns, single-flight load tracking, when
 * the pipeline starts); each member runs one VM-owning slice at exactly its
 * old position in the sequence. See each member's KDoc in the interface
 * declarations for what it folds together — the member sets are durable
 * across B2–B4 while these implementations shrink.
 *
 * The command-lambda split: real collaborators arrive as constructor
 * parameters (every member that delegates to one, verbatim); everything
 * that touches the VM's own state — the ui state bag, the high-frequency
 * display flows, the cached aggregate, the media-detail holder, the
 * play-session id resolver — arrives as a NARROW command lambda (one
 * concern each, never a generic state transformer), so this file never
 * names the ui state bag and the god-count ratchet is unmoved. The VM-side
 * members the split keeps VM-owned route through their commands:
 * [routeToRemotePlaySession] (writes the initializing flag + the remote
 * play strategy stay VM-side), [releaseInternalsVmPart] (the documented
 * VM teardown half, keepAcrossItems uiState rebuild included) and the
 * per-item video-effects hydration (the config-rebuild nudge + the style
 * controller are VM collaborators).
 *
 * The wiring-order idiom: [VideoPlayerViewModel] declares this host BEFORE
 * its `playbackSession` (which takes the host as its hooks/outputs) while
 * several command lambdas — the playhead pre-seed, the coordinator
 * new-item latch reset, the cinema sequencing, the play-session id
 * fallback — read `playbackSession` (and `trackSelectionHelper` /
 * `episodeContinuation`) lazily. They run only from the session's call
 * chain, long after those properties initialise — the established
 * explicit-type-annotation / lazy-read-of-a-later-collaborator idiom
 * (progressReporter / mediaDetailProjection shape).
 */
internal class VideoSessionHost(
    /** Launch scope for the fire-and-forget fetches (segments); the VM's own. */
    private val scope: CoroutineScope,
    // ── Real collaborators (pure delegates) ─────────────────────────────────
    private val mediaContentProjector: MediaContentProjector,
    private val pipTransport: PipTransportController,
    private val videoMiniPlayerState: VideoMiniPlayerState,
    private val trickplayManager: TrickplayController,
    private val trickplayPreparation: TrickplayPreparation,
    private val syncPlay: SyncPlayBridge,
    private val syncPlayManager: SyncPlayManager,
    private val playbackSourceResolver: PlaybackSourceResolver,
    private val autoplayController: AutoPlayController,
    private val stillWatching: StillWatchingController,
    private val progressReporter: PlaybackProgressReporter,
    private val mediaSessionController: MediaSessionController,
    private val mediaDetailProjection: MediaDetailProjection,
    private val playbackRepository: PlaybackRepository,
    private val offlinePlaybackFacade: OfflinePlaybackFacade,
    // ── VM-domain command lambdas (one concern each) ────────────────────────
    /** The load pipeline's prefs projection onto the residual ui state. */
    private val applyPrefsProjection: (PrefsProjection) -> Unit,
    /** Raises/lowers the loading veil (onInitializing + the reclaim arm). */
    private val setInitializing: (Boolean) -> Unit,
    /** Pre-seeds the seek-bar denominator (the zero-guard stays VM-side). */
    private val seedDuration: (Long) -> Unit,
    /** Session-owned playhead display pre-seed (the playbackSession call). */
    private val preSeedPlayhead: (Long) -> Unit,
    /** The one-shot "Resumed — Restart" chip emission (the >0 gate stays here). */
    private val seedPlayheadChip: (Long) -> Unit,
    /** Clears the autoplay-cancelled mirror for the new item. */
    private val setAutoplayCancelled: (Boolean) -> Unit,
    /** The coordinator's new-item fallback-latch reset (playbackSession call). */
    private val resetEngineEventCoordinator: () -> Unit,
    /** Seeds the track-selection helper's pending audio/subtitle streams. */
    private val setPendingStreams: (selection: MediaStreamSelection?) -> Unit,
    /** The VM-owned "Play On" routing early-return (ui state + strategy). */
    private val routeRemotePlay: (LoadRequest) -> Boolean,
    /** The VM teardown half of the per-item/full release (stays a VM fun). */
    private val releaseVmInternals: () -> Unit,
    /** Hands the pre-roll intros to the session-owned cinema sequencing. */
    private val beginCinemaMode: (intros: List<MediaItem>, request: LoadRequest) -> Unit,
    /** The remembered-muted uiState mirror write (engine write flows [getEngine]). */
    private val setMutedMirror: (Boolean) -> Unit,
    /** The VM-side per-item hydration (video filters + config nudge + delay). */
    private val applyItemHydration: (itemId: String, hydrated: VideoPlayerAggregate) -> Unit,
    /** The single trickplay uiState write (the prepared manifest, non-null). */
    private val seedTrickplayInfo: (TrickplayInfo) -> Unit,
    /** The incognito gate (the VM's cached aggregate read). */
    private val isIncognito: () -> Boolean,
    /** The resolved play-session id (session state, VM fallback). */
    private val getPlaySessionId: () -> String,
    /** The VM's media-detail holder (the cinema gate's item-type check). */
    private val getMediaDetail: () -> MediaDetail?,
    /** The live engine handle (the remembered-muted engine write). */
    private val getEngine: () -> MediaEngine?,
    /** Replaces the segment overlay's list (the fetch's ui state write). */
    private val setSegments: (List<MediaSegment>) -> Unit,
    /** Adjacent-episodes fetch (the continuation controller, declared later). */
    private val fetchAdjacentEpisodes: (detail: MediaDetail) -> Unit,
    /** Series-episodes fetch (the continuation controller, declared later). */
    private val loadSeriesEpisodes: (detail: MediaDetail) -> Unit,
) : SessionHost {

    // ── SessionLoadOutputs ---------------------------------------------------

    override fun onPrefsProjected(ui: PrefsProjection) {
        applyPrefsProjection(ui)
    }

    override fun onInitializing(visible: Boolean) {
        setInitializing(visible)
    }

    override fun onDurationSeeded(runtimeMs: Long) {
        seedDuration(runtimeMs)
    }

    override fun onPlayheadSeeded(startPositionTicks: Long) {
        // Playhead display pre-seed is session-owned since B4; the write
        // itself flows through the session's seedDisplayedPositionMs seam.
        preSeedPlayhead(startPositionTicks)
        // Surface a one-shot "Resumed — Restart" reminder when opening at a
        // saved position; emitted here (not in the synchronous prologue) so
        // offline-resolved resume positions — invisible in the raw request
        // ticks — raise the chip too.
        if (startPositionTicks > 0) {
            seedPlayheadChip(startPositionTicks / 10_000)
        }
    }

    override fun onStreamUrlResolved(url: String) {
        mediaContentProjector.onStreamUrl(url)
    }

    // ── SessionLifecycleHooks ------------------------------------------------

    override fun rearmTransports() {
        // The engine-event coordinator re-arm that used to run here is
        // session-owned as of B2 — PlaybackSession.initialize performs it
        // directly after this hook.
        pipTransport.registerPipTransport()
    }

    override fun resetForNewItem(selection: MediaStreamSelection) {
        autoplayController.resetForNewItem()
        // Defensive: a prompt can never survive an item switch (its own
        // Continue/Stop arms clear it first; this catches a racing load).
        stillWatching.resetForItem()
        setAutoplayCancelled(false)
        // Coordinator fallback-latch reset — a pure latch flip that ran
        // between the (session-owned) seek-latch and Stop-dedup resets in
        // the old inlined body; bundled here with the other
        // synchronous-prefix writes.
        resetEngineEventCoordinator()
        setPendingStreams(selection)
    }

    override fun routeToRemotePlaySession(request: LoadRequest): Boolean =
        routeRemotePlay(request)

    // Typed reclaim (no downcast): the mini-player holder resolves the
    // player-contract MediaEngine through the capability the depositing
    // video feature registered at deposit time — the asMedia3Player
    // "typed capability, not a cast" rule.
    override fun tryReclaimMiniPlayer(itemId: String): MediaEngine? =
        videoMiniPlayerState.tryReclaimMediaEngine(itemId)

    override fun onMiniPlayerReclaimed() {
        // Reclaim promotes an already-playing mini-player engine to
        // fullscreen — playback is continuous, so no load screen. The
        // reclaim BODY is session-side since B4; this is the veil write,
        // at exactly its old position (before the body launch).
        setInitializing(false)
    }

    override fun hydrateReclaimedItem(itemId: String, detail: MediaDetail) {
        // Old loadReclaimedEngine-hook tail: the uiState-writing
        // hydration fetches, in their old order.
        fetchMediaSegments(itemId)
        fetchAdjacentEpisodes(detail)
        loadSeriesEpisodes(detail)
    }

    override fun releaseMiniPlayerState() {
        videoMiniPlayerState.release()
    }

    override fun releaseInternalsVmPart() {
        releaseVmInternals()
    }

    override fun clearTrickplay() {
        trickplayManager.clear()
    }

    override fun reattachSyncPlay() {
        syncPlay.reattachSession()
    }

    override fun wasInSyncPlay(): Boolean {
        // Pure flag read since B3 — the outgoing session's stop-report
        // moved session-side and fires directly after this read inside
        // PlaybackSession.initialize, at exactly its old position.
        return syncPlayManager.isInSyncPlaySession
    }

    // ── Per-item hydration fetches (shared by the hooks + the reclaim) ──────

    /**
     * The segments fetch behind the load spine's `fetchMediaSegments` hook
     * and [hydrateReclaimedItem]: offline-first — prefer segments bundled
     * with the download so skip controls (intro/outro/recap) work without a
     * server round-trip.
     */
    private fun fetchMediaSegments(itemId: String) {
        scope.launch {
            val local = offlinePlaybackFacade.loadSegments(itemId)
            if (local != null) {
                setSegments(local)
                return@launch
            }
            val segments = playbackRepository.getMediaSegments(itemId).getOrDefault(emptyList())
            setSegments(segments)
        }
    }

    // ── The session load's hook bodies (VM-owning slices, old bodies verbatim)

    /**
     * Cinema Mode is only attempted on fresh starts (never on resume /
     * next-episode auto-advance / SyncPlay / external player / mini-mode
     * reclaim). Server-side intros are best-effort: any failure returns an
     * empty list and falls back to normal playback.
     */
    private fun shouldAttemptCinemaMode(
        agg: VideoPlayerAggregate,
        itemId: String,
        startPositionTicks: Long,
    ): Boolean {
        if (!agg.videoPlayer.cinemaModeEnabled) return false
        if (startPositionTicks != 0L) return false
        if (agg.playback.preferredPlayer == PlayerType.EXTERNAL) return false
        if (syncPlayManager.isInSyncPlaySession) return false
        // Skip for non-video items — intros are only meaningful for movies/episodes.
        val existingDetail = getMediaDetail()
        if (existingDetail != null && existingDetail.item.id == itemId) {
            val type = existingDetail.item.mediaType
            if (type != MediaType.MOVIE &&
                type != MediaType.EPISODE &&
                type != MediaType.UNKNOWN
            ) {
                return false
            }
        }
        return true
    }

    /**
     * Reapplies the remembered-muted preference: the uiState mirror first,
     * then the engine — order preserved from the former inline hook body.
     */
    private fun restoreRememberedMuted(agg: VideoPlayerAggregate) {
        if (agg.videoPlayer.videoRememberMuted && agg.videoPlayer.videoMuted) {
            setMutedMirror(true)
            getEngine()?.setMuted(true)
        }
    }

    /**
     * The server start report, incognito-gated: incognito never reaches the
     * server (the same invariant the session-owned stop reports enforce).
     * The play-session id resolves through [getPlaySessionId] — the same
     * single-value resolver the session's reports and persists use.
     */
    private suspend fun reportPlaybackStart(itemId: String, source: MediaSource?, playMethod: PlayMethod) {
        if (isIncognito()) return
        playbackRepository.reportPlaybackStart(
            PlaybackStartInfo(
                itemId = itemId,
                sessionId = getPlaySessionId(),
                mediaSourceId = source?.id,
                playMethod = playMethod,
            )
        )
    }

    /**
     * The session load's hook bundle, built once beside the members it
     * forwards to. Pure one-line delegates are wired directly to their
     * collaborator; the VM-domain halves route through the command lambdas
     * above. The order the pipeline CALLS these in lives in
     * [SessionLoadPipeline] (pinned by SessionLoadPipelineTest) — this
     * bundle owns only the bodies.
     */
    val loadHooks: SessionLoadHooks = SessionLoadHooks(
        reconcileSyncPlayQueue = { itemId, mediaSourceId, startPositionTicks ->
            syncPlay.reconcileQueueForItem(itemId, mediaSourceId, startPositionTicks)
        },
        shouldAttemptCinemaMode = { agg, itemId, startPositionTicks ->
            shouldAttemptCinemaMode(agg, itemId, startPositionTicks)
        },
        // Cinema sequencing is session-owned since B4; this hook is now a
        // thin delegate (the context latch + the intro loads live there).
        beginCinemaMode = { intros, request -> beginCinemaMode(intros, request) },
        resolveOfflineResumeTicks = { itemId, startPositionTicks ->
            playbackSourceResolver.resolveStartPositionTicks(itemId, startPositionTicks)
        },
        onSessionPrefsApplied = { agg ->
            autoplayController.setEnabled(agg.videoPlayer.videoAutoplayNext)
            autoplayController.setStillWatchingThreshold(agg.videoPlayer.stillWatchingEpisodeThreshold)
        },
        restoreRememberedMuted = { agg -> restoreRememberedMuted(agg) },
        onItemHydrated = { itemId, hydratedAgg -> applyItemHydration(itemId, hydratedAgg) },
        createMediaSession = { itemId, title, subtitle ->
            mediaSessionController.createForItem(itemId, title, subtitle)
        },
        applyMediaDetail = { detail -> mediaDetailProjection.applyDetail(detail) },
        initializeTrickplay = { itemId, source ->
            // Trickplay selection + dispatch live in [TrickplayPreparation];
            // a non-null result means exactly one arm initialized the
            // controller, so this is the single uiState write the former
            // three inline arms produced between them.
            trickplayPreparation.prepare(itemId, source)?.let { info -> seedTrickplayInfo(info) }
        },
        reportPlaybackStart = { itemId, source, playMethod ->
            reportPlaybackStart(itemId, source, playMethod)
        },
        startPositionTracking = { progressReporter.startPositionTracking() },
        startProgressReporting = { progressReporter.startProgressReporting() },
        fetchMediaSegments = { itemId -> fetchMediaSegments(itemId) },
        fetchAdjacentEpisodes = { detail -> fetchAdjacentEpisodes(detail) },
        loadSeriesEpisodes = { detail -> loadSeriesEpisodes(detail) },
        // No terminal-outcome action today; stated explicitly here so a
        // future consumer is a construction-site change, not a hidden
        // default somewhere else.
        onOutcome = { },
    )
}
