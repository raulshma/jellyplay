package com.raulshma.jellyplay.feature.player.video

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.playback.SleepCountdown
import com.raulshma.jellyplay.core.data.playback.VideoQueueController
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.util.ArtworkUrls
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.core.model.mediaRuleContentType
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.player.video.session.createSessionSlices
import com.raulshma.jellyplay.feature.player.video.session.PlayerSessionSlices

import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.raulshma.jellyplay.feature.player.video.chrome.mirrorPlaying
import com.raulshma.jellyplay.feature.player.video.state.ReadySubtitleHint
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayPreparation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long a next-episode load may hold its single-flight latch while waiting
 * for the session to settle onto the new item before the button re-arms. Error
 * paths release earlier via [SessionEvent.ShowError]; this backstop covers
 * paths that change nothing (e.g. remote-play routing). Sized for the slowest
 * network preset's timeout chain (VERY_RELAXED 60 s + failover retries).
 */

/**
 * Builds the [SegmentOverlayState] for a given live position against a
 * pre-built [SegmentCalculatorInput].
 *
 * Calls [SegmentCalculator] directly with the projected inputs — no
 * throwaway [VideoPlayerUiState] allocation. The input is rebuilt only
 * when the projection/duration changes; the position-dependent evaluation
 * runs per tick against the cached input. The active segment is scanned
 * exactly once per tick and threaded through the precomputed-segment
 * overloads, so the intro/credits/up-next verdicts do not each re-run the
 * scan.
 */
private fun computeOverlay(positionMs: Long, input: SegmentCalculatorInput): SegmentOverlayState {
    val activeSegment = SegmentCalculator.computeActiveSegment(input, positionMs)
    return SegmentOverlayState(
        activeSegment = activeSegment,
        activeSegmentBehavior = activeSegment?.let {
            SegmentCalculator.behaviorForType(input, it.type)
        } ?: SegmentBehavior.IGNORE,
        isInIntro = SegmentCalculator.isInSegmentType(input, activeSegment, MediaSegmentType.INTRO),
        isInCredits = SegmentCalculator.isInSegmentType(input, activeSegment, MediaSegmentType.OUTRO),
        shouldShowUpNext = SegmentCalculator.shouldShowUpNext(input, positionMs, activeSegment),
    )
}

