package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlayerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The arguments of one session load. Mirrors [PlaybackSession.initialize]'s
 * post-routing parameters — everything the ordered load stages need after the
 * synchronous prefix (latch resets, transport re-arm, routing early-returns)
 * has run.
 */
data class LoadRequest(
    val itemId: String,
    val mediaSourceId: String?,
    val startPositionTicks: Long,
    val allowCinemaMode: Boolean,
    val subtitleStreamIndex: Int?,
    val audioStreamIndex: Int?,
)

/** Terminal state of one session load. */
sealed interface LoadOutcome {
    /** The full ordered spine ran to completion. */
    data object Completed : LoadOutcome

    /** Cinema Mode pre-roll took over; the main feature loads via the hook. */
    data class CinemaIntro(val introItemId: String) : LoadOutcome
}

/**
 * The ui-state → ui-state transform [SessionLoadOutputs.onPrefsProjected]
 * carries. Internal alias so implementers of the outputs seam (formerly the
 * deleted [VideoSessionHost]; then the deleted `PlayerWiring` builder; today
 * [PlaybackSession], the composition root the builder collapsed into
 * that implements it) can take the transform through the command-lambda
 * split: ControllerOwnershipTest's migrated-controller ratchet forbids the
 * literal type name in the migrated controllers (KDoc prose is exempt; the
 * builder — the VM's construction surface — is not ratchet-listed, so its
 * override spells the alias for continuity). The alias is transparent —
 * overrides may spell either form. This is the ratchet's ONE declared
 * state-transformer exception (see that suite's KDoc): the projection is
 * the pipeline's load-stage vocabulary — the seed the implementer forwards
 * for the VM to apply — not state the implementer reads or owns.
 */
internal typealias PrefsProjection = VideoPlayerUiState.() -> VideoPlayerUiState

/**
 * UiState-shaped load outputs. Implemented by [PlaybackSession] (the
 * composition root that owns the collaborator graph — the ViewModel's
 * former object-literal, then the deleted VideoSessionHost, then the deleted
 * `PlayerWiring` builder) — uiState ownership stays with the ViewModel. Each method is called at a
 * defined point of the [SessionLoadPipeline] spine; the interface exists so
 * the *order* of the stages is testable against a fake.
 */
interface SessionLoadOutputs {
    /** Applies a prefs-derived transform to the residual uiState. */
    fun onPrefsProjected(ui: VideoPlayerUiState.() -> VideoPlayerUiState)

    /** Raises/lowers the loading screen. */
    fun onInitializing(visible: Boolean)

    /** Pre-seeds the seek-bar denominator from the server-reported runtime. */
    fun onDurationSeeded(runtimeMs: Long)

    /**
     * Seeds the seek-bar playhead from the RESOLVED start ticks (explicit
     * request ticks, or the offline-mirror resume position they resolve to).
     * Display-only — the engine is started at the same ticks by `loadMedia`.
     */
    fun onPlayheadSeeded(startPositionTicks: Long)

    /** Surfaces the resolved stream URL. */
    fun onStreamUrlResolved(url: String)
}

/**
 * Injected ViewModel operations the pipeline calls at defined points of its
 * spine. The pipeline owns *the order stages run in*; these hooks own the
 * VM-bound bodies (controllers, session bookkeeping, reporting choreography)
 * that must not move into a load-ordering module. (Since the
 * [VideoSessionHost] deletion, the three stage bodies with real logic are
 * pipeline members below — [fetchMediaSegments], [shouldAttemptCinemaMode]
 * and [restoreRememberedMuted] — instead of hooks; the server start report
 * lives on [PlaybackSession] beside its stop-report twin, and its hook is a
 * one-line delegate.)
 *
 * Every hook is a required constructor parameter — there are no silent no-op
 * defaults; a caller that ignores a stage says so at the construction site.
 */
