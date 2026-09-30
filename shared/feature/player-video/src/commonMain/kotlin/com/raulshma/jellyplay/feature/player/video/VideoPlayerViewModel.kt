package com.raulshma.jellyplay.feature.player.video

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.playback.SleepCountdown
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.dischargePipDismissal
import com.raulshma.jellyplay.core.data.playback.PlaybackIdentity
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.data.repository.LyricsRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.core.model.mediaRuleContentType
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import org.jetbrains.compose.resources.getString
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_direct_play_fallback
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_error_next_episode_load


import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.SegmentCalculator
import com.raulshma.jellyplay.feature.player.video.engine.SegmentCalculatorInput
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.raulshma.jellyplay.feature.player.video.engine.mirrorPlaying
import com.raulshma.jellyplay.feature.player.video.engine.stepSeekTargetMs
import com.raulshma.jellyplay.feature.player.video.state.GesturePrefsState
import com.raulshma.jellyplay.feature.player.video.state.PlayerUiPrefsState
import com.raulshma.jellyplay.feature.player.video.state.ReadySubtitleHint
import com.raulshma.jellyplay.feature.player.video.state.SegmentState
import com.raulshma.jellyplay.feature.player.video.state.VideoFxState
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayPreparation
import com.raulshma.jellyplay.core.model.VideoEffectsConfig

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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

// The smart-download cleanup duration gate (MIN_DURATION_FOR_SMART_DELETE_MS)
// moved into EpisodeContinuationController.kt with the cleanup itself.

/**
 * How long a next-episode load may hold its single-flight latch while waiting
 * for the session to settle onto the new item before the button re-arms. Error
 * paths release earlier via [SessionEvent.ShowError]; this backstop covers
 * paths that change nothing (e.g. remote-play routing). Sized for the slowest
 * network preset's timeout chain (VERY_RELAXED 60 s + failover retries).
 */
// The process-death resume-position persistence (SavedStateHandle keys,
// throttle/coalesce windows, the staleness threshold and the pure
// resolveResumeTicks resolver) moved into PlaybackSession.kt behind the
// SessionPositionStore seam at B3.

// The debounced engine config-sync (CONFIG_SYNC_DEBOUNCE_MS + the
// configChangeIntent debounce collector) moved into EngineConfigSync.kt.

/** How long the "Skipped …" segment-skip confirmation caption stays up. */
private const val SKIPPED_SEGMENT_NOTICE_MS = 2_500L

