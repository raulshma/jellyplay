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
     * resolution, now on [VideoSessionHost], pivots onto
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

    /**
     * True while a next-episode load is in flight and unsettled. The Up Next
     * overlay disables its play button on this flag, so rapid re-taps can
     * neither stack duplicate loads nor restart playback once per tap (#146).
     */

    private val playerSessionManager = PlayerSessionManager(
        scope = scope,
        mediaRepository = mediaRepository,
        playbackRepository = playbackRepository,
        playbackIdentity = playbackIdentity,
        downloadRepository = downloadRepository,
        offlineRepository = offlineRepository,
        aggregateStore = stores.aggregateStore,
        playerLifecycleManager = playerLifecycleManager,
        adaptiveBitrateManager = adaptiveBitrateManager,
        playerEngineFactory = playerEngineFactory,
        pipController = pipController,
        playbackSourceResolver = playbackSourceResolver,
        streamingSubtitleStore = streamingSubtitleStore,
        offlineMediaProbe = platform.offlineMediaProbe,
        offlineModeManager = offlineModeManager,
        userMessageBus = userMessageBus,
        // Preferred-version memory: item scope wins over series scope, the
        // same precedence the ItemPlaybackPreferenceResolver applies to the
        // language rows. Consulted by loadOnline before the first-sources
        // fallback.
        getPreferredMediaSourceId = { itemId, seriesId ->
            itemPlaybackPreferenceRepository.get(
                com.raulshma.jellyplay.core.model.PlaybackPrefScope.ITEM,
                itemId,
            )?.preferredMediaSourceId
                ?: seriesId?.let {
                    itemPlaybackPreferenceRepository.get(
                        com.raulshma.jellyplay.core.model.PlaybackPrefScope.SERIES,
                        it,
                    )?.preferredMediaSourceId
                }
        },
        nowPlayingReporter = nowPlayingReporter,
    )

    // ── Engine-event orchestration ──────────────────────────────────────────
    // The coordinator's construction, re-arm and decision execution live in
    // [PlaybackSession] (moved at B2); this VM keeps only the engine MIRROR
    // collectors started by [startEngineEventCoordinatorOutputs] — they write
    // the ui state (play/buffering flags) and poke VM collaborators
    // (SyncPlay, PiP), so they stay VM-owned. Session-level outcomes arrive
    // as [SessionEvent]s through the init-block collector below.
    /** Fan-out collectors for the coordinator's mirrors + decisions. */
    private var engineEventOutputsJob: Job? = null

    // @Volatile: written from launched coroutines (the media-detail
    // projection's setDetail seam) and read
    // cross-coroutine (the episode-continuation controller's next-episode
    // advance through the getDetail seam); without it readers can see stale null.
    @Volatile
    private var mediaDetail: MediaDetail? = null

    /**
     * Single resolved playback-session id. The server issues its own id
     * via the `PlaybackInfo` endpoint (stored in [PlayerSessionState.playSessionId]);
     * [PlaybackSession.playSessionId] is the locally-allocated UUID fallback. Previously
     * start/stop reports read the local UUID directly while progress reports
     * read `sessionState.playSessionId ?: playSessionId`, so the two could
     * desync (start reported id A, stop reported id B). Routing every report
     * and the position persist through this resolver guarantees a single
     * value is used for the whole session lifecycle. (Since B3 the session
     * owns the reports/persists and carries an identical resolver; this VM
     * copy feeds the reporter's session-id getter and the start-report hook.)
     */
    private val currentPlaySessionId: String
        get() = playerSessionManager.sessionState.value.playSessionId ?: playbackSession.playSessionId
    private val autoplayController = AutoPlayController()
    // @Volatile: written by the init collector, read off-Main (e.g. from the
    // session's stop-report reading the incognito gate off-Main).
    @Volatile
    private var cachedAggregate: VideoPlayerAggregate = VideoPlayerAggregate()

    // Cinema Mode sequencing (cinemaIntroContext + loadCinemaIntro /
    // advanceCinemaIntro + the beginCinemaMode entry point) moved into
    // PlaybackSession at B4 — the uiState cinemaIntroState write flows through
    // the session's setCinemaIntroState seam. This VM still reads
    // playbackSession.cinemaIntroContext (end-of-media policy, skipIntro, the
    // per-item video-effects persist gate) and clears it in
    // releaseInternalsVmPart at exactly its old slot.

    private val trickplayManager = platform.createTrickplayController(playbackRepository)

    /**
     * The trickplay three-way selection for the session load spine (server
     * manifest cached into the download dir → locally bundled meta.json →
     * live server fetch cached for the next offline session). Owns the dir
     * derivations, the download-path probe, the precedence and the
     * controller dispatch; this VM keeps only the uiState write.
     */
    private val trickplayPreparation = TrickplayPreparation(
        controller = trickplayManager,
        offlinePlaybackFacade = offlinePlaybackFacade,
        mediaRepository = mediaRepository,
    )

    internal val subtitles = SubtitleManager(
        contentGateway = platform,
        playbackRepository = playbackRepository,
        mediaRepository = mediaRepository,
        subtitleProviderRepository = subtitleProviderRepository,
        streamingSubtitleStore = streamingSubtitleStore,
        userMessageBus = userMessageBus,
        scope = scope,
        addExternalSubtitle = { playerSessionManager.addExternalSubtitle(it) },
        getMediaStreams = { _uiState.value.media.mediaStreams },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getCurrentSourceId = { playerSessionManager.sessionState.value.currentMediaSource?.id },
        onMediaDetailRefreshed = { refresh -> mediaDetailProjection.applyRefreshedDetail(refresh) },
        getCurrentMediaDetail = { mediaDetail },
        // allowSyntheticRow = true is deliberate here (it's the default, spelled
        // out so the policy doesn't hinge on a distant parameter): for "is the
        // downloaded row usable", a synthetic server row IS a usable answer —
        // selecting it runs the selectServerTrack reload. This is the opposite
        // of the auto-select path in TrackSelectionHelper, which excludes
        // synthetic rows because it must never surprise-reload playback.
        isSubtitleTrackAttached = { hint ->
            trackSelectionHelper.findSubtitleOptionFor(hint, allowSyntheticRow = true) != null
        },
        isOffline = { offlineModeManager.isOffline },
    )
    internal val sleepTimer = SleepTimerController(
        sleepCountdown = sleepCountdown,
        audioStore = stores.audio,
        scope = scope,
        getEngine = { playerSessionManager.engine },
        isMuted = { _uiState.value.isMuted },
    )
    internal val abRepeat = AbRepeatController(
        scope = scope,
        getEngine = { playerSessionManager.engine },
        positionFlow = currentPositionMs,
    ).also { it.start() }
    internal val cast = platform.createCastController(
        playbackRepository = playbackRepository,
        adaptiveBitrateManager = adaptiveBitrateManager,
        syncPlayCastStore = stores.syncPlayCast,
        getEngine = { playerSessionManager.engine },
        getCurrentPlaybackMode = { _uiState.value.uiPrefs.playbackMode },
        getSessionState = { playerSessionManager.sessionState.value },
    )
    private val settingsProjector = SettingsProjector(
        getUiState = { _uiState.value },
        updateUiState = { transform -> _uiState.update(transform) },
        getItemId = { playerSessionManager.sessionState.value.currentItemId },
        getMediaStreams = { _uiState.value.media.mediaStreams },
    )

    /**
     * The `media` slice's single writer (A8, the EpisodeNavigator
     * `updateEpisodes` seam shape): every MediaContentState write routes
     * through it, and the refreshed-detail choreography ORDER (session
     * manager first, streams write, track rebuild last) lives there,
     * jvmTest-pinned. The cross-controller fan-outs stay here, passed in as
     * the constructor lambdas below (aspect mirrors, track rebuild, the
     * companion-lyrics fetch inside [MediaDetailProjection.applyDetail]).
     */
    private val mediaContentProjector = MediaContentProjector(
        updateMedia = { update ->
            _uiState.update { it.copy(media = update(it.media)) }
        },
        applyDetail = { detail -> mediaDetailProjection.applyDetail(detail) },
        applyRefreshedDetail = { detail, attachToEngine ->
            playerSessionManager.applyRefreshedDetail(detail, attachToEngine)
        },
        matchMediaSource = { detail ->
            playerSessionManager.matchedMediaSource(detail, fallbackToFirst = true)
        },
        onStreamsRefreshed = { streams, newSubtitleStreamIndex ->
            _uiState.update { it.copy(videoFx = it.videoFx.copy(detectedAspectRatio = detectAspectRatio(streams))) }
            pipTransport.updatePipAspectRatio(streams)
            // Rebuild the audio/subtitle track options from the refreshed server
            // streams. The side-load above re-emits the engine's availableTracks
            // (its own collector re-runs this), but that emission is async — call
            // it directly too so the picker updates immediately on transcode,
            // where `mergeServerStreams` surfaces the stream without the engine.
            trackSelectionHelper.updateTracksFromEngine()
            newSubtitleStreamIndex?.let { index ->
                trackSelectionHelper.requestSubtitleSelection(
                    ReadySubtitleHint(
                        trackId = externalSubtitleTrackId(index),
                        serverStreamIndex = index,
                    ),
                )
            }
        },
        // Session-collector fold seams (the SubtitleStyleController
        // narrow-mirror pattern — named per concern, never a generic state
        // transformer): the whole onSessionState fold lives in the
        // projector; these fire from it with the cadence/order the inline
        // collector had. The lambdas capture only stable handles (the
        // `_uiState` StateFlow, the VM) and read later-declared
        // collaborators lazily — the trackSelectionHelper/
        // persistRememberedTrack pattern — because they run only from
        // init's collector, long after those properties initialise.
        setTitleSubtitle = { title, subtitle ->
            _uiState.update { it.copy(title = title, subtitle = subtitle) }
        },
        onStoredSelectionChanged = { stored ->
            trackSelectionHelper.onStoredSelectionChanged(stored)
        },
        getStoredSelection = { itemId ->
            itemId?.let { cachedAggregate.engine.mediaStreamSelections[it] }
        },
        refreshPlaybackPreferences = {
            trackSelectionHelper.refreshPlaybackPreferences()
        },
        onSessionItemChanged = { itemId, seriesId ->
            render.onSessionItemChanged(itemId, seriesId)
        },
        launchAsync = { block ->
            // Fire-and-forget, never awaited — the inline collector's
            // `launch { render.onSessionItemChanged(...) }` verbatim.
            launch { block() }
        },
    )

    /**
     * The media-detail application cluster (the [MediaContentProjector] twin,
     * extracted verbatim): the ordered fan-out a fresh or refreshed
     * [com.raulshma.jellyplay.core.model.MediaDetail] runs — detail holder →
     * chapters → media slice → episode adoption → companion lyrics → volume
     * memory — and the subtitle-download re-sync
     * ([MediaDetailProjection.applyRefreshedDetail]). Ordering-sensitive;
     * the ordering lives in the projection. Its lambdas read
     * later-declared collaborators (episodeContinuation) lazily — invoked
     * long after construction.
     *
     * Explicit type: the projector's `applyDetail` wiring above reaches into
     * this property while this constructor's onDetail/onLyrics/
     * onDetailRefreshed lambdas reach back into the projector — an implicit
     * type would make the inference mutually recursive (the
     * ItemPlaybackPreferenceWriter / trackSelectionHelper annotation shape).
     */
    private val mediaDetailProjection: MediaDetailProjection = MediaDetailProjection(
        scope = scope,
        lyricsRepository = lyricsRepository,
        volumeProfileStore = stores.volumeProfile,
        setDetail = { detail -> mediaDetail = detail },
        setChapters = { chapters ->
            _uiState.update { it.copy(chapters = chapters) }
        },
        onDetail = { detail, artworkUrl ->
            mediaContentProjector.onDetail(detail, artworkUrl)
        },
        artworkUrl = { itemId -> getImageUrl(itemId, 400) },
        adoptSeasonOf = { detail -> episodeContinuation.adoptSeasonOf(detail) },
        onLyrics = { lines -> mediaContentProjector.onLyrics(lines) },
        onDetailRefreshed = { refresh -> mediaContentProjector.onDetailRefreshed(refresh) },
        getEngine = { playerSessionManager.engine },
    )
    private val mediaSessionController = mediaSessionFactory.create(
        getEngine = { playerSessionManager.engine },
        getImageUrl = { itemId, maxWidth -> playbackRepository.getImageUrl(itemId = itemId, maxWidth = maxWidth) },
    )

    /**
     * Owns audio-focus (duck/restore) + becoming-noisy auto-pause. Shared with
     * the live TV VM to eliminate the prior copy-paste. [control] reads the
     * current engine on every callback so engine swaps (retry/fallback) and
     * teardown stay correct. [onRegain] applies the `videoSkipBackOnResumeMs`
     * resume-skip the VOD path needs (live has no equivalent) — the same
     * [applyResumeSkip] math [resumePlayback] uses, but WITHOUT its
     * is-playing guard; that divergence is deliberate, see [resumePlayback].
     */
    private val playerAudioLifecycle = platform.createAudioLifecycle(
        getEngine = { playerSessionManager.engine },
        isMuted = { _uiState.value.isMuted },
        onRegain = {
            // No is-playing guard (deliberate divergence from
            // [resumePlayback], which keeps one): focus REGAIN follows a
            // transient loss (duck/pause), where the skip is always wanted
            // regardless of the engine's current play flag. Shared clamp
            // math via [applyResumeSkip] / [resumeSkipTargetMs].
            // Declared delta vs the pre-fold body: a NULL engine is a no-op
            // (the old body issued a degenerate seekTo(0) through the full
            // dispatcher — no local playback exists to skip back).
            playerSessionManager.engine?.let { applyResumeSkip(it) }
        },
    )

    /**
     * The PiP-facing surface (the [SubtitlePreviewController] shape): the
     * transport registration behind the PiP window's remote actions and the
     * aspect/source-rect pushes. Re-armed from init AND from the
     * `rearmTransports` session hook — see PipTransportController's KDoc for
     * why the re-arm must ride the load lifecycle. The dispatch lambdas read
     * later-declared collaborators lazily (the episodeContinuation /
     * trackSelectionHelper pattern — invoked long after construction).
     */
    private val pipTransport = PipTransportController(
        pipController = pipController,
        getEngine = { playerSessionManager.engine },
        routedPlay = { play -> routedPlay(play) },
        seekByStep = { direction -> seekByStep(direction) },
        playNextEpisode = { episodeContinuation.playNextEpisode() },
    )

    private val _passOutEvents = Channel<String>(Channel.BUFFERED)
    val passOutEvents: kotlinx.coroutines.flow.Flow<String> = _passOutEvents.receiveAsFlow()

    /**
     * The "Still watching?" confirm overlay's prompt lifecycle (feature 1.3) —
     * the [EpisodeContinuationController] shape: the prompt StateFlow and the
     * show/continue/stop/tick choreography live in [StillWatchingController];
     * this VM raises the overlay (the end-of-playback gate's episode arm in
     * [handlePlaybackEnded], the session's hours arm via
     * `SessionEvent.StillWatchingPrompt`) and forwards the overlay's event arms
     * one-line. Pure decisions: [StillWatchingGate] / [StillWatchingPromptState]. The dispatch lambdas
     * read later-declared collaborators lazily — invoked long after construction.
     */
    private val stillWatching = StillWatchingController(
        getCountdownSeconds = { _uiState.value.autoplay.autoPlayCountdownSec },
        onUserInteraction = { autoplayController.onUserInteraction() },
        playNextEpisode = { episodeContinuation.playNextEpisode() },
        resumePlayback = { resumePlayback() },
        pauseEngine = { playerSessionManager.engine?.pause() },
        cancelAutoplay = { episodeContinuation.cancelAutoplay() },
    )

    /**
     * The overlay's state surface; `null` = hidden. The screen collects this
     * at the overlay tier (thin alias over the controller's flow).
     */
    val stillWatchingPrompt: StateFlow<StillWatchingPromptState?>
        get() = stillWatching.prompt

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
            is VideoPlayerUiEvent.SeekTo -> seekTo(event.positionMs)
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
     * The seek entry point. [userInitiated] marks the paths a human drove —
     * the seek bar / gesture commit, the step buttons / keyboard / D-pad
     * (through [seekByStep]), restart — and only those pay attention to the
     * `skipSegmentsOnSeek` clamp (a target landing strictly inside an
     * AUTO_SKIP segment is pulled to the segment's end, with the "Skipped …"
     * notice). Internal, app-driven seeks pass `false` and are never touched:
     * the auto-skip execution, the SyncPlay position sync and the
     * resume-skip. The A-B repeat loop seeks the engine directly (bypassing
     * this funnel), so nothing there can be clamped by accident either.
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
                enabled = cachedAggregate.videoPlayer.skipSegmentsOnSeek,
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
     * The single discrete skip-step owner: the screen's skip buttons /
     * keyboard / D-pad commits and the PiP transport's SKIP actions all
     * reduce to this funnel (C3). The clamp math stays in
     * [stepSeekTargetMs] — floor at 0 on the back path, cap at the engine's
     * duration on the forward path (skipped for live streams with no
     * resolved duration). The seek is issued through the same routing the
     * screen's `doSeekTo` used — SyncPlay group seek while in a session,
     * cast seek while casting, local engine otherwise — so PiP steps and
     * on-screen steps can never diverge. Gesture / hold-speed paths do NOT
     * go through here. [direction] < 0 steps back, anything else forward.
     */
    private fun seekByStep(direction: Int) {
        // User-initiated step: resets the interaction clock + the
        // still-watching counter (the SyncPlay/cast arms bypass seekTo).
        playbackSession.engineEventCoordinator.onUserInteraction()
        val engine = playerSessionManager.engine
        val target = stepSeekTargetMs(
            direction = direction,
            currentPositionMs = engine?.currentPositionMs ?: 0L,
            stepMs = _uiState.value.gestures.seekDurationMs,
            durationMs = engine?.durationMs ?: 0L,
        )
        when {
            _uiState.value.isInSyncPlaySession -> syncPlay.seekTo(target)
            cast.isConnectedFlow.value -> cast.castSeekTo(target)
            else -> seekTo(target)
        }
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

    // Explicit type: the getPlaySessionId lambda below reaches into
    // `playbackSession`, so leaving this type implicit would make the
    // inference of `playbackSession` (which takes this property as a
    // constructor argument) recursive.
    private val progressReporter: PlaybackProgressReporter = PlaybackProgressReporter(
        playbackRepository = playbackRepository,
        scope = viewModelScope,
        uiState = _uiState,
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getPlaySessionId = { playerSessionManager.sessionState.value.playSessionId ?: playbackSession.playSessionId },
        getResolvedPlayMethod = { playerSessionManager.sessionState.value.playMethod },
        getMediaEngine = { playerSessionManager.engine },
        getIncognitoModeEnabled = { cachedAggregate.videoPlayer.incognitoModeEnabled },
        onAutoSkip = { segment -> autoSkipSegment(segment) },
        onPlaybackEndedNoNext = { onEndedWithNoNext() },
        onWatchedThresholdReached = { itemId ->
            // Forwarded into the episode-continuation controller declared
            // below — the lambda only runs long after construction, so its
            // (lazy) read of the not-yet-initialised property is safe (the
            // trackSelectionHelper.persistRememberedTrack pattern).
            episodeContinuation.handleSmartDownloadCleanup(itemId)
            // Closes the gap where playback crossed the watched threshold but no
            // clean Stop telemetry reached the server (process kill / crash),
            // leaving the item unplayed server-side.
            //
            // Normal mode: mark watched through PlayedStateSync.flip (via
            // markPlayed) so the change applies to the offline store AND reaches
            // the server — immediately when online, or via the playback outbox
            // on reconnect when offline.
            //
            // Incognito: never reach the server or create outbox rows — the same
            // invariant reportCurrentPlaybackStopped enforces. Fall back to the
            // local-only offline mark so a downloaded copy still shows watched,
            // matching how persistPlaybackPosition keeps writing the local resume
            // cache in incognito.
            // NonCancellable: this callback fires at the very end of playback,
            // exactly when VM teardown cancels the scope — without it the
            // launch body may never run and the PLAYED outbox row is never
            // enqueued. Losing that row is the #153 "watched offline, online
            // home shows mostly completed" bug — once written to the outbox
            // it survives anything, so the enqueue itself must land.
            launch(NonCancellable) {
                if (cachedAggregate.videoPlayer.incognitoModeEnabled) {
                    offlinePlaybackFacade.recordPlayed(itemId)
                } else {
                    // Silent mode (plan 03): the player is not a detail surface —
                    // no in-place flip, the repository write (PlayedStateSync
                    // fan-out + self-invalidation) is all this path needs.
                    userDataMutator.setPlayed(itemId, played = true)
                }
            }
        },
        onPositionPersisted = { positionMs -> playbackSession.persistPlaybackPosition(positionMs, force = false) },
        onEnginePositionUpdate = { positionMs, durationMs, _, videoStats ->
            _currentPositionMs.value = positionMs
            _durationMs.value = durationMs
            _videoStats.value = videoStats
        },
    )
    internal val syncPlay = SyncPlayBridge(
        syncPlayManager = syncPlayManager,
        getMediaEngine = { playerSessionManager.engine },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        onLoadItem = { itemId, positionTicks ->
            if (playerSessionManager.sessionState.value.currentItemId != itemId) {
                initialize(itemId, null, positionTicks)
            } else {
                // Group-driven position sync, not a user seek — never clamped.
                seekTo(positionTicks / 10_000, userInitiated = false)
            }
        },
        // Session-state write seam: the bridge no longer holds the UiState
        // handle; the play/pause mirror it maintained goes through this narrow
        // lambda.
        setIsPlaying = { playing -> _uiState.update { s -> s.copy(isPlaying = playing) } },
        scope = scope,
    )

    // ── Session load pipeline ────────────────────────────────────────────────
    // The pipeline owns the ORDER of the load stages that the initialize path
    // used to inline; the host below owns the uiState writes and the
    // VM-bound bodies (controllers, session bookkeeping, reports).
    /**
     * The extracted [SessionHost] implementation (the object literal this VM
     * used to inline, now beside the other controllers in VideoSessionHost.kt):
     * the ViewModel-bound halves of the session stack — the load pipeline's
     * uiState-shaped outputs ([SessionLoadOutputs], first five members) and
     * the initialize/release lifecycle slices ([SessionLifecycleHooks]) —
     * plus the load pipeline's [SessionLoadHooks] bundle
     * ([VideoSessionHost.loadHooks], wired into the pipeline below). The
     * session owns the ORDER; each member runs one VM-owning slice at
     * exactly its old position in the sequence. Real collaborators are
     * constructor parameters; everything that touches THIS VM's state (the
     * uiState bag, the display flows, the cached aggregate, the media-detail
     * holder, the play-session id) arrives as a narrow command lambda —
     * VideoSessionHost.kt's KDoc documents the split.
     */
    // Explicit type: the wiring lambdas below read later-declared
    // collaborators (playbackSession — the playhead pre-seed, the
    // coordinator latch reset, the cinema sequencing, the play-session id
    // fallback — plus trackSelectionHelper and episodeContinuation) lazily;
    // they run only from the session's call chain, long after those
    // properties initialise. The annotation keeps the construction from
    // depending on them at inference time (the progressReporter /
    // mediaDetailProjection shape).
    private val videoSessionHost: VideoSessionHost = VideoSessionHost(
        scope = scope,
        mediaContentProjector = mediaContentProjector,
        pipTransport = pipTransport,
        videoMiniPlayerState = videoMiniPlayerState,
        trickplayManager = trickplayManager,
        trickplayPreparation = trickplayPreparation,
        syncPlay = syncPlay,
        syncPlayManager = syncPlayManager,
        playbackSourceResolver = playbackSourceResolver,
        autoplayController = autoplayController,
        stillWatching = stillWatching,
        progressReporter = progressReporter,
        mediaSessionController = mediaSessionController,
        mediaDetailProjection = mediaDetailProjection,
        playbackRepository = playbackRepository,
        offlinePlaybackFacade = offlinePlaybackFacade,
        applyPrefsProjection = { transform -> _uiState.update(transform) },
        setInitializing = { visible -> _uiState.update { it.copy(isInitializing = visible) } },
        seedDuration = { runtimeMs ->
            // Guarded so a value already set by the engine (e.g. ExoPlayer
            // resolving duration on prepare) is never clobbered.
            if (_durationMs.value == 0L) {
                _durationMs.value = runtimeMs
            }
        },
        preSeedPlayhead = { ticks -> playbackSession.preSeedPlayhead(ticks) },
        seedPlayheadChip = { ticks -> _resumeReminder.tryEmit(ticks) },
        setAutoplayCancelled = { cancelled ->
            _uiState.update { it.copy(autoplay = it.autoplay.copy(autoplayCancelled = cancelled)) }
        },
        resetEngineEventCoordinator = { playbackSession.engineEventCoordinator.onNewItem() },
        setPendingStreams = { selection -> trackSelectionHelper.setPendingStreams(selection) },
        routeRemotePlay = { request ->
            routeToRemotePlaySession(
                itemId = request.itemId,
                mediaSourceId = request.mediaSourceId,
                startPositionTicks = request.startPositionTicks,
                subtitleStreamIndex = request.subtitleStreamIndex,
                audioStreamIndex = request.audioStreamIndex,
            )
        },
        releaseVmInternals = { releaseInternalsVmPart() },
        beginCinemaMode = { intros, request -> playbackSession.beginCinemaMode(intros, request) },
        setMutedMirror = { muted -> _uiState.update { it.copy(isMuted = muted) } },
        applyItemHydration = { itemId, hydratedAgg -> onItemHydrated(itemId, hydratedAgg) },
        seedTrickplayInfo = { info ->
            _uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(trickplayInfo = info)) }
        },
        isIncognito = { cachedAggregate.videoPlayer.incognitoModeEnabled },
        getPlaySessionId = { currentPlaySessionId },
        getMediaDetail = { mediaDetail },
        getEngine = { playerSessionManager.engine },
        setSegments = { segments ->
            _uiState.update { it.copy(segmentState = it.segmentState.copy(segments = segments)) }
        },
        fetchAdjacentEpisodes = { detail -> episodeContinuation.refreshAdjacent(detail) },
        loadSeriesEpisodes = { detail -> episodeContinuation.loadSeries(detail) },
    )

    // Explicit type: the beginCinemaMode command above reaches into
    // `playbackSession` (the session owns the cinema sequencing since B4),
    // which itself takes this property as a constructor argument — an
    // implicit type would make the inference mutually recursive (same shape
    // as the progressReporter / videoSessionHost comments).
    private val sessionLoadPipeline: SessionLoadPipeline = SessionLoadPipeline(
        sessionManager = playerSessionManager,
        mediaExtrasReads = mediaExtrasReads,
        aggregateStore = stores.aggregateStore,
        networkOfflineStore = stores.networkOffline,
        outputs = videoSessionHost,
        // The 17-lambda hook bundle the pipeline calls at defined points of
        // its spine — built and owned by the host beside the members it
        // forwards to (VideoSessionHost.kt).
        hooks = videoSessionHost.loadHooks,
    )

    /**
     * Process-death resume-position persistence behind the
     * [SessionPositionStore] seam. The VM keeps the [SavedStateHandle]
     * constructor parameter SOLELY to build this store — every read/write of
     * the resume keys goes through the session from B3 on.
     */
    private val sessionPositionStore: SessionPositionStore =
        SavedStateHandlePositionStore(savedStateHandle)

    /**
     * Teardown scope handed to [playbackSession] (injected, like the VM scope):
     * the final stop-report and the pending-seek join must outlive the
     * viewModelScope on clear(), so they launch here — IO dispatcher +
     * supervisor so one failing write cannot cancel the other. This owner
     * cancels the scope in [onCleared] AFTER release(), preserving the same
     * cancel-after-release ordering the session's `onOwnerCleared` applied
     * back when the session built the scope internally.
     */
    private val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Playback-session deep module (Stage B): owns the session-scoped latches
     * and bookkeeping (release flag, Stop-report dedup, seek + persist
     * positions, play-session id, load/seek jobs), as of
     * B1b the initialize sequence driving the hooks above and the
     * pipeline-start ownership ([PlaybackSession.initialize]), as of B2 the
     * engine reload/retry paths plus the
     * [com.raulshma.jellyplay.feature.player.video.engine.EngineEventCoordinator]
     * (construction, re-arm, decision execution), and as of B3 the reporting
     * + release surface (stop-reports, seek/position persistence through
     * [sessionPositionStore], the release split and the final stop-report on
     * the release scope). Every ui-state value the moved code needs is
     * supplied here as a parameter or getter/setter lambda — the session
     * never touches the ui state; its outcomes come back as [SessionEvent]s
     * collected in `init`.
     *
     * Declared above `init` per the construction-order convention (the
     * session-state mirror collector launched from init collects
     * `playbackSession.sessionState`). The reporter, the load pipeline and
     * the media-session controller are constructed above and passed in
     * already built — their ui-state handle wiring stays in this file by
     * design.
     */
    private val playbackSession = PlaybackSession(
        scope = scope,
        releaseScope = releaseScope,
        playerSessionManager = playerSessionManager,
        progressReporter = progressReporter,
        sessionLoadPipeline = sessionLoadPipeline,
        hooks = videoSessionHost,
        mediaSessionController = mediaSessionController,
        playbackStore = stores.playback,
        adaptiveBitrateManager = adaptiveBitrateManager,
        playbackRepository = playbackRepository,
        offlinePlaybackFacade = offlinePlaybackFacade,
        mediaRepository = mediaRepository,
        setCinemaIntroState = { state ->
            _uiState.update { it.copy(cinemaIntroState = state) }
        },
        seedDisplayedPositionMs = { positionMs -> _currentPositionMs.value = positionMs },
        positionStore = sessionPositionStore,
        getStreamingQuality = { _uiState.value.uiPrefs.streamingQuality },
        setUiPlaybackMode = { mode ->
            _uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(playbackMode = mode)) }
        },
        getIncognitoModeEnabled = { cachedAggregate.videoPlayer.incognitoModeEnabled },
        setPendingStreams = { selection ->
            trackSelectionHelper.setPendingStreams(selection)
        },
        getPlaybackMode = { _uiState.value.uiPrefs.playbackMode },
        directPlayFallbackNotice = { errorText ->
            // KMP seam: compose-resources' suspend resolver replaces
            // context.getString; the lambda contract went suspend with it
            // (DetailStrings precedent) — EngineEventCoordinator invokes it
            // from its error-flow collector, already a coroutine.
            getString(Res.string.player_direct_play_fallback, errorText)
        },
        passOutHours = _uiState.flow.map { it.uiPrefs.passOutProtectionHours }.distinctUntilChanged(),
        upgradesPassOutToOverlay = {
            StillWatchingGate.upgradesPassOutToOverlay(cachedAggregate.videoPlayer.stillWatchingMode)
        },
        onEngineEventCoordinatorRearmed = { startEngineEventCoordinatorOutputs() },
    )

    /**
     * Episode continuation — the extracted module behind the season/episode
     * browsing, adjacent discovery, previous/next choreography with the #146
     * single-flight latch, the "mark watched & skip" / "mark unwatched & quit"
     * overflow orchestration, the autoplay-cancel wiring, the Up Next
     * overlay's loading flag and the smart-download cleanup
     * ([EpisodeContinuationController]); the VM keeps thin funnels for the
     * screen, the PiP transport and the end-of-playback autoplay decision.
     *
     * Declared after [playbackSession] (the navigator latches onto its
     * events). The lambdas below capture only stable handles and read
     * later-declared collaborators lazily — invoked long after construction,
     * the trackSelectionHelper.persistRememberedTrack pattern — so the
     * load-bearing construction order (progressReporter / videoSessionHost /
     * sessionLoadPipeline above) is unchanged.
     */
    private val episodeContinuation = EpisodeContinuationController(
        scope = scope,
        sessionState = playerSessionManager.sessionState,
        sessionEvents = playbackSession.events,
        episodeCatalogue = episodeCatalogue,
        getDetail = { mediaDetail },
        getSeriesId = { mediaDetail?.item?.seriesId ?: _uiState.value.media.seriesId },
        updateEpisodes = { update ->
            _uiState.update { it.copy(episodes = update(it.episodes)) }
        },
        initializeItem = { itemId, startPositionTicks ->
            initialize(itemId, null, startPositionTicks)
        },
        reportLoadError = {
            userMessageBus.error(getString(Res.string.player_video_error_next_episode_load))
        },
        isInSyncPlayGroup = { syncPlayManager.isInSyncPlaySession },
        getCurrentGroup = { syncPlayManager.currentGroup },
        sendNextItem = { currentPlaylistItemId ->
            syncPlay.sendNextItem(currentPlaylistItemId)
        },
        sendPreviousItem = { currentPlaylistItemId ->
            syncPlay.sendPreviousItem(currentPlaylistItemId)
        },
        isIncognito = { cachedAggregate.videoPlayer.incognitoModeEnabled },
        markPlayed = { itemId -> userDataMutator.setPlayed(itemId, played = true) },
        recordPlayedOffline = { itemId -> offlinePlaybackFacade.recordPlayed(itemId) },
        markUnwatched = { itemId -> userDataMutator.setPlayed(itemId, played = false) },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        hasNextEpisode = { _uiState.value.episodes.nextEpisode != null },
        isInSyncPlaySession = { _uiState.value.isInSyncPlaySession },
        closePlayer = { _closePlayer.trySend(Unit) },
        cancelAutoplayDecision = { autoplayController.cancel() },
        setAutoplayCancelledMirror = { cancelled ->
            _uiState.update { it.copy(autoplay = it.autoplay.copy(autoplayCancelled = cancelled)) }
        },
        isSmartDownloadsEnabled = { stores.downloads.downloads.value.smartDownloadsEnabled },
        getDurationMs = { _uiState.value.duration },
        deleteDownload = { itemId -> offlinePlaybackFacade.deleteDownload(itemId) },
        notifySmartDownloadDeleted = { userMessageBus.info(PlayerVideoMessage.SmartDownloadDeleted) },
    )

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
    val incognitoModeEnabled: Boolean get() = cachedAggregate.videoPlayer.incognitoModeEnabled

    // Declared BEFORE the `init {}` block below because the engine-flow
    // collector launched from init calls `trackSelectionHelper.updateTracksFromEngine()`.
    // Kotlin initialises properties and init blocks in declaration order, so a
    // declaration after init would leave this field uninitialised at the moment
    // the collector callback is registered. The latent NPE has not fired only
    // because engine is null until loadMedia(); this removes the foot-gun.
    private val playbackPreferenceResolver = ItemPlaybackPreferenceResolver(
        repository = itemPlaybackPreferenceRepository,
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getCurrentSeriesId = { playerSessionManager.sessionState.value.mediaDetail?.item?.seriesId },
        scope = scope,
    )
    private val trackSelectionHelper = TrackSelectionHelper(
        engineStore = stores.engine,
        subtitleStore = stores.subtitleLanguage,
        getEngine = { playerSessionManager.engine },
        getMediaStreams = { _uiState.value.media.mediaStreams },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getCurrentSeriesId = { playerSessionManager.sessionState.value.mediaDetail?.item?.seriesId },
        getPlayMethod = { playerSessionManager.sessionState.value.playMethod },
        onReloadForStreamChange = { selection ->
            // Method indirection (not a direct `playbackSession.` reference):
            // this helper's construction would otherwise mutually recurse with
            // the session's, whose own wiring reaches back into
            // trackSelectionHelper (setPendingStreams).
            reloadForStreamChange(selection)
        },
        playbackPreferenceResolver = playbackPreferenceResolver,
        persistRememberedTrack = { type, track ->
            // Forwards into the write-side twin declared below — the lambda
            // only runs long after construction, so its (lazy) read of the
            // writer is safe.
            playbackPreferenceWriter.rememberTrack(type, track)
        },
        // Rule-engine context: content type + the series/item names a
        // rule's title pattern matches (series first, then the item's own).
        getRuleContentType = {
            mediaRuleContentType(playerSessionManager.sessionState.value.mediaDetail?.item?.mediaType)
        },
        getRuleTitles = {
            val item = playerSessionManager.sessionState.value.mediaDetail?.item
            listOfNotNull(item?.seriesName?.takeIf { it.isNotBlank() }, item?.name?.takeIf { it.isNotBlank() })
        },
        scope = scope,
    )
    // The write-side twin of the resolver above: one owner of the save/clear +
    // mandatory-refresh choreography (see its KDoc). The explicit type is
    // load-bearing: this declaration sits after trackSelectionHelper (whose
    // persistRememberedTrack lambda reads it) while its own wiring reads that
    // helper back — without the annotation, property type inference would
    // recurse.
    private val playbackPreferenceWriter: ItemPlaybackPreferenceWriter = ItemPlaybackPreferenceWriter(
        repository = itemPlaybackPreferenceRepository,
        getCurrentSeriesId = { playerSessionManager.sessionState.value.mediaDetail?.item?.seriesId },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        scope = scope,
        onPreferencesChanged = { trackSelectionHelper.refreshPlaybackPreferences() },
    )

    /**
     * Owns the "Rendering" sheet + deinterlace write choreography (the
     * [SubtitleStyleController] shape): the pure session semantics live in
     * [SessionRenderState] (composed as [RenderControls.state]); the
     * controller composes them with the repository/DataStore writes around
     * them — the global mpv slice persist, the per-item/series render-profile
     * rows and the session item-change re-resolution. The save lambdas read
     * [playbackPreferenceWriter] lazily (invoked long after construction, the
     * trackSelectionHelper.persistRememberedTrack pattern). Engine-side
     * application stays here: the controller only reports a dirty config
     * through `onConfigDirty` → [updateConfigWithUiState].
     */
    internal val render = RenderControls(
        scope = scope,
        getGlobalMpvConfig = { cachedAggregate.engine.mpvConfig },
        saveGlobalMpvConfig = { config -> stores.engine.setMpvConfig(config) },
        loadStoredRow = { prefScope, id -> itemPlaybackPreferenceRepository.get(prefScope, id) },
        saveRenderProfile = { overrides -> playbackPreferenceWriter.setRenderProfile(overrides) },
        clearStoredRenderProfile = { playbackPreferenceWriter.clearRenderProfile() },
        onConfigDirty = { updateConfigWithUiState() },
    )

    /**
     * The session-scoped render state (sheet + deinterlace cycle), read-only
     * alias of [RenderControls.state]. Internal: the player screen reads it
     * to render the Rendering sheet's pickers and the deinterlace menu label;
     * every WRITE routes through [render] so the choreography has one owner.
     */
    internal val sessionRender: SessionRenderState
        get() = render.state

    /**
     * Owns the AV-sync sheet's cue preview (external track load + embedded cue
     * accumulation + the sheet-visibility gate) — the cluster that used to live
     * as the flat `subtitlePreviewCues` / `subtitlePreviewSource` /
     * `previewSheetVisible` uiState fields. The engineFlow collector below
     * feeds it the engine's cue list; [selectSubtitleTrack] pokes it eagerly.
     */
    internal val subtitlePreview = SubtitlePreviewController(
        scope = scope,
        loadCues = { source, headers -> subtitlePreviewRepository.loadCues(source, headers) },
        clearCuesCache = { subtitlePreviewRepository.clearCache() },
        getExternalSubtitles = { playerSessionManager.currentExternalSubtitles },
        getPlaybackHeaders = { playerSessionManager.currentPlaybackHeaders },
        getSelectedSubtitleTrack = {
            trackSelectionHelper.state.value.subtitleTracks.firstOrNull { it.isSelected && it.index >= 0 }
        },
        getEngineCues = { playerSessionManager.engine?.currentCues?.value?.takeIf { it.isNotEmpty() } },
    )

    /**
     * Owns the subtitle-style + dialogue-boost + subtitle-delay choreography
     * (A7): style edits, per-item delay writes and their debounced engine
     * re-sync, the per-item dialogue-boost persist, and — folded back from
     * SubtitleFontController (the style edit it performed WAS a
     * [SubtitleStyleController.setStyle] call; no init-order coupling, so the
     * fold is pure) — the user-font install and the direct engine re-apply of
     * the current style. Step-1 shape of the recorded design — the state
     * stays in this VM's uiState mirrors, the controller writes through the
     * narrow lambdas below (no raw uiState handle crosses; the god-count
     * ratchet is untouched). The saveDialogueBoost lambda reads
     * [playbackPreferenceWriter] lazily (invoked long after construction, the
     * trackSelectionHelper.persistRememberedTrack pattern).
     */
    internal val subtitleStyleController = SubtitleStyleController(
        scope = scope,
        getStyle = { _uiState.value.subtitleStyle },
        setStyleMirror = { style ->
            _uiState.update { it.copy(subtitleStyle = style) }
        },
        setDialogueBoostMirror = { strength, enabled ->
            _uiState.update {
                it.copy(dialogueBoostStrength = strength, dialogueBoostEnabled = enabled)
            }
        },
        isDialogueBoostEnabled = { _uiState.value.dialogueBoostEnabled },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getGlobalOffsetMs = { cachedAggregate.subtitle.subtitleStyle.offsetMs },
        saveGlobalStyle = { style -> stores.subtitleLanguage.setSubtitleStyle(style) },
        saveItemDelay = { itemId, delayMs -> stores.subtitleLanguage.setSubtitleDelayForItem(itemId, delayMs) },
        saveDialogueBoost = { strength -> playbackPreferenceWriter.setDialogueBoostStrength(strength) },
        syncEngineConfig = { updateConfigWithUiState() },
        syncEngineConfigDebounced = { updateConfigWithUiStateDebounced() },
        fontProvider = fontProvider,
        getEngine = { playerSessionManager.engine },
    )

    /**
     * Owns the uniform engine-effect setters (night mode, audio delay,
     * decoder, passthrough, normalization, channel mix, bass, virtualizer,
     * reverb) and the [com.raulshma.jellyplay.feature.player.video.state.AudioEffectsState]
     * slice they mutate. Extracted from the VM body. Public VM methods delegate
     * so the 27 test references + the public API stay valid. Dialogue Boost,
     * Equalizer, and Video Effects stay inline because their state lives
     * outside this controller (per-item repo / VM field / cinema gate).
     */
    // Explicit type is load-bearing (the compiler's own workaround for
    // "Type checking has run into a recursive problem"): the initializer's
    // lambda reaches the later-declared `engineConfigSync` (through
    // updateConfigWithUiState), whose initializer reads `effects.state` —
    // without the annotation the property-type inference loops.
    internal val effects: VideoEffectsController = VideoEffectsController(
        scope = scope,
        audioStore = stores.audio,
        audioEffectsStore = stores.audioEffects,
        playbackStore = stores.playback,
        syncConfig = { updateConfigWithUiState() },
    )

    /**
     * The ordered engine-attach choreography ([EngineAttachController],
     * beside the other controllers): everything the `init` engineFlow
     * collector below used to inline on every engine emission — the
     * previous-collectors cancel, the remote-control registry bind/clear,
     * the style seed, the capability mirror, the effects seed, the cast
     * strategy re-pick, the unsupported-audio-delay heads-up, the PiP
     * next-action mirror, the track-selection reset and the three per-engine
     * fan-out collectors. Declared AFTER every collaborator its wiring
     * hands over (cast → syncPlay → trackSelectionHelper → subtitlePreview
     * → subtitleStyleController → effects); the collector body is the single
     * forwarding call, and the choreography's order is jvmTest-pinned in
     * EngineAttachControllerTest. The lambdas read only VM-owned slices
     * (the aggregate cache, the session state, the media-detail holder);
     * the ONE ui-state write goes through the narrow onEngineCapabilities
     * lambda — the god-count ratchet is untouched.
     */
    private val engineAttachController = EngineAttachController(
        scope = scope,
        activePlayerController = activePlayerController,
        getAggregate = { cachedAggregate },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getSeriesIdForPip = { mediaDetail?.item?.seriesId },
        getIsHdr = { isHdrFromStreams(playerSessionManager.sessionState.value.mediaStreams) },
        subtitleStyleController = subtitleStyleController,
        effects = effects,
        cast = cast,
        userMessageBus = userMessageBus,
        pipController = pipController,
        trackSelectionHelper = trackSelectionHelper,
        subtitlePreview = subtitlePreview,
        syncPlay = syncPlay,
        onEngineCapabilities = { capabilities, keepScreenOnDuringVideo ->
            _uiState.update { it.copy(
                engineCapabilities = capabilities,
                uiPrefs = it.uiPrefs.copy(keepScreenOnDuringVideo = keepScreenOnDuringVideo),
            ) }
        },
    )

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

    /**
     * The aggregate-prefs collector's side-effecting half (P4): the seeds,
     * the two engine-config rebuild triggers, the autoplay flip and the duck
     * registration — extracted beside [settingsProjector] (whose `project`
     * stays the pure-projection half). Declared after every collaborator its
     * wiring reads; the lambdas run only from init's collector, long after
     * construction.
     */
    private val prefsFanout = PlayerPrefsFanout(
        projectPrefs = settingsProjector::project,
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        seedSleepTimerLastUsedMs = sleepTimer::seedLastUsedDurationMs,
        onStoredSelectionChanged = trackSelectionHelper::onStoredSelectionChanged,
        seedDefaultSearchLanguage = subtitles::seedDefaultSearchLanguage,
        isAutoplayNextApplied = { applied -> _uiState.value.autoplay.videoAutoplayNext == applied },
        applyAutoplayNextPref = { enabled ->
            _uiState.update { it.copy(autoplay = it.autoplay.copy(videoAutoplayNext = enabled)) }
            autoplayController.setEnabled(enabled)
        },
        rebuildEngineConfigIfRunning = { playerSessionManager.engine?.let { updateConfigWithUiState() } },
        isAudioFocusActive = { playerAudioLifecycle.isAudioFocusActive() },
        registerAudioFocus = { playerAudioLifecycle.registerAudioFocus() },
        unregisterAudioFocus = { playerAudioLifecycle.unregisterAudioFocus() },
    )

    init {
        castManager.acquireConsumer()
        // Subscribe the engine-event fan-out FIRST: the coordinator's mirrors
        // collector must be active before any initialize() can produce an
        // engine state change (subscription timing). The coordinator's
        // decision executor lives in the session and is subscribed there.
        startEngineEventCoordinatorOutputs()
        // Single forwarder for the session's outcomes: one collector maps
        // each [SessionEvent] into this VM's existing sinks. The autoplay /
        // cinema / close policy of [handlePlaybackEnded] stays here — the
        // session only reports that playback ended.
        launch {
            playbackSession.events.collect { event ->
                when (event) {
                    is SessionEvent.ShowError -> _uiState.update { s ->
                        if (event.clearBuffering) {
                            s.copy(
                                playerError = event.error,
                                playerErrorRetryable = event.retryable,
                                showPlaybackErrorDialog = true,
                                isBuffering = false,
                            )
                        } else {
                            s.copy(
                                playerError = event.error,
                                playerErrorRetryable = event.retryable,
                                showPlaybackErrorDialog = true,
                            )
                        }
                    }
                    is SessionEvent.InformUser -> userMessageBus.info(event.message)
                    SessionEvent.PlaybackEnded -> handlePlaybackEnded()
                    SessionEvent.ClosePlayerRequested -> _closePlayer.trySend(Unit)
                    SessionEvent.PassOutPause ->
                        _passOutEvents.trySend("Playback paused — pass-out protection")
                    is SessionEvent.StillWatchingPrompt ->
                        // The session's hours arm: the engine is already
                        // paused; the overlay's Continue resumes, Stop keeps
                        // it paused and cancels autoplay.
                        stillWatching.show(event.reason)
                }
            }
        }
        // Register the PiP transport bridge so the Activity can dispatch PiP
        // remote-action intents (play/pause/skip/next) to the active engine.
        // Also re-armed on every load via the rearmTransports hook — see
        // [reArmPipTransport] for why the re-arm must ride the load lifecycle.
        pipTransport.registerPipTransport()
        // The PiP-dismissal discharge (pause → teardown → close → the
        // defensive latch clear, issue #145) lives on the shared core:data
        // helper — this host supplies only its teardown list and its close
        // pipe.
        scope.dischargePipDismissal(
            pip = pipController,
            teardown = {
                activePlayerController.engine?.pause()
                playerSessionManager.engine?.pause()
                mediaSessionController.release()
                videoMiniPlayerState.release()
                release()
            },
            close = { _closePlayer.trySend(Unit) },
        )
        launch {
            // The aggregate-prefs collector (P4): cache bookkeeping here, the
            // whole pref-diff choreography (projection + the five controller
            // seeds + the two engine-config rebuild triggers + the autoplay
            // flip + the duck registration) in [prefsFanout.onAggregateChanged].
            stores.aggregateStore.aggregate.collect { agg ->
                val oldAggregate = cachedAggregate
                cachedAggregate = agg
                prefsFanout.onAggregateChanged(oldAggregate, agg)
                // Still-watching threshold seed (feature 1.3) — the
                // change-time counterpart of onSessionPrefsApplied, the same
                // diff-guard the fanout's controller seeds use.
                if (oldAggregate.videoPlayer.stillWatchingEpisodeThreshold != agg.videoPlayer.stillWatchingEpisodeThreshold) {
                    autoplayController.setStillWatchingThreshold(agg.videoPlayer.stillWatchingEpisodeThreshold)
                }
            }
        }
        launch {
            // Surface the metered-network state so the playback metadata can
            // explain why a quality cap is being applied (AUTO on a metered link
            // caps at AdaptiveBitrateManager.MAX_BITRATE_METERED). Guarded so a
            // redundant emission (no change) doesn't allocate a fresh uiState.
            networkMonitor.isMetered.collect { metered ->
                if (_uiState.value.isConnectionMetered != metered) {
                    _uiState.update { it.copy(isConnectionMetered = metered) }
                }
            }
        }
        // Pass-out protection (interaction clock + poller) and the play-state
        // resume reset live in [EngineEventCoordinator]; the PassOutPause
        // decision is executed by the session and arrives here as a
        // [SessionEvent.PassOutPause] through the events collector above.
        syncPlay.start()

        // Mirror the bridge's session flag into the residual UiState: it feeds
        // SegmentProjection/toSegmentInput() inside segmentOverlayState's
        // combine, and moving that combine onto the bridge's flow would couple
        // the segment projection to the bridge. One-way derived mirror — the
        // bridge's SyncPlayUiState.isInSyncPlaySession stays the single home.
        launch {
            syncPlay.state.map { it.isInSyncPlaySession }.distinctUntilChanged()
                .collect { inSession ->
                    if (_uiState.value.isInSyncPlaySession != inSession) {
                        _uiState.update { it.copy(isInSyncPlaySession = inSession) }
                    }
                }
        }

        // Headphone unplug auto-pause (delegated to the shared audio-lifecycle owner).
        playerAudioLifecycle.registerBecomingNoisy()

        launch {
            // Upstream is the session's DIRECT alias of the manager's flow —
            // same StateFlow instance, so dispatch ordering relative to the
            // engineFlow collector below is unchanged. No operators/buffering:
            // each emission folds synchronously through the projector
            // (title/subtitle + media mirror + stored-selection seed every
            // emission; on an item/series change: preference refresh then a
            // fire-and-forget render poke) — MediaContentProjector's
            // onSessionState is the whole former inline body, VM-lifetime
            // fold state included (never reset per item, so it survives
            // releaseInternalsVmPart like the collector's local vars did).
            playbackSession.sessionState.collect { session ->
                mediaContentProjector.onSessionState(session, session.mediaDetail?.item?.seriesId)
            }
        }

        // Reflect the resolved per-item/series language preference into the
        // track slice (series-pref toggle rows) + dialogue boost so the sheets
        // show the series-pref toggle state.
        launch {
            playbackPreferenceResolver.resolved.collect { pref ->
                // Dialogue Boost is resolved per-item: a stored rule
                // pins the strength; otherwise the effective default is OFF (NONE),
                // so the effect never silently carries across items. The global
                // setting is intentionally NOT used as the auto fallback here.
                val resolvedBoost = pref?.dialogueBoostStrength
                    ?: com.raulshma.jellyplay.core.model.EffectStrength.NONE
                _uiState.update {
                    it.copy(
                        dialogueBoostStrength = resolvedBoost,
                        dialogueBoostEnabled = resolvedBoost != com.raulshma.jellyplay.core.model.EffectStrength.NONE,
                    )
                }
                trackSelectionHelper.onSeriesPreferenceResolved(pref)
                updateConfigWithUiState()
                // Re-apply the language preference once it resolves. The DAO
                // read in ItemPlaybackPreferenceResolver is async; on next-episode
                // autoplay the engine often publishes its track list (triggering
                // updateTracksFromEngine) before the preference lands. Without
                // re-running here, the preference never gets applied for that
                // load. Only re-run when a language preference actually exists so
                // we don't churn on null resolutions (no engine yet ⇒ no-op).
                val hasLangPref = pref?.audioLanguage != null || pref?.subtitleLanguage != null ||
                    pref?.subtitleDisabled == true
                if (hasLangPref) {
                    trackSelectionHelper.updateTracksFromEngine()
                }
            }
        }

        launch {
            playerSessionManager.engineFlow.collect { engine ->
                // The whole ordered engine-attach choreography (previous-
                // collectors cancel → bind → style seed → capability mirror
                // → effects seed → cast strategy → delay heads-up → PiP
                // mirror → track reset → the three per-engine collectors,
                // and clearEngine on the null arm) lives in
                // [engineAttachController] — jvmTest-pinned there; this
                // collector is the single forwarding point.
                engineAttachController.attach(engine)
            }
        }
    }

    /**
     * Starts (or restarts, after the session re-arms a disposed coordinator)
     * the engine-event MIRROR collectors: the coordinator's guarded
     * play/buffering flows turned into uiState writes and collaborator calls
     * (SyncPlay, PiP). Called once from `init` and again through the
     * session's rearm callback when a disposed coordinator is re-created —
     * this VM is Activity-scoped and survives release() across media, so the
     * mirrors must be re-armed alongside it. Decision *execution* lives in
     * [PlaybackSession] (B2); its outcomes arrive as [SessionEvent]s.
     */
    private fun startEngineEventCoordinatorOutputs() {
        engineEventOutputsJob?.cancel()
        val coordinator = playbackSession.engineEventCoordinator
        engineEventOutputsJob = launch {
            // The play-state mirror (uiState write + SyncPlay forward + PiP
            // icon) is the shared [mirrorPlaying] collector — its same-value
            // guard and fan-out order live in player-contract.
            mirrorPlaying(
                coordinator.isPlaying,
                // The mirror already swallows same-value emissions; this second
                // check trims only the re-arm replay, where the live uiState
                // may already hold the replayed value — skip the copy so the
                // uiState collectors are not invalidated.
                { isPlaying ->
                    _uiState.update { s ->
                        if (s.isPlaying == isPlaying) s else s.copy(isPlaying = isPlaying)
                    }
                },
                { isPlaying -> syncPlay.onIsPlayingChanged(isPlaying) },
                { isPlaying -> pipController.setPlaying(isPlaying) },
            )
            launch {
                coordinator.isBuffering.collect { buffering ->
                    _uiState.update { s ->
                        if (s.isBuffering == buffering) s else s.copy(isBuffering = buffering)
                    }
                }
            }
            launch {
                // Step 4's interaction signal (feature 1.3): every
                // user-initiated play/pause/seek/speed command and screen
                // interaction routes through the coordinator's ONE intake;
                // this collector feeds the same signal to the still-watching
                // episode counter (the pass-out clock is reset inside the
                // intake itself).
                coordinator.userInteractions.collect { autoplayController.onUserInteraction() }
            }
        }
    }

    /**
     * The transport routing funnel (A1): SyncPlay group first, cast receiver
     * second, local engine last — the same order [seekByStep] routes seeks
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
     * controller's connection flow value) — the same seam [seekByStep]
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
     * `resolveOfflineResumeTicks` (the load spine's resolution hook) moved
     * into [VideoSessionHost] with the rest of the load-hook bodies — it is
     * a one-line delegate to
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
     * collaborators) and handed to [videoSessionHost] as its
     * `applyItemHydration` command. Restores the per-item persisted video
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
            updateConfigWithUiStateDebounced()
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
     * Stays VM-side (reached through [videoSessionHost]'s `routeRemotePlay`
     * command): it writes the ui state's initializing flag and reads the
     * remote-play strategy — both VM-owned.
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
        updateConfigWithUiStateDebounced()
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

    /**
     * Owns the runtime engine-config sync (the [SubtitleStyleController]
     * shape): the [EngineConfigBuilder] invocation over narrow state slices +
     * the live-engine dispatch — both trigger paths moved VERBATIM: the
     * immediate rebuild (`updateConfigWithUiState` → [EngineConfigSync.markDirty])
     * and the drag-settling debounce (the `configChangeIntent` SharedFlow +
     * `configSyncJob` collector + the CONFIG_SYNC_DEBOUNCE_MS window →
     * [EngineConfigSync.markDirtyDebounced]). The uiState bag never crosses
     * (god-count ratchet unmoved); `getEngine` is read at dispatch time, so a
     * debounce settling after an engine swap lands on the NEW engine.
     */
    private val engineConfigSync = EngineConfigSync(
        scope = scope,
        getSubtitleStyle = { _uiState.value.subtitleStyle },
        getVideoEffects = { _uiState.value.videoFx.videoEffects },
        isDialogueBoostEnabled = { _uiState.value.dialogueBoostEnabled },
        getDialogueBoostStrength = { _uiState.value.dialogueBoostStrength },
        getMediaStreams = { _uiState.value.media.mediaStreams },
        getEffectsState = { effects.state.value },
        getAggregate = { cachedAggregate },
        // the session's effective mpv config (global slice + the
        // item/series render override + in-sheet quality pick) rides EVERY
        // runtime build — the render sheet's writes reach the engine
        // through this path (the engines' diff caches apply the delta).
        getEngineSpecific = { sessionRender.effectiveMpvConfig(cachedAggregate.engine.mpvConfig) },
        // the session-scoped deinterlace cycle.
        getDeinterlace = { sessionRender.deinterlace },
        getEngine = { playerSessionManager.engine },
    )

    // Explicit Unit returns: bare expression bodies would pull the
    // later-declared engineConfigSync into an inference cycle with `effects`.
    private fun updateConfigWithUiState(): Unit = engineConfigSync.markDirty()

    private fun updateConfigWithUiStateDebounced(): Unit = engineConfigSync.markDirtyDebounced()

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
                    mode = cachedAggregate.videoPlayer.stillWatchingMode,
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
    // write) moved into VideoSessionHost with the rest of the load-hook
    // bodies; the cinema SEQUENCING is session-owned since B4.

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
    // createMediaSession hook) moved into VideoSessionHost — a one-line
    // delegate to MediaSessionController.createForItem.

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
        mediaDetail = null
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
        if (playbackSession.released) return
        playbackSession.released = true
        performRelease()
    }

    override fun onCleared() {
        super.onCleared()
        release()
        // Same cancel-after-release ordering as before the release scope became
        // an injected constructor parameter: the final stop-report /
        // pending-seek join (launched on the release scope by release()) run
        // first, and the scope is only cancelled once the owner is going away
        // for good.
        releaseScope.cancel()
    }

    private fun performRelease() {
        // The now-playing seam's Stopped event (feature 4.2): the FULL
        // teardown — not the per-item re-initialization, which shares
        // [PlaybackSession]'s internals release — abandons playback without
        // an end-of-stream, so the shell-level consumers (Discord presence,
        // hooks) drop the activity here.
        nowPlayingReporter.clear()
        pipController.requestAutoEnterPip(false)
        // Tear down the engine-event collectors BEFORE the engine is released so
        // no policy observes a released engine mid-teardown (the decisions
        // executor is additionally idempotent after release).
        playbackSession.engineEventCoordinator.dispose()
        // Tear down audio-focus + becoming-noisy (idempotent; safe if never registered).
        playerAudioLifecycle.release()
        sleepTimer.onRelease()
        // Full teardown (B3): the session owns the tail — snapshot of the
        // stop-report inputs, the releaseInternals split (session half, then
        // this VM's [releaseInternalsVmPart] half, back-to-back), the VM's
        // post-internals release steps passed as the callback, then the
        // pending-seek join and the final stop-report on the release scope.
        playbackSession.release {
            // Full teardown: clear the transport too (releaseInternals keeps it so
            // PiP stays usable across per-item reloads while the VM is alive).
            pipController.reset()
            castManager.releaseConsumer()
            activePlayerController.clearEngine()
            // the deinterlace cycle (and the render sheet's session
            // lenses) are session-scoped — they revert on player exit, while
            // the persisted override rows survive for the next playback.
            render.onReleased()
            // drop the volume-memory capture hook with the engine it
            // was armed on (the lambda holds this VM through `launch`).
            playerSessionManager.engine?.onUserVolumeChange = null
        }
    }

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