class SessionLoadHooks(
    /** SyncPlay queue reconciliation before any prefs/load work. */
    val reconcileSyncPlayQueue: suspend (
        itemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
    ) -> Unit,
    /** Takes over playback with the first pre-roll intro. */
    val beginCinemaMode: (intros: List<MediaItem>, request: LoadRequest) -> Unit,
    /** Offline-start resolution (completed-download resume ticks). */
    val resolveOfflineResumeTicks: suspend (itemId: String, startPositionTicks: Long) -> Long,
    /** Applies aggregate prefs to session-owned controllers (autoplay…). */
    val onSessionPrefsApplied: (agg: VideoPlayerAggregate) -> Unit,
    /** Per-item hydration after loadMedia (video filters, subtitle delay). */
    val onItemHydrated: (itemId: String, hydrated: VideoPlayerAggregate) -> Unit,
    /** Media-session factory. */
    val createMediaSession: (itemId: String, title: String, subtitle: String) -> Unit,
    /** Applies the resolved detail to uiState (title, chapters, artwork…). */
    val applyMediaDetail: (detail: MediaDetail) -> Unit,
    /** Trickplay three-way selection (offline cache / local bundled / server). */
    val initializeTrickplay: suspend (itemId: String, source: MediaSource?) -> Unit,
    /** Server start report (incognito-gated, session-id aware). */
    val reportPlaybackStart: suspend (
        itemId: String,
        mediaSource: MediaSource?,
        playMethod: PlayMethod,
    ) -> Unit,
    val startPositionTracking: () -> Unit,
    val startProgressReporting: () -> Unit,
    val fetchAdjacentEpisodes: (detail: MediaDetail) -> Unit,
    val loadSeriesEpisodes: (detail: MediaDetail) -> Unit,
    /** Terminal outcome observer. */
    val onOutcome: (outcome: LoadOutcome) -> Unit,
)

/**
 * Owns the ordered load choreography that used to be inlined in
 * [VideoPlayerViewModel.initializeInternal].
 * The previously-unwritten ordering constraints become this class's spine:
 *
 *  1. SyncPlay queue reconciliation (before anything session-shaped runs)
 *  2. prefs projection + session-pref application (aspect ratio, modes…)
 *  3. remembered-muted restore
 *  4. cinema gate — early return with [LoadOutcome.CinemaIntro]
 *  5. offline-start resolution → playhead seed (resolved ticks) →
 *     `sessionManager.loadMedia`
 *  6. per-item hydration from the *hydrated* aggregate (fresh
 *     `aggregateRaw.first()`, not the cold-start snapshot)
 *  7. stream URL → media session → duration seed → detail apply
 *  8. loading-screen lift (seek bar's first paint is the resume fraction)
 *  9. trickplay selection
 * 10. start report → position/progress tracking → segments → episodes
 * 11. `finally`: loading screen always lifts, even on failure or the cinema
 *     early return
 *
 * A parameterized pipeline **that the ViewModel calls and that calls
 * [PlayerSessionManager]** — not one PSM calls. PSM owns *what a load means*
 * (source resolution, engine creation, request building); this pipeline owns
 * *the order stages run in*. Cancellation semantics are unchanged: `start`
 * returns the launched [Job] which the VM assigns to its `loadJob` and
 * cancels before the next load's `releaseInternals()`.
 */