class VideoPlayerViewModel(
    /**
     * Aggregate platform seam: replaces the former
     * `android.content.Context` slot — carries the low-RAM gate, subtitle
     * content-URI IO, the offline-media probe and the factory methods for the
     * androidMain trickplay/cast-controller/becoming-noisy/focus collaborators.
     */
    private val platform: VideoPlayerPlatform,
    /**
     * The thirteen datastore stores bundled at construction — see [PlayerStores]
     * (home's HomeStores move: a new store dependency widens the bundle and the
     * DI definitions, not this constructor).
     */
    private val stores: PlayerStores,
    /**
     * The construction bundles (the [PlayerStores] pattern): each groups the
     * pass-throughs that feed ONE internally-built session module cluster (or,
     * for [PlayerSessionSources], the session's direct engine-room
     * collaborators). A new session collaborator widens its bundle and the DI
     * definitions, not this constructor — the bundles are forwarded to
     * [PlaybackSession] verbatim below.
     */
    private val subtitleSources: PlayerSubtitleSources,
    private val offlineSources: PlayerOfflineSources,
    private val sessionStack: PlayerSessionStackSources,
    private val itemContent: PlayerItemContentSources,
    private val sessionSources: PlayerSessionSources,
    private val imageUrlProvider: ImageUrlProvider,
    /**
     * The tagged-artwork resolver (the item-based seam over [imageUrlProvider]):
     * backs the item-based accessors the player surfaces use (episode rows,
     * companion-dashboard tabs, the Up Next card) so every URL carries the
     * item's image tag — the etag cache-buster that makes re-uploaded art
     * visible without manual cache clears.
     */
    private val artworkUrls: ArtworkUrls,
    // Bare ctor parameter; DI passes it by name and no public val exposes it.
    castManager: CastManager,
    private val syncPlayManager: SyncPlayManager,
    private val activePlayerController: ActivePlayerController,
    val pipController: PipController,
    // Public for the screen: the zoom-safe Compose overlay consumes the same
    // singleton (LRU typeface cache + startup prewarm) instead of building a
    // private FontProvider per composition (androidMain narrows it to
    // AndroidFontProvider). Same singleton the subtitle-sources bundle carries
    // to the session's style controller.
    val fontProvider: FontProvider,
    private val userMessageBus: PlayerVideoMessageBus,
    private val savedStateHandle: SavedStateHandle,
    /**
     * The process-wide manual up-next queue (the details screen's "play
     * next/last" feeds it). Defaulted null for test graphs — the accessor
     * below falls back to a private in-memory instance so the player's queue
     * semantics work identically without DI.
     */
    videoQueue: VideoQueueController? = null,
    /**
     * "Play (with pre-rolls)": the process-wide one-shot Cinema Mode
     * override the detail screen arms; forwarded to the session, whose
     * user-open funnel consumes it into the load request. Nullable like
     * [videoQueue] so test graphs that don't wire it keep compiling.
     */
    cinemaModeOverride: com.raulshma.jellyplay.core.data.playback.CinemaModeOverride? = null,
    /** The next-item stream-cache pre-warm seam; null disables it (test graphs). */
    private val nextStreamByteWarmer: NextStreamByteWarmer? = null,
    /**
     * The #14 delete seams (can-delete capability + server delete + cache
     * invalidation), bundled for one slot; null in test graphs — the
     * post-playback delete offer never arms without it.
     */
    private val postPlaybackDeleteSources: PostPlaybackDeleteSources? = null,
    /**
     * TRUE when the teardown release about to run is caused by the HOST
     * going away (the Android activity finishing behind the player — the app
     * was swiped from recents mid-playback) rather than an in-app player
     * close. That distinction decides the restore prompt's teardown marker
     * (the ledger's [SessionPositionLedger] host-teardown probe): an in-app
     * close is "done for now" (persist, next launch stays silent), a host
     * exit is an INTERRUPTED session (skip, next launch prompts). Default
     * FALSE — desktop's only VM teardowns are in-app closes (a window close
     * kills the process before onCleared runs) and test graphs stay on the
     * persist side.
     */
    private val isAppExiting: () -> Boolean = { false },
) : JellyPlayViewModel() {

    private val castManager: CastManager = castManager

    private val _uiState = stateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.flow

    /**
     * Remote "TakeScreenshot" requests: the receiver emits through the
     * active-engine registry while this screen's engine is bound; the screen
     * collects this and runs its screenshot action — the SAME capture path as
     * the overflow-menu button. Passthrough (no state): fire-and-forget.
     */
    val remoteScreenshotRequests: kotlinx.coroutines.flow.SharedFlow<Unit>
        get() = activePlayerController.screenshotRequests

    // --- High-frequency playback streams ---------------------------------------
    // currentPosition / bufferedPosition / videoStats (and duration) update at
    // up to 4 Hz while controls are visible. They live on dedicated StateFlows
    // collected only inside the leaf composables that render them
    // (PlayerControls seek bar, VideoStatsOverlay); the remaining uiState is
    // thereby a low-frequency stream.
    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    /**
     * Multi-band buffered surface, bridged from the active engine's
     * own [com.raulshma.jellyplay.feature.player.video.engine.MediaEngine.bufferedRanges]
     * — the seek bar's shaded bands and the stats overlay's ranges readout
     * collect this leaf-side like [currentPositionMs]; it deliberately does
     * NOT ride the position-update callback (that scalar path is
     * reporter-owned) and re-subscribes per engine via the session's
     * [PlaybackSession.engineFlow] alias. `lazy` because [playbackSession] is
     * constructed further down the class body; the WhileSubscribed stateIn
     * collects nothing until first read.
     */
    val bufferedRanges: StateFlow<List<LongRange>> by lazy {
        stateIn(
            initial = emptyList(),
            flow = playbackSession.engineFlow.flatMapLatest { it?.bufferedRanges ?: emptyFlow() },
        )
    }

    private val _videoStats = MutableStateFlow(EngineVideoStats())
    val videoStats: StateFlow<EngineVideoStats> = _videoStats.asStateFlow()

    /**
     * Low-frequency view of the segment / up-next overlays, derived by folding
     * the high-frequency [currentPositionMs] / [durationMs] into [uiState] only
     * to compute the active segment. The result is `distinctUntilChanged` via
     * [StateFlow], so collectors (the screen root) only recompose when one of
     * these values actually changes — i.e. at segment boundaries, not at 4 Hz.
     *
     * Only the segment-relevant slice of [uiState] is projected (via
     * [SegmentProjection] + `distinctUntilChanged`) so a 4 Hz position tick does
     * not allocate a fresh `VideoPlayerUiState.copy(...)` and re-run
     * `computeActiveSegment()` when no segment-relevant field changed.
     */
    val segmentOverlayState: StateFlow<SegmentOverlayState> = stateIn(
        initial = SegmentOverlayState(),
    flow = combine(
        currentPositionMs,
        combine(
            durationMs,
            uiState.map(::SegmentProjection).distinctUntilChanged(),
            // #16: the configured up-next pre-roll window
            // (`nextUpButtonPrerollSec * 1000`) rides the aggregate leaf so a
            // settings change re-derives the overlay; the input rebuild itself
            // still only happens when a low-frequency input changes.
            stores.aggregateStore.aggregate
                .map { it.videoPlayer.nextUpButtonPrerollSec * 1000L }
                .distinctUntilChanged(),
        ) { dur, proj, prerollMs ->
            proj.toSegmentInput(dur, nextUpPrerollMs = prerollMs)
        },
    ) { pos, input ->
        computeOverlay(pos, input)
    },
    )

    private val _closePlayer = Channel<Unit>(Channel.BUFFERED)
    val closePlayer = _closePlayer.receiveAsFlow()

    /**
     * Fires once when playback resumes from a saved position, so the screen can
     * surface a transient "Resumed — Restart" affordance. Carries
     * the resumed position in ms so the chip can label it. `null`/0 means "no
     * reminder pending".
     */
    private val _resumeReminder = kotlinx.coroutines.flow.MutableSharedFlow<Long>(
        extraBufferCapacity = 1,
    )
    val resumeReminder: kotlinx.coroutines.flow.SharedFlow<Long> = _resumeReminder

    // ── Collaborator graph ───────────────────────────────────────────────────
    // The session stack, the controllers and the whole collaborator graph are
    // constructed and armed by [playbackSession] ([PlaybackSession], the
    // composition root the deleted `PlayerWiring` builder collapsed into):
    // its class body constructs every collaborator once — the six
    // mutual-recursion construction cycles dissolved by ownership
    // (self-referencing seams, an owned projection cluster, owner funnels;
    // see PlaybackSession's KDoc for the per-cycle map) — and [arm] registers
    // the collectors this class's former init block launched inline (the
    // engine-event mirrors, the session-event forwarder, the aggregate-prefs
    // fan-out, the engine-attach choreography). This VM keeps only the state
    // holders, the [onEvent] funnel + its one-line arms, and the expose-only
    // flows; the screen drives the controller slices only through the
    // session-slice seam ([sessionSlices] below).

    private val _passOutEvents = Channel<String>(Channel.BUFFERED)
    val passOutEvents: kotlinx.coroutines.flow.Flow<String> = _passOutEvents.receiveAsFlow()

    /**
     * The playback session — the player's deep module and composition root
     * (see [PlaybackSession]'s KDoc for the construction contract): its class
     * body constructs every collaborator ONCE with the six mutual-recursion
     * construction cycles dissolved by ownership (no lateinit back-reference
     * slots remain), and its arm phase - [PlaybackSession.arm], called from
     * `init` below - registers the collectors the VM's state holders and
     * event funnel need. The session owns the collaborator graph, the load
     * sequence, the engine-swap choreography, the reporting + release surface,
     * the session-event forwarder and the session-policy dispatches (the
     * end-of-media fold + the segment-skip arm, whose every fact and effect
     * target is session-owned); this VM keeps the constructor dependencies,
     * the state holders, the [onEvent] funnel + its one-line arms and the
     * expose-only flows. The screens never read this handle — they
     * program against the per-region session slices ([sessionSlices], the
     * GLOSSARY "Session slice" seam); this property stays the VM-internal
     * composition-root alias the slices and the funnel arms project.
     */
    internal val playbackSession = PlaybackSession(
        scope = scope,
        platform = platform,
        stores = stores,
        videoQueue = videoQueue ?: VideoQueueController(),
        cinemaModeOverride = cinemaModeOverride,
        imageUrlProvider = imageUrlProvider,
        itemPlaybackPreferenceRepository = sessionSources.itemPlaybackPreferenceRepository,
        castManager = castManager,
        jellyfinRemotePlayCastStrategy = sessionSources.jellyfinRemotePlayCastStrategy,
        syncPlayManager = syncPlayManager,
        adaptiveBitrateManager = sessionSources.adaptiveBitrateManager,
        networkMonitor = sessionSources.networkMonitor,
        activePlayerController = activePlayerController,
        pipController = pipController,
        videoMiniPlayerState = sessionSources.videoMiniPlayerState,
        sleepCountdown = sessionSources.sleepCountdown,
        userMessageBus = userMessageBus,
        savedStateHandle = savedStateHandle,
        userDataMutator = sessionSources.userDataMutator,
        playbackSourceResolver = sessionSources.playbackSourceResolver,
        nowPlayingReporter = sessionSources.nowPlayingReporter,
        playbackStore = stores.playback,
        playbackRepository = sessionSources.playbackRepository,
        offlinePlaybackFacade = sessionSources.offlinePlaybackFacade,
        detailFeed = sessionSources.detailFeed,
        // The construction bundles (the PlayerStores pattern): each groups
        // the pass-throughs that feed ONE internally-built module cluster.
        subtitleSources = subtitleSources,
        offlineSources = offlineSources,
        sessionStack = sessionStack,
        itemContent = itemContent,
        handles = PlayerStateHandles(
            uiState = _uiState, positionMs = _currentPositionMs, durationMs = _durationMs, videoStats = _videoStats,
            resumeReminder = _resumeReminder, closePlayer = _closePlayer, passOutEvents = _passOutEvents,
        ),
        // #14's delete seams — consumed by the session's own
        // [PlaybackSession.postPlaybackActions] controller.
        postPlaybackDeleteSources = postPlaybackDeleteSources,
        // The host-exit probe rides the session's position ledger as a
        // constructor input (the probe, not a per-release flag write): the
        // ledger reads it at the final stop report to decide the restore
        // prompt's handled-offer marker (see [isAppExiting]).
        isHostTeardown = isAppExiting,
    )

    /**
     * The per-region session slices (the GLOSSARY "Session slice" seam): the
     * ONE read surface the player screens program against — hot flows are
     * collected in the leaf that reads them, and the session controllers sit
     * behind the slices, so no screen reaches [playbackSession] (or a
     * re-exposed controller pass-through) directly. Built in one place by
     * [createSessionSlices] so construction has locality.
     */
    // The route-button projection rides the platform cast-chrome seam.
    internal val sessionSlices: PlayerSessionSlices = playbackSession.createSessionSlices(
        routeButton = platformCastRouteButton(),
    )

    /**
     * Runs the session's arm phase: registers every collector the former
     * inline init block launched (the engine-event mirrors FIRST, the
     * session-event forwarder, the PiP transport registration + dismissal
     * discharge, the aggregate-prefs and metered-network collectors, the
     * SyncPlay start + session mirror, the becoming-noisy registration, the
     * video-focus binding, the session-state fold, the preference resolver,
     * and the engine-attach choreography LAST) — in the same order, so
     * subscription timing relative to the first session/engine emission is
     * unchanged.
     */
    init {
        // A user-driven player open acquires the cast consumer; released in
        // the full teardown ([PlaybackSession.performRelease]).
        castManager.acquireConsumer()
        playbackSession.arm()
        // Warm-resolve the Up Next card's item while the card counts down:
        // its detail/segments land in the repository caches and the PlaybackInfo
        // resolution is stored for the advance to consume (PlayerSessionManager).
        // The CARD is the target — the queue head beats the episode sibling,
        // exactly like the advance, so the warm-up never pre-runs an item the
        // advance would skip. SyncPlay excluded — the group's advance follows
        // the server queue, and offline resolve degrades silently inside the
        // warmer.
        launch {
            segmentOverlayState.collect { overlay ->
                val state = uiState.value
                if (overlay.shouldShowUpNext) {
                    Log.d(
                        UP_NEXT_LOG_TAG,
                        "card show=${overlay.shouldShowUpNext} queued=${state.hasQueuedNext} " +
                            "nextEpisode=${state.episodes.nextEpisode?.id} seg=${overlay.activeSegment?.type}",
                    )
                }
                if (overlay.shouldShowUpNext && !state.isInSyncPlaySession) {
                    playbackSession.upNext.upNextCard.value?.itemId?.let { nextId ->
                        playbackSession.warmNextEpisode(nextId)
                    }
                }
            }
        }
        // Queue mirror: the up-next overlay and the autoplay gate read
        // hasQueuedNext from the uiState projection; the Up Next controller's
        // flow IS the truth (queue non-empty under the SyncPlay gate). The
        // mirror survives item switches via keepAcrossItems — the controller's
        // flow won't re-emit to re-set a dropped mirror.
        launch {
            playbackSession.upNext.hasQueuedNext.collect { queued ->
                if (_uiState.value.hasQueuedNext != queued) {
                    Log.d(
                        UP_NEXT_LOG_TAG,
                        "queue mirror -> $queued (head=${playbackSession.upNext.upNextCard.value?.itemId})",
                    )
                    _uiState.update { it.copy(hasQueuedNext = queued) }
                }
            }
        }
        NextStreamByteWarmWatcher(
            scope = scope,
            warmNextStream = playbackSession.warmNextStream,
            upNextCard = playbackSession.upNext.upNextCard,
            engineFlow = playbackSession.engineFlow,
            sessionState = playbackSession.sessionState,
            isInSyncPlaySession = { _uiState.value.isInSyncPlaySession },
            warmer = nextStreamByteWarmer,
        ).install()
    }

    /**
     * The single command funnel (the AudioPlayerUiEvent / AudioPlayerViewModel
     * `.onEvent` precedent): every user intent the screen expresses arrives as
     * a [VideoPlayerUiEvent] and routes once here to a private handler — the
     * former per-action public funs. The public surface beyond the funnel is
     * the state flows, the sync getters, the result-returning queries
     * ([getImageUrl], [loadTrickplayThumbnail], [verifyPlayerLockPin],
     * [useDownloadedSubtitle]), [release] (the screen's dispose hook) and the
     * per-region session slices ([sessionSlices]) the screen drives directly —
     * pinned by VideoPlayerViewModelOwnershipTest.
     */
    fun onEvent(event: VideoPlayerUiEvent) {
        when (event) {
            is VideoPlayerUiEvent.Initialize -> {
                // A user-driven player open: the still-watching streak starts
                // fresh (auto-advance loads bypass onEvent and keep it) — and
                // so does the autoplay-limit binge streak (#17).
                playbackSession.autoplayController.onUserInteraction()
                playbackSession.upNext.resetAutoAdvanceStreak()
                playbackSession.initialize(
                    itemId = event.itemId,
                    mediaSourceId = event.mediaSourceId,
                    startPositionTicks = event.startPositionTicks,
                    subtitleStreamIndex = event.subtitleStreamIndex,
                    audioStreamIndex = event.audioStreamIndex,
                    forceStream = event.forceStream,
                )
            }
            is VideoPlayerUiEvent.PlayEpisode -> {
                // Manual episode navigation resets the still-watching streak.
                playbackSession.autoplayController.onUserInteraction()
                playbackSession.upNext.playEpisode(event.episodeId, event.startPositionTicks)
            }
            is VideoPlayerUiEvent.RestartPlayback -> playbackSession.transport.restart()
            is VideoPlayerUiEvent.RetryPlayback -> retryPlayback()
            is VideoPlayerUiEvent.RetryWithEngine -> retryWithEngine(event.playerType)
            is VideoPlayerUiEvent.SwitchPlayerEngine ->
                // The sheets slice's ONE engine-swap verdict — membership check, never a re-fold.
                if (event.playerType in sessionSlices.sheets.switchableEngines.value) retryWithEngine(event.playerType, sessionOnly = true)
            is VideoPlayerUiEvent.DismissPlaybackError -> dismissPlaybackError()
            is VideoPlayerUiEvent.SetControlsVisible -> playbackSession.engineCommands.setControlsVisible(event.visible)
            is VideoPlayerUiEvent.InstallUserFont -> playbackSession.subtitleStyleController.installUserFont(event.uri)
            is VideoPlayerUiEvent.ReattachFromBackgroundCast -> reattachFromBackgroundCast()
            is VideoPlayerUiEvent.DetachForBackgroundCast -> detachForBackgroundCast()
            is VideoPlayerUiEvent.SetScreenLocked -> setScreenLocked(event.locked)
            is VideoPlayerUiEvent.TransportPlay -> playbackSession.transport.play(event.play)
            is VideoPlayerUiEvent.SeekTo -> playbackSession.transport.seek(event.positionMs, userInitiated = true)
            is VideoPlayerUiEvent.SeekByStep -> playbackSession.transport.step(event.direction)
            is VideoPlayerUiEvent.ToggleMute -> playbackSession.uiCommands.toggleMute()
            is VideoPlayerUiEvent.UserInteraction -> onUserInteraction()
            is VideoPlayerUiEvent.StartHoldSpeed -> playbackSession.engineCommands.startHoldSpeed()
            is VideoPlayerUiEvent.StopHoldSpeed -> playbackSession.engineCommands.stopHoldSpeed()
            is VideoPlayerUiEvent.ApplySubtitleStyle -> playbackSession.subtitleStyleController.applySubtitleStyle()
            is VideoPlayerUiEvent.UpdatePipSourceRect ->
                playbackSession.pipTransport.updatePipSourceRect(event.left, event.top, event.right, event.bottom)
            is VideoPlayerUiEvent.PlayPreviousEpisode -> {
                // Manual navigation: the still-watching streak restarts.
                playbackSession.autoplayController.onUserInteraction()
                playbackSession.upNext.playPreviousEpisode()
            }
            is VideoPlayerUiEvent.PlayNextEpisode -> {
                // Manual navigation: the still-watching streak restarts.
                playbackSession.autoplayController.onUserInteraction()
                // MANUAL play-next (the card's "Watch now" button): the
                // autoplay-limit binge streak restarts too (#17) — the
                // countdown's expiry arm routes through the slice instead.
                playbackSession.upNext.playNextManually()
            }
            is VideoPlayerUiEvent.MarkWatchedAndSkip -> {
                // A deliberate user advance — resets the still-watching streak.
                playbackSession.autoplayController.onUserInteraction()
                playbackSession.upNext.markWatchedAndSkip()
            }
            is VideoPlayerUiEvent.MarkUnwatchedAndQuit -> playbackSession.upNext.markUnwatchedAndQuit()
            is VideoPlayerUiEvent.ToggleDialogueBoost -> toggleDialogueBoost()
            is VideoPlayerUiEvent.SetDialogueBoostStrength -> setDialogueBoostStrength(event.strength)
            is VideoPlayerUiEvent.ToggleVideoStats -> playbackSession.uiCommands.toggleVideoStats()
            is VideoPlayerUiEvent.ToggleAudioOnly -> toggleAudioOnly()
            is VideoPlayerUiEvent.SetSubtitleDelay -> setSubtitleDelay(event.ms)
            is VideoPlayerUiEvent.SkipIntro -> playbackSession.skipIntro()
            is VideoPlayerUiEvent.SkipSegment -> playbackSession.skipSegment(event.segment)
            is VideoPlayerUiEvent.SaveBrightness -> playbackSession.uiCommands.saveBrightness(event.level)
            is VideoPlayerUiEvent.SetPlaybackSpeed -> playbackSession.engineCommands.setPlaybackSpeed(event.speed)
            is VideoPlayerUiEvent.SelectAudioTrack -> selectAudioTrack(event.option)
            is VideoPlayerUiEvent.SelectSubtitleTrack -> selectSubtitleTrack(event.option)
            is VideoPlayerUiEvent.ResetAudioTrack -> resetAudioTrack()
            is VideoPlayerUiEvent.ResetSubtitleTrack -> resetSubtitleTrack()
            is VideoPlayerUiEvent.SelectMediaSource -> selectMediaSource(event.mediaSourceId)
            is VideoPlayerUiEvent.SetPreferredMediaVersion -> setPreferredMediaVersion(event.remember)
            is VideoPlayerUiEvent.SetSeriesAudioLanguagePreference ->
                setSeriesAudioLanguagePreference(event.language)
            is VideoPlayerUiEvent.SetSeriesSubtitlePreference ->
                setSeriesSubtitlePreference(event.language, event.forced, event.hearingImpaired)
            is VideoPlayerUiEvent.SetSeriesSubtitleDisabled -> setSeriesSubtitleDisabled(event.disabled)
            is VideoPlayerUiEvent.SetSeriesAudioDescriptionPreference ->
                setSeriesPreferAudioDescription(event.remember)
            is VideoPlayerUiEvent.PlayQueueItem -> {
                // Manual navigation: the still-watching streak restarts.
                playbackSession.autoplayController.onUserInteraction()
                playbackSession.upNext.playQueueItem(event.index)
            }
            is VideoPlayerUiEvent.RemoveQueueItem -> playbackSession.videoQueue.removeAt(event.index)
            is VideoPlayerUiEvent.MoveQueueItem -> playbackSession.videoQueue.move(event.fromIndex, event.toIndex)
            VideoPlayerUiEvent.ClearQueue -> playbackSession.videoQueue.clear()
            is VideoPlayerUiEvent.SetAspectRatio -> playbackSession.uiCommands.setAspectRatio(event.ratio)
            is VideoPlayerUiEvent.SetSubtitleStyle -> playbackSession.subtitleStyleController.setStyle(event.style)
            is VideoPlayerUiEvent.ToggleSubtitles -> playbackSession.trackSelectionHelper.toggleSubtitles()
            is VideoPlayerUiEvent.SetPlaybackMode -> playbackSession.uiCommands.setPlaybackMode(event.mode)
            is VideoPlayerUiEvent.SetStreamingQuality -> playbackSession.uiCommands.setStreamingQuality(event.quality)
            is VideoPlayerUiEvent.SetAdaptiveBitrateEnabled -> playbackSession.uiCommands.setAdaptiveBitrateEnabled(event.enabled)
            is VideoPlayerUiEvent.SetVideoEffects -> playbackSession.uiCommands.setVideoEffects(event.effects)
            is VideoPlayerUiEvent.SetVideoEffectsSettled -> playbackSession.uiCommands.setVideoEffectsSettled(event.effects)
            is VideoPlayerUiEvent.SetRenderShaderPack -> setRenderShaderPack(event.pack, event.persist)
            is VideoPlayerUiEvent.SetRenderToneMapping -> setRenderToneMapping(event.mapping, event.persist)
            is VideoPlayerUiEvent.SetRenderQuality -> playbackSession.render.setRenderQuality(event.quality)
            is VideoPlayerUiEvent.SetMpvHdrPassthrough -> playbackSession.render.setMpvHdrPassthrough(event.enabled)
            is VideoPlayerUiEvent.SetExoHdrToneMapping -> playbackSession.render.setExoHdrToneMapping(event.mode)
            is VideoPlayerUiEvent.ClearRenderOverride -> playbackSession.render.clearRenderOverride()
            is VideoPlayerUiEvent.CycleDeinterlace -> cycleDeinterlace()
            is VideoPlayerUiEvent.CancelAutoplay -> playbackSession.upNext.cancelAutoplay()
            is VideoPlayerUiEvent.StillWatchingContinue -> playbackSession.stillWatching.onContinue()
            is VideoPlayerUiEvent.StillWatchingStop -> playbackSession.stillWatching.onStop()
            is VideoPlayerUiEvent.StillWatchingTick -> playbackSession.stillWatching.onTick()
            is VideoPlayerUiEvent.SetVideoAutoplayNext -> playbackSession.uiCommands.setVideoAutoplayNext(event.enabled)
            is VideoPlayerUiEvent.SetInputBindingEnabled -> playbackSession.inputBindingToggle.setEnabled(event.bindingId, event.enabled)
            is VideoPlayerUiEvent.SetSyncPlayRepeatMode -> setSyncPlayRepeatMode(event.mode)
            is VideoPlayerUiEvent.SetSyncPlayShuffleMode -> setSyncPlayShuffleMode(event.mode)
            is VideoPlayerUiEvent.LoadSeasonEpisodes -> playbackSession.upNext.loadSeason(event.seasonId)
        }
    }

    /** Resets the pass-out interaction clock (delegates to the coordinator). */
    private fun onUserInteraction() {
        playbackSession.engineEventCoordinator.onUserInteraction()
    }

    // The segment-skip dispatch (the skip buttons + the position-tick
    // auto-skip arm) and the end-of-media fold moved onto the session —
    // their every fact (the position/duration flows, the autoplay/sleep/
    // still-watching verdicts, the Up Next keep gate, the cinema chain) and
    // every effect target (the seek funnel, the advance, the #14 offer, the
    // close channel) is session-owned; the pure decision halves stay in
    // SegmentSkipPolicy.kt / EndOfMediaDecision.kt. The funnel arms below
    // forward one-line.

    // The SyncPlay slice the screen drives lives on the session's slices
    // ([sessionSlices].controls / .sheets), built by the session.

    // ── Session load pipeline ────────────────────────────────────────────────
    // The pipeline owns the ORDER of the load stages; the outputs and hook
    // bodies are implemented by [PlaybackSession] itself (its
    // SessionLoadOutputs + SessionLifecycleHooks implementations). The three
    // stage bodies with real logic live in SessionLoadPipeline
    // (fetchMediaSegments / shouldAttemptCinemaMode / restoreRememberedMuted)
    // and the server start report in PlaybackSession (beside its stop-report
    // twin).

    val hapticsEnabled: Boolean get() = stores.appearance.appearance.value.hapticsEnabled

    /**
     * The incognito gate the overflow's "Mark unwatched & exit" item hides
     * behind — same read pattern as [hapticsEnabled]: a rare-flip
     * pref read at render time, not a dedicated flow.
     */
    val incognitoModeEnabled: Boolean get() = playbackSession.cachedAggregate.videoPlayer.incognitoModeEnabled

    // ── Session slices ──────────────────────────────────────────────────────
    // Each migrated controller owns its slice as a MutableStateFlow; the
    // screens collect `slice.state` (and any per-slice streams) at the leaf
    // composables that render it and call the slice's command funs — through
    // the per-region projections of [sessionSlices], never through a
    // controller handle or a re-exposed pass-through. The residual [uiState]
    // keeps only session + prefs-mirror state. The ViewModel does NOT relay
    // slice commands: it keeps only real orchestration (load/session/lifecycle
    // and cross-controller flows like background-cast detach).

    // The engine-event MIRROR collectors (play/buffering uiState writes, the
    // still-watching interaction feed) are registered once from
    // [PlaybackSession.arm] and again through the session's rearm callback.

    // The transport routing funnels (routedSeek / routedPlay — the
    // SyncPlay → cast → local ladders) moved onto the session's
    // [PlaybackSession.transport] router; the funnel arms forward one-line.
    // Their KDoc contracts (live routing reads, the group-owns-play-state
    // SyncPlay behavior, the resume-skip import for PiP's play arm) carried
    // onto the router.

    // The engine handle the screen composes with (reactive flow + one-shot
    // reads) lives on the controls session slice
    // ([com.raulshma.jellyplay.feature.player.video.session.ControlsSessionSlice.engineFlow]
    // / `.engine`).

    // The load funnel lives on [playbackSession] (`initialize(itemId, …)` —
    // the PiP event-flag clear + the process-death tick resolution are
    // session-side since the wiring collapse); [VideoPlayerUiEvent.Initialize]
    // and the session-internal collaborators (SyncPlay group loads,
    // next-episode advances) all take that one funnel.

    /**
     * Offline resume: the offline entry points (Downloads, OfflineLibrary,
     * MediaDetail (offline), deep links, remote control, mini-player) all navigate with
     * `startPositionTicks = 0`. When no explicit position was requested and the
     * item is a completed download, fall back to the last-known position stored
     * on the downloaded item (seeded from server UserData and updated while
     * watching offline). Streaming keeps the caller-provided value.
     *
     * `resolveOfflineResumeTicks` (the load spine's resolution hook) lives on
     * [SessionLoadPipeline] as a one-line call into
     * [com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver.resolveStartPositionTicks],
     * where the resume-position rule (explicit > 0 wins, else the
     * offline-store ticks) lives once in core.
     */

    // The per-item hydration body (`onItemHydrated`) moved into the session's
    // loadHooks bundle (the videoFx mirror write, the engine-config rebuild
    // nudge and the style-controller hydration are all session collaborators
    // since the wiring collapse), and the "Play On" routing early-return
    // (`routeToRemotePlaySession`) is a session lifecycle hook over the
    // remote-play strategy the session now owns.

    private fun setScreenLocked(locked: Boolean) {
        _uiState.update { it.copy(isScreenLocked = locked) }
    }

    suspend fun verifyPlayerLockPin(pin: String): Boolean {
        return stores.security.verifyPinOffMainThread(pin)
    }

    // The speed family (the picker arm + the hold-speed gesture with its
    // captured restore speed) and the engine cadence commands (scrubbing
    // bracket, controls-visibility polling) moved onto the session's
    // [PlaybackSession.engineCommands] funnel — the funnel arms forward
    // one-line.

    private fun selectAudioTrack(option: TrackOption) {
        playbackSession.trackSelectionHelper.selectAudioTrack(option)
    }

    private fun selectSubtitleTrack(option: TrackOption) {
        playbackSession.trackSelectionHelper.selectSubtitleTrack(option)
        // The active subtitle track changed — refresh the cue preview
        // eagerly so the AV-sync sheet (if open) shows the newly selected
        // track's cues without a reopen.
        playbackSession.subtitlePreview.onTrackSelectionChanged()
    }

    /**
     * Thin delegate to [PlaybackSession.reloadForStreamChange]: reloads the
     * current item at the current position with a new audio/subtitle stream
     * selection (server-origin track picks during transcoded playback — the
     * server must re-issue the stream with the chosen index).
     */
    private fun reloadForStreamChange(selection: MediaStreamSelection) {
        playbackSession.reloadForStreamChange(selection)
    }

    /**
     * Switches the playing version (media source) of the current item — the
     * Version sheet's pick. Thin delegate to
     * [PlaybackSession.switchMediaSource], which swaps at the current
     * position via `PlayerSessionManager.switchMediaSource`.
     */
    private fun selectMediaSource(mediaSourceId: String) {
        playbackSession.switchMediaSource(mediaSourceId)
    }

    /**
     * Saves/clears the "remember this version" preference for the current
     * item/series. Remembering pins the CURRENT media source id (SERIES scope
     * for an episode, ITEM scope for a standalone movie); forgetting clears
     * both scopes so no leftover row keeps winning.
     */
    private fun setPreferredMediaVersion(remember: Boolean) {
        if (remember) {
            val currentSourceId = playbackSession.playbackFacts().currentMediaSourceId
            if (currentSourceId != null) {
                playbackSession.playbackPreferenceWriter.setPreferredMediaSource(currentSourceId)
            }
        } else {
            playbackSession.playbackPreferenceWriter.clearPreferredMediaSource()
        }
    }

    private fun resetAudioTrack() {
        playbackSession.trackSelectionHelper.resetAudioSelection()
    }

    private fun resetSubtitleTrack() {
        playbackSession.trackSelectionHelper.resetSubtitleSelection()
    }

    // --- Per-series playback-language preferences -------------------------------
    // The headline use case is "remember the audio/subtitle language for this
    // series". Saving writes a SERIES-scope row, preserving the other language
    // if already set; deleting clears just the relevant field. The resolver is
    // refreshed afterwards so the cached value (read by TrackSelectionHelper)
    // and the sheet toggle state stay in sync.

    /**
     * Saves/clears a per-series preferred audio language. Pass the language of
     * the currently-selected audio track to remember it, or null to forget.
     * No-op when the current item has no series (e.g. a standalone movie).
     */
    private fun setSeriesAudioLanguagePreference(language: String?) {
        playbackSession.playbackPreferenceWriter.setSeriesAudioLanguage(language)
    }

    /**
     * Saves/clears a per-series preferred subtitle descriptor (language + role).
     * Pass the language/role of the currently-selected subtitle track to remember
     * it, or a null language to forget. The role fields ([forced] /
     * [hearingImpaired]) are optional: null means "don't care about that role",
     * a value pins it so the restore matcher prefers e.g. "English SDH" episode
     * to episode. No-op when the current item has no series.
     */
    private fun setSeriesSubtitlePreference(
        language: String?,
        forced: Boolean? = null,
        hearingImpaired: Boolean? = null,
    ) {
        playbackSession.playbackPreferenceWriter.setSeriesSubtitlePreference(language, forced, hearingImpaired)
    }

    /**
     * Saves/clears a per-series "subtitles off" intent. When [disabled] is true
     * every episode of the series loads with subtitles off (the resolver skips
     * the language matcher and forces Off); when false the intent is forgotten
     * so the global/per-item rules take effect again. No-op when the current
     * item has no series. Mutually exclusive with [setSeriesSubtitlePreference]:
     * enabling one clears the other's row fields.
     */
    private fun setSeriesSubtitleDisabled(disabled: Boolean) {
        playbackSession.playbackPreferenceWriter.setSeriesSubtitleDisabled(disabled)
    }

    /**
     * Saves/clears the per-series audio-description pin. `remember = true`
     * prefers AD narration for every episode; `false` clears the pin so the
     * global setting stands again. No-op when the current item has no series.
     */
    private fun setSeriesPreferAudioDescription(remember: Boolean) {
        playbackSession.playbackPreferenceWriter.setSeriesPreferAudioDescription(if (remember) true else null)
    }

    // ── Manual up-next queue (the queue sheet's funnel) ───────────────────

    // "Play now" on a queue row routes to the Up Next controller's
    // settle-then-consume protocol (playQueueItem) — the old
    // consume-then-play body here silently deleted the row on a failed load.
    // The queue rows and the Up Next card are read through the session's
    // slices ([sessionSlices].sheets.queueItems / .overlay.upNextCard) — the
    // former VM pass-throughs are gone with the slice seam.
    // -----------------------------------------------------------------------

    // The pref-write funnels (setAspectRatio / setPlaybackMode /
    // setStreamingQuality / setAdaptiveBitrateEnabled + their shared
    // choreography and the reload delegate) moved onto the session's
    // [PlaybackSession.uiCommands]; the funnel arms forward one-line. The
    // mirror-before-persist order and the pre-write sibling-snapshot rule
    // carried onto the commands class.

    // Subtitle style/delay + dialogue-boost choreography (the style edit
    // persist, the per-item delay write + its debounced engine apply, the
    // per-item boost persist, the user-font install + the direct engine
    // style re-apply) lives in [SubtitleStyleController]; the functions
    // below are the remaining screen/PiP-facing funnels.

    private fun toggleDialogueBoost() {
        playbackSession.subtitleStyleController.toggleDialogueBoost()
    }

    private fun setDialogueBoostStrength(strength: com.raulshma.jellyplay.core.model.EffectStrength) {
        playbackSession.subtitleStyleController.setDialogueBoost(strength)
    }

    private fun setSubtitleDelay(ms: Long) {
        playbackSession.subtitleStyleController.setDelay(ms)
    }

    /**
     * The error-dialog dismissal write: all three fields move together (flag
     * down, error + retryable nulled) — one home for the triple instead of a
     * hand-copied `copy(...)` at each dismissal site.
     */
    private fun clearPlaybackErrorState() {
        _uiState.update {
            it.copy(
                showPlaybackErrorDialog = false,
                playerError = null,
                playerErrorRetryable = false,
            )
        }
    }

    /**
     * Switch-engine retry (backs the error dialog's engine picker). The
     * error-dialog clear is a synchronous ui-state write and stays here;
     * everything from the reporter cancel to the engine swap and
     * tracking/media-session rebuild is session-owned
     * ([PlaybackSession.retryWithEngine]). A [sessionOnly] switch preserves
     * pause state and the saved default engine.
     */
    private fun retryWithEngine(playerType: PlayerType, sessionOnly: Boolean = false) {
        clearPlaybackErrorState()
        _uiState.update {
            it.copy(preferredPlayerType = playerType)
        }
        playbackSession.retryWithEngine(
            playerType = playerType,
            playbackSpeed = _uiState.value.playbackSpeed,
            streamingQuality = _uiState.value.uiPrefs.streamingQuality,
            sessionOnly = sessionOnly,
        )
    }

    /**
     * Same-engine retry for recoverable [EngineError]s (Network, Render, or the
     * buffering watchdog timeout). Reloads the current engine at the current
     * position, mirroring [retryWithEngine] without changing engine. UI gates
     * this button on [VideoPlayerUiState.playerErrorRetryable]; fatal errors
     * (Decoder, Drm) only offer switch-engine.
     */
    private fun retryPlayback() {
        clearPlaybackErrorState()
        playbackSession.retryPlayback(
            playbackSpeed = _uiState.value.playbackSpeed,
            streamingQuality = _uiState.value.uiPrefs.streamingQuality,
            preferredPlayerType = _uiState.value.preferredPlayerType,
        )
    }

    private fun dismissPlaybackError() {
        clearPlaybackErrorState()
    }

    // ── Rendering sheet + deinterlace ──────────────
    // The write choreography lives in [render] (RenderControls); the funs
    // below are the screen-facing funnels — one-line delegates that preserve
    // the public names/signatures (the sheet router + gear menu call them).
    // The sheet's READS (the global slice + the session lens) go through the
    // sheets session slice (`sessionSlices.sheets.globalMpvConfig` / `.render`).

    /**
     * The "Rendering" sheet's shader-pack pick. Applies to the session
     * immediately (folded into every config build via [sessionRender]) and,
     * when [persist] is on (the "save for this series" toggle), pins the
     * override to the series row (or the item row for standalone movies).
     */
    private fun setRenderShaderPack(pack: com.raulshma.jellyplay.core.model.MpvShaderPack, persist: Boolean) =
        playbackSession.render.setRenderShaderPack(pack, persist)

    /** The sheet's tone-mapping pick — same session/persist choreography as [setRenderShaderPack]. */
    private fun setRenderToneMapping(mapping: com.raulshma.jellyplay.core.model.MpvToneMapping, persist: Boolean) =
        playbackSession.render.setRenderToneMapping(mapping, persist)

    /**
     * cycle the session-scoped deinterlace override AUTO→ON→OFF→AUTO.
     * Held in [sessionRender] (survives next-episode advance, reverts on
     * player exit) — deliberately NOT persisted.
     */
    private fun cycleDeinterlace() = playbackSession.render.cycleDeinterlace()

    // Funnel handlers route their dirty-config reports through the wiring's
    // markEngineConfigDirty / markEngineConfigDirtyDebounced.

    // The end-of-media dispatch (the engine-ENDED arm via the session-event
    // forwarder, the reporter's no-next safety net, and the #14 offer's own
    // release) moved onto the session — its every fact
    // (playbackSession.autoplayController / upNext / sleepTimer /
    // stillWatching / cinemaIntroContext / cachedAggregate) and every effect
    // target (the advance, the cinema chain, the still-watching overlay, the
    // #14 offer, the close channel, nowPlayingReporter.markEnded) is
    // session-owned; the pure [EndOfMediaDecision] fold module stays as is.

    private fun setSyncPlayRepeatMode(mode: SyncPlayRepeatMode) {
        launch {
            syncPlayManager.setGroupRepeatMode(mode)
        }
    }

    private fun setSyncPlayShuffleMode(mode: SyncPlayShuffleMode) {
        launch {
            syncPlayManager.setGroupShuffleMode(mode)
        }
    }

    // shouldAttemptCinemaMode (the load spine's cinema gate — the cached
    // aggregate, the SyncPlay flag, the media-detail holder) and
    // fetchMediaSegments (the offline-first segments fetch + its uiState
    // write) live on SessionLoadPipeline, whose spine owns when both stages
    // run; the cinema SEQUENCING is session-owned.

    fun getImageUrl(itemId: String, maxWidth: Int = 400): String =
        imageUrlProvider.getImageUrl(itemId, maxWidth = maxWidth)

    /**
     * Tag-carrying poster URL for an item the surface holds in full (episode
     * picker rows, the companion dashboard's episodes tab, the Up Next card's
     * episode sibling): the [ArtworkUrls] poster slot, so the URL carries the
     * item's own primary image tag. Raw-id twin: [getImageUrl].
     */
    fun posterUrl(item: MediaItem, maxWidth: Int = 400): String =
        artworkUrls.poster(item, maxWidth = maxWidth)

    /**
     * Tag-carrying portrait for the companion dashboard's cast-and-crew row:
     * the person's own primary image tag rides along, same etag contract as
     * [posterUrl].
     */
    fun personPortraitUrl(person: PersonInfo, maxWidth: Int = 400): String =
        artworkUrls.personPortrait(person, maxWidth = maxWidth)

    /**
     * Scrubbing-session bracket for the engine (seek-bar drag, swipe seek,
     * double-tap-hold). Media3's adapter maps this to
     * `setScrubbingModeEnabled` — decode-only frames while the drag is live —
     * other engines no-op. See [MediaEngine.onScrubbingChanged].
     */
    fun setScrubbing(active: Boolean) {
        playbackSession.engineCommands.setScrubbing(active)
    }

    /**
     * Chapter thumbnail URLs for the chapter sheet, parallel to [chapters].
     * Entries are null when the chapter carries no server-extracted
     * `imageTag` (the sheet falls back to a text-only row) or no item is
     * loaded. URL building is pure string assembly (LRU-cached in
     * [ImageUrlProvider]) — no network happens here.
     */
    fun chapterImageUrls(chapters: List<ChapterInfo>): List<String?> {
        val itemId = playbackSession.playbackFacts().currentItemId
            ?: return List(chapters.size) { null }
        return chapters.mapIndexed { index, chapter ->
            chapter.imageTag?.let { imageUrlProvider.getChapterImageUrl(itemId, index, it) }
        }
    }

    /**
     * "Use" action for a downloaded-subtitle row: activates that subtitle as
     * the current track. [rowKey] is the plain remote-subtitle id for Jellyfin
     * rows and the composite `"{provider}:{id}"` key for external-provider
     * rows — matching how [SubtitleManager] records its ready hints.
     *
     * Returns true when the subtitle was activated now; false when it has not
     * surfaced yet (the side-load into the engine is asynchronous — mpv
     * republishes its track list on a delay, ExoPlayer re-prepares the media
     * item). In that case the ready hint is armed as a pending selection so it
     * applies automatically on the next track-list emissions instead of the
     * user having to re-tap "Use" — and callers should not navigate away from
     * the download row.
     */
    fun useDownloadedSubtitle(rowKey: String): Boolean {
        val hint = playbackSession.subtitles.state.value.readySubtitles[rowKey]
        Log.d(
            USE_LOG_TAG,
            "Use pressed: rowKey=$rowKey, hint=$hint, playMethod=${playbackSession.playbackFacts().playMethod}, " +
                "pickerRows=" + playbackSession.trackSelectionHelper.state.value.subtitleTracks
                    .joinToString { "(i=${it.index},id=${it.id},si=${it.streamIndex},sel=${it.isSelected},'${it.label.take(24)}')" },
        )
        if (hint == null) {
            userMessageBus.info("Subtitle not active yet — please try again shortly")
            return false
        }
        val option = playbackSession.trackSelectionHelper.findSubtitleOptionFor(hint)
        Log.d(USE_LOG_TAG, "Resolution: ${option?.let { "index=${it.index} id=${it.id}" } ?: "<none>"}")
        if (option == null) {
            playbackSession.trackSelectionHelper.requestSubtitleSelection(hint)
            userMessageBus.info("Subtitle still loading — it will be selected automatically")
            return false
        }
        selectSubtitleTrack(option)
        userMessageBus.info("Subtitle selected")
        return true
    }

    // ── Background-cast media-session swap ──────────────────────────────────
    // The background-casting state lives on [CastManager]
    // (markBackgroundCasting / isBackgroundCasting) — reattach is a no-op
    // unless a detach armed it. The window session drives the pair through
    // the DetachForBackgroundCast / ReattachFromBackgroundCast events.

    /**
     * Hands the media session from the local engine to the cast receiver:
     * playback continues headless in the background, owned by the cast
     * player.
     */
    private fun detachForBackgroundCast() {
        castManager.markBackgroundCasting(true)
        castManager.softRelease()

        // The cast receiver's player is resolved behind the androidMain seam;
        // this no-ops when no cast session is active.
        playbackSession.mediaSessionController.createForBackgroundCast("jellyplay_cast_bg")
    }

    /**
     * Rebuilds the local player session on return — a no-op unless a detach
     * armed the background-casting flag, and without a bound engine.
     */
    private fun reattachFromBackgroundCast() {
        if (!castManager.isBackgroundCasting) return
        castManager.markBackgroundCasting(false)

        val engine = playbackSession.engine
        if (engine != null) {
            val itemId = playbackSession.playbackFacts().currentItemId ?: return
            // Narrows the engine to its media3 player via asMedia3Player and
            // no-ops when the engine hosts none.
            playbackSession.mediaSessionController.createForPlayer(engine, "jellyplay_video_$itemId", itemId)
        }
    }

    private fun toggleAudioOnly() {
        _uiState.update { it.copy(audioOnly = !it.audioOnly) }
    }

    /**
     * Trickplay thumbnail as an opaque platform handle (seam): the
     * screen narrows it back to [PlatformBitmap] (Bitmap/BufferedImage) at the
     * call site.
     */
    suspend fun loadTrickplayThumbnail(positionMs: Long): Any? {
        val state = _uiState.value
        if (!state.uiPrefs.trickplayEnabled && !state.uiPrefs.trickplayOnSeekGesture) return null
        return playbackSession.trickplayManager.getThumbnail(positionMs)
    }

    // The per-item/full teardown's controller+ui-state half
    // (`releaseInternalsVmPart`) is a session lifecycle hook now — its body
    // moved into [PlaybackSession] with the collaborators it drives; the VM's
    // teardown entry points below route through the session.

    fun release() {
        // The released-latch guard + the full teardown choreography live on
        // the session ([PlaybackSession.performRelease], idempotent).
        playbackSession.performRelease()
    }

    override fun onCleared() {
        super.onCleared()
        // The release this onCleared triggers is either an in-app close (nav
        // popped the player route, host alive) or the host going away (the
        // activity finished behind the player — app swiped from recents).
        // That distinction decides whether the teardown persists the restore
        // prompt's handled-offer marker; the session's position ledger reads
        // it through the constructor-wired [isAppExiting] probe at the final
        // stop report — no per-release flag write here.
        release()
        // Cancel-after-release ordering: the final stop-report /
        // pending-seek join (launched on the session's release scope by
        // release()) run first, and the scope is only cancelled once the owner
        // is going away for good.
        playbackSession.releaseScope.cancel()
    }

    private companion object {
        const val TAG = "VideoPlayerViewModel"

        /**
         * Shared tag across the whole "downloaded subtitle → Use → activate"
         * chain (VM, session manager, engines) so one logcat filter captures
         * the full path: `adb logcat -s SubtitleUse`.
         */
        const val USE_LOG_TAG = "SubtitleUse"

        /**
         * Up Next card + manual queue trace (the queue mirror, the card's
         * overlay flag, the end-of-playback autoplay decision): one filter,
         * `adb logcat -s UpNext UpNextQueue`, covers the whole chain.
         */
        const val UP_NEXT_LOG_TAG = "UpNext"
    }
}