// The subtitle-delay apply debounce (SUBTITLE_DELAY_APPLY_DEBOUNCE_MS) moved
// into SubtitleStyleController.kt with the debounce itself (A7).

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
     * androidMain trickplay/cast-controller/audio-lifecycle collaborators.
     */
    private val platform: VideoPlayerPlatform,
    private val mediaRepository: MediaRepository,
    /** The item-attached extras seam (Cinema Mode intros for the session-load pipeline). */
    private val mediaExtrasReads: com.raulshma.jellyplay.core.data.repository.MediaExtrasReads,
    private val lyricsRepository: LyricsRepository,
    private val playbackRepository: PlaybackRepository,
    private val playbackIdentity: PlaybackIdentity,
    private val subtitleProviderRepository: com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository,
    private val streamingSubtitleStore: com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore,
    private val imageUrlProvider: ImageUrlProvider,
    private val downloadRepository: DownloadRepository,
    private val offlineRepository: OfflineRepository,
    private val offlinePlaybackFacade: OfflinePlaybackFacade,
    /**
     * The deep "playback source resolver" — single owner of the
     * completed-download predicate (the load spine's offline-resume
     * resolution, now on [SessionLoadPipeline], pivots onto
     * [com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver.resolveStartPositionTicks]
     * and [PlayerSessionManager] consumes
     * [com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver.resolveUsableDownload]).
     */
    private val playbackSourceResolver: com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver,
    /**
     * The consolidated series seasons/episodes snapshot. [resolveSeasons] and
     * [resolveEpisodes] delegate here (online and offline), so the player's
     * episode discovery shares the same single-flight + cache as the detail
     * screen and the download paths. Offline-ness is read per-session from
     * [playerSessionManager] and passed as a parameter.
     */
    private val episodeCatalogue: com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue,
    private val itemPlaybackPreferenceRepository: ItemPlaybackPreferenceRepository,
    /**
     * The thirteen datastore stores bundled at construction — see [PlayerStores]
     * (home's HomeStores move: a new store dependency widens the bundle and the
     * DI definitions, not this constructor).
     */
    private val stores: PlayerStores,
    /**
     * Media-session factory seam: replaces the former legacy
     * `PlaybackSessionManager` slot — that type is now captured inside the
     * androidMain factory alongside the Context the controller needs.
     */
    private val mediaSessionFactory: VideoMediaSessionFactory,
    // Public: the screen's cast UI (route button, disconnect handler) needs the
    // manager directly; every playback-side use stays private above.:
    // typed as the commonMain seam interface — the androidMain screen reaches
    // the full legacy surface through the `androidCastManager` extension.
    val castManager: CastManager,
    private val jellyfinRemotePlayCastStrategy: JellyfinRemotePlayCastStrategy,
    private val syncPlayManager: SyncPlayManager,
    private val adaptiveBitrateManager: AdaptiveBitrateManager,
    private val networkMonitor: NetworkMonitor,
    private val activePlayerController: ActivePlayerController,
    val playerLifecycleManager: PlayerLifecycleManager,
    val pipController: PipController,
    val videoMiniPlayerState: VideoMiniPlayerState,
    private val sleepCountdown: SleepCountdown,
    private val userMessageBus: PlayerVideoMessageBus,
    private val playerEngineFactory: com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory,
    // Public for the screen: the zoom-safe Compose overlay consumes the same
    // singleton (LRU typeface cache + startup prewarm) instead of building a
    // private FontProvider per composition.
    val fontProvider: FontProvider,
    private val savedStateHandle: SavedStateHandle,
    private val subtitlePreviewRepository: com.raulshma.jellyplay.feature.player.video.subtitle.SubtitlePreviewRepository,
    private val userDataMutator: com.raulshma.jellyplay.core.data.repository.UserDataMutator,
    private val offlineModeManager: com.raulshma.jellyplay.core.data.offline.OfflineModeManager,
    /**
     * The app-wide now-playing seam (feature 4.2): the session manager
     * publishes loads through it; this VM owns the end/stop events
     * ([NowPlayingReporter.markEnded] on the session's PlaybackEnded,
     * [NowPlayingReporter.clear] on the full release — NOT on the per-item
     * re-initialization, where the session manager's release also runs).
     */
    private val nowPlayingReporter: com.raulshma.jellyplay.core.data.playback.NowPlayingReporter,
) : JellyPlayViewModel() {

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
    // up to 4 Hz while controls are visible. Previously they were folded into
    // the ~60-field [VideoPlayerUiState], so every tick invalidated the entire
    // VideoPlayerScreen body. They now live on dedicated StateFlows and are
    // collected only inside the leaf composables that render them
    // (PlayerControls seek bar, VideoStatsOverlay). The remaining uiState is
    // thereby reduced to a low-frequency stream.
    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    // (The former bufferedPositionMs display flow died in the X1a dead-surface
    // cut: the seek bar and the stats overlay consume the multi-band
    // [bufferedRanges] — the scalar's only readers — and the reporter's
    // buffered-position callback parameter had no remaining consumer.)

    /**
     * Multi-band buffered surface, bridged from the active engine's
     * own [com.raulshma.jellyplay.feature.player.video.engine.MediaEngine.bufferedRanges]
     * — the seek bar's shaded bands and the stats overlay's ranges readout
     * collect this leaf-side like [currentPositionMs]; it deliberately does
     * NOT ride the position-update callback (that scalar path is
     * reporter-owned) and re-subscribes per engine via [engineFlow]. `lazy`
     * because [PlayerSessionManager] is constructed further down the class
     * body; the WhileSubscribed stateIn collects nothing until first read.
     */
    val bufferedRanges: StateFlow<List<LongRange>> by lazy {
        stateIn(
            initial = emptyList(),
            flow = playerSessionManager.engineFlow.flatMapLatest { it?.bufferedRanges ?: emptyFlow() },
        )
    }

    private val _videoStats = MutableStateFlow(EngineVideoStats())
    val videoStats: StateFlow<EngineVideoStats> = _videoStats.asStateFlow()

    // ---------------------------------------------------------------------------

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
        ) { dur, proj -> proj.toSegmentInput(dur) },
    ) { pos, input ->
        computeOverlay(pos, input)
    },
    )

    private val _closePlayer = Channel<Unit>(Channel.BUFFERED)
    val closePlayer = _closePlayer.receiveAsFlow()

    /** Bumped per notice so same-type consecutive skips re-trigger the UI. */
    private var skippedSegmentNoticeSeq: Long = 0

    /** The auto-clear job backing [showSkippedSegmentNotice]'s ~2.5 s window. */
    private var skippedSegmentNoticeJob: Job? = null

    /**
     * Raises the ephemeral "Skipped …" caption on the uiState and
     * re-arms the auto-clear. A notice replacing a live one restarts the
     * window; the caption's [SkippedSegmentNotice.seq] makes the same-type
     * consecutive case visible to the collector.
     */
    private fun showSkippedSegmentNotice(segmentType: MediaSegmentType) {
        _uiState.update {
            it.copy(
                skippedSegmentNotice = SkippedSegmentNotice(
                    segmentType = segmentType,
                    seq = ++skippedSegmentNoticeSeq,
                ),
            )
        }
        skippedSegmentNoticeJob?.cancel()
        skippedSegmentNoticeJob = launch {
            delay(SKIPPED_SEGMENT_NOTICE_MS)
            _uiState.update { current ->
                // Only clear OUR notice — a newer one may have replaced it.
                if (current.skippedSegmentNotice?.seq == skippedSegmentNoticeSeq) {
                    current.copy(skippedSegmentNotice = null)
                } else {
                    current
                }
            }
        }
    }

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

    // The session manager (and every other collaborator) is constructed once,
    // inside [wiring] — see the Collaborator graph note above.

    // The former `trickplayManager` / `trickplayPreparation` / `subtitles` /
    // `sleepTimer` / `abRepeat` / `cast` / `settingsProjector` /
    // `mediaContentProjector` / `mediaDetailProjection` /
    // `mediaSessionController` / `playerAudioLifecycle` constructions moved
    // into [PlayerWiring] phase 1 verbatim; the screen-facing slices stay
    // exposed below as read-only aliases of the wiring's instances.

    // ── Collaborator graph ───────────────────────────────────────────────────
    // The session stack, the controllers and the whole collaborator graph are
    // constructed and armed by [wiring] ([PlayerWiring], the two-phase
    // composition builder): its phase 1 constructs every collaborator once
    // with late-bound back-references breaking the six mutual-recursion
    // construction cycles (the former load-bearing explicit-type
    // annotations), and `arm()` — called from `init` below — binds those
    // back-references and registers the collectors this class's former init
    // block launched inline (the engine-event mirrors, the session-event
    // forwarder, the aggregate-prefs fan-out, the engine-attach
    // choreography). This VM keeps only the state holders, the [onEvent]
    // funnel + its handlers, and the expose-only flows.

    // Cinema Mode sequencing (cinemaIntroContext + loadCinemaIntro /
    // advanceCinemaIntro + the beginCinemaMode entry point) moved into
    // PlaybackSession at B4 — the uiState cinemaIntroState write flows through
    // the session's setCinemaIntroState seam. This VM still reads
    // playbackSession.cinemaIntroContext (end-of-media policy, skipIntro, the
    // per-item video-effects persist gate) and clears it in
    // releaseInternalsVmPart at exactly its old slot.

    /** The subtitle slice the screen drives directly (built by [PlayerWiring]). */
    internal val subtitles: SubtitleManager get() = wiring.subtitles
    internal val sleepTimer: SleepTimerController get() = wiring.sleepTimer
    internal val abRepeat: AbRepeatController get() = wiring.abRepeat
    internal val cast: PlayerCastController get() = wiring.cast

    private val _passOutEvents = Channel<String>(Channel.BUFFERED)
    val passOutEvents: kotlinx.coroutines.flow.Flow<String> = _passOutEvents.receiveAsFlow()

    /**
     * The composition builder (see [PlayerWiring]'s KDoc for the two-phase
     * contract): its phase 1 constructs every collaborator ONCE with
     * late-bound back-references breaking the six mutual-recursion
     * construction cycles (the former load-bearing explicit-type
     * annotations), and its phase 2 — [PlayerWiring.arm], called from `init`
     * below — binds those back-references and registers the collectors this
     * class's former init block launched inline. The builder owns the
     * collaborator graph; this VM keeps the constructor dependencies, the
     * state holders, the [onEvent] funnel + its handlers and the
     * expose-only flows.
     *
     * [PlayerWiring.Host] is the funnel's mirror — the VM-owned behaviors
     * the wiring calls back into: the transport funnels
     * ([seekTo]/[seekByStep]/[routedPlay]/[resumePlayback]), the load funnel
     * ([initialize]), the session-policy dispatch
     * ([autoSkipSegment]/[onEndedWithNoNext]/[handlePlaybackEnded]) and the
     * lifecycle slices ([routeToRemotePlaySession] — the remote-play
     * strategy is VM-only, [releaseInternalsVmPart] with the
     * keepAcrossItems rebuild, [onItemHydrated], [release]). Implemented as
     * this private adapter object so the VM's public/internal member surface
     * — and with it the ownership ratchet — is unchanged; every override is
     * a one-line delegation to the private handler of the same name. The
     * seam is STORED while this class is still under construction but only
     * INVOKED from `arm()` onward, so no override can observe an
     * uninitialized VM field — the same lazy-callback discipline the wiring
     * lambdas always applied.
     */
    private val wiring = PlayerWiring(
        scope = scope,
        platform = platform,
        mediaRepository = mediaRepository,
        mediaExtrasReads = mediaExtrasReads,
        lyricsRepository = lyricsRepository,
        playbackRepository = playbackRepository,
        playbackIdentity = playbackIdentity,
        subtitleProviderRepository = subtitleProviderRepository,
        streamingSubtitleStore = streamingSubtitleStore,
        imageUrlProvider = imageUrlProvider,
        downloadRepository = downloadRepository,
        offlineRepository = offlineRepository,
        offlinePlaybackFacade = offlinePlaybackFacade,
        playbackSourceResolver = playbackSourceResolver,
        episodeCatalogue = episodeCatalogue,
        itemPlaybackPreferenceRepository = itemPlaybackPreferenceRepository,
        stores = stores,
        mediaSessionFactory = mediaSessionFactory,
        castManager = castManager,
        syncPlayManager = syncPlayManager,
        adaptiveBitrateManager = adaptiveBitrateManager,
        networkMonitor = networkMonitor,
        activePlayerController = activePlayerController,
        playerLifecycleManager = playerLifecycleManager,
        pipController = pipController,
        videoMiniPlayerState = videoMiniPlayerState,
        sleepCountdown = sleepCountdown,
        userMessageBus = userMessageBus,
        playerEngineFactory = playerEngineFactory,
        fontProvider = fontProvider,
        savedStateHandle = savedStateHandle,
        subtitlePreviewRepository = subtitlePreviewRepository,
        userDataMutator = userDataMutator,
        offlineModeManager = offlineModeManager,
        nowPlayingReporter = nowPlayingReporter,
        uiState = _uiState,
        positionMs = _currentPositionMs,
        durationMs = _durationMs,
        videoStats = _videoStats,
        resumeReminder = _resumeReminder,
        closePlayer = _closePlayer,
        passOutEvents = _passOutEvents,
        host = object : PlayerWiring.Host {
            override fun initialize(itemId: String, mediaSourceId: String?, startPositionTicks: Long) {
                this@VideoPlayerViewModel.initialize(itemId, mediaSourceId, startPositionTicks)
            }

            override fun seekTo(positionMs: Long, userInitiated: Boolean) {
                this@VideoPlayerViewModel.seekTo(positionMs, userInitiated)
            }

            override fun seekByStep(direction: Int) {
                this@VideoPlayerViewModel.seekByStep(direction)
            }

            override fun routedPlay(play: Boolean) {
                this@VideoPlayerViewModel.routedPlay(play)
            }

            override fun resumePlayback() {
                this@VideoPlayerViewModel.resumePlayback()
            }

            override fun applyResumeSkip(engine: MediaEngine) {
                this@VideoPlayerViewModel.applyResumeSkip(engine)
            }

            override fun autoSkipSegment(segment: MediaSegment) {
                this@VideoPlayerViewModel.autoSkipSegment(segment)
            }

            override fun onEndedWithNoNext() {
                this@VideoPlayerViewModel.onEndedWithNoNext()
            }

            override fun handlePlaybackEnded() {
                this@VideoPlayerViewModel.handlePlaybackEnded()
            }

            override fun routeToRemotePlaySession(request: LoadRequest): Boolean =
                this@VideoPlayerViewModel.routeToRemotePlaySession(
                    itemId = request.itemId,
                    mediaSourceId = request.mediaSourceId,
                    startPositionTicks = request.startPositionTicks,
                    subtitleStreamIndex = request.subtitleStreamIndex,
                    audioStreamIndex = request.audioStreamIndex,
                )

            override fun releaseInternalsVmPart() {
                this@VideoPlayerViewModel.releaseInternalsVmPart()
            }

            override fun onItemHydrated(itemId: String, hydratedAgg: VideoPlayerAggregate) {
                this@VideoPlayerViewModel.onItemHydrated(itemId, hydratedAgg)
            }

            override fun release() {
                this@VideoPlayerViewModel.release()
            }
        },
    )

    // Private aliases: the funnel handlers below read the collaborator graph
    // through the builder. One line each — the graph itself lives in
    // [PlayerWiring].
    private val playerSessionManager get() = wiring.playerSessionManager
    private val playbackSession get() = wiring.playbackSession
    private val episodeContinuation get() = wiring.episodeContinuation
    private val trackSelectionHelper get() = wiring.trackSelectionHelper
    private val autoplayController get() = wiring.autoplayController
    private val stillWatching get() = wiring.stillWatching
    private val trickplayManager get() = wiring.trickplayManager
    private val pipTransport get() = wiring.pipTransport
    private val mediaSessionController get() = wiring.mediaSessionController
    private val playbackPreferenceWriter get() = wiring.playbackPreferenceWriter

    /**
     * Runs the builder's phase 2: binds the late-bound cycle slots, then
     * registers every collector the former inline init block launched (the
     * engine-event mirrors FIRST, the session-event forwarder, the PiP
     * transport registration + dismissal discharge, the aggregate-prefs and
     * metered-network collectors, the SyncPlay start + session mirror, the
     * becoming-noisy registration, the session-state fold, the preference
     * resolver, and the engine-attach choreography LAST) — in the same
     * order, so subscription timing relative to the first session/engine
     * emission is unchanged.
     */
    init {
        // A user-driven player open acquires the cast consumer; released in
        // the full teardown ([wiring.performRelease]).
        castManager.acquireConsumer()
        wiring.arm()
    }

    /**
     * The overlay's state surface; `null` = hidden. The screen collects this
     * at the overlay tier (thin alias over the wiring's controller).
     */
    val stillWatchingPrompt: StateFlow<StillWatchingPromptState?>
        get() = wiring.stillWatching.prompt

    /**
     * The single command funnel (the AudioPlayerUiEvent / AudioPlayerViewModel
     * `.onEvent` precedent): every user intent the screen expresses arrives as
     * a [VideoPlayerUiEvent] and routes once here to a private handler — the
     * former per-action public funs. The public surface beyond the funnel is
     * the state flows, the sync getters, the result-returning queries
     * ([getImageUrl], [loadTrickplayThumbnail], [verifyPlayerLockPin],
     * [useDownloadedSubtitle]), [release] (the screen's dispose hook) and the
     * internal controller slices the screen drives directly
     * (cast/syncPlay/subtitles/sleepTimer/abRepeat/effects/render) — pinned by
     * VideoPlayerViewModelOwnershipTest.
     */
    fun onEvent(event: VideoPlayerUiEvent) {
        when (event) {
            is VideoPlayerUiEvent.Initialize -> {
                // A user-driven player open: the still-watching streak starts
                // fresh (auto-advance loads bypass onEvent and keep it).
                autoplayController.onUserInteraction()
                initialize(
                    itemId = event.itemId,
                    mediaSourceId = event.mediaSourceId,
                    startPositionTicks = event.startPositionTicks,
                    subtitleStreamIndex = event.subtitleStreamIndex,
                    audioStreamIndex = event.audioStreamIndex,
                )
            }
            is VideoPlayerUiEvent.PlayEpisode -> {
                // Manual episode navigation resets the still-watching streak.
                autoplayController.onUserInteraction()
                episodeContinuation.playEpisode(event.episodeId, event.startPositionTicks)
            }
            is VideoPlayerUiEvent.RestartPlayback -> restartPlayback()
            is VideoPlayerUiEvent.RetryPlayback -> retryPlayback()
            is VideoPlayerUiEvent.RetryWithEngine -> retryWithEngine(event.playerType)
            is VideoPlayerUiEvent.DismissPlaybackError -> dismissPlaybackError()
            is VideoPlayerUiEvent.SetControlsVisible -> setControlsVisible(event.visible)
            is VideoPlayerUiEvent.InstallUserFont -> subtitleStyleController.installUserFont(event.uri)
            is VideoPlayerUiEvent.ReattachFromBackgroundCast -> reattachFromBackgroundCast()
            is VideoPlayerUiEvent.DetachForBackgroundCast -> detachForBackgroundCast()
            is VideoPlayerUiEvent.SetScreenLocked -> setScreenLocked(event.locked)
            is VideoPlayerUiEvent.TransportPlay -> routedPlay(event.play)
            is VideoPlayerUiEvent.SeekTo -> routedSeek(event.positionMs)
            is VideoPlayerUiEvent.SeekByStep -> seekByStep(event.direction)
            is VideoPlayerUiEvent.ToggleMute -> toggleMute()
            is VideoPlayerUiEvent.UserInteraction -> onUserInteraction()
            is VideoPlayerUiEvent.StartHoldSpeed -> startHoldSpeed()
            is VideoPlayerUiEvent.StopHoldSpeed -> stopHoldSpeed()
            is VideoPlayerUiEvent.ApplySubtitleStyle -> subtitleStyleController.applySubtitleStyle()
            is VideoPlayerUiEvent.UpdatePipSourceRect ->
                pipTransport.updatePipSourceRect(event.left, event.top, event.right, event.bottom)
            is VideoPlayerUiEvent.PlayPreviousEpisode -> {
                // Manual navigation: the still-watching streak restarts.
                autoplayController.onUserInteraction()
                episodeContinuation.playPreviousEpisode()
            }
            is VideoPlayerUiEvent.PlayNextEpisode -> {
                // Manual navigation: the still-watching streak restarts.
                autoplayController.onUserInteraction()
                episodeContinuation.playNextEpisode()
            }
            is VideoPlayerUiEvent.MarkWatchedAndSkip -> {
                // A deliberate user advance — resets the still-watching streak.
                autoplayController.onUserInteraction()
                episodeContinuation.markWatchedAndSkip()
            }
            is VideoPlayerUiEvent.MarkUnwatchedAndQuit -> episodeContinuation.markUnwatchedAndQuit()
            is VideoPlayerUiEvent.ToggleDialogueBoost -> toggleDialogueBoost()
            is VideoPlayerUiEvent.SetDialogueBoostStrength -> setDialogueBoostStrength(event.strength)
            is VideoPlayerUiEvent.ToggleVideoStats -> toggleVideoStats()
            is VideoPlayerUiEvent.ToggleAudioOnly -> toggleAudioOnly()
            is VideoPlayerUiEvent.SetSubtitleDelay -> setSubtitleDelay(event.ms)
            is VideoPlayerUiEvent.SkipIntro -> skipIntro()
            is VideoPlayerUiEvent.SkipSegment -> skipSegment(event.segment)
            is VideoPlayerUiEvent.SaveBrightness -> saveBrightness(event.level)
            is VideoPlayerUiEvent.SetPlaybackSpeed -> setPlaybackSpeed(event.speed)
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
            is VideoPlayerUiEvent.SetAspectRatio -> setAspectRatio(event.ratio)
            is VideoPlayerUiEvent.SetSubtitleStyle -> subtitleStyleController.setStyle(event.style)
            is VideoPlayerUiEvent.SetPlaybackMode -> setPlaybackMode(event.mode)
            is VideoPlayerUiEvent.SetStreamingQuality -> setStreamingQuality(event.quality)
            is VideoPlayerUiEvent.SetAdaptiveBitrateEnabled -> setAdaptiveBitrateEnabled(event.enabled)
            is VideoPlayerUiEvent.SetVideoEffects -> setVideoEffects(event.effects)
            is VideoPlayerUiEvent.SetRenderShaderPack -> setRenderShaderPack(event.pack, event.persist)
            is VideoPlayerUiEvent.SetRenderToneMapping -> setRenderToneMapping(event.mapping, event.persist)
            is VideoPlayerUiEvent.SetRenderQuality -> setRenderQuality(event.quality)
            is VideoPlayerUiEvent.ClearRenderOverride -> clearRenderOverride()
            is VideoPlayerUiEvent.CycleDeinterlace -> cycleDeinterlace()
            is VideoPlayerUiEvent.CancelAutoplay -> episodeContinuation.cancelAutoplay()
            is VideoPlayerUiEvent.StillWatchingContinue -> stillWatching.onContinue()
            is VideoPlayerUiEvent.StillWatchingStop -> stillWatching.onStop()
            is VideoPlayerUiEvent.StillWatchingTick -> stillWatching.onTick()
            is VideoPlayerUiEvent.SetVideoAutoplayNext -> setVideoAutoplayNext(event.enabled)
            is VideoPlayerUiEvent.SetSyncPlayRepeatMode -> setSyncPlayRepeatMode(event.mode)
            is VideoPlayerUiEvent.SetSyncPlayShuffleMode -> setSyncPlayShuffleMode(event.mode)
            is VideoPlayerUiEvent.LoadSeasonEpisodes -> episodeContinuation.loadSeason(event.seasonId)
        }
    }

    /** Resets the pass-out interaction clock (delegates to the coordinator). */
    private fun onUserInteraction() {
        playbackSession.engineEventCoordinator.onUserInteraction()
    }

    /**
     * Snapshot of [uiState] with the live playback position/duration injected
     * from the dedicated high-frequency flows. Use this anywhere that
     * needs the position-aware derived properties ([activeSegment],
     * [shouldShowUpNext], …) so the logic does not depend on the (now stale)
     * `currentPosition`/`duration` fields stored on uiState itself.
     */
    private fun positionAwareState(): VideoPlayerUiState = _uiState.value.copy(
        currentPosition = _currentPositionMs.value,
        duration = _durationMs.value,
    )

    /**
     * The ONE position-aware snapshot fold behind the segment-skip dispatch
     * ([dispatchSegmentSkip]): every fact the skip ladder consults, read from
     * a single [positionAwareState] snapshot so the active segment and its end
     * ticks can never come from different reads (the pairing rule
     * [SegmentDispatchFacts] exists to enforce). Lives VM-side on purpose —
     * it reads the position-aware ui state, which no migrated controller may
     * touch.
     */
    private fun segmentDispatchFacts(): SegmentDispatchFacts {
        val state = positionAwareState()
        val seg = state.activeSegment
        return SegmentDispatchFacts(
            cinemaIntroActive = state.cinemaIntroState != null,
            isOutroNearEnd = state.isOutroNearEnd,
            canSkipToNext = autoplayController.canSkipToNext(state.episodes.nextEpisode),
            segments = SegmentSnapshot(
                activeType = seg?.type,
                activeEndTicks = seg?.let { state.segmentEndTicks(it) },
                introEndTicks = state.introSegmentEndTicks,
                creditEndTicks = state.creditSegmentEndTicks,
            ),
        )
    }

    /**
     * The segment-skip dispatch halves (folded back from
     * SegmentDispatchController, whose three funs + executor were the whole
     * story): the skip buttons ([skipIntro] / [skipSegment]) and the
     * position-tick auto-skip arm ([autoSkipSegment]). The DECISION halves
     * stay in SegmentSkipPolicy.kt ([segmentSkipTarget] /
     * [segmentEndSeekTarget] / [SegmentSnapshot]) — only the dispatch lives
     * here: the facts snapshot comes from [segmentDispatchFacts] (ONE
     * position-aware read, above), the per-segment end-ticks resolution
     * against this VM's segment list, and each effect — the seek funnel, the
     * next-episode load, the session's cinema advance, the "Skipped …"
     * notice — routes onto its owning collaborator directly.
     */

    /** The "Skip Intro" button / menu arm. */
    private fun skipIntro() {
        dispatchSegmentSkip(SegmentSkipKind.INTRO)
    }

    /**
     * The overlay button press for the active segment (user-initiated — the
     * seek itself is the feedback, so no confirmation notice).
     */
    private fun skipSegment(segment: MediaSegment) {
        executeSegmentSkip(segmentEndSeekTarget(_uiState.value.segmentEndTicks(segment)), userInitiated = true)
    }

    /**
     * The position-tick auto-skip arm ([PlaybackProgressReporter]'s
     * `onAutoSkip`): not user-initiated (never clamped) and confirmed with
     * the "Skipped …" notice so an invisible automatic jump is explained.
     */
    private fun autoSkipSegment(segment: MediaSegment) {
        executeSegmentSkip(segmentEndSeekTarget(_uiState.value.segmentEndTicks(segment)), userInitiated = false)
        showSkippedSegmentNotice(segment.type)
    }

    /**
     * Shared dispatch for the skip buttons: snapshot the position-aware facts,
     * reduce them to a [SegmentSkipTarget] via the pure policy in
     * SegmentSkipPolicy.kt, then execute the one-line effect. The active
     * segment's end ticks ride the snapshot ([SegmentSnapshot.activeEndTicks]
     * — resolved against the same read the active segment came from); the
     * policy sees only plain values.
     */
    private fun dispatchSegmentSkip(kind: SegmentSkipKind) {
        val facts = segmentDispatchFacts()
        executeSegmentSkip(
            segmentSkipTarget(
                kind = kind,
                cinemaIntroActive = facts.cinemaIntroActive,
                isOutroNearEnd = facts.isOutroNearEnd,
                canSkipToNext = facts.canSkipToNext,
                segments = facts.segments,
            ),
            userInitiated = true,
        )
    }

    private fun executeSegmentSkip(target: SegmentSkipTarget, userInitiated: Boolean) {
        when (target) {
            is SegmentSkipTarget.SeekToPosition -> seekTo(target.positionMs, userInitiated)
            SegmentSkipTarget.SkipToNextEpisode -> episodeContinuation.playNextEpisode()
            SegmentSkipTarget.AdvanceCinemaIntro -> playbackSession.advanceCinemaIntro()
            SegmentSkipTarget.None -> Unit
        }
    }

    /**
     * The local-engine seek arm of [routedSeek]. [userInitiated] marks the
     * paths a human drove — the seek bar / gesture commit, the step buttons /
     * keyboard / D-pad (through [seekByStep]), restart — and only those pay
     * attention to the `skipSegmentsOnSeek` clamp (a target landing strictly
     * inside an AUTO_SKIP segment is pulled to the segment's end, with the
     * "Skipped …" notice). Internal, app-driven seeks pass `false` and are
     * never touched: the auto-skip execution, the SyncPlay position sync and
     * the resume-skip. The A-B repeat loop seeks the engine directly
     * (bypassing this funnel), so nothing there can be clamped by accident
     * either.
     *
     * The gesture path only commits on release — scrub preview writes the
     * display flows, never this method — so the clamp sees exactly one final
     * target per gesture (the SegmentSeekClamp risk note).
     */
    private fun seekTo(positionMs: Long, userInitiated: Boolean = true) {
        // Seek latches + the process-death position snapshot (via the
        // session's position store) + the coalesced offline-mirror write are
        // session-owned since B3; the display write and the engine command
        // stay here.
        if (userInitiated) {
            // A user-initiated seek resets the pass-out interaction clock and
            // feeds the still-watching counter through the same signal.
            playbackSession.engineEventCoordinator.onUserInteraction()
        }
        val effectiveTargetMs = if (userInitiated) {
            resolveForwardSeekSegmentClamp(
                targetMs = positionMs,
                currentPositionMs = _currentPositionMs.value,
                segments = _uiState.value.segmentState.segments,
                segmentBehaviors = _uiState.value.segmentState.segmentBehaviors,
                durationMs = _durationMs.value,
                enabled = wiring.cachedAggregate.videoPlayer.skipSegmentsOnSeek,
            )?.also { showSkippedSegmentNotice(it.segmentType) }?.adjustedTargetMs ?: positionMs
        } else {
            positionMs
        }
        playbackSession.seekPersisted(effectiveTargetMs)
        // Update the dedicated position flow so the seek bar reflects the
        // new position immediately; uiState is no longer the source of truth.
        _currentPositionMs.value = effectiveTargetMs
        playerSessionManager.engine?.seekTo(effectiveTargetMs)
    }

    /**
     * The seek routing funnel (the [routedPlay] companion): SyncPlay group
     * first, cast receiver second, local engine last — every absolute seek
     * lands here (the [VideoPlayerUiEvent.SeekTo] funnel arm and [seekByStep]'s
     * step target alike), so the transports can never diverge. Routing reads
     * are LIVE (`_uiState`'s SyncPlay mirror + the cast controller's
     * connection flow value) — the same seam [routedPlay] reads. The
     * SyncPlay/cast arms deliberately bypass [seekTo]: the group owns the
     * position (the engine seek + group broadcast happen inside
     * SyncPlayBridge.seekTo) and the receiver owns the cast timeline, so the
     * `skipSegmentsOnSeek` clamp and the local position-flow write stay
     * local-arm only.
     */
    private fun routedSeek(positionMs: Long) {
        when {
            _uiState.value.isInSyncPlaySession -> syncPlay.seekTo(positionMs)
            cast.isConnectedFlow.value -> cast.castSeekTo(positionMs)
            else -> seekTo(positionMs)
        }
    }

    /**
     * The single discrete skip-step owner: the screen's skip buttons /
     * keyboard / D-pad commits and the PiP transport's SKIP actions all
     * reduce to this funnel (C3). The clamp math stays in
     * [stepSeekTargetMs] — floor at 0 on the back path, cap at the engine's
     * duration on the forward path (skipped for live streams with no
     * resolved duration). The seek itself routes through [routedSeek] — the
     * ONE SyncPlay → cast → local ladder — so PiP steps and on-screen steps
     * can never diverge. Gesture / hold-speed paths do NOT
     * go through here. [direction] < 0 steps back, anything else forward.
     */
    private fun seekByStep(direction: Int) {
        // User-initiated step: resets the interaction clock + the
        // still-watching counter (the SyncPlay/cast arms bypass seekTo).
        playbackSession.engineEventCoordinator.onUserInteraction()
        val engine = playerSessionManager.engine
        routedSeek(
            stepSeekTargetMs(
                direction = direction,
                currentPositionMs = engine?.currentPositionMs ?: 0L,
                stepMs = _uiState.value.gestures.seekDurationMs,
                durationMs = engine?.durationMs ?: 0L,
            ),
        )
    }

    /**
     * Restarts the current item from the beginning. Backs the
     * "Restart" action on the resume-reminder chip shown when playback resumes
     * from a saved position.
     */
    private fun restartPlayback() {
        seekTo(0L)
    }

    private fun resumePlayback() {
        val engine = playerSessionManager.engine ?: return
        // The is-playing guard here is DELIBERATE and its absence from the
        // audio-focus regain path ([playerAudioLifecycle.onRegain], which
        // applies the same skip unguarded) is equally deliberate: resume is
        // the play/pause toggle's play arm, so it can fire while already
        // playing — re-seeking then would scrub an active stream — whereas a
        // focus regain only follows a transient loss, where the skip is
        // always wanted. The clamp math itself is shared via
        // [applyResumeSkip] / [resumeSkipTargetMs] (PlaybackVolumePolicy
        // style: math shared, per-site divergence declared).
        if (!engine.isPlaying.value) {
            applyResumeSkip(engine)
        }
        engine.play()
    }

    /**
     * The `videoSkipBackOnResumeMs` resume-skip funnel: rewinds the engine's
     * current position by the configured skip through
     * [resumeSkipTargetMs] (floor at zero; a non-positive skip disables the
     * feature and seeks nothing). Shared by the audio-focus regain path
     * ([playerAudioLifecycle.onRegain]) and [resumePlayback] — the two former
     * hand-copied bodies. Each call site keeps its OWN guard; the divergence
     * is declared at both (see [resumePlayback]).
     */
    private fun applyResumeSkip(engine: MediaEngine) {
        val skipMs = stores.aggregateStore.aggregate.value.videoPlayer.videoSkipBackOnResumeMs
        if (skipMs <= 0L) return
        // Not user-initiated: an app-driven rewind (focus regain / resume),
        // so the skip-on-seek clamp must not look at it.
        seekTo(
            resumeSkipTargetMs(currentPositionMs = engine.currentPositionMs, skipMs = skipMs),
            userInitiated = false,
        )
    }

    // getReportPositionMs moved into PlaybackSession at B3 (seek latches +
    // engine position are session-owned).

    // The former `progressReporter` construction (whose getPlaySessionId /
    // onPositionPersisted lambdas were the cycle-2 edge to `playbackSession`)
    // moved into [PlayerWiring] phase 1 — the session id and persist reads go
    // through the wiring's late-bound playbackSession slot, so the
    // load-bearing explicit type is gone with the cycle.

    /** The SyncPlay slice the screen drives directly (built by [PlayerWiring]). */
    internal val syncPlay: SyncPlayBridge get() = wiring.syncPlay

    // ── Session load pipeline ────────────────────────────────────────────────
    // The pipeline owns the ORDER of the load stages that the initialize path
    // used to inline; the VM-bound outputs and hook bodies are implemented by
    // [PlayerWiring] (its SessionLoadOutputs + SessionLifecycleHooks
    // implementations, written at the collaborators' construction sites when
    // VideoSessionHost — the former pass-through layer — was deleted). The
    // three stage bodies with real logic moved INTO SessionLoadPipeline
    // (fetchMediaSegments / shouldAttemptCinemaMode / restoreRememberedMuted)
    // and the server start report into PlaybackSession (beside its
    // stop-report twin) — see those classes' KDoc.

    // ── Session load pipeline ────────────────────────────────────────────────
    // The pipeline owns the ORDER of the load stages that the initialize path
    // used to inline; the host below owns the uiState writes and the
    // VM-bound bodies (controllers, session bookkeeping, reports).
    // The former `sessionLoadPipeline` / `sessionPositionStore` / `releaseScope`
    // / `playbackSession` / `episodeContinuation` constructions moved into
    // [PlayerWiring] phase 1 verbatim. The pipeline's `outputs`/`hooks` are the
    // wiring builder itself (this class's session-facing seams became builder
    // methods), which is what lets the whole VideoSessionHost pass-through
    // layer die: the cycles it existed to survive are broken by the builder's
    // late-bound slots instead. The VM reaches the moved collaborators through
    // the private aliases declared beside [wiring].

    /** True while a next-episode advance is in flight and unsettled (#146). */
    val isNextEpisodeLoading: StateFlow<Boolean> get() = episodeContinuation.isNextEpisodeLoading

    // The segment-skip dispatch glue (the former `segmentDispatch`
    // SegmentDispatchController property) folded back into this VM — the
    // skipIntro / skipSegment / autoSkipSegment arms + the shared executor
    // now live beside [segmentDispatchFacts]; the pure decision halves stay
    // in SegmentSkipPolicy.kt (SegmentSkipPolicyTest pins those).

    // markWatchedAndSkip / markUnwatchedAndQuit (the overflow mark-and-then
    // orchestration) moved into EpisodeContinuationController — the onEvent
    // arms above route to it directly.
    //
    // The engine collector's per-engine collection job moved into
    // [engineAttachController] with the choreography that cancels and
    // re-arms it.

    // The subtitle-delay apply job (subtitleDelayApplyJob) moved into
    // SubtitleStyleController with the debounce it backs (A7).

    // handleSmartDownloadCleanup (the smart-download auto-remove behind the
    // watched-threshold callback) moved into EpisodeContinuationController;
    // the reporter's callback above forwards into it.

    val hapticsEnabled: Boolean get() = stores.appearance.appearance.value.hapticsEnabled

    /**
     * The incognito gate the overflow's "Mark unwatched & exit" item hides
     * behind — same read pattern as [hapticsEnabled]: a rare-flip
     * pref read at render time, not a dedicated flow.
     */
    val incognitoModeEnabled: Boolean get() = wiring.cachedAggregate.videoPlayer.incognitoModeEnabled

    // The former init-order comment (trackSelectionHelper had to be declared
    // before `init` because init's engine-flow collector called into it) is
    // superseded: every collector is now registered in [PlayerWiring.arm],
    // which cannot run before the builder's phase 1 has constructed the whole
    // graph. The builder-level order pin lives in PlayerWiringCompositionTest.

    // The former `playbackPreferenceResolver` / `trackSelectionHelper` /
    // `playbackPreferenceWriter` / `render` constructions moved into
    // [PlayerWiring] phase 1 verbatim. Cycle 5 (the helper's
    // persistRememberedTrack lambda vs the writer's onPreferencesChanged
    // read-back) is broken by the wiring's late-bound playbackPreferenceWriter
    // slot, so the writer's load-bearing explicit type is gone with the cycle.
    // The stream-change reload lambda reaches the session through the wiring's
    // late-bound playbackSession slot (the former VM method-indirection).

    /** The Rendering-sheet controller the screen drives directly (built by [PlayerWiring]). */
    internal val render: RenderControls get() = wiring.render

    /**
     * The session-scoped render state (sheet + deinterlace cycle), read-only
     * alias of [RenderControls.state]. Internal: the player screen reads it
     * to render the Rendering sheet's pickers and the deinterlace menu label;
     * every WRITE routes through [render] so the choreography has one owner.
     */
    internal val sessionRender: SessionRenderState
        get() = render.state

    // The former `subtitlePreview` / `subtitleStyleController` / `effects` /
    // `engineAttachController` constructions moved into [PlayerWiring] phase 1
    // verbatim. Cycle 6 (effects' syncConfig lambda vs engineConfigSync's
    // getEffectsState read-back — plus the render/style-controller/prefs
    // triggers) is broken by the wiring's late-bound engineConfigSync slot, so
    // the effects controller's load-bearing explicit type is gone with the
    // cycle. The engine-attach choreography is registered by [PlayerWiring.arm]
    // at the same point the former init block ran it; its order stays
    // jvmTest-pinned in EngineAttachControllerTest.

    /** The cue-preview controller the screen drives directly (built by [PlayerWiring]). */
    internal val subtitlePreview: SubtitlePreviewController get() = wiring.subtitlePreview
    internal val subtitleStyleController: SubtitleStyleController get() = wiring.subtitleStyleController
    internal val effects: VideoEffectsController get() = wiring.effects

    // ── Controller slice handles ────────────────────────────────────────────
    // Each migrated controller owns its slice as a MutableStateFlow and is
    // exposed directly; the screen collects `handle.state` (and any
    // per-slice streams) at the leaf composables that render it, and calls
    // commands on the handle directly. The residual [uiState] keeps only
    // session + prefs-mirror state. The ViewModel does NOT relay slice
    // commands: it keeps only real orchestration (load/session/lifecycle and
    // cross-controller flows like background-cast detach).

    val trackState: StateFlow<com.raulshma.jellyplay.feature.player.video.state.TrackState>
        get() = trackSelectionHelper.state

    // The former `prefsFanout` construction (PlayerPrefsFanout, the
    // aggregate-prefs collector's side-effecting half) moved into [PlayerWiring]
    // phase 1 verbatim; [PlayerWiring.arm] collects the aggregate store and
    // drives it exactly where the former init block did.


    // The former `startEngineEventCoordinatorOutputs` (the engine-event MIRROR
    // collectors: play/buffering uiState writes, the still-watching interaction
    // feed) moved into [PlayerWiring] — called once from `arm` and again
    // through the session's rearm callback, exactly as before.

    /**
     * The transport routing funnel (A1): SyncPlay group first, cast receiver
     * second, local engine last — the same order [routedSeek] routes seeks
     * and the screen's `doPlay`/`doPause` used to inline. The PiP window's
     * PLAY/PAUSE remote actions and the screen's play/pause controls both
     * land here, so they can never diverge again (PiP previously called
     * `engine.play()`/`engine.pause()` raw, which no-oped a SyncPlay group's
     * or cast receiver's pause state).
     *
     * While in a SyncPlay session BOTH arms toggle the group transport (the
     * screen's pre-fold behavior — the group, not this device, owns the play
     * state). The local play arm deliberately routes through
     * [resumePlayback] rather than a raw `engine.play()`: that imports the
     * configured `videoSkipBackOnResumeMs` resume-skip for PiP too, aligning
     * PiP's play button with the on-screen one (declared divergence from the
     * old raw PiP call, not an accident).
     *
     * Routing reads are LIVE (`_uiState`'s SyncPlay mirror + the cast
     * controller's connection flow value) — the same seam [routedSeek]
     * reads — so a cast connect/disconnect racing a recomposition can no
     * longer route a press to a stale target.
     */
    private fun routedPlay(play: Boolean) {
        // A user-initiated play/pause: reset the pass-out interaction clock
        // (previously only the resume transition reset it) — the episode
        // counter rides the same signal (feature 1.3).
        playbackSession.engineEventCoordinator.onUserInteraction()
        when {
            _uiState.value.isInSyncPlaySession -> syncPlay.togglePlayPause()
            cast.isConnectedFlow.value -> if (play) cast.castPlay() else cast.castPause()
            play -> resumePlayback()
            else -> playerSessionManager.engine?.pause()
        }
    }

    val playerEngineRef: com.raulshma.jellyplay.feature.player.video.engine.MediaEngine? get() = playerSessionManager.engine

    /**
     * Reactive engine handle for composition. The screen previously read
     * [playerEngineRef] as a plain property; Compose had no subscription, so a
     * engine swap only re-created the surface `AndroidView` if some unrelated
     * state happened to recompose. Exposing the session manager's StateFlow
     * and collecting it with `collectAsStateWithLifecycle` makes engine swaps
     * deterministic: `key(engine)` now always re-keys on a real swap.
     * [playerEngineRef] is retained for the one-shot lambda reads that want
     * the current value without subscribing.
     */
    val playerEngineFlow: StateFlow<com.raulshma.jellyplay.feature.player.video.engine.MediaEngine?>
        get() = playerSessionManager.engineFlow

    /**
     * Thin delegate to [PlaybackSession.initialize]: the only VM-side pre-bit
     * is the process-death start-ticks resolution, delegated to the session
     * (which owns the position store since B3 — this VM no longer touches the
     * handle). The session then owns the load sequence — synchronous prologue
     * hooks, routing early-returns, single-flight load tracking, and the
     * pipeline start.
     */
    private fun initialize(
        itemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
        subtitleStreamIndex: Int? = null,
        audioStreamIndex: Int? = null,
    ) {
        // Defensive: PipController is a process @Singleton whose one-shot event
        // flags outlive this Activity. A flag left set by an abnormally torn
        // down previous session must never greet the next load — the fresh
        // screen would react to it instantly and close (issue #145). Legitimate
        // in-flight dismiss flows end in release + close, never a new
        // initialize, so this cannot swallow a live signal.
        pipController.clearPipDismissed()
        pipController.consumeAutoExitPip()
        playbackSession.initialize(
            LoadRequest(
                itemId = itemId,
                mediaSourceId = mediaSourceId,
                startPositionTicks = playbackSession.resolveStartTicksAfterProcessDeath(itemId, startPositionTicks),
                allowCinemaMode = true,
                subtitleStreamIndex = subtitleStreamIndex,
                audioStreamIndex = audioStreamIndex,
            )
        )
    }

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

    // persistPlaybackPosition (throttled process-death persist via the
    // session's position store + offline-mirror write) and
    // scheduleCoalescedSeekProgress (the seek-scrub DB-write coalescer, still
    // launching on this VM's scope) moved into PlaybackSession at B3; the
    // reporter reaches them through its onPositionPersisted callback and
    // seekPersisted respectively.

    /**
     * Per-item hydration after `loadMedia` — the load spine's
     * `onItemHydrated` hook body, kept VM-side (the videoFx mirror write,
     * the engine-config rebuild nudge and the style controller are all VM
     * collaborators) and reached by [PlayerWiring]'s loadHooks bundle
     * through its [PlayerWiring.Host] seam. Restores the per-item persisted video
     * filters (if any) before playback kicks off, then routes the
     * subtitle-delay hydration through the style controller (A7): resolve
     * the effective delay for this item (per-item correction, else the
     * global default) — always applied so the previous item's in-memory
     * delay can't bleed into this one — and pushed to the engine immediately
     * so the AV-sync slider and the rendered cues stay in sync on resume.
     */
    private fun onItemHydrated(itemId: String, hydratedAgg: VideoPlayerAggregate) {
        val hydratedEffects = hydratedAgg.engine.videoEffectsByItem[itemId] ?: VideoEffectsConfig()
        if (_uiState.value.videoFx.videoEffects != hydratedEffects) {
            _uiState.update { it.copy(videoFx = it.videoFx.copy(videoEffects = hydratedEffects)) }
            wiring.markEngineConfigDirtyDebounced()
        }
        subtitleStyleController.onItemHydrated(hydratedAgg.subtitle, itemId)
    }

    /**
     * "Play On" routing early-return for the session's initialize path: if a
     * Jellyfin remote session is connected (via the Home FAB "Play On" entry),
     * send the video to that session instead of playing locally — mirrors
     * official Jellyfin clients where picking a device routes subsequent
     * plays to it. The Home "Play On" VM uses the same strategy instance
     * directly, so this connection is independent of the video player's own
     * CastManager cast state. Returns true when the load was routed away and
     * initialization is complete.
     *
     * Stays VM-side (reached through [PlayerWiring]'s
     * routeToRemotePlaySession lifecycle hook, which the VM's
     * [PlayerWiring.Host] adapter serves): it writes the ui state's
     * initializing flag and reads the remote-play strategy — both VM-owned.
     */
    private fun routeToRemotePlaySession(
        itemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
        subtitleStreamIndex: Int?,
        audioStreamIndex: Int?,
    ): Boolean {
        if (!jellyfinRemotePlayCastStrategy.isConnected.value) return false
        jellyfinRemotePlayCastStrategy.loadMedia(
            itemId = itemId,
            startPositionMs = startPositionTicks / 10_000,
            mediaSourceId = mediaSourceId,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
        )
        // Local player isn't loading — clear the flag so a later local UI
        // mount never shows a stuck loading screen.
        _uiState.update { it.copy(isInitializing = false) }
        return true
    }

    // restoreOrAllocatePlaySessionId (the process-death play-session id
    // restore behind the session's position store) moved into
    // PlaybackSession at B3 — initialize assigns the restored id at exactly
    // its old position in the sequence.

    // preSeedPlayhead (the resolved-ticks playhead display seed) and the
    // mini-player reclaim body (detail fetch, engine bind, media session,
    // tracking restart) moved into PlaybackSession at B4 — the display and
    // veil writes flow through the session's seedDisplayedPositionMs /
    // onMiniPlayerReclaimed seams, the hydration fetches through
    // hydrateReclaimedItem.

    private fun setScreenLocked(locked: Boolean) {
        _uiState.update { it.copy(isScreenLocked = locked) }
    }

    suspend fun verifyPlayerLockPin(pin: String): Boolean {
        return stores.security.verifyPinOffMainThread(pin)
    }

    private fun setPlaybackSpeed(speed: Float) {
        // A user-initiated speed change resets the pass-out interaction clock
        // and feeds the still-watching counter through the same signal.
        playbackSession.engineEventCoordinator.onUserInteraction()
        _uiState.update { it.copy(playbackSpeed = speed) }
        playerSessionManager.engine?.setPlaybackSpeed(speed)
    }

    private var speedBeforeHold: Float? = null

    private fun startHoldSpeed() {
        if (_uiState.value.gestures.isHoldSpeedActive) return
        speedBeforeHold = _uiState.value.playbackSpeed
        val targetSpeed = _uiState.value.gestures.holdSpeedMultiplier
        playerSessionManager.engine?.setPlaybackSpeed(targetSpeed)
        _uiState.update { it.copy(playbackSpeed = targetSpeed, gestures = it.gestures.copy(isHoldSpeedActive = true)) }
    }

    private fun stopHoldSpeed() {
        if (!_uiState.value.gestures.isHoldSpeedActive) return
        val restoreSpeed = speedBeforeHold ?: _uiState.value.gestures.defaultSpeed
        speedBeforeHold = null
        playerSessionManager.engine?.setPlaybackSpeed(restoreSpeed)
        _uiState.update { it.copy(playbackSpeed = restoreSpeed, gestures = it.gestures.copy(isHoldSpeedActive = false)) }
    }

    private fun selectAudioTrack(option: TrackOption) {
        trackSelectionHelper.selectAudioTrack(option)
    }

    private fun selectSubtitleTrack(option: TrackOption) {
        trackSelectionHelper.selectSubtitleTrack(option)
        // G10: the active subtitle track changed — refresh the cue preview
        // eagerly so the AV-sync sheet (if open) shows the newly selected
        // track's cues without a reopen.
        subtitlePreview.onTrackSelectionChanged()
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
            val currentSourceId = playerSessionManager.sessionState.value.currentMediaSource?.id
            if (currentSourceId != null) {
                playbackPreferenceWriter.setPreferredMediaSource(currentSourceId)
            }
        } else {
            playbackPreferenceWriter.clearPreferredMediaSource()
        }
    }

    private fun resetAudioTrack() {
        trackSelectionHelper.resetAudioSelection()
    }

    private fun resetSubtitleTrack() {
        trackSelectionHelper.resetSubtitleSelection()
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
        playbackPreferenceWriter.setSeriesAudioLanguage(language)
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
        playbackPreferenceWriter.setSeriesSubtitlePreference(language, forced, hearingImpaired)
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
        playbackPreferenceWriter.setSeriesSubtitleDisabled(disabled)
    }
    // -----------------------------------------------------------------------

    private fun setAspectRatio(ratio: AspectRatio) {
        _uiState.update { it.copy(videoFx = it.videoFx.copy(aspectRatio = ratio)) }
        if (ratio == AspectRatio.AUTO) {
            val detected = detectAspectRatio(_uiState.value.media.mediaStreams)
            _uiState.update { it.copy(videoFx = it.videoFx.copy(detectedAspectRatio = detected)) }
        }
        // The PiP aspect ratio always tracks the underlying media, independent
        // of the in-app resize mode, so it does not need re-deriving here.
    }

    // updatePipAspectRatio / updatePipSourceRect (the PiP window's
    // aspect-ratio + source-rect pushes) moved into PipTransportController
    // with the transport registration; the projector's streams-refresh seam
    // and the UpdatePipSourceRect event arm route to it.

    // Subtitle style/delay + dialogue-boost choreography (the style edit
    // persist, the per-item delay write + its debounced engine apply, the
    // per-item boost persist) lives in [SubtitleStyleController] since A7 —
    // and, since the SubtitleFontController fold, so do the user-font
    // install + the direct engine style re-apply; the functions below are
    // the remaining screen/PiP-facing funnels.

    private fun toggleDialogueBoost() {
        subtitleStyleController.toggleDialogueBoost()
    }

    private fun setDialogueBoostStrength(strength: com.raulshma.jellyplay.core.model.EffectStrength) {
        subtitleStyleController.setDialogueBoost(strength)
    }

    private fun setSubtitleDelay(ms: Long) {
        subtitleStyleController.setDelay(ms)
    }

    private fun setPlaybackMode(mode: PlaybackMode) {
        val prefs = _uiState.value.uiPrefs
        if (prefs.playbackMode == mode) return
        // User explicitly changed the mode — re-arm the direct-play fallback so
        // a future FORCE_DIRECT_PLAY attempt can fail-and-retry again.
        playbackSession.engineEventCoordinator.onPlaybackModeChanged()
        // The sibling quality the reload must still resolve against, captured
        // from the SAME pre-write snapshot the guard read (this setter does not
        // change it) — never read back after the mirror write.
        val quality = prefs.streamingQuality
        _uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(playbackMode = mode)) }
        applyPlaybackPrefChange(
            mode = mode,
            quality = quality,
            persist = { stores.playback.setPlaybackMode(mode) },
        )
    }

    private fun setStreamingQuality(quality: StreamingQuality) {
        val prefs = _uiState.value.uiPrefs
        if (prefs.streamingQuality == quality) return
        val mode = prefs.playbackMode
        _uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(streamingQuality = quality)) }
        applyPlaybackPrefChange(
            mode = mode,
            quality = quality,
            persist = { stores.playback.setStreamingQuality(quality) },
        )
    }

    /**
     * Toggles adaptive bitrate (the AUTO-mode network cap). Persisted and
     * re-resolved immediately so the cap change takes effect for the running
     * stream: disabling it drops the cap so the server direct-plays instead of
     * transcoding high-bitrate media. ABR changes NEITHER the mode nor the
     * quality tier — the reload resolves against the pre-change mirror
     * snapshot (captured synchronously, before any suspension can interleave
     * a projection); it re-resolves at all because the resolved cap feeds the
     * server's PlaybackInfo decision.
     */
    private fun setAdaptiveBitrateEnabled(enabled: Boolean) {
        val prefs = _uiState.value.uiPrefs
        if (prefs.adaptiveBitrateEnabled == enabled) return
        val mode = prefs.playbackMode
        val quality = prefs.streamingQuality
        _uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(adaptiveBitrateEnabled = enabled)) }
        applyPlaybackPrefChange(
            mode = mode,
            quality = quality,
            persist = { stores.networkOffline.setAdaptiveBitrateEnabled(enabled) },
        )
    }

    /**
     * The playback-pref write choreography shared by [setPlaybackMode],
     * [setStreamingQuality] and [setAdaptiveBitrateEnabled]: launch →
     * persist the caller-specific store write → reload the running session
     * with EXPLICIT [mode]/[quality] values. The reload never reads the
     * ui-prefs mirror back — the setters' mirror writes stay a pure UI
     * projection, and each caller passes exactly the post-change values
     * (its own new argument for the pref it changed; the pre-write snapshot
     * for the siblings it did not). The relative order mirror-write → launch
     * is unchanged from the former per-setter bodies.
     */
    private fun applyPlaybackPrefChange(
        mode: PlaybackMode,
        quality: StreamingQuality,
        persist: suspend () -> Unit,
    ) {
        launch {
            persist()
            reloadPlaybackForMode(mode = mode, quality = quality)
        }
    }

    /**
     * Thin delegate to [PlaybackSession.reloadForMode]: the VM supplies the
     * caller's post-change mode + quality (passed in explicitly — this never
     * reads the ui-prefs mirror) and the stored per-item stream selection
     * from the engine store (the session never reads either); the session
     * owns the stop-report / reload / selection re-arm / media-session-rebuild
     * choreography and surfaces its transcode notices as
     * [SessionEvent.InformUser]s.
     */
    private suspend fun reloadPlaybackForMode(mode: PlaybackMode, quality: StreamingQuality) {
        // Carry the stored per-item stream selection into the re-POST: the
        // server bakes one audio track into a transcoded manifest and burns in
        // image subs, so dropping the indices would reset those choices. The
        // client-side selection is re-armed separately (setPendingStreams inside
        // PlaybackSession.reloadForMode).
        val itemId = playerSessionManager.sessionState.value.currentItemId
        val selection = itemId?.let { stores.engine.playerEngine.value.mediaStreamSelections[it] }
        playbackSession.reloadForMode(
            mode = mode,
            quality = quality,
            selection = selection,
        )
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
     * ([PlaybackSession.retryWithEngine]).
     */
    private fun retryWithEngine(playerType: PlayerType) {
        clearPlaybackErrorState()
        _uiState.update {
            it.copy(preferredPlayerType = playerType)
        }
        playbackSession.retryWithEngine(
            playerType = playerType,
            playbackSpeed = _uiState.value.playbackSpeed,
            streamingQuality = _uiState.value.uiPrefs.streamingQuality,
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

    // (Four former quick-settings funs died in the X1a dead-surface cut:
    // setFrameRateMatching / setRefreshRateMode / toggleEqualizer /
    // setEqualizerSettings had no player-screen callers — the settings
    // screens own those preference stores directly, and the aggregate prefs
    // collector rebuilds the engine config when the stored values change.)

    private fun setVideoEffects(effects: VideoEffectsConfig) {
        _uiState.update { it.copy(videoFx = it.videoFx.copy(videoEffects = effects)) }
        wiring.markEngineConfigDirtyDebounced()
        // Persist per item so the same filter preset is restored next time.
        // Skip when in Cinema Mode pre-roll — the intro is transient.
        val itemId = playerSessionManager.sessionState.value.currentItemId
        if (itemId != null && playbackSession.cinemaIntroContext == null) {
            launch {
                stores.engine.setVideoEffectsForItem(itemId, effects)
            }
        }
    }

    // ── Rendering sheet + deinterlace ──────────────
    // The write choreography lives in [render] (RenderControls); the funs
    // below are the screen-facing funnels — one-line delegates that preserve
    // the public names/signatures (the sheet router + gear menu call them).

    /** Effective global mpv slice for the sheet's pickers (before the session lens). */
    internal val globalMpvConfig: com.raulshma.jellyplay.core.model.MpvEngineConfig
        get() = render.globalMpvConfig

    /**
     * The "Rendering" sheet's shader-pack pick. Applies to the session
     * immediately (folded into every config build via [sessionRender]) and,
     * when [persist] is on (the "save for this series" toggle), pins the
     * override to the series row (or the item row for standalone movies).
     */
    private fun setRenderShaderPack(pack: com.raulshma.jellyplay.core.model.MpvShaderPack, persist: Boolean) =
        render.setRenderShaderPack(pack, persist)

    /** The sheet's tone-mapping pick — same session/persist choreography as [setRenderShaderPack]. */
    private fun setRenderToneMapping(mapping: com.raulshma.jellyplay.core.model.MpvToneMapping, persist: Boolean) =
        render.setRenderToneMapping(mapping, persist)

    /**
     * "Inherit (follow global)": clears the persisted override (both scopes)
     * and drops the session lens — the effective config is derived from the
     * global settings again.
     */
    private fun clearRenderOverride() = render.clearRenderOverride()

    /**
     * The sheet's render-quality pick: a GLOBAL preference (part of the mpv
     * config slice, not the per-item override). Written through the store —
     * the session lens mirrors it so the engine reflects the pick before the
     * DataStore round-trip lands.
     */
    private fun setRenderQuality(quality: com.raulshma.jellyplay.core.model.MpvRenderQuality) =
        render.setRenderQuality(quality)

    /**
     * cycle the session-scoped deinterlace override AUTO→ON→OFF→AUTO.
     * Held in [sessionRender] (survives next-episode advance, reverts on
     * player exit) — deliberately NOT persisted.
     */
    private fun cycleDeinterlace() = render.cycleDeinterlace()

    // The former `engineConfigSync` construction (EngineConfigSync — the
    // runtime engine-config sync) moved into [PlayerWiring] phase 1; the
    // funnel handlers route their dirty-config reports through the wiring's
    // markEngineConfigDirty / markEngineConfigDirtyDebounced. The former
    // `configSyncReady` construction gate died with the move: it existed
    // because the init-block collectors could fire before the (late-declared)
    // sync existed, while in the two-phase shape no collector is registered
    // until after phase 1 has built the whole graph.

    // cancelAutoplay (the Up Next overlay's countdown dismissal) moved into
    // EpisodeContinuationController; the CancelAutoplay arm above routes to it.

    private fun handlePlaybackEnded() {
        // The app-wide now-playing seam's Ended event (feature 4.2): a
        // genuine end-of-stream, fired before the autoplay/advance decision
        // so shell hooks see ended-then-started for an auto-advance chain.
        nowPlayingReporter.markEnded()
        val next = _uiState.value.episodes.nextEpisode
        if (autoplayController.shouldAutoPlayNext(next)) {
            // "Still watching?" gate (feature 1.3, episode arm): when the
            // unattended streak reached the armed threshold (mode
            // EPISODES/BOTH, no SyncPlay — group pacing wins), raise the
            // confirm overlay INSTEAD of advancing; no answer stops autoplay.
            if (StillWatchingGate.shouldPrompt(
                    mode = wiring.cachedAggregate.videoPlayer.stillWatchingMode,
                    episodeCheck = autoplayController.needsStillWatchingCheck(),
                    isInSyncPlaySession = _uiState.value.isInSyncPlaySession,
                )
            ) {
                stillWatching.show(StillWatchingReason.EPISODE_COUNT)
            } else {
                autoplayController.recordAutoAdvance()
                episodeContinuation.playNextEpisode()
            }
        } else {
            onEndedWithNoNext()
        }
    }

    /**
     * End-of-stream with nothing queued next: a cinema-intro chain advances to
     * its next item, anything else closes the player. The byte-identical
     * bodies of the progress reporter's `onPlaybackEndedNoNext` callback and
     * [handlePlaybackEnded]'s no-autoplay branch, folded so the two can never
     * drift.
     */
    private fun onEndedWithNoNext() {
        if (playbackSession.cinemaIntroContext != null) {
            playbackSession.advanceCinemaIntro()
        } else {
            _closePlayer.trySend(Unit)
        }
    }

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

    private fun saveBrightness(level: Float) {
        _uiState.update { it.copy(gestures = it.gestures.copy(brightnessLevel = level)) }
        if (_uiState.value.gestures.rememberBrightness) {
            launch {
                stores.videoPlayer.setVideoBrightnessLevel(level)
            }
        }
    }

    // shouldAttemptCinemaMode (the load spine's cinema gate — the cached
    // aggregate, the SyncPlay flag, the media-detail holder) and
    // fetchMediaSegments (the offline-first segments fetch + its uiState
    // write) live on SessionLoadPipeline, whose spine owns when both stages
    // run; the cinema SEQUENCING is session-owned since B4.

    // (skipCredits died in the X1a dead-surface cut: no caller anywhere —
    // the overlays expose only the intro button, and the dispatch controller's
    // skipSegment covers the tapped active segment regardless of type.)

    // applyMediaDetail / applyVolumeMemory / fetchCompanionLyrics /
    // applyMediaDetailAndSourceState (the media-detail application fan-out,
    // ordering-sensitive) moved verbatim into MediaDetailProjection — the
    // mediaContentProjector, sessionLoadPipeline and subtitles wirings above
    // route to it one-line.

    fun getImageUrl(itemId: String, maxWidth: Int = 400): String =
        imageUrlProvider.getImageUrl(itemId, maxWidth = maxWidth)

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
        val hint = subtitles.state.value.readySubtitles[rowKey]
        Log.d(
            USE_LOG_TAG,
            "Use pressed: rowKey=$rowKey, hint=$hint, playMethod=${playerSessionManager.sessionState.value.playMethod}, " +
                "pickerRows=" + trackSelectionHelper.state.value.subtitleTracks
                    .joinToString { "(i=${it.index},id=${it.id},si=${it.streamIndex},sel=${it.isSelected},'${it.label.take(24)}')" },
        )
        if (hint == null) {
            userMessageBus.info("Subtitle not active yet — please try again shortly")
            return false
        }
        val option = trackSelectionHelper.findSubtitleOptionFor(hint)
        Log.d(USE_LOG_TAG, "Resolution: ${option?.let { "index=${it.index} id=${it.id}" } ?: "<none>"}")
        if (option == null) {
            trackSelectionHelper.requestSubtitleSelection(hint)
            userMessageBus.info("Subtitle still loading — it will be selected automatically")
            return false
        }
        selectSubtitleTrack(option)
        userMessageBus.info("Subtitle selected")
        return true
    }

    // endregion

    // ── Background-cast media-session swap ──────────────────────────────────
    // Folded back from BackgroundCastController (the behaviour-thin
    // media-session owner swap): this VM already holds [castManager], the
    // media-session controller and the engine access, so the pair is two
    // private funs again. The background-casting state itself lives on
    // [CastManager] (markBackgroundCasting / isBackgroundCasting) exactly
    // where the controller kept it — reattach is a no-op unless a detach
    // armed it. The window-session → event → fun flow is unchanged.

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
        mediaSessionController.createForBackgroundCast("jellyplay_cast_bg")
    }

    /**
     * Rebuilds the local player session on return — a no-op unless a detach
     * armed the background-casting flag, and without a bound engine.
     */
    private fun reattachFromBackgroundCast() {
        if (!castManager.isBackgroundCasting) return
        castManager.markBackgroundCasting(false)

        val engine = playerSessionManager.engine
        if (engine != null) {
            val itemId = playerSessionManager.sessionState.value.currentItemId ?: return
            // Narrows the engine to its media3 player via asMedia3Player and
            // no-ops when the engine hosts none.
            mediaSessionController.createForPlayer(engine, "jellyplay_video_$itemId", itemId)
        }
    }

    private fun toggleVideoStats() {
        val newValue = !_uiState.value.uiPrefs.showVideoStats
        _uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(showVideoStats = newValue)) }
        playerSessionManager.engine?.setVideoStatsEnabled(newValue)
    }

    private fun toggleAudioOnly() {
        _uiState.update { it.copy(audioOnly = !it.audioOnly) }
    }

    private fun toggleMute() {
        val engine = playerSessionManager.engine ?: return
        val currentlyMuted = _uiState.value.isMuted
        val nowMuted = !currentlyMuted
        engine.setMuted(nowMuted)
        _uiState.update { it.copy(isMuted = nowMuted) }
        if (stores.aggregateStore.aggregate.value.videoPlayer.videoRememberMuted) {
            launch { stores.videoPlayer.setVideoMuted(nowMuted) }
        }
    }

    private fun setControlsVisible(visible: Boolean) {
        playerSessionManager.engine?.setPollingIntervalMs(if (visible) 250L else 1000L)
    }

    /** Toggle the autoplay-next-episode preference from the in-player Up Next card. */
    private fun setVideoAutoplayNext(enabled: Boolean) {
        _uiState.update { it.copy(autoplay = it.autoplay.copy(videoAutoplayNext = enabled)) }
        autoplayController.setEnabled(enabled)
        launch { stores.videoPlayer.setVideoAutoplayNext(enabled) }
    }

    /**
     * Trickplay thumbnail as an opaque platform handle (seam): the
     * screen narrows it back to [PlatformBitmap] (Bitmap/BufferedImage) at the
     * call site.
     */
    suspend fun loadTrickplayThumbnail(positionMs: Long): Any? {
        val state = _uiState.value
        if (!state.uiPrefs.trickplayEnabled && !state.uiPrefs.trickplayOnSeekGesture) return null
        return trickplayManager.getThumbnail(positionMs)
    }

    // reportCurrentPlaybackStopped (incognito gate, session-id resolution,
    // dedup latch) moved into PlaybackSession at B3 — both write sites (the
    // reload/decision paths and the final teardown) live session-side now,
    // along with the media-session release (releaseVideoMediaSession died
    // with the split; the session calls MediaSessionController.release in its
    // own teardown half). createVideoMediaSession (the load spine's
    // createMediaSession hook) is a one-line delegate to
    // MediaSessionController.createForItem, wired on [PlayerWiring]'s
    // loadHooks bundle.

    /**
     * The item-switch uiState rebuild, declared once: the surviving leaves are
     * exactly the constructor arguments here — everything else resets to its
     * slice default. Each listed slice is rebuilt FRESH (not a `.copy`),
     * mirroring the old flat reset whitelist where unlisted leaves took slice
     * defaults.
     */
    private fun VideoPlayerUiState.keepAcrossItems(): VideoPlayerUiState = VideoPlayerUiState(
        preferredPlayerType = preferredPlayerType,
        // uiPrefs: the prefs-mirror leaves carry across an item switch
        // (orientation, controls timeout, metadata/clock/time-remaining
        // visibility, keep-screen-on); the per-item / runtime leaves
        // (stats overlay, pass-out hours, trickplay info + toggles,
        // quality, ABR, playback mode, lock/PIN flags) reset to defaults.
        uiPrefs = PlayerUiPrefsState(
            defaultOrientation = uiPrefs.defaultOrientation,
            controlsTimeoutMs = uiPrefs.controlsTimeoutMs,
            showPlaybackMetadata = uiPrefs.showPlaybackMetadata,
            showClock = uiPrefs.showClock,
            showTimeRemaining = uiPrefs.showTimeRemaining,
            keepScreenOnDuringVideo = uiPrefs.keepScreenOnDuringVideo,
        ),
        // gestures: the prefs-mirror leaves carry across an item switch
        // (seek window, gesture tier flags, default speed, swipe cap,
        // brightness flag + level); the runtime leaves (hold-speed
        // toggle/multiplier/active flag, indicator side, frame-rate
        // matching, refresh-rate mode) reset to defaults. Fresh slice —
        // same tight semantics as uiPrefs above.
        gestures = GesturePrefsState(
            seekDurationMs = gestures.seekDurationMs,
            gestureMode = gestures.gestureMode,
            defaultSpeed = gestures.defaultSpeed,
            swipeSeekMaxMs = gestures.swipeSeekMaxMs,
            rememberBrightness = gestures.rememberBrightness,
            brightnessLevel = gestures.brightnessLevel,
        ),
        // segmentState: only the behaviors carry across an item switch —
        // the per-item segment list resets to default (empty).
        segmentState = SegmentState(
            segmentBehaviors = segmentState.segmentBehaviors,
        ),
        // episodes resets through the navigator's seam right after this
        // update: only the browser feature toggle carries across an
        // item switch — adjacency, season/episode lists, season id and
        // the loading flag are per-item and reset to defaults.
        // videoFx: only the TV zoom carries across an item switch —
        // the per-item effects and both aspect fields reset to defaults.
        videoFx = VideoFxState(tvZoomModePercent = videoFx.tvZoomModePercent),
        subtitleStyle = subtitleStyle,
        // Reset per-item dialogue boost so it doesn't bleed into the next
        // item before the resolver re-applies the per-item rule. (The one
        // per-item exception in the former effects whitelist; dialogue
        // boost stays resolver-driven session state — see
        // VideoEffectsController's KDoc.)
        dialogueBoostEnabled = false,
        dialogueBoostStrength = com.raulshma.jellyplay.core.model.EffectStrength.NONE,
    )

    /**
     * The ViewModel-owned half of the per-item/full teardown (B3 split of the
     * old `releaseInternals`). The session-owned half — in-flight load cancel,
     * reporter jobs, media-session release, PSM release, seek-latch clear —
     * runs FIRST inside PlaybackSession; this half runs back-to-back right
     * after it from the same synchronous call chain (no dispatch hop — an
     * interleaved recomposition could flash the outgoing item's rebuilt stale
     * title). Reached on the item-switch path through the
     * [SessionLifecycleHooks.releaseInternalsVmPart] hook and on full release
     * through [PlaybackSession.release].
     */
    private fun releaseInternalsVmPart() {
        // Raise the loading screen across the state reset + fresh load so
        // the seek bar never paints a stale/zero fraction during the
        // transition. It lifts once position & duration are seeded (in the
        // load coroutine), so the bar's first paint is already at the
        // resume fraction. (On the full-release path this is a same-value
        // write: the rebuild below constructs a fresh state whose
        // isInitializing default is already true.)
        _uiState.update { it.copy(isInitializing = true) }
        syncPlay.reset()
        // Clear per-item PiP mirrors but KEEP pipTransport: it is a VM-owned
        // bridge re-armed in init AND on every load via the rearmTransports
        // hook (the screen's onDispose runs pipController.reset(), nulling
        // it). Nulling it here would deaden PiP controls mid-session, since
        // initialize() calls releaseInternals() on every item load. The full
        // reset() (transport included) runs in performRelease() on teardown,
        // and the next load re-arms it.
        pipController.setPlaying(false)
        pipController.pipHasNext = false
        trickplayManager.clear()
        // Per-item resets for controller-owned slices: each slice's
        // semantics now live with its owner instead of an implicit UiState
        // rebuild. Sleep timer + audio effects deliberately persist (no call).
        trackSelectionHelper.reset()
        trackSelectionHelper.resetForItem()
        subtitles.resetForItem()
        abRepeat.resetForItem()
        subtitlePreview.resetForItem()
        wiring.mediaDetail = null
        autoplayController.setEnabled(false)
        // Cinema latch clear — the FIELD is session-owned since B4; the clear
        // itself stays here at exactly its old slot (between equalizer-off and
        // the uiState rebuild): moving it into the session-owned teardown half
        // would relocate it ahead of every VM-part neighbor instead. The
        // uiState rebuild below already nulls cinemaIntroState implicitly
        // (fresh constructor).
        playbackSession.cinemaIntroContext = null

        // Residual reset: session + prefs-mirror fields only. Everything that
        // reset implicitly (track lists, subtitle search, sleep timer, audio
        // effects, SyncPlay display, A/B repeat) is now reset — or deliberately
        // not reset — by its owning controller above. The surviving leaves are
        // declared in [keepAcrossItems].
        _uiState.update { it.keepAcrossItems() }
        // The episode-slice reset goes through the continuation controller's
        // seam — the navigator is the slice's single writer (CONTEXT.md).
        episodeContinuation.resetForItemSwitch()

        // Clear the high-frequency display streams the seek bar reads. They live
        // outside uiState (to avoid ~4 Hz whole-screen recomposition) and are
        // only ever reset on a fresh VM, so without this the previous item's
        // position/duration bleed into the next item until the new engine emits
        // its first position tick (~1-2 s). With duration == 0 the seek bar
        // renders empty (its else-branch) instead of the stale fraction.
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        _videoStats.value = EngineVideoStats()

        // Closes this VM half: the player-lifecycle callbacks clear stays
        // VM-side (a public VM constructor dependency, not a session dep).
        // It used to run mid-body, directly after the PSM release — the B3
        // split places the whole session-owned teardown (PSM release
        // included) ahead of this half, so the relative order against the
        // PSM release is preserved.
        playerLifecycleManager.reset()
    }

    fun release() {
        if (wiring.playbackSession.released) return
        wiring.playbackSession.released = true
        wiring.performRelease()
    }

    override fun onCleared() {
        super.onCleared()
        release()
        // Same cancel-after-release ordering as before the release scope became
        // an injected constructor parameter: the final stop-report /
        // pending-seek join (launched on the wiring's release scope by
        // release()) run first, and the scope is only cancelled once the owner
        // is going away for good.
        wiring.releaseScope.cancel()
    }

    // The former `performRelease` body (now-playing Stopped event → collector
    // teardown → audio-lifecycle + sleep-timer release → the session-owned
    // release split with the VM's post-internals steps as the callback →
    // pending-seek join + final stop-report) moved verbatim into
    // [PlayerWiring.performRelease] with the collaborator graph it drives.

    private companion object {
        const val TAG = "VideoPlayerViewModel"

        /**
         * Shared tag across the whole "downloaded subtitle → Use → activate"
         * chain (VM, session manager, engines) so one logcat filter captures
         * the full path: `adb logcat -s SubtitleUse`.
         */
        const val USE_LOG_TAG = "SubtitleUse"
    }
}