internal class SessionLoadPipeline(
    private val sessionManager: PlayerSessionManager,
    /**
     * The item-attached extras seam — the pipeline's one repository read is the
     * Cinema Mode intros lookup, which left the wide union for
     * [LibraryApiClient] (uncached forward, no cache state).
     */
    private val libraryApiClient: LibraryApiClient,
    private val aggregateStore: VideoPlayerAggregateStore,
    private val networkOfflineStore: NetworkOfflineStore,
    /**
     * The offline facade behind [fetchMediaSegments]' offline-first
     * precedence (the segments bundled with a download win over a server
     * round-trip so skip controls work without a connection). Moved here from
     * the deleted [VideoSessionHost]: the pipeline owns WHEN the segments
     * stage runs, and the mini-player reclaim's hydration
     * ([PlaybackSession.loadReclaimedEngine] → `hydrateReclaimedItem`) shares
     * this one fetch through it.
     */
    private val offlinePlaybackFacade: OfflinePlaybackFacade,
    /** The SyncPlay session flag — a [shouldAttemptCinemaMode] veto. */
    private val syncPlayManager: SyncPlayManager,
    /**
     * The VM's media-detail holder — [shouldAttemptCinemaMode]'s item-type
     * guard reads it (intros are only meaningful for movies/episodes).
     */
    private val getMediaDetail: () -> MediaDetail?,
    /** The server segments read behind [fetchMediaSegments]' fallback arm. */
    private val playbackRepository: com.raulshma.jellyplay.core.data.repository.PlaybackRepository,
    /** The remembered-muted uiState mirror write ([restoreRememberedMuted]). */
    private val setMutedMirror: (Boolean) -> Unit,
    /** The segments fetch's uiState write (the overlay's segment list). */
    private val onSegmentsFetched: (List<MediaSegment>) -> Unit,
    private val outputs: SessionLoadOutputs,
    private val hooks: SessionLoadHooks,
) {

    /**
     * Launches the ordered load spine in [scope]; the returned job is the
     * caller's `loadJob`. Cancel semantics unchanged from the inlined
     * coroutine this replaces.
     */
    fun start(scope: CoroutineScope, request: LoadRequest): Job = scope.launch {
        try {
            runStages(scope, request)
        } finally {
            // Guarantee the loading screen lifts even if the load throws or
            // takes the cinema-intro early return — otherwise the player is
            // stranded behind a permanent black overlay. A no-op on the happy
            // path (the mid-spine lift already cleared it before trickplay).
            outputs.onInitializing(false)
        }
    }

    private suspend fun runStages(scope: CoroutineScope, request: LoadRequest) {
        hooks.reconcileSyncPlayQueue(request.itemId, request.mediaSourceId, request.startPositionTicks)

        val agg = aggregateStore.aggregate.value

        // Prefs → uiState seed. The field-by-field mapping (which pref feeds
        // which leaf) lives in [PlayerPrefsSeed]; this spine only owns WHEN the
        // seed runs — before session-pref application, remembered-muted restore
        // and the cinema gate.
        outputs.onPrefsProjected(
            PlayerPrefsSeed.seededProjection(
                agg = agg,
                adaptiveBitrateEnabled = networkOfflineStore.networkOffline.value.adaptiveBitrateEnabled,
            ),
        )
        hooks.onSessionPrefsApplied(agg)

        // Volume is driven by the device media stream, which Android itself
        // persists across sessions — no app-level restore needed. Mute is
        // still reapplied here when "remember muted" is on.
        restoreRememberedMuted(agg)

        if (request.allowCinemaMode &&
            shouldAttemptCinemaMode(agg, request.itemId, request.startPositionTicks)
        ) {
            val intros = libraryApiClient.getIntros(request.itemId).getOrDefault(emptyList())
            if (intros.isNotEmpty()) {
                hooks.beginCinemaMode(intros, request)
                hooks.onOutcome(LoadOutcome.CinemaIntro(intros.first().id))
                return
            }
        }

        val resolvedStartTicks = hooks.resolveOfflineResumeTicks(request.itemId, request.startPositionTicks)

        // Seed the playhead from the resolved ticks BEFORE the engine starts:
        // zero-tick entries (Downloads, episode browser) resolve their resume
        // position here, so seeding from the raw request ticks would paint the
        // bar at 0 and jump to resume on the engine's first tick.
        outputs.onPlayheadSeeded(resolvedStartTicks)

        sessionManager.loadMedia(request.itemId, request.mediaSourceId, resolvedStartTicks)

        // loadMedia reports its own failures (offline gate, detail-fetch miss,
        // vanished offline file) by leaving isReady = false. Continuing would
        // create a media session and report playback START for an item that
        // never started, so stop here; the finally lifts the loading veil.
        if (!sessionManager.sessionState.value.isReady) return

        val sessionState = sessionManager.sessionState.value
        val source = sessionState.currentMediaSource
        val detail = sessionState.mediaDetail

        // Re-snapshot the aggregate now that loadMedia has returned. loadMedia
        // awaits aggregateRaw.first() internally, so by here the DataStore has
        // emitted the hydrated preferences — but the `agg` captured above may
        // still be the cold-start empty default. Per-item maps read from the
        // stale `agg` (subtitle delay, video effects) resolve to 0/empty and
        // then clobber the real values the engine already booted with. Use a
        // fresh hydrated snapshot for per-item lookups; global UI seeding above
        // is reconciled by downstream collectors, so it keeps `agg`.
        val hydratedAgg = aggregateStore.aggregateRaw.first()

        hooks.onItemHydrated(request.itemId, hydratedAgg)

        sessionState.streamUrl?.let { outputs.onStreamUrlResolved(it) }

        hooks.createMediaSession(request.itemId, sessionState.title, sessionState.subtitle)

        if (detail != null) {
            // Pre-seed the seek-bar denominator from the server-reported
            // runtime so the playhead fraction is correct from open. The guard
            // (never clobber an engine-resolved duration) lives in the output
            // implementation, which owns the duration flow.
            val runtimeMs = (detail.item.runTimeTicks ?: 0L) / 10_000
            if (runtimeMs > 0L) {
                outputs.onDurationSeeded(runtimeMs)
            }
            hooks.applyMediaDetail(detail)
        }

        // Position & duration are now seeded; lift the loading screen so the
        // seek bar's first paint is the correct resume fraction (no 0-flicker).
        outputs.onInitializing(false)

        hooks.initializeTrickplay(request.itemId, source)

        hooks.reportPlaybackStart(request.itemId, source, sessionState.playMethod)

        hooks.startPositionTracking()
        hooks.startProgressReporting()
        fetchMediaSegments(scope, request.itemId)
        if (detail != null) {
            coroutineScope {
                launch { hooks.fetchAdjacentEpisodes(detail) }
                launch { hooks.loadSeriesEpisodes(detail) }
            }
        }
        hooks.onOutcome(LoadOutcome.Completed)
    }

    // ── The three stage bodies with real logic ──────────────────────────────
    //
    // These lived on the deleted [VideoSessionHost] as load-hook bodies; they
    // moved here because this spine owns WHEN each of them runs (and the
    // mini-player reclaim's hydration reuses [fetchMediaSegments] through the
    // session). Their uiState writes arrive as the [setMutedMirror] /
    // [onSegmentsFetched] commands; the detail holder read arrives as
    // [getMediaDetail] — the command-lambda split, unchanged.

    /**
     * The segments fetch behind the load spine's stage 10 and the reclaim's
     * hydration: offline-first — prefer segments bundled with the download so
     * skip controls (intro/outro/recap) work without a server round-trip.
     * Fire-and-forget on the caller's scope (the VM scope — the fetch must
     * outlive this load coroutine's siblings only as long as the VM lives,
     * exactly as the former host's launch did).
     */
    internal fun fetchMediaSegments(scope: CoroutineScope, itemId: String) {
        scope.launch {
            val local = offlinePlaybackFacade.loadSegments(itemId)
            if (local != null) {
                onSegmentsFetched(local)
                return@launch
            }
            val segments = playbackRepository.getMediaSegments(itemId).getOrDefault(emptyList())
            onSegmentsFetched(segments)
        }
    }

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
            sessionManager.engine?.setMuted(true)
        }
    }
}
