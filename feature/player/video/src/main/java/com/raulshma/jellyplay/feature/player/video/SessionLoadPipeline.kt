package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The arguments of one session load. Mirrors [VideoPlayerViewModel.initializeInternal]'s
 * post-routing parameters — everything the ordered load stages need after the
 * synchronous prefix (latch resets, transport re-arm, routing early-returns)
 * has run in the ViewModel.
 */
class LoadRequest(
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
 * UiState-shaped load outputs. Implemented by the ViewModel — uiState
 * ownership stays there. Each method is called at a
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
 * that must not move into a load-ordering module.
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
    /** Cinema Mode gate predicate (fresh starts, video items, not SyncPlay…). */
    val shouldAttemptCinemaMode: (
        agg: VideoPlayerAggregate,
        itemId: String,
        startPositionTicks: Long,
    ) -> Boolean,
    /** Takes over playback with the first pre-roll intro. */
    val beginCinemaMode: (intros: List<MediaItem>, request: LoadRequest) -> Unit,
    /** Offline-start resolution (completed-download resume ticks). */
    val resolveOfflineResumeTicks: suspend (itemId: String, startPositionTicks: Long) -> Long,
    /** Applies aggregate prefs to session-owned controllers (autoplay…). */
    val onSessionPrefsApplied: (agg: VideoPlayerAggregate) -> Unit,
    /** Reapplies the remembered-muted preference to uiState + engine. */
    val restoreRememberedMuted: (agg: VideoPlayerAggregate) -> Unit,
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
    val fetchMediaSegments: (itemId: String) -> Unit,
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
class SessionLoadPipeline(
    private val sessionManager: PlayerSessionManager,
    private val mediaRepository: MediaRepository,
    private val aggregateStore: VideoPlayerAggregateStore,
    private val networkOfflineStore: NetworkOfflineStore,
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
            runStages(request)
        } finally {
            // Guarantee the loading screen lifts even if the load throws or
            // takes the cinema-intro early return — otherwise the player is
            // stranded behind a permanent black overlay. A no-op on the happy
            // path (the mid-spine lift already cleared it before trickplay).
            outputs.onInitializing(false)
        }
    }

    private suspend fun runStages(request: LoadRequest) {
        hooks.reconcileSyncPlayQueue(request.itemId, request.mediaSourceId, request.startPositionTicks)

        val agg = aggregateStore.aggregate.value

        val defaultAspectRatio = when (agg.videoPlayer.videoDefaultAspectRatio) {
            "FIT" -> AspectRatio.FIT
            "FILL" -> AspectRatio.FILL
            "CROP" -> AspectRatio.CROP
            "16:9" -> AspectRatio.RATIO_16_9
            "4:3" -> AspectRatio.RATIO_4_3
            "21:9" -> AspectRatio.RATIO_21_9
            else -> AspectRatio.AUTO
        }

        outputs.onPrefsProjected {
            copy(
                preferredPlayerType = agg.playback.preferredPlayer,
                uiPrefs = uiPrefs.copy(
                    defaultOrientation = agg.videoPlayer.videoDefaultOrientation,
                    controlsTimeoutMs = agg.videoPlayer.videoControlsTimeoutMs,
                    passOutProtectionHours = agg.videoPlayer.videoPassOutProtectionHours,
                    trickplayEnabled = agg.videoPlayer.trickplayEnabled,
                    trickplayOnSeekGesture = agg.videoPlayer.trickplayOnSeekGesture,
                    showPlaybackMetadata = agg.videoPlayer.videoShowPlaybackMetadata,
                    showClock = agg.videoPlayer.showClockInPlayer,
                    showTimeRemaining = agg.videoPlayer.showTimeRemaining,
                    keepScreenOnDuringVideo = agg.playback.keepScreenOnDuringVideo,
                    streamingQuality = agg.playback.streamingQuality,
                    adaptiveBitrateEnabled = networkOfflineStore.networkOffline.value.adaptiveBitrateEnabled,
                    playbackMode = agg.playback.playbackMode,
                ),
                gestures = gestures.copy(
                    gesturesEnabled = agg.videoPlayer.videoGesturesEnabled,
                    holdSpeedEnabled = agg.videoPlayer.videoHoldSpeedEnabled,
                    holdSpeedMultiplier = agg.videoPlayer.videoHoldSpeedMultiplier,
                    defaultSpeed = agg.videoPlayer.videoDefaultSpeed,
                    swipeSeekMaxMs = agg.videoPlayer.videoSwipeSeekMaxMs,
                    seekDurationMs = agg.videoPlayer.videoSeekDurationMs,
                    rememberBrightness = agg.videoPlayer.videoRememberBrightness,
                    brightnessLevel = agg.videoPlayer.videoBrightnessLevel,
                    gestureIndicatorSide = agg.videoPlayer.videoGestureIndicatorSide,
                    frameRateMatching = agg.playback.frameRateMatching,
                    refreshRateMode = agg.playback.refreshRateMode,
                ),
                videoFx = videoFx.copy(
                    aspectRatio = defaultAspectRatio,
                    tvZoomModePercent = agg.videoPlayer.tvZoomModePercent,
                ),
                segmentState = segmentState.copy(
                    segmentBehaviors = run {
                        val base = agg.videoPlayer.segmentBehaviors.toMutableMap()
                        if (agg.videoPlayer.videoAutoSkipIntro) {
                            base[com.raulshma.jellyplay.core.model.MediaSegmentType.INTRO] =
                                com.raulshma.jellyplay.core.model.SegmentBehavior.AUTO_SKIP
                        }
                        if (agg.videoPlayer.videoAutoSkipOutro) {
                            base[com.raulshma.jellyplay.core.model.MediaSegmentType.OUTRO] =
                                com.raulshma.jellyplay.core.model.SegmentBehavior.AUTO_SKIP
                        }
                        base.toMap()
                    },
                ),
                episodes = episodes.copy(
                    videoEpisodeBrowserEnabled = agg.videoPlayer.videoEpisodeBrowserEnabled,
                ),
                autoplay = autoplay.copy(
                    videoAutoplayNext = agg.videoPlayer.videoAutoplayNext,
                    autoPlayCountdownSec = agg.playback.autoPlayCountdownSec,
                ),
            )
        }
        hooks.onSessionPrefsApplied(agg)

        // Volume is driven by the device media stream, which Android itself
        // persists across sessions — no app-level restore needed. Mute is
        // still reapplied here when "remember muted" is on.
        hooks.restoreRememberedMuted(agg)

        if (request.allowCinemaMode &&
            hooks.shouldAttemptCinemaMode(agg, request.itemId, request.startPositionTicks)
        ) {
            val intros = mediaRepository.getIntros(request.itemId).getOrDefault(emptyList())
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
        hooks.fetchMediaSegments(request.itemId)
        if (detail != null) {
            coroutineScope {
                launch { hooks.fetchAdjacentEpisodes(detail) }
                launch { hooks.loadSeriesEpisodes(detail) }
            }
        }
        hooks.onOutcome(LoadOutcome.Completed)
    }
}
