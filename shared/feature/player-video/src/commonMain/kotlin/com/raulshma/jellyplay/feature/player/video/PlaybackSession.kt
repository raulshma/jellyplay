package com.raulshma.jellyplay.feature.player.video

import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.core.concurrency.TaskBundle
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.SleepCountdown
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.playback.dischargePipDismissal
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackFocus
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackSurfaceId
import com.raulshma.jellyplay.core.data.playback.focus.VideoFocusPolicyInput
import com.raulshma.jellyplay.core.data.playback.focus.claimOnPlayEdge
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.playback.NowPlayingReporter
import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.PlaybackPrefScope
import com.raulshma.jellyplay.core.model.isWatchedPercentage
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import com.raulshma.jellyplay.core.model.mediaRuleContentType
import com.raulshma.jellyplay.feature.player.video.engine.EngineDecision
import com.raulshma.jellyplay.feature.player.video.engine.EngineEventCoordinator
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.EngineSessionShell
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.toEngineEventSource
import com.raulshma.jellyplay.feature.player.video.chrome.mirrorPlaying
import com.raulshma.jellyplay.feature.player.video.state.InputBindingToggleController
import com.raulshma.jellyplay.feature.player.video.state.ReadySubtitleHint
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayController
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayPreparation
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_direct_play_fallback
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_error_next_episode_load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString

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

/** Minimum wall-clock interval (ms) between throttled process-death persists. */
private const val POSITION_PERSIST_MIN_WALL_CLOCK_INTERVAL_MS = 5_000L

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

private const val LOAD = "PlaybackSession.load"
private const val SEEK_PROGRESS = "PlaybackSession.seekProgress"

/**
 * One playback session's lifecycle — the player's DEEP MODULE and, since the
 * C6 collapse, its COMPOSITION ROOT: the collaborator graph that the deleted
 * [PlayerWiring] builder used to construct in its two-phase protocol is built
 * here, inside the session, in one deliberate construction pass (the class
 * body's collaborator section, in dependency order) and armed by [arm] (the
 * former phase 2: the builder's late-bound back-reference slots and the
 * collector registrations collapsed into this one fun — there are no slots
 * left to bind, because the cycles they broke dissolved by OWNERSHIP:
 *
 *  - cycle 1 (projector ↔ detail projection): [MediaContentProjector]
 *    constructs and owns [MediaDetailProjection] — the dependency flows one
 *    way through its own fold methods;
 *  - cycles 2 + 3 (the reporter's id/persist reads, the cinema handoff, the
 *    start report, the stream-change reload, the playhead pre-seed): the
 *    reporter, the load hooks and the track helper are constructed BY the
 *    session and reach it through self-references (this session's own members
 *    — the owner is always there when a lambda runs), never through a
 *    back-reference slot;
 *  - cycle 5 (track helper ↔ preference writer): one forward reference
 *    through the composition root's body, severed at compile time by the
 *    helper's explicit type — no runtime seam;
 *  - cycle 6 (render/style/effects/prefs-fanout → config sync): the dirty
 *    triggers route through the session's own [markEngineConfigDirty]
 *    funnel — the controllers depend on their owner, the sync reads their
 *    state backwards;
 *  - cycle 7 (session subtitle sourcing ↔ session manager): the sourcing is
 *    declared first and reads the manager through the root's
 *    explicitly-typed [playerSessionManager] property.
 *
 * The ViewModel keeps the user-intent funnel ([VideoPlayerViewModel.onEvent]
 * + its transport/policy handlers), the state-holder construction and the
 * expose-only flows; the session owns the collaborator graph, the load
 * sequence, the engine-swap choreography, the reporting + release surface and
 * the single forwarder that maps [SessionEvent]s into the VM's sinks (the
 * genuinely VM-bound outcomes ride the [SessionHostLambdas] bundle).
 *
 * Construction contract:
 * - both scopes are INJECTED, never constructed here: [scope]
 *   (session-launched coroutines, e.g. the coalesced seek-mirror write
 *   tracked by the seek-progress task slot — never on [releaseScope]) is the
 *   owner's scope, and [releaseScope] is built here (the teardown work that
 *   must outlive the viewModelScope on clear(); the owner cancels it from
 *   `onCleared` AFTER release(), the cancel-after-release ordering);
 * - the wall [clock] is injectable for tests;
 * - the engine-room collaborators ([playerSessionManager],
 *   [progressReporter], [sessionLoadPipeline], [mediaSessionController], the
 *   [hooks]) are built here from the constructor dependencies; each accepts a
 *   nullable test override so the behavioral suites can substitute recording
 *   doubles without a production no-op path;
 * - the ui-mirror seams the session reads and writes (streaming quality,
 *   playback-mode mirror, incognito gate, cinema-intro state, playhead seed,
 *   pass-out hours…) are derived HERE over the [handles] bundle — the
 *   session owns its ui-mirror writes through the same narrow-lambda
 *   discipline the controllers always had (no controller sees the state bag;
 *   the mirrored reads stay [VideoPlayerUiState]-name-free via inference);
 * - [directPlayFallbackNotice] is injectable so tests can capture the raw
 *   error text; production resolves the localized resource.
 */
internal class PlaybackSession(
    val scope: CoroutineScope,
    /**
     * Wall-clock millis behind the seek-latch freshness window
     * ([getReportPositionMs]), the position-persist throttle
     * ([persistPlaybackPosition]) and the process-death staleness check — a
     * WALL clock (not monotonic), because the persisted-at stamps it is
     * compared against were written by a previous process. Injectable for
     * tests.
     */
    private val clock: () -> Long = { System.currentTimeMillis() },

    // ── Engine-room test seams ───────────────────────────────────────────────
    //    Production builds each of these internally (the composition root's
    //    collaborator section below); the behavioral suites pass recording
    //    doubles through these nullable overrides.

    /** Test override; production builds the manager from the dependencies below. */
    playerSessionManagerOverride: PlayerSessionManager? = null,
    /** Test override; production builds the reporter with self-referencing seams. */
    progressReporterOverride: PlaybackProgressReporter? = null,
    /** Test override; production builds the pipeline with `outputs = this`. */
    sessionLoadPipelineOverride: SessionLoadPipeline? = null,
    /** Test override; production builds it from the session stack's factory. */
    mediaSessionControllerOverride: MediaSessionController? = null,
    /** Test override; production the session itself implements the hooks. */
    private val hooksOverride: SessionLifecycleHooks? = null,
    /** Test override; production resolves the localized FORCE_DIRECT_PLAY fallback notice. */
    directPlayFallbackNotice: (suspend (String) -> String)? = null,

    // ── Constructor dependencies (what the deleted wiring builder injected) ──

    /**
     * Aggregate platform seam: the factory methods for the trickplay /
     * cast-controller / becoming-noisy collaborators, the playback-focus
     * authority + the VIDEO-family commandable surface, and the offline-media
     * probe the session manager consumes.
     */
    private val platform: VideoPlayerPlatform,
    private val stores: PlayerStores,
    private val imageUrlProvider: ImageUrlProvider,
    private val itemPlaybackPreferenceRepository: ItemPlaybackPreferenceRepository,
    val castManager: CastManager,
    /** The "Play On" routing strategy — the load's remote-play early-return reads it. */
    private val jellyfinRemotePlayCastStrategy: JellyfinRemotePlayCastStrategy,
    private val syncPlayManager: SyncPlayManager,
    private val adaptiveBitrateManager: AdaptiveBitrateManager,
    private val networkMonitor: NetworkMonitor,
    private val activePlayerController: ActivePlayerController,
    private val pipController: PipController,
    private val videoMiniPlayerState: VideoMiniPlayerState,
    private val sleepCountdown: SleepCountdown,
    private val userMessageBus: PlayerVideoMessageBus,
    /** Reached ONLY through the [SessionPositionStore] built from it. */
    private val savedStateHandle: SavedStateHandle,
    private val userDataMutator: UserDataMutator,
    /**
     * The deep "playback source resolver" — single owner of the
     * completed-download predicate: the load spine's offline-resume resolution
     * (the pipeline's `resolveOfflineResumeTicks` hook) and the session
     * manager's usable-download gate both read it.
     */
    private val playbackSourceResolver: PlaybackSourceResolver,
    /**
     * The app-wide now-playing seam (feature 4.2): the session manager
     * publishes loads through it; the full teardown ([performRelease]) clears
     * it — NOT the per-item re-initialization, where the session manager's
     * release also runs.
     */
    private val nowPlayingReporter: NowPlayingReporter,
    private val subtitleSources: PlayerSubtitleSources,
    private val offlineSources: PlayerOfflineSources,
    private val sessionStack: PlayerSessionStackSources,
    private val itemContent: PlayerItemContentSources,
    private val handles: PlayerStateHandles,
    /** The owner's transport/policy funnels — see [SessionHostLambdas]. */
    private val host: SessionHostLambdas,

    // ── Engine-room dependencies (kept injectable for the behavioral suites) ──

    private val playbackStore: PlaybackStore,
    /** Server playback telemetry (the session-owned Stop/Start reports). */
    private val playbackRepository: PlaybackRepository,
    /** Offline-mirror writes for the seek-coalesced + throttled position persists. */
    private val offlinePlaybackFacade: OfflinePlaybackFacade,
    /** Media-detail fetch for the mini-player reclaim body. */
    private val mediaRepository: MediaRepository,
    /** The process-death resume-position persistence; production: the SavedStateHandle store. */
    positionStoreOverride: SessionPositionStore? = null,
) : SessionLoadOutputs, SessionLifecycleHooks {

    // ── The state-holder bundle, unpacked ────────────────────────────────────
    //
    //    Inferred aliases (no state-bag type name crosses this file — the
    //    migrated-controller ratchet): every session write goes through these
    //    narrow handles exactly as the wiring builder's did.

    private val uiState get() = handles.uiState
    private val positionMs get() = handles.positionMs
    private val durationMs get() = handles.durationMs
    private val videoStats get() = handles.videoStats
    private val resumeReminder get() = handles.resumeReminder
    private val closePlayer get() = handles.closePlayer
    private val passOutEvents get() = handles.passOutEvents

    // ── The ui-mirror seams (derived, not injected) ───────────────────────────
    //
    //    The values the moved code used to receive as constructor lambdas from
    //    the VM, now derived over the handles + the cached aggregate. The
    //    lambdas run only from session/engine call chains — long after every
    //    collaborator below initializes — so the forward reads are safe.

    /** Current in-memory streaming quality (the ui-prefs mirror). */
    private val getStreamingQuality = { uiState.value.uiPrefs.streamingQuality }

    /** Writes the in-memory playback-mode mirror. */
    private val setUiPlaybackMode = { mode: PlaybackMode ->
        uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(playbackMode = mode)) }
    }

    /** Incognito gate for the session-owned stop/start reports. */
    private val getIncognitoModeEnabled = { cachedAggregate.videoPlayer.incognitoModeEnabled }

    /** Feeds the track-selection helper's pending stream selection before a stream-change reload. */
    private val setPendingStreams = { selection: MediaStreamSelection? ->
        trackSelectionHelper.setPendingStreams(selection)
    }

    /** Synchronous playback-mode read feeding the coordinator's fallback latch policy. */
    private val getPlaybackMode = { uiState.value.uiPrefs.playbackMode }

    /** Localized FORCE_DIRECT_PLAY fallback notice for [SessionEvent.InformUser]. */
    private val resolveDirectPlayFallbackNotice = directPlayFallbackNotice
        ?: { errorText: String -> getString(Res.string.player_direct_play_fallback, errorText) }

    /** Pass-out protection hours; values <= 0 disable the poller. */
    private val passOutHours: Flow<Int> =
        uiState.flow.map { it.uiPrefs.passOutProtectionHours }.distinctUntilChanged()

    /**
     * Whether the still-watching mode includes HOURS (feature 1.3): a tripped
     * pass-out pause then arrives as [SessionEvent.StillWatchingPrompt] (the
     * confirm overlay) instead of the bare [SessionEvent.PassOutPause] toast.
     */
    private val upgradesPassOutToOverlay = {
        StillWatchingGate.upgradesPassOutToOverlay(cachedAggregate.videoPlayer.stillWatchingMode)
    }

    /**
     * The uiState `cinemaIntroState` write seam: every write the cinema
     * sequencing needs flows through this setter — the same seam shows
     * ([loadCinemaIntro]) and clears ([advanceCinemaIntro]) the intro.
     */
    private val setCinemaIntroState = { state: CinemaIntroUiState? ->
        uiState.update { it.copy(cinemaIntroState = state) }
    }

    /**
     * Writes the high-frequency position display flow — the seam behind
     * [preSeedPlayhead].
     */
    private val seedDisplayedPositionMs = { seed: Long ->
        positionMs.value = seed
    }


    // ── The collaborator graph (the composition root's construction pass) ────
    //
    //    Built once, in dependency order, with their wiring lambdas attached
    //    at their construction sites. Every former construction cycle is
    //    dissolved by ownership — see the class KDoc for the per-cycle map.

    /**
     * Cycle 7's half: the session's subtitle-sourcing collaborator (the
     * load-spine sourcing bodies extracted from [PlayerSessionManager]):
     * the streaming-store / offline-manifest / server-stream side-load
     * builders plus the attach-new diff. Declared BEFORE the manager — the
     * manager takes it as a constructor argument — so its wiring lambdas read
     * the manager through the explicitly-typed [playerSessionManager] property
     * below; they run only from load chains, long after construction.
     */
    private val sessionSubtitleSources = SessionSubtitleSources(
        streamingSubtitleStore = subtitleSources.streamingSubtitleStore,
        downloadRepository = offlineSources.downloadRepository,
        playbackRepository = playbackRepository,
        addExternalSubtitle = { this.playerSessionManager.addExternalSubtitle(it) },
        getExternalSubtitles = { this.playerSessionManager.currentExternalSubtitles },
        getCurrentItemId = { this.playerSessionManager.sessionState.value.currentItemId },
        getCurrentPlayMethod = { this.playerSessionManager.sessionState.value.playMethod },
        matchPlayingMediaSource = { detail ->
            this.playerSessionManager.matchedMediaSource(detail, fallbackToFirst = true)
        },
        getEngineCapabilities = { this.playerSessionManager.engine?.capabilities },
    )

    /**
     * The session manager (the engine stack + load bookkeeping deep module).
     * Explicit type: severs the sourcing's forward-reference inference (cycle
     * 7) at compile time — no runtime seam. Preferred-version memory: item
     * scope wins over series scope, the same precedence the
     * ItemPlaybackPreferenceResolver applies to the language rows; consulted
     * by loadOnline before the first-sources fallback.
     */
    internal val playerSessionManager: PlayerSessionManager = playerSessionManagerOverride
        ?: PlayerSessionManager(
            scope = scope,
            mediaRepository = mediaRepository,
            playbackRepository = playbackRepository,
            imageUrlProvider = imageUrlProvider,
            playbackIdentity = sessionStack.playbackIdentity,
            offlineRepository = offlineSources.offlineRepository,
            aggregateStore = stores.aggregateStore,
            playerLifecycleManager = sessionStack.playerLifecycleManager,
            adaptiveBitrateManager = adaptiveBitrateManager,
            playerEngineFactory = sessionStack.playerEngineFactory,
            pipController = pipController,
            playbackSourceResolver = playbackSourceResolver,
            sessionSubtitleSources = sessionSubtitleSources,
            offlineMediaProbe = platform.offlineMediaProbe,
            offlineModeManager = offlineSources.offlineModeManager,
            userMessageBus = userMessageBus,
            getPreferredMediaSourceId = { itemId, seriesId ->
                itemPlaybackPreferenceRepository.get(PlaybackPrefScope.ITEM, itemId)
                    ?.preferredMediaSourceId
                    ?: seriesId?.let {
                        itemPlaybackPreferenceRepository.get(PlaybackPrefScope.SERIES, it)
                            ?.preferredMediaSourceId
                    }
            },
            nowPlayingReporter = nowPlayingReporter,
        )

    // ── Engine-event orchestration ──────────────────────────────────────────
    // The player-contract EngineSessionShell owns the session-structural
    // plumbing both players used to hand-roll: the EngineEventCoordinator's
    // construction/re-arm/dispose, the engine-event intake wiring, the
    // decision fan-out to the executor below and the one-shot SessionEvent
    // pipe. The session keeps the DECISION EXECUTION (reload choreography,
    // reporting, engine commands) and the `released` latch — the shell never
    // sees them. VOD pins: the coordinator Config defaults
    // (FallbackPolicy.FORCE_DIRECT_PLAY_ONE_SHOT +
    // WatchdogScope.INITIAL_BUFFER_ONLY).
    private val engineEventShell = EngineSessionShell<SessionEvent>(
        scope = scope,
        // The coordinator consumes the engine-agnostic EngineEventSource slice;
        // each MediaEngine swap maps to a fresh source (same emission points as
        // when the coordinator collected the engine flow directly).
        engineSources = playerSessionManager.engineFlow.map { it?.toEngineEventSource() },
        onDecision = ::executeEngineDecision,
        config = EngineSessionShell.Config(
            getPlaybackMode = getPlaybackMode,
            directPlayFallbackNotice = resolveDirectPlayFallbackNotice,
            passOutHours = passOutHours,
            onRearmed = { startEngineEventCoordinatorOutputs() },
        ),
    )

    /**
     * The live coordinator (the shell's current instance). Exposed so the VM
     * can drive the pieces that stay VM-owned: the mirror collectors
     * ([isPlaying]/[isBuffering] ui-state writes), the latch resets
     * ([EngineEventCoordinator.onNewItem] /
     * [EngineEventCoordinator.onPlaybackModeChanged]), the interaction clock
     * ([EngineEventCoordinator.onUserInteraction]) and the teardown-time
     * [EngineEventCoordinator.dispose] — the shell re-arms a disposed
     * instance on the next [initialize].
     */
    internal val engineEventCoordinator: EngineEventCoordinator
        get() = engineEventShell.coordinator

    /**
     * Session-level outcomes (errors to surface, user notices, end of
     * playback, close/pass-out requests) emitted by the decision fan-out and
     * the reload paths. The session is the single forwarder: the arm-phase
     * collector below maps each event into the VM's sinks (the genuinely
     * VM-bound ones through [host]). `tryEmit`-only — a mid-teardown emission
     * never suspends (same contract as the coordinator's decision stream).
     */
    val events: SharedFlow<SessionEvent> = engineEventShell.events

    // Task slots for the session's cancel-and-replace choreographies. The
    // bundle owns only the slot bookkeeping; scope lifecycle (the injected
    // owner scope + releaseScope) stays exactly where it was.
    private val sessionTasks = TaskBundle(scope)

    /**
     * Teardown scope for the work that must outlive the viewModelScope on
     * clear() — the final stop-report and the pending-seek join launch here —
     * IO dispatcher + supervisor so one failing write cannot cancel the
     * other. The owner cancels it in `onCleared` AFTER release(), preserving
     * the cancel-after-release ordering.
     */
    internal val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The process-death resume-position persistence (production: the SavedStateHandle store). */
    private val positionStore: SessionPositionStore =
        positionStoreOverride ?: SavedStateHandlePositionStore(savedStateHandle)

    /**
     * Direct alias of the session manager's session flow — the SAME
     * [StateFlow] instance, no re-publish and no stateIn, so dispatch
     * ordering relative to [engineFlow] collectors is unchanged from when the
     * ViewModel collected the manager directly.
     */
    val sessionState: StateFlow<PlayerSessionState> = playerSessionManager.sessionState

    /** Direct alias of the session manager's engine flow — same instance, no re-publish. */
    val engineFlow: StateFlow<MediaEngine?> = playerSessionManager.engineFlow

    internal val autoplayController = AutoPlayController()

    /** The platform trickplay controller handle; the load-time selection lives on [trickplayPreparation] below. */
    internal val trickplayManager: TrickplayController = platform.createTrickplayController(playbackRepository)

    /**
     * The trickplay three-way selection for the session load spine (server
     * manifest cached into the download dir → locally bundled meta.json →
     * live server fetch cached for the next offline session). Owns the dir
     * derivations, the download-path probe, the precedence and the
     * controller dispatch; the single uiState write rides the loadHooks'
     * initializeTrickplay below.
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
        subtitleProviderRepository = subtitleSources.subtitleProviderRepository,
        streamingSubtitleStore = subtitleSources.streamingSubtitleStore,
        userMessageBus = userMessageBus,
        scope = scope,
        addExternalSubtitle = { playerSessionManager.addExternalSubtitle(it) },
        getMediaStreams = { uiState.value.media.mediaStreams },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getCurrentSourceId = { playerSessionManager.sessionState.value.currentMediaSource?.id },
        onMediaDetailRefreshed = { refresh -> mediaContentProjector.applyRefreshedDetail(refresh) },
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
        isOffline = { offlineSources.offlineModeManager.isOffline },
    )

    internal val sleepTimer = SleepTimerController(
        sleepCountdown = sleepCountdown,
        audioStore = stores.audio,
        scope = scope,
        getEngine = { playerSessionManager.engine },
        isMuted = { uiState.value.isMuted },
    )

    internal val abRepeat = AbRepeatController(
        scope = scope,
        getEngine = { playerSessionManager.engine },
        positionFlow = positionMs.asStateFlow(),
    ).also { it.start() }

    internal val cast = platform.createCastController(
        playbackRepository = playbackRepository,
        imageUrlProvider = imageUrlProvider,
        adaptiveBitrateManager = adaptiveBitrateManager,
        syncPlayCastStore = stores.syncPlayCast,
        getEngine = { playerSessionManager.engine },
        getCurrentPlaybackMode = { uiState.value.uiPrefs.playbackMode },
        getSessionState = { playerSessionManager.sessionState.value },
    )

    private val settingsProjector = SettingsProjector(
        getUiState = { uiState.value },
        updateUiState = { transform -> uiState.update(transform) },
        getItemId = { playerSessionManager.sessionState.value.currentItemId },
        getMediaStreams = { uiState.value.media.mediaStreams },
    )

    /**
     * The `media` slice's single writer (the EpisodeNavigator
     * `updateEpisodes` seam shape): every MediaContentState write routes
     * through it, and the refreshed-detail choreography ORDER (session
     * manager first, streams write, track rebuild last) lives there,
     * jvmTest-pinned. Cycle 1's ownership: this projector CONSTRUCTS the
     * detail-application cluster ([MediaDetailProjection]) inside itself, so
     * the former projector↔projection mutual reference flows one way.
     */
    internal val mediaContentProjector = MediaContentProjector(
        updateMedia = { update ->
            uiState.update { it.copy(media = update(it.media)) }
        },
        applyRefreshedDetail = { detail, attachToEngine ->
            playerSessionManager.applyRefreshedDetail(detail, attachToEngine)
        },
        matchMediaSource = { detail ->
            playerSessionManager.matchedMediaSource(detail, fallbackToFirst = true)
        },
        onStreamsRefreshed = { streams, newSubtitleStreamIndex ->
            uiState.update { it.copy(videoFx = it.videoFx.copy(detectedAspectRatio = detectAspectRatio(streams))) }
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
        // collector had. The lambdas capture only stable handles and read
        // later-declared collaborators lazily — invoked only from the
        // arm-phase collector, long after those properties initialise.
        setTitleSubtitle = { title, subtitle ->
            uiState.update { it.copy(title = title, subtitle = subtitle) }
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
            scope.launch { block() }
        },
        // The owned detail-application cluster's inputs (cycle 1's ownership).
        scope = scope,
        lyricsRepository = itemContent.lyricsRepository,
        volumeProfileStore = stores.volumeProfile,
        setDetail = { detail -> mediaDetail = detail },
        setChapters = { chapters ->
            uiState.update { it.copy(chapters = chapters) }
        },
        artworkUrl = { itemId -> imageUrlProvider.getImageUrl(itemId, maxWidth = 400) },
        adoptSeasonOf = { detail -> episodeContinuation.adoptSeasonOf(detail) },
        getEngine = { playerSessionManager.engine },
    )

    /**
     * The becoming-noisy auto-pause owner (headphone unplug → pause; the
     * focus half of the former audio-lifecycle moved into the PlaybackFocus
     * module at the video slice) plus its opt-in resume-on-headset-insert
     * twin. Registered in [arm], released in [performRelease]. [getEngine] is
     * re-read on every broadcast so engine swaps (retry/fallback) and
     * teardown stay correct; the resume pref is re-read at every plug event
     * (the store's StateFlow `value`), so a mid-session settings flip is
     * observed live.
     */
    private val becomingNoisy = platform.createBecomingNoisy(
        getEngine = { playerSessionManager.engine },
        isResumeOnPlugEnabled = { stores.videoPlayer.videoPlayer.value.videoResumeOnHeadsetPlug },
    )

    // ── Cross-player exclusivity (the video focus slice, ADR-0004) ──────────
    // Both ride the platform aggregate seam: the module-owned exclusivity
    // authority — VIDEO claims ride the play edge (the music manager's
    // `onIsPlayingChanged` pattern), OS losses come back as surface commands —
    // and the VIDEO-family commandable surface singleton (null on desktop,
    // where the focus binding registers only the music surface and the
    // displaced-holder self-pause rides the claimState observer in [arm]).
    private val playbackFocus: PlaybackFocus = platform.playbackFocus
    private val videoFocusSurface = platform.videoFocusSurface

    /**
     * The PiP-facing surface (the [SubtitlePreviewController] shape): the
     * transport registration behind the PiP window's remote actions and the
     * aspect/source-rect pushes. Re-armed from [arm] AND from the
     * [SessionLifecycleHooks.rearmTransports] hook — see
     * PipTransportController's KDoc for why the re-arm must ride the load
     * lifecycle. The dispatch lambdas route through [host] (the owner's
     * transport funnels) or read later-declared collaborators lazily.
     */
    internal val pipTransport = PipTransportController(
        pipController = pipController,
        getEngine = { playerSessionManager.engine },
        routedPlay = { play -> host.routedPlay(play) },
        seekByStep = { direction -> host.seekByStep(direction) },
        playNextEpisode = { episodeContinuation.playNextEpisode() },
    )

    /**
     * The "Still watching?" confirm overlay's prompt lifecycle (feature 1.3) —
     * the [EpisodeContinuationController] shape: the prompt StateFlow and the
     * show/continue/stop/tick choreography live in [StillWatchingController];
     * the VM raises the overlay (the end-of-playback gate's episode arm in
     * its handlePlaybackEnded, the session's hours arm via
     * `SessionEvent.StillWatchingPrompt`) and forwards the overlay's event arms
     * one-line. Pure decisions: [StillWatchingGate] / [StillWatchingPromptState].
     */
    internal val stillWatching = StillWatchingController(
        getCountdownSeconds = { uiState.value.autoplay.autoPlayCountdownSec },
        onUserInteraction = { autoplayController.onUserInteraction() },
        playNextEpisode = { episodeContinuation.playNextEpisode() },
        resumePlayback = { host.resumePlayback() },
        pauseEngine = { playerSessionManager.engine?.pause() },
        cancelAutoplay = { episodeContinuation.cancelAutoplay() },
    )

    /**
     * The playback progress reporter. Cycles 2's reads dissolve as
     * SELF-references: the play-session id resolves through this session's
     * [currentPlaySessionId] resolver and the position persist calls this
     * session's [persistPlaybackPosition] — the owner is the late-arriving
     * dependency, delivered through the lambdas (no back-reference slot).
     */
    private val progressReporter: PlaybackProgressReporter = progressReporterOverride
        ?: PlaybackProgressReporter(
            playbackRepository = playbackRepository,
            scope = scope,
            uiState = uiState,
            getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
            getPlaySessionId = { currentPlaySessionId },
            getResolvedPlayMethod = { playerSessionManager.sessionState.value.playMethod },
            getMediaEngine = { playerSessionManager.engine },
            getIncognitoModeEnabled = getIncognitoModeEnabled,
            onAutoSkip = { segment -> host.autoSkipSegment(segment) },
            onPlaybackEndedNoNext = { host.onEndedWithNoNext() },
            onWatchedThresholdReached = { itemId ->
                // Forwarded into the episode-continuation controller declared
                // below — the lambda only runs long after construction, so its
                // (lazy) read of the not-yet-initialised property is safe.
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
                //
                // NonCancellable: this callback fires at the very end of playback,
                // exactly when VM teardown cancels the scope — without it the
                // launch body may never run and the PLAYED outbox row is never
                // enqueued. Losing that row is the #153 "watched offline, online
                // home shows mostly completed" bug — once written to the outbox
                // it survives anything, so the enqueue itself must land.
                scope.launch(NonCancellable) {
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
            onPositionPersisted = { positionMsValue ->
                persistPlaybackPosition(positionMsValue, force = false)
            },
            onEnginePositionUpdate = { position, duration, _, stats ->
                this.positionMs.value = position
                durationMs.value = duration
                videoStats.value = stats
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
                host.seekTo(positionTicks / 10_000, false)
            }
        },
        // Session-state write seam: the bridge no longer holds the UiState
        // handle; the play/pause mirror it maintained goes through this narrow
        // lambda.
        setIsPlaying = { playing -> uiState.update { s -> s.copy(isPlaying = playing) } },
        scope = scope,
    )

    /**
     * The session load's hook bundle — the load pipeline's VM-bound bodies,
     * wired at their construction site. Pure one-line delegates go straight
     * to their collaborator; the former session back-references (cycle 3:
     * the cinema handoff, the start report) are SELF-references into this
     * session's own members. The order the pipeline CALLS these in lives in
     * [SessionLoadPipeline] (pinned by SessionLoadPipelineTest) — this bundle
     * owns only the bodies. The three stage bodies with real logic
     * (`fetchMediaSegments`, `shouldAttemptCinemaMode`, `restoreRememberedMuted`)
     * are NOT here — they live in the pipeline, whose spine owns when each
     * stage runs.
     */
    private val loadHooks: SessionLoadHooks = SessionLoadHooks(
        reconcileSyncPlayQueue = { itemId, mediaSourceId, startPositionTicks ->
            syncPlay.reconcileQueueForItem(itemId, mediaSourceId, startPositionTicks)
        },
        // Cycle 3's cinema handoff: self-reference — the sequencing is
        // session-owned and the session constructs this bundle.
        beginCinemaMode = { intros, request -> beginCinemaMode(intros, request) },
        resolveOfflineResumeTicks = { itemId, startPositionTicks ->
            playbackSourceResolver.resolveStartPositionTicks(itemId, startPositionTicks)
        },
        onSessionPrefsApplied = { agg ->
            autoplayController.setEnabled(agg.videoPlayer.videoAutoplayNext)
            autoplayController.setStillWatchingThreshold(agg.videoPlayer.stillWatchingEpisodeThreshold)
        },
        // The per-item hydration body moved here from the ViewModel (its every
        // collaborator is session-internal now): the videoFx mirror write, the
        // engine-config rebuild nudge and the style-controller hydration.
        onItemHydrated = { itemId, hydratedAgg ->
            val hydratedEffects = hydratedAgg.engine.videoEffectsByItem[itemId] ?: VideoEffectsConfig()
            if (uiState.value.videoFx.videoEffects != hydratedEffects) {
                uiState.update { it.copy(videoFx = it.videoFx.copy(videoEffects = hydratedEffects)) }
                markEngineConfigDirtyDebounced()
            }
            subtitleStyleController.onItemHydrated(hydratedAgg.subtitle, itemId)
        },
        createMediaSession = { itemId, title, subtitle ->
            mediaSessionController.createForItem(itemId, title, subtitle)
        },
        applyMediaDetail = { detail -> mediaContentProjector.applyDetail(detail) },
        initializeTrickplay = { itemId, source ->
            // Trickplay selection + dispatch live in [TrickplayPreparation];
            // a non-null result means exactly one arm initialized the
            // controller, so this is the single uiState write the former
            // three inline arms produced between them.
            trickplayPreparation.prepare(itemId, source)?.let { info ->
                uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(trickplayInfo = info)) }
            }
        },
        // Cycle 3's start report: self-reference (the canonical resolver +
        // the incognito gate live on this session, beside the stop-report twin).
        reportPlaybackStart = { itemId, source, playMethod ->
            reportPlaybackStart(itemId, source, playMethod)
        },
        startPositionTracking = { progressReporter.startPositionTracking() },
        startProgressReporting = { progressReporter.startProgressReporting() },
        fetchAdjacentEpisodes = { detail -> episodeContinuation.refreshAdjacent(detail) },
        loadSeriesEpisodes = { detail -> episodeContinuation.loadSeries(detail) },
        // No terminal-outcome action today; stated explicitly here so a
        // future consumer is a construction-site change, not a hidden
        // default somewhere else.
        onOutcome = { },
    )

    /**
     * The extracted load spine: owns the ORDER of the load stages that the
     * initialize path used to inline (SyncPlay reconcile → prefs seed →
     * muted restore → cinema gate → offline resolution → loadMedia →
     * hydration → media session → trickplay → reports). Its uiState-shaped
     * outputs are this session ([SessionLoadOutputs]); its VM-bound bodies
     * are [loadHooks]. The three stage bodies with real logic are pipeline
     * members (see its KDoc).
     */
    private val sessionLoadPipeline: SessionLoadPipeline = sessionLoadPipelineOverride
        ?: SessionLoadPipeline(
            sessionManager = playerSessionManager,
            libraryApiClient = itemContent.libraryApiClient,
            aggregateStore = stores.aggregateStore,
            networkOfflineStore = stores.networkOffline,
            offlinePlaybackFacade = offlinePlaybackFacade,
            syncPlayManager = syncPlayManager,
            getMediaDetail = { mediaDetail },
            playbackRepository = playbackRepository,
            setMutedMirror = { muted -> uiState.update { it.copy(isMuted = muted) } },
            onSegmentsFetched = { segments ->
                uiState.update { it.copy(segmentState = it.segmentState.copy(segments = segments)) }
            },
            outputs = this,
            hooks = loadHooks,
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
     * Declared after the engine room (the navigator latches onto the
     * session's events). The lambdas capture only stable handles and read
     * later-declared collaborators lazily — invoked long after construction.
     */
    internal val episodeContinuation = EpisodeContinuationController(
        scope = scope,
        sessionState = playerSessionManager.sessionState,
        sessionEvents = events,
        episodeCatalogue = itemContent.episodeCatalogue,
        getDetail = { mediaDetail },
        getSeriesId = { mediaDetail?.item?.seriesId ?: uiState.value.media.seriesId },
        updateEpisodes = { update ->
            uiState.update { it.copy(episodes = update(it.episodes)) }
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
        hasNextEpisode = { uiState.value.episodes.nextEpisode != null },
        isInSyncPlaySession = { uiState.value.isInSyncPlaySession },
        closePlayer = { closePlayer.trySend(Unit) },
        cancelAutoplayDecision = { autoplayController.cancel() },
        setAutoplayCancelledMirror = { cancelled ->
            uiState.update { it.copy(autoplay = it.autoplay.copy(autoplayCancelled = cancelled)) }
        },
        isSmartDownloadsEnabled = { stores.downloads.downloads.value.smartDownloadsEnabled },
        getDurationMs = { uiState.value.duration },
        deleteDownload = { itemId -> offlinePlaybackFacade.deleteDownload(itemId) },
        notifySmartDownloadDeleted = { userMessageBus.info(PlayerVideoMessage.SmartDownloadDeleted) },
    )

    /**
     * The incognito/prefs gate cache the session-owned reports read off-Main.
     * The arm-phase aggregate collector keeps it fresh (see [arm]).
     */
    // @Volatile: written by the arm-phase aggregate collector, read
    // off-Main (the session's stop/start reports read the incognito gate
    // off-Main).
    @Volatile
    internal var cachedAggregate: VideoPlayerAggregate = VideoPlayerAggregate()

    /**
     * The single resolved media-detail holder — set by
     * [MediaDetailProjection.setDetail] (through the owned projector cluster),
     * cleared by the per-item teardown; read by the cinema gate, the episode
     * navigation and the PiP series mirror.
     */
    @Volatile
    internal var mediaDetail: MediaDetail? = null

    private val playbackPreferenceResolver = ItemPlaybackPreferenceResolver(
        repository = itemPlaybackPreferenceRepository,
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getCurrentSeriesId = { playerSessionManager.sessionState.value.mediaDetail?.item?.seriesId },
        scope = scope,
    )

    // Explicit type: severs the helper↔writer lambda-reference inference
    // (cycle 5) at compile time — the persist hook below reads the writer
    // (declared after this) through the composition root's body, no runtime
    // seam.
    internal val trackSelectionHelper: TrackSelectionHelper = TrackSelectionHelper(
        engineStore = stores.engine,
        subtitleStore = stores.subtitleLanguage,
        getEngine = { playerSessionManager.engine },
        getMediaStreams = { uiState.value.media.mediaStreams },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getCurrentSeriesId = { playerSessionManager.sessionState.value.mediaDetail?.item?.seriesId },
        getPlayMethod = { playerSessionManager.sessionState.value.playMethod },
        onReloadForStreamChange = { selection ->
            // Cycle 2's stream-change reload edge: SELF-reference — the
            // reload is session-owned and the session constructs this helper.
            reloadForStreamChange(selection)
        },
        playbackPreferenceResolver = playbackPreferenceResolver,
        persistRememberedTrack = { type, track ->
            // Cycle 5's helper→writer edge: the write-side twin is declared
            // below; the forward read through the composition root's body
            // (severed by this property's explicit type) replaces the former
            // lateinit back-reference slot.
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
        // Toggle: the cue preview (declared below — the lambda defers every
        // read, so the forward reference is init-order safe) refreshes like a
        // sheet pick when the toggle flips the active subtitle.
        onSubtitleSelectionChanged = { subtitlePreview.onTrackSelectionChanged() },
        scope = scope,
    )

    // The write-side twin of the resolver above: one owner of the save/clear +
    // mandatory-refresh choreography (see its KDoc). Declared after
    // trackSelectionHelper (whose persistRememberedTrack lambda reads it) while
    // its own wiring reads that helper back directly — the mutual lambda
    // reference is severed by the helper's explicit type.
    internal val playbackPreferenceWriter = ItemPlaybackPreferenceWriter(
        repository = itemPlaybackPreferenceRepository,
        getCurrentSeriesId = { playerSessionManager.sessionState.value.mediaDetail?.item?.seriesId },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        scope = scope,
        onPreferencesChanged = { trackSelectionHelper.refreshPlaybackPreferences() },
    )

    /**
     * The in-player input-binding quick toggle (issue #171): flips one
     * binding's enabled flag — the uiState write moves the gate immediately
     * (the detectors resolve through the mapping), the flip persists through
     * the store's read-modify-write verb (a settings-editor write is never
     * clobbered by a stale whole-map write).
     */
    internal val inputBindingToggle = InputBindingToggleController(
        getMap = { uiState.value.gestures.inputMap },
        updateMap = { map -> uiState.update { it.copy(gestures = it.gestures.copy(inputMap = map)) } },
        requestPersist = { bindingId, enabled ->
            scope.launch {
                stores.videoPlayer.updateVideoInputBindings { it.withBindingEnabled(bindingId, enabled) }
            }
        },
    )

    /**
     * Owns the "Rendering" sheet + deinterlace write choreography (the
     * [SubtitleStyleController] shape): the pure session semantics live in
     * [SessionRenderState] (composed as [RenderControls.state]); the
     * controller composes them with the repository/DataStore writes around
     * them — the global mpv slice persist, the per-item/series render-profile
     * rows and the session item-change re-resolution. The config-dirty
     * trigger routes through this session's [markEngineConfigDirty] funnel —
     * cycle 6's ownership pattern (the controllers depend on their owner; the
     * sync reads their state backwards). Engine-side application stays in the
     * sync: the controller only reports a dirty config through `onConfigDirty`.
     */
    internal val render = RenderControls(
        scope = scope,
        getGlobalMpvConfig = { cachedAggregate.engine.mpvConfig },
        saveGlobalMpvConfig = { config -> stores.engine.setMpvConfig(config) },
        loadStoredRow = { prefScope, id -> itemPlaybackPreferenceRepository.get(prefScope, id) },
        saveRenderProfile = { overrides -> playbackPreferenceWriter.setRenderProfile(overrides) },
        clearStoredRenderProfile = { playbackPreferenceWriter.clearRenderProfile() },
        onConfigDirty = { markEngineConfigDirty() },
    )

    /**
     * Owns the AV-sync sheet's cue preview (external track load + embedded cue
     * accumulation + the sheet-visibility gate) — the cluster that used to live
     * as the flat `subtitlePreviewCues` / `subtitlePreviewSource` /
     * `previewSheetVisible` uiState fields. The engine-attach choreography
     * feeds it the engine's cue list; track selection pokes it eagerly.
     */
    internal val subtitlePreview = SubtitlePreviewController(
        scope = scope,
        loadCues = { source, headers -> subtitleSources.subtitlePreviewRepository.loadCues(source, headers) },
        clearCuesCache = { subtitleSources.subtitlePreviewRepository.clearCache() },
        getExternalSubtitles = { playerSessionManager.currentExternalSubtitles },
        getPlaybackHeaders = { playerSessionManager.currentPlaybackHeaders },
        getSelectedSubtitleTrack = {
            trackSelectionHelper.state.value.subtitleTracks.firstOrNull { it.isSelected && it.index >= 0 }
        },
        getEngineCues = { playerSessionManager.engine?.currentCues?.value?.takeIf { it.isNotEmpty() } },
    )

    /**
     * Owns the subtitle-style + dialogue-boost + subtitle-delay choreography
     * Style edits, per-item delay writes and their debounced engine
     * re-sync, the per-item dialogue-boost persist, and — folded back from
     * SubtitleFontController (the style edit it performed WAS a
     * [SubtitleStyleController.setStyle] call; no init-order coupling, so the
     * fold is pure) — the user-font install and the direct engine re-apply of
     * the current style. Step-1 shape of the recorded design — the state
     * stays in the uiState mirrors, the controller writes through the
     * narrow lambdas below (no raw uiState handle crosses; the god-count
     * ratchet is untouched). The config triggers route through this
     * session's [markEngineConfigDirty] funnels (cycle 6).
     */
    internal val subtitleStyleController = SubtitleStyleController(
        scope = scope,
        getStyle = { uiState.value.subtitleStyle },
        setStyleMirror = { style ->
            uiState.update { it.copy(subtitleStyle = style) }
        },
        setDialogueBoostMirror = { strength, enabled ->
            uiState.update {
                it.copy(dialogueBoostStrength = strength, dialogueBoostEnabled = enabled)
            }
        },
        isDialogueBoostEnabled = { uiState.value.dialogueBoostEnabled },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getGlobalOffsetMs = { cachedAggregate.subtitle.subtitleStyle.offsetMs },
        saveGlobalStyle = { style -> stores.subtitleLanguage.setSubtitleStyle(style) },
        saveItemDelay = { itemId, delayMs -> stores.subtitleLanguage.setSubtitleDelayForItem(itemId, delayMs) },
        saveDialogueBoost = { strength -> playbackPreferenceWriter.setDialogueBoostStrength(strength) },
        syncEngineConfig = { markEngineConfigDirty() },
        syncEngineConfigDebounced = { markEngineConfigDirtyDebounced() },
        fontProvider = subtitleSources.fontProvider,
        getEngine = { playerSessionManager.engine },
        // named style presets: pure data over the controller — the live
        // list (the sheet's preset row) plus the synchronous read and the
        // persist lambda behind savePreset/deletePreset.
        userStylePresets = stores.subtitleLanguage.subtitle.map { it.userStylePresets },
        getUserStylePresets = { stores.subtitleLanguage.subtitle.value.userStylePresets },
        saveStylePresets = { presets -> stores.subtitleLanguage.setSubtitleStylePresets(presets) },
    )

    /**
     * Owns the uniform engine-effect setters (night mode, audio delay,
     * decoder, passthrough, normalization, channel mix, bass, virtualizer,
     * reverb) and the [com.raulshma.jellyplay.feature.player.video.state.AudioEffectsState]
     * slice they mutate. Public VM methods delegate so the 27 test references
     * + the public API stay valid. Dialogue Boost, Equalizer, and Video
     * Effects stay inline because their state lives outside this controller
     * (per-item repo / VM field / cinema gate). Cycle 6's effects→sync edge
     * routes through this session's [markEngineConfigDirty] funnel; the
     * sync's own getEffectsState read of `effects` is the direct (backward)
     * half.
     */
    internal val effects = VideoEffectsController(
        scope = scope,
        audioStore = stores.audio,
        audioEffectsStore = stores.audioEffects,
        playbackStore = playbackStore,
        syncConfig = { markEngineConfigDirty() },
    )

    /**
     * The ordered engine-attach choreography ([EngineAttachController],
     * beside the other controllers): everything the former `init` engineFlow
     * collector ran inline on every engine emission — the previous-collectors
     * cancel, the remote-control registry bind/clear, the style seed, the
     * capability mirror, the effects seed, the cast strategy re-pick, the
     * unsupported-audio-delay heads-up, the PiP next-action mirror, the
     * track-selection reset and the three per-engine fan-out collectors.
     * Declared AFTER every collaborator its wiring hands over (cast →
     * syncPlay → trackSelectionHelper → subtitlePreview →
     * subtitleStyleController → effects); the arm-phase engineFlow collector
     * is the single forwarding call, and the choreography's order is
     * jvmTest-pinned in EngineAttachControllerTest. The lambdas read only
     * session-owned slices (the aggregate cache, the session state, the
     * media-detail holder); the ONE ui-state write goes through the narrow
     * onEngineCapabilities lambda — the god-count ratchet is untouched.
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
            uiState.update { it.copy(
                engineCapabilities = capabilities,
                uiPrefs = it.uiPrefs.copy(keepScreenOnDuringVideo = keepScreenOnDuringVideo),
            ) }
        },
    )

    /**
     * The aggregate-prefs collector's side-effecting half (P4): the seeds,
     * the engine-config rebuild triggers, the autoplay flip and the video
     * focus-policy push — extracted beside [settingsProjector] (whose
     * `project` stays the pure-projection half). Declared after every
     * collaborator its wiring reads; the lambdas run only from the arm-phase
     * collector, long after construction. The rebuild trigger routes through
     * this session's [markEngineConfigDirty] funnel (cycle 6).
     */
    private val prefsFanout = PlayerPrefsFanout(
        projectPrefs = settingsProjector::project,
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        seedSleepTimerLastUsedMs = sleepTimer::seedLastUsedDurationMs,
        onStoredSelectionChanged = trackSelectionHelper::onStoredSelectionChanged,
        seedDefaultSearchLanguage = subtitles::seedDefaultSearchLanguage,
        isAutoplayNextApplied = { applied -> uiState.value.autoplay.videoAutoplayNext == applied },
        applyAutoplayNextPref = { enabled ->
            uiState.update { it.copy(autoplay = it.autoplay.copy(videoAutoplayNext = enabled)) }
            autoplayController.setEnabled(enabled)
        },
        rebuildEngineConfigIfRunning = { playerSessionManager.engine?.let { markEngineConfigDirty() } },
        applyVideoFocusPolicy = { osLegEnabled, duckOnTransientLoss ->
            (playbackFocus as? VideoFocusPolicyInput)
                ?.onVideoFocusPolicy(osLegEnabled, duckOnTransientLoss)
        },
    )

    /**
     * Owns the runtime engine-config sync (the [SubtitleStyleController]
     * shape): the [EngineConfigBuilder] invocation over the [EngineConfigSlices]
     * snapshot + the live-engine dispatch — both trigger paths moved VERBATIM:
     * the immediate rebuild (`markDirty` — the former `updateConfigWithUiState`)
     * and the drag-settling debounce (`markDirtyDebounced`). The uiState bag
     * never crosses (god-count ratchet unmoved) — this ONE sanctioned uiState
     * site projects the slices; `getEngine` is read at dispatch time, so a
     * debounce settling after an engine swap lands on the NEW engine.
     *
     * Declared AFTER the controllers whose state it reads (backward reads
     * only — cycle 6's notification edges all route through the session's
     * funnel instead), so no inference severing is needed here.
     */
    private val engineConfigSync = EngineConfigSync(
        scope = scope,
        slices = {
            EngineConfigSlices(
                subtitleStyle = uiState.value.subtitleStyle,
                videoEffects = uiState.value.videoFx.videoEffects,
                dialogueBoostEnabled = uiState.value.dialogueBoostEnabled,
                dialogueBoostStrength = uiState.value.dialogueBoostStrength,
                mediaStreams = uiState.value.media.mediaStreams,
                effects = effects.state.value,
                agg = cachedAggregate,
                // the session's effective mpv config (global slice + the
                // item/series render override + in-sheet quality pick) rides EVERY
                // runtime build — the render sheet's writes reach the engine
                // through this path (the engines' diff caches apply the delta).
                engineSpecific = sessionRender.effectiveMpvConfig(cachedAggregate.engine.mpvConfig),
                // the session-scoped deinterlace cycle.
                deinterlace = sessionRender.deinterlace,
            )
        },
        getEngine = { playerSessionManager.engine },
    )

    /**
     * The resolved-preference collector's side-effecting half (the
     * [PlayerPrefsFanout] shape): the dialogue-boost default fold + enabled
     * mirror write, the track helper's series-pref reflection, the
     * engine-config rebuild trigger and the language-preference re-apply
     * ladder (with its autoplay-race rationale) live in
     * [TrackPreferenceFanout.onPreferenceResolved]; the arm-phase collector
     * is the only registration. Declared after [trackSelectionHelper] — its
     * lambdas read it, but only run from the arm-phase collector, long after
     * construction. The rebuild trigger routes through the session's funnel
     * (cycle 6).
     */
    private val trackPreferenceFanout = TrackPreferenceFanout(
        applyDialogueBoostResolution = { resolvedBoost ->
            uiState.update {
                it.copy(
                    dialogueBoostStrength = resolvedBoost,
                    dialogueBoostEnabled = resolvedBoost != EffectStrength.NONE,
                )
            }
        },
        onSeriesPreferenceResolved = trackSelectionHelper::onSeriesPreferenceResolved,
        rebuildEngineConfig = { markEngineConfigDirty() },
        reapplyTracksFromEngine = { trackSelectionHelper.updateTracksFromEngine() },
    )

    /**
     * The displaced-holder self-pause collector's decision half (the reader's
     * observation pattern — the rationale lives on
     * [DisplacedHolderSelfPause]): which focus-claim states warrant pausing a
     * still-playing engine. The arm phase only registers the collector.
     */
    private val displacedHolderSelfPause = DisplacedHolderSelfPause(
        isEnginePlaying = { playerSessionManager.engine?.isPlaying?.value == true },
        pauseEngine = { playerSessionManager.engine?.pause() },
    )

    /** The render sheet's session-scoped state (sheet + deinterlace cycle). */
    internal val sessionRender: SessionRenderState
        get() = render.state

    /**
     * The media-session controller, built from the session stack's factory
     * (test override wins).
     */
    internal val mediaSessionController: MediaSessionController = mediaSessionControllerOverride
        ?: sessionStack.mediaSessionFactory.create(
            getEngine = { playerSessionManager.engine },
            getImageUrl = { itemId, maxWidth -> imageUrlProvider.getImageUrl(itemId = itemId, maxWidth = maxWidth) },
        )


    /**
     * The lifecycle hooks the session calls on the owner's behalf —
     * production: the session itself (the owner's slices are session-internal
     * since the wiring collapse); tests: recording doubles.
     */
    private val lifecycleHooks: SessionLifecycleHooks get() = hooksOverride ?: this

    /**
     * The immediate engine-config rebuild trigger (the former
     * `updateConfigWithUiState`): the collaborators' dirty-config reports
     * (render sheet, style controller, effects controller, prefs fan-out)
     * route through this funnel — cycle 6's ownership seam.
     */
    internal fun markEngineConfigDirty() {
        engineConfigSync.markDirty()
    }

    /** The drag-settling trigger (the former `updateConfigWithUiStateDebounced`). */
    internal fun markEngineConfigDirtyDebounced() {
        engineConfigSync.markDirtyDebounced()
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
                // Any engine-error surface latches the reporter's
                // watched-threshold suppression for the remainder of this
                // item (and flags the teardown stop `failed`).
                progressReporter.onEngineError()
                engineEventShell.emitEvent(
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
                if (!released) {
                    // A genuine EOF (engine ENDED, not an error)
                    // counts as watched even below the 95 % threshold.
                    progressReporter.onGenuineEof()
                    engineEventShell.emitEvent(SessionEvent.PlaybackEnded)
                }
            }
            EngineDecision.PassOutPause -> {
                playerSessionManager.engine?.pause()
                // Hours arm of the still-watching mode (feature 1.3): the same
                // pause arrives as the confirm prompt instead of the silent
                // toast; the toast survives where the overlay doesn't take
                // over (mode OFF/EPISODES).
                if (upgradesPassOutToOverlay()) {
                    engineEventShell.emitEvent(
                        SessionEvent.StillWatchingPrompt(StillWatchingReason.HOURS_IDLE)
                    )
                } else {
                    engineEventShell.emitEvent(SessionEvent.PassOutPause)
                }
            }
            is EngineDecision.InformUser -> engineEventShell.emitEvent(
                SessionEvent.InformUser(decision.message)
            )
        }
    }

    // @Volatile: set in release()/performRelease() (off Main) and
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

    /**
     * In-flight media-load coroutine, so a new initialize call can cancel the
     * previous one before launching its own — prevents overlapping
     * network/teardown side effects when a SyncPlay load event races a user
     * navigation.
     */

    /**
     * Job returned from [initialize] when a hook early-returns before any
     * load was launched (remote "Play On" routing, same-item short-circuit):
     * callers get a uniformly typed, already-finished handle instead of a
     * dangling active job.
     */
    private val noLoadJob: Job = Job().also { it.complete() }

    /**
     * The user-facing load funnel (the ViewModel's former `initialize`):
     * clears the PiP event flags a process singleton may have left set (#145),
     * resolves the process-death resume position, then runs the session-owned
     * [initialize] sequence. Also the entry the session-internal collaborators
     * use (SyncPlay group loads, next-episode advances) — the SAME funnel the
     * user's open action takes.
     */
    fun initialize(
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
        initialize(
            LoadRequest(
                itemId = itemId,
                mediaSourceId = mediaSourceId,
                startPositionTicks = resolveStartTicksAfterProcessDeath(itemId, startPositionTicks),
                allowCinemaMode = true,
                subtitleStreamIndex = subtitleStreamIndex,
                audioStreamIndex = audioStreamIndex,
            )
        )
    }

    /**
     * The load sequence previously inlined as the ViewModel's
     * `initializeInternal`, order preserved 1:1:
     *
     * 1. `released = false`;
     * 2. [SessionLifecycleHooks.rearmTransports] (PiP transport re-arm)
     *    followed by the session-owned engine-event coordinator re-arm
     *    ([EngineSessionShell.reArm] — a no-op unless a previous release
     *    disposed the coordinator);
     * 3. [SessionLifecycleHooks.resetForNewItem] (autoplay reset,
     *    autoplay-cancelled clear, coordinator new-item latch, pending stream
     *    indices);
     * 4. seek-latch + Stop-dedup latch resets (steps 3–4 were interleaved with
     *    these pure field resets in the old body; the writes keep
     *    their relative order, the three field resets run here as one block);
     * 5. remote-routing early-return via
     *    [SessionLifecycleHooks.routeToRemotePlaySession];
     * 6. same-item short-circuit via [shouldShortCircuitSameItemReload];
     * 7. [SessionLifecycleHooks.wasInSyncPlay] (SyncPlay flag read) followed
     *    by the outgoing session's stop-report ([reportCurrentPlaybackStopped],
     *    session-side since B3, directly after the flag read);
     * 8. cancel any in-flight the load task slot;
     * 9. mini-player reclaim early-return: the GATE stays a hook
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
     *     the controller/ui-state teardown), followed at exactly its old
     *     position by the process-death play-session restore
     *     ([restoreOrAllocatePlaySessionId]);
     * 12. persistence-latch resets ([lastPersistedPositionMs],
     *     [lastPersistedAtMs], the seek-progress task slot);
     * 13. [SessionLifecycleHooks.clearTrickplay];
     * 14. [SessionLifecycleHooks.reattachSyncPlay] (conditional on step 7);
     * 15. start the [SessionLoadPipeline] and track it as the load task slot.
     */
    fun initialize(request: LoadRequest): Job {
        released = false
        lifecycleHooks.rearmTransports()
        engineEventShell.reArm()
        lifecycleHooks.resetForNewItem(
            MediaStreamSelection(
                audioStreamIndex = request.audioStreamIndex,
                subtitleStreamIndex = request.subtitleStreamIndex,
            ),
        )
        // New item = new session: drop the seek latch and clear the Stop
        // dedup latch so the upcoming session's Stop can be reported.
        lastSeekPositionMs = null
        lastSeekTimestamp = 0L
        stopReportedForSession = null

        // "Play On" routing: a connected Jellyfin remote session takes the
        // video instead of local playback. (Full rationale on the routing
        // hook.)
        if (lifecycleHooks.routeToRemotePlaySession(request)) return noLoadJob

        if (shouldShortCircuitSameItemReload(request.itemId, request.startPositionTicks)) return noLoadJob

        val wasInSyncPlay = lifecycleHooks.wasInSyncPlay()
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
        sessionTasks.cancel(LOAD)

        lifecycleHooks.tryReclaimMiniPlayer(request.itemId)?.let { reclaimed ->
            return sessionTasks.replace(LOAD) {
                loadReclaimedEngine(reclaimed, request.itemId)
            }
        }

        lifecycleHooks.releaseMiniPlayerState()
        // Per-item teardown, split at B3: the session-owned half runs FIRST,
        // then the controller/ui-state half back-to-back from this same
        // synchronous chain. The play-session restore runs at exactly its old
        // position right after the teardown (it used to be the hook's return
        // value).
        releaseInternalsSessionPart()
        lifecycleHooks.releaseInternalsVmPart()
        playSessionId = restoreOrAllocatePlaySessionId(request.itemId)
        // The playhead is seeded by the load pipeline from the RESOLVED start
        // ticks (see onPlayheadSeeded) — seeding from the raw request ticks
        // missed the offline-mirror resume that resolution produces.
        lastPersistedPositionMs = Long.MIN_VALUE
        lastPersistedAtMs = 0L
        sessionTasks.cancel(SEEK_PROGRESS)
        lifecycleHooks.clearTrickplay()

        if (wasInSyncPlay) {
            lifecycleHooks.reattachSyncPlay()
        }

        // The ordered load spine (SyncPlay reconcile → prefs projection →
        // cinema gate → offline-start resolution → loadMedia → per-item
        // hydration → media session + duration seed → trickplay → reports)
        // lives in [SessionLoadPipeline]; its stage order is pinned by
        // SessionLoadPipelineTest.
        return sessionTasks.replace(LOAD) {
            sessionLoadPipeline.start(scope = scope, request = request)
        }
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
     * the caller wrapper from its ui-prefs mirror (never read back);
     * [selection] carries the currently selected server streams
     * (supplied from the stored per-item [MediaStreamSelection]) so the
     * re-POST keeps the server-side choices — the baked-in audio track and
     * any burned-in image sub — instead of resetting them.
     * Surfaces a notice via [SessionEvent.InformUser] when switching to a
     * transcode since the brief re-buffer is otherwise surprising, and
     * auto-falls-back to transcode when a forced-direct-play request yields
     * no playable method.
     */
    suspend fun reloadForMode(
        mode: PlaybackMode,
        quality: StreamingQuality,
        selection: MediaStreamSelection? = null,
    ) {
        val pos = playerSessionManager.engine?.currentPositionMs ?: 0L

        // Stop-report the *current* server session before the swap: reloadPlayback
        // overwrites sessionState.playSessionId with the new server id, so without
        // this the previous session is never reported stopped (the server would
        // see start(idA) → progress(idB) → stop(idB), orphaning idA — the same
        // desync class the currentPlaySessionId resolver prevents elsewhere).
        reportCurrentPlaybackStopped()
        progressReporter.cancelJobs()

        // Re-arm the track-selection machinery for the engine swap, mirroring
        // reloadForStreamChange: the replacement engine renumbers tracks and
        // re-side-loads subtitles, so seeding the pending indices (which also
        // clears the held-selection latches) lets the ladder restore the
        // selection on the new engine's first track emissions.
        setPendingStreams(selection)
        val resolved = playerSessionManager.reloadPlayback(
            mode = mode,
            quality = quality,
            currentPositionMs = pos,
            selection = selection,
        ) ?: return
        rebindSessionTracking(playerSessionManager.sessionState.value.currentItemId ?: "")

        if (resolved.playMethod == PlayMethod.TRANSCODE) {
            engineEventShell.emitEvent(SessionEvent.InformUserKey(PlayerVideoMessage.TranscodeSwitched))
        }
        if (mode == PlaybackMode.FORCE_DIRECT_PLAY &&
            resolved.playMethod != PlayMethod.DIRECT_PLAY
        ) {
            engineEventShell.emitEvent(
                SessionEvent.InformUserKey(PlayerVideoMessage.DirectPlayUnavailable)
            )
            launchFallbackToTranscode(
                fromPositionMs = playerSessionManager.engine?.currentPositionMs ?: pos,
                quality = quality,
                selection = selection,
            )
        }
    }

    /**
     * Re-binds the system media session and the position/progress tracking to
     * the engine that just (re)loaded — the ONE funnel for every path that
     * swaps or adopts an engine without running the [SessionLoadPipeline]:
     * the reload/retry family ([reloadForMode], [launchFallbackToTranscode],
     * [retryWithEngine], [retryPlayback]) and the mini-player reclaim
     * ([loadReclaimedEngine]).
     *
     * [itemId] is the item the media session is created for — the reload
     * family passes the session state's current item, the reclaim path its
     * bound id. Position tracking ALWAYS restarts (the seek/buffer bars, the
     * stats overlay and the segment auto-skip read it, and the previous
     * engine — whose `positionFlow` the tracking job collected — has been
     * released, so the job would otherwise go silent). [trackProgress]
     * additionally gates the SERVER-side progress reporting: cinema pre-roll
     * intros are not part of the user's library history, so
     * [loadCinemaIntro] passes `false` — the deliberate divergence from the
     * other adopt sites.
     */
    private fun rebindSessionTracking(
        itemId: String,
        trackProgress: Boolean = true,
    ) {
        val sessionState = playerSessionManager.sessionState.value
        mediaSessionController.createForItem(
            itemId,
            sessionState.title,
            sessionState.subtitle,
        )
        progressReporter.startPositionTracking()
        if (trackProgress) {
            progressReporter.startProgressReporting()
        }
    }

    private fun launchFallbackToTranscode(
        fromPositionMs: Long,
        quality: StreamingQuality,
        selection: MediaStreamSelection? = null,
    ) {
        setUiPlaybackMode(PlaybackMode.FORCE_TRANSCODE)
        scope.launch {
            playbackStore.setPlaybackMode(PlaybackMode.FORCE_TRANSCODE)
            reportCurrentPlaybackStopped()
            progressReporter.cancelJobs()
            setPendingStreams(selection)
            playerSessionManager.reloadPlayback(
                PlaybackMode.FORCE_TRANSCODE,
                quality,
                fromPositionMs,
                selection,
            )
            rebindSessionTracking(playerSessionManager.sessionState.value.currentItemId ?: "")
        }
    }

    /**
     * Retry playback on a different engine after a fatal error.
     * [playbackSpeed] and [streamingQuality] are supplied by the caller
     * wrapper from its ui-state mirror; the error-dialog clear that used to
     * precede the engine swap stays caller-side (a synchronous ui-state write).
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
            rebindSessionTracking(playerSessionManager.sessionState.value.currentItemId ?: "")
        }
    }

    /**
     * Same-engine retry for recoverable errors (Network, Render, or the
     * buffering watchdog timeout). Reloads the current engine at the current
     * position, mirroring [retryWithEngine] without changing engine.
     * [preferredPlayerType] selects the engine (the ui-state mirror of the
     * last-chosen engine, supplied by the caller wrapper).
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
            rebindSessionTracking(playerSessionManager.sessionState.value.currentItemId ?: "")
        }
    }

    /**
     * Reload playback for the current item at the current position with a new
     * audio/subtitle stream [selection]. Used when the user picks a
     * server-origin audio or subtitle track during transcoded playback — mpv
     * cannot switch audio in-place on an HLS manifest, and embedded subs aren't
     * in the transcode, so the server must re-issue the stream with the chosen
     * index.
     */
    fun reloadForStreamChange(selection: MediaStreamSelection) {
        if (playerSessionManager.engine == null) return
        val positionMs = getReportPositionMs()
        scope.launch {
            setPendingStreams(selection)
            playerSessionManager.reloadForStreamChange(selection, positionMs)
        }
    }

    /**
     * Switches the playing version (media source) of the current item at the
     * current position — the Version sheet's pick. The pending stream-index
     * hints are CLEARED first: indices of the previous version are
     * meaningless server-side (and would bake the old audio/sub choice into
     * the new version's re-POST), so the new version starts on its own
     * defaults.
     */
    fun switchMediaSource(mediaSourceId: String) {
        if (playerSessionManager.engine == null) return
        val positionMs = getReportPositionMs()
        scope.launch {
            setPendingStreams(null)
            playerSessionManager.switchMediaSource(mediaSourceId, positionMs)
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
     * The GATE ([SessionLifecycleHooks.tryReclaimMiniPlayer]) stays a hook;
     * the two uiState/controller-bound slices of the old body stay in the
     * hook implementation at exactly their old positions: the loading-veil
     * lift ([SessionLifecycleHooks.onMiniPlayerReclaimed], synchronously
     * before the body launch) and the post-bind hydration
     * ([SessionLifecycleHooks.hydrateReclaimedItem]).
     */
    internal fun loadReclaimedEngine(
        reclaimed: MediaEngine,
        itemId: String,
    ): Job {
        // Reclaim promotes an already-playing mini-player engine to
        // fullscreen — playback is continuous, so no load screen.
        lifecycleHooks.onMiniPlayerReclaimed()
        return scope.launch {
            val detailResult = mediaRepository.getMediaDetail(itemId)
            val detail = detailResult.getOrNull()
            if (detail != null) {
                playerSessionManager.bindReclaimedEngine(reclaimed, itemId, detail)
                rebindSessionTracking(itemId)
                lifecycleHooks.hydrateReclaimedItem(itemId, detail)
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
    // cleared by the teardown half at exactly its old slot — see
    // [SessionLifecycleHooks.releaseInternalsVmPart].
    @Volatile
    internal var cinemaIntroContext: CinemaIntroContext? = null

    /**
     * Cinema Mode take-over: queues the pre-roll [intros] and loads the first
     * one. Invoked through the pipeline's `beginCinemaMode` hook — the
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
            // server-side playback reporting (trackProgress = false) and the
            // segment/next-episode/trickplay bookkeeping for them; the media
            // session + position tracking still rebind so the notification and
            // the seek bar track the intro.
            playerSessionManager.loadMedia(intro.id, null, 0L)
            rebindSessionTracking(intro.id, trackProgress = false)
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
     * is used for the whole session lifecycle.
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
     * for one session — this one and the final teardown in [release] — never
     * double-report; the reporter's stalled-finish stop for the same session
     * dedups through [PlaybackProgressReporter.hasReportedStopFor] the same
     * way). The report carries `failed = true` when the reporter's error
     * latch is held: an error-aborted session must not trip the
     * server's own "≥X % = played" rule — the actual spoiler-protection fix.
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
            // instead of silently dropping the stop entirely (#153). The > 0
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
                // No manual cache invalidation (plan 08): the end-of-item
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
     * single-value resolver this session's stop reports and persists use.
     * Reached through the load spine's `reportPlaybackStart` hook at stage
     * 10, directly before position/progress tracking starts.
     */
    internal suspend fun reportPlaybackStart(itemId: String, source: MediaSource?, playMethod: PlayMethod) {
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
     * The persist half of the ViewModel's `seekTo`: records the seek latches
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
        // The DB mirror is coalesced: rapid scrubbing no longer queues one
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
        sessionTasks.replace(SEEK_PROGRESS) {
            scope.launch {
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
     * relies on (#153).
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
     * followed by the controller/ui-state half
     * ([SessionLifecycleHooks.releaseInternalsVmPart]) from the same
     * synchronous call chain.
     */
    private fun releaseInternalsSessionPart() {
        sessionTasks.cancel(LOAD)
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
     *    session state these values read; the preamble that used to
     *    sit between the old snapshot site and the teardown touches none of
     *    these values);
     * 2. [releaseInternalsSessionPart] + the teardown callback
     *    ([vmTeardownAfterInternals] runs the post-internals release
     *    steps: PiP transport reset, cast consumer release, engine-controller
     *    clear) — the order of the old `performRelease` tail is preserved;
     * 3. flush a pending coalesced seek-mirror write (joined on the release
     *    scope so it survives the viewModelScope cancellation on clear());
     * 4. the final Stop report, deduped through [stopReportedForSession].
     */
    fun release(vmTeardownAfterInternals: () -> Unit) {
        val itemId = playerSessionManager.sessionState.value.currentItemId
        val sessionId = currentPlaySessionId
        val positionTicks = getReportPositionMs() * 10_000
        // A latched error at teardown flags the final stop `failed`
        // so the server does not apply its own "≥X % = played" rule to the
        // aborted session. Snapshotted BEFORE the teardown statements below.
        val failed = progressReporter.isErrorLatched()

        releaseInternalsSessionPart()
        lifecycleHooks.releaseInternalsVmPart()
        vmTeardownAfterInternals()

        // Belt-and-suspenders: flush a pending coalesced seek-mirror write so the
        // offline store doesn't lag the final position on release. The write is
        // moved onto the release scope (IO + NonCancellable) so it survives the
        // viewModelScope being cancelled on clear().
        val pendingSeek = sessionTasks[SEEK_PROGRESS]
        if (pendingSeek != null && itemId != null) {
            releaseScope.launch(NonCancellable) {
                pendingSeek.join()
            }
        }
        // Skip the second Stop if reportCurrentPlaybackStopped already
        // sent one for this session — duplicate Stop reports confuse the
        // server's resume/progress bookkeeping. The reporter's
        // stalled-finish stop (sent at the FULL duration) dedups the same
        // way: a teardown stop at the stalled position would only downgrade
        // the position the server already resolved.
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
                    // No manual cache invalidation here (plan 08): the detail
                    // screen's re-entry freshness comes from the provider's forced
                    // re-resolve (requestRevalidate) and the auto-advance path
                    // already evicts via markPlayed inside the repository — the
                    // old invalidateUserDataCaches call duplicated both.
                }
            }
        }
    }

    /**
     * The FULL teardown behind the owner's `release()` dispose hook (the
     * former wiring `performRelease` body, absorbed with the collaborator
     * graph it drives): the now-playing Stopped event, the collector/engine
     * teardown ordering, the audio-lifecycle + sleep-timer release, the
     * session-owned release split with the post-internals steps as the
     * callback, then the pending-seek join and the final stop-report on the
     * release scope. Idempotent through the [released] latch (the owner's
     * former guard).
     */
    internal fun performRelease() {
        if (released) return
        released = true
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
        engineEventCoordinator.dispose()
        // Abandon the focus claim + unbind the surface target, and stop the
        // becoming-noisy receiver (all idempotent; safe if never registered).
        playbackFocus.release(PlaybackSurfaceId.VIDEO)
        videoFocusSurface?.unbind()
        becomingNoisy.release()
        sleepTimer.onRelease()
        // Full teardown: the session owns the tail — snapshot of the
        // stop-report inputs, the releaseInternals split (session half, then
        // the controller/ui-state half, back-to-back), the post-internals
        // release steps passed as the callback, then the pending-seek join
        // and the final stop-report on the release scope.
        release {
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
            // was armed on (the lambda holds the session through `launch`).
            playerSessionManager.engine?.onUserVolumeChange = null
        }
    }

    // ── Arm (the former phase 2, collapsed into the session) ─────────────────

    /**
     * Registers every collector the ViewModel's former `init` block launched,
     * in the same order (the engine-event mirrors FIRST, the session-event
     * forwarder, the PiP transport registration + dismissal discharge, the
     * aggregate-prefs and metered-network collectors, the SyncPlay start +
     * session mirror, the becoming-noisy registration, the video-focus
     * binding, the displaced-holder observer, the session-state fold, the
     * preference resolver, and the engine-attach choreography LAST). Called
     * EXACTLY ONCE from the ViewModel's `init`, immediately after the session
     * is constructed. There are no late-bound slots to bind — the former
     * builder's back-references dissolved into ownership seams at construction
     * (see the class KDoc).
     */
    internal fun arm() {
        // Subscribe the engine-event fan-out FIRST: the coordinator's mirrors
        // collector must be active before any initialize() can produce an
        // engine state change (subscription timing). The coordinator's
        // decision executor lives in the session and is subscribed there.
        startEngineEventCoordinatorOutputs()
        // Single forwarder for the session's outcomes: one collector maps
        // each [SessionEvent] into the VM's existing sinks. The autoplay /
        // cinema / close policy of the VM's handlePlaybackEnded stays
        // VM-side — the session only reports that playback ended.
        scope.launch {
            events.collect { event ->
                when (event) {
                    is SessionEvent.ShowError -> uiState.update { s ->
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
                    is SessionEvent.InformUserKey -> userMessageBus.info(event.message)
                    SessionEvent.PlaybackEnded -> host.handlePlaybackEnded()
                    SessionEvent.ClosePlayerRequested -> closePlayer.trySend(Unit)
                    SessionEvent.PassOutPause ->
                        passOutEvents.trySend("Playback paused — pass-out protection")
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
        // PipTransportController's KDoc for why the re-arm must ride the load
        // lifecycle.
        pipTransport.registerPipTransport()
        // The PiP-dismissal discharge (pause → teardown → close → the
        // defensive latch clear, issue #145) lives on the shared core:data
        // helper — this supplies only its teardown list and its close pipe.
        scope.dischargePipDismissal(
            pip = pipController,
            teardown = {
                activePlayerController.engine?.pause()
                playerSessionManager.engine?.pause()
                mediaSessionController.release()
                videoMiniPlayerState.release()
                performRelease()
            },
            close = { closePlayer.trySend(Unit) },
        )
        scope.launch {
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
        scope.launch {
            // Surface the metered-network state so the playback metadata can
            // explain why a quality cap is being applied (AUTO on a metered link
            // caps at AdaptiveBitrateManager.MAX_BITRATE_METERED). Guarded so a
            // redundant emission (no change) doesn't allocate a fresh uiState.
            networkMonitor.isMetered.collect { metered ->
                if (uiState.value.isConnectionMetered != metered) {
                    uiState.update { it.copy(isConnectionMetered = metered) }
                }
            }
        }
        // Pass-out protection (interaction clock + poller) and the play-state
        // resume reset live in [EngineEventCoordinator]; the PassOutPause
        // decision is executed by the session and arrives as a
        // [SessionEvent.PassOutPause] through the events collector above.
        syncPlay.start()

        // Mirror the bridge's session flag into the residual UiState: it feeds
        // SegmentProjection/toSegmentInput() inside the VM's segmentOverlayState
        // combine, and moving that combine onto the bridge's flow would couple
        // the segment projection to the bridge. One-way derived mirror — the
        // bridge's SyncPlayUiState.isInSyncPlaySession stays the single home.
        scope.launch {
            syncPlay.state.map { it.isInSyncPlaySession }.distinctUntilChanged()
                .collect { inSession ->
                    if (uiState.value.isInSyncPlaySession != inSession) {
                        uiState.update { it.copy(isInSyncPlaySession = inSession) }
                    }
                }
        }

        // Headphone unplug auto-pause (the becoming-noisy half of the former
        // audio-lifecycle owner; the focus half is module-owned now).
        becomingNoisy.register()

        // The video focus slice: bind the current engine as the module's
        // commandable VIDEO surface target, with the resume-skip riding the
        // restore hook exactly where the legacy focus-regain hook did (no
        // is-playing guard — a REGAIN follows a transient loss, where the
        // skip is always wanted; a NULL engine is a no-op). [getEngine]
        // re-reads per command, so engine swaps mid-duck are observed.
        videoFocusSurface?.bind(
            target = { playerSessionManager.engine?.let { engine -> MediaEngineFocusTarget(engine, { uiState.value.isMuted }) } },
            onRestore = { playerSessionManager.engine?.let { host.applyResumeSkip(it) } },
        )

        // Displaced-holder self-pause: the decision (which claim states
        // warrant the pause, and why the desktop depends on it) lives in
        // [displacedHolderSelfPause] — this is the registration only.
        scope.launch {
            playbackFocus.claimState.collect { state ->
                displacedHolderSelfPause.onClaimStateChanged(state)
            }
        }

        scope.launch {
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
            sessionState.collect { session ->
                mediaContentProjector.onSessionState(session, session.mediaDetail?.item?.seriesId)
            }
        }

        // Reflect the resolved per-item/series language preference into the
        // track slice (series-pref toggle rows) + dialogue boost so the sheets
        // show the series-pref toggle state. The whole fold (boost default +
        // enabled mirror, the series-pref reflection, the config rebuild and
        // the hasLangPref-gated re-apply with its autoplay-race rationale)
        // lives in [trackPreferenceFanout] — this is the registration only.
        scope.launch {
            playbackPreferenceResolver.resolved.collect { pref ->
                trackPreferenceFanout.onPreferenceResolved(pref)
            }
        }

        scope.launch {
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

    // ── Engine-event mirror collectors ───────────────────────────────────────

    /** Fan-out collectors for the coordinator's mirrors + decisions. */
    private var engineEventOutputsJob: Job? = null

    /**
     * Starts (or restarts, after the session re-arms a disposed coordinator)
     * the engine-event MIRROR collectors: the coordinator's guarded
     * play/buffering flows turned into uiState writes and collaborator calls
     * (SyncPlay, PiP). Called once from [arm] and again through the shell's
     * rearm callback when a disposed coordinator is re-created — the VM is
     * Activity-scoped and survives release() across media, so the mirrors
     * must be re-armed alongside it. Decision *execution* lives in
     * [executeEngineDecision]; its outcomes arrive as [SessionEvent]s.
     */
    private fun startEngineEventCoordinatorOutputs() {
        engineEventOutputsJob?.cancel()
        val coordinator = engineEventCoordinator
        engineEventOutputsJob = scope.launch {
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
                    uiState.update { s ->
                        if (s.isPlaying == isPlaying) s else s.copy(isPlaying = isPlaying)
                    }
                },
                { isPlaying -> syncPlay.onIsPlayingChanged(isPlaying) },
                { isPlaying -> pipController.setPlaying(isPlaying) },
                // The focus claim rides this ONE edge — every play path
                // (user play, autoplay, next-episode, mini-player reclaim)
                // crosses the coordinator mirror, so no per-entry-point
                // claim sites can drift (the music manager's
                // onIsPlayingChanged pattern). Newest user action wins:
                // this publishes Held(VIDEO), and MUSIC — a commandable
                // victim since the video slice — pauses on the command.
                ::onVideoPlayEdge,
            )
            scope.launch {
                coordinator.isBuffering.collect { buffering ->
                    uiState.update { s ->
                        if (s.isBuffering == buffering) s else s.copy(isBuffering = buffering)
                    }
                }
            }
            scope.launch {
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
     * The VIDEO claim edge (the video focus slice, ADR-0004), folded onto
     * the shared [claimOnPlayEdge] body: a granted claim evicts the other
     * surfaces synchronously before returning; a DENIED claim pauses the
     * engine (see the helper's KDoc for the contract). The duck path never
     * crosses here: a ducked claim stays Held and the engine keeps playing.
     */
    private fun onVideoPlayEdge(isPlaying: Boolean) {
        playbackFocus.claimOnPlayEdge(
            surfaceId = PlaybackSurfaceId.VIDEO,
            isPlaying = isPlaying,
            onDenied = { playerSessionManager.engine?.pause() },
        )
    }

    // ── SessionLoadOutputs (the load pipeline's uiState-shaped outputs) ─────

    override fun onPrefsProjected(ui: PrefsProjection) {
        uiState.update(ui)
    }

    override fun onInitializing(visible: Boolean) {
        uiState.update { it.copy(isInitializing = visible) }
    }

    override fun onDurationSeeded(runtimeMs: Long) {
        // Guarded so a value already set by the engine (e.g. ExoPlayer
        // resolving duration on prepare) is never clobbered.
        if (durationMs.value == 0L) {
            durationMs.value = runtimeMs
        }
    }

    override fun onPlayheadSeeded(startPositionTicks: Long) {
        // Playhead display pre-seed is session-owned; the write
        // itself flows through the session's seedDisplayedPositionMs seam.
        preSeedPlayhead(startPositionTicks)
        // Surface a one-shot "Resumed — Restart" reminder when opening at a
        // saved position; emitted here (not in the synchronous prologue) so
        // offline-resolved resume positions — invisible in the raw request
        // ticks — raise the chip too.
        if (startPositionTicks > 0) {
            resumeReminder.tryEmit(startPositionTicks / 10_000)
        }
    }

    override fun onStreamUrlResolved(url: String) {
        mediaContentProjector.onStreamUrl(url)
    }

    // ── SessionLifecycleHooks (the per-item teardown slices + gates) ────────
    //
    // The session implements its own hooks (production `hooks` arg = null →
    // [lifecycleHooks] resolves to `this`); the behavioral suites substitute
    // recording doubles to pin the ORDER of the choreography.

    override fun rearmTransports() {
        // The engine-event coordinator re-arm is session-owned —
        // PlaybackSession.initialize performs it directly after this hook.
        pipTransport.registerPipTransport()
    }

    override fun resetForNewItem(selection: MediaStreamSelection) {
        autoplayController.resetForNewItem()
        // Defensive: a prompt can never survive an item switch (its own
        // Continue/Stop arms clear it first; this catches a racing load).
        stillWatching.resetForItem()
        uiState.update { it.copy(autoplay = it.autoplay.copy(autoplayCancelled = false)) }
        // Coordinator fallback-latch reset — a pure latch flip that ran
        // between the (session-owned) seek-latch and Stop-dedup resets in
        // the old inlined body; bundled here with the other
        // synchronous-prefix writes.
        engineEventCoordinator.onNewItem()
        trackSelectionHelper.setPendingStreams(selection)
    }

    // Typed reclaim (no downcast): the mini-player holder resolves the
    // player-contract MediaEngine through the capability the depositing
    // video feature registered at deposit time — the asMedia3Player
    // "typed capability, not a cast" rule.
    override fun tryReclaimMiniPlayer(itemId: String): MediaEngine? =
        videoMiniPlayerState.tryReclaimMediaEngine(itemId)

    override fun onMiniPlayerReclaimed() {
        // Reclaim promotes an already-playing mini-player engine to
        // fullscreen — playback is continuous, so no load screen. The reclaim
        // BODY is session-side; this is the veil write,
        // at exactly its old position (before the body launch).
        uiState.update { it.copy(isInitializing = false) }
    }

    override fun hydrateReclaimedItem(itemId: String, detail: MediaDetail) {
        // Old loadReclaimedEngine-hook tail: the hydration fetches, in
        // their old order — segments (the pipeline's offline-first fetch,
        // shared with the load spine) then the episode fetches.
        sessionLoadPipeline.fetchMediaSegments(scope, itemId)
        episodeContinuation.refreshAdjacent(detail)
        episodeContinuation.loadSeries(detail)
    }

    override fun releaseMiniPlayerState() {
        videoMiniPlayerState.release()
    }

    override fun releaseInternalsVmPart() {
        // The controller/ui-state teardown half (the former VM body — every
        // collaborator it touches is session-internal since the collapse).
        // Raise the loading screen across the state reset + fresh load so
        // the seek bar never paints a stale/zero fraction during the
        // transition. It lifts once position & duration are seeded (in the
        // load coroutine), so the bar's first paint is already at the
        // resume fraction. (On the full-release path this is a same-value
        // write: the rebuild below constructs a fresh state whose
        // isInitializing default is already true.)
        uiState.update { it.copy(isInitializing = true) }
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
        // Cinema latch clear — the FIELD is session-owned; the clear itself
        // stays in this half immediately before the uiState rebuild:
        // moving it into the session-owned teardown half would relocate it
        // ahead of every neighbor. The uiState rebuild below
        // already nulls cinemaIntroState implicitly (fresh constructor).
        cinemaIntroContext = null

        // Residual reset: session + prefs-mirror fields only. Everything that
        // reset implicitly (track lists, subtitle search, sleep timer, audio
        // effects, SyncPlay display, A/B repeat) is now reset — or deliberately
        // not reset — by its owning controller above. The surviving leaves are
        // declared in [keepAcrossItems].
        uiState.update { it.keepAcrossItems() }
        // The episode-slice reset goes through the continuation controller's
        // seam — the navigator is the slice's single writer (CONTEXT.md).
        episodeContinuation.resetForItemSwitch()

        // Clear the high-frequency display streams the seek bar reads. They live
        // outside uiState (to avoid ~4 Hz whole-screen recomposition) and are
        // only ever reset on a fresh VM, so without this the previous item's
        // position/duration bleed into the next item until the new engine emits
        // its first position tick (~1-2 s). With duration == 0 the seek bar
        // renders empty (its else-branch) instead of the stale fraction.
        positionMs.value = 0L
        durationMs.value = 0L
        videoStats.value = EngineVideoStats()

        // Closes this half: the player-lifecycle callbacks clear stays with
        // the owner's lifecycle dependency. The whole session-owned teardown
        // (PSM release included) runs ahead of this half, preserving the
        // clear's relative order against the PSM release.
        sessionStack.playerLifecycleManager.reset()
    }

    override fun clearTrickplay() {
        trickplayManager.clear()
    }

    override fun reattachSyncPlay() {
        syncPlay.reattachSession()
    }

    override fun wasInSyncPlay(): Boolean {
        // Pure flag read — the outgoing session's stop-report
        // moved session-side and fires directly after this read inside
        // PlaybackSession.initialize, at exactly its old position.
        return syncPlayManager.isInSyncPlaySession
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
     */
    override fun routeToRemotePlaySession(request: LoadRequest): Boolean {
        if (!jellyfinRemotePlayCastStrategy.isConnected.value) return false
        jellyfinRemotePlayCastStrategy.loadMedia(
            itemId = request.itemId,
            startPositionMs = request.startPositionTicks / 10_000,
            mediaSourceId = request.mediaSourceId,
            audioStreamIndex = request.audioStreamIndex,
            subtitleStreamIndex = request.subtitleStreamIndex,
        )
        // Local player isn't loading — clear the flag so a later local UI
        // mount never shows a stuck loading screen.
        uiState.update { it.copy(isInitializing = false) }
        return true
    }
}

/**
 * ViewModel-bound slices of [PlaybackSession.initialize], in the exact order
 * the session calls them (see [PlaybackSession.initialize] for the numbered
 * sequence). The session owns the sequence; the hooks own the per-item
 * teardown slices and the gates whose knowledge lives outside the session
 * (the remote-play strategy probe, the mini-player reclaim, the SyncPlay
 * re-attach flag). Since the C6 wiring collapse the session implements this
 * interface ITSELF (the production `hooks` argument is null); the behavioral
 * suites substitute recording doubles to pin the call ORDER without weakening
 * the pins.
 */
internal interface SessionLifecycleHooks {
    /**
     * Re-arms the PiP transport bridge for a new load: release()
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
     * audio/subtitle stream selection for the track selection helper.
     */
    fun resetForNewItem(selection: MediaStreamSelection)

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
     * gate stays a hook because the mini-player state it reads is
     * owner-lifecycle knowledge.
     */
    fun tryReclaimMiniPlayer(itemId: String): MediaEngine?

    /**
     * Lowers the loading veil when the reclaim routing takes over — playback
     * is continuous, no load screen. The uiState write stays in the hook
     * implementation; the session's [PlaybackSession.loadReclaimedEngine]
     * invokes this synchronously BEFORE launching the body, at exactly the
     * old position of the veil write (it used to be the first statement of
     * the old `loadReclaimedEngine` hook).
     */
    fun onMiniPlayerReclaimed()

    /**
     * Post-bind hydration for a reclaimed mini-player engine: the segments,
     * adjacent-episodes and series-episodes fetches whose uiState writes and
     * collaborators keep them in the hook implementation. Called by the
     * session's reclaim body at exactly the old position (after the engine
     * bind, media session and tracking restart). Replaced the old
     * `loadReclaimedEngine` hook's tail at B4.
     */
    fun hydrateReclaimedItem(itemId: String, detail: MediaDetail)

    /** Releases the mini-player state when its engine was not reclaimed. */
    fun releaseMiniPlayerState()

    /**
     * The controller/ui-state part of the per-item teardown (the old
     * `releaseInternals` body's second half), prefixed by the loading-veil
     * raise. Called back-to-back AFTER the session-owned teardown half — the
     * session cancels the in-flight load, reporter jobs, media session and
     * PSM, and clears the seek latches directly before this hook — from the
     * same synchronous call chain (no dispatch hop). Also invoked by
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
 * Event surface a [PlaybackSession] exposes to the ViewModel: the session's
 * arm-phase forwarder is the single mapper, routing each event into the
 * existing sinks (the close-player channel, the uiState error fields, the
 * user-message bus, the pass-out event channel); the genuinely VM-bound
 * policy event ([PlaybackEnded]) rides [SessionHostLambdas.handlePlaybackEnded].
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

    /** A transient informational message for the user (already-resolved text). */
    data class InformUser(val message: String) : SessionEvent

    /**
     * The resource-backed twin of [InformUser]: the notice resolves from a
     * compose-resources key ([PlayerVideoMessage]) at the message seam, not
     * from a hardcoded literal here — the coordinator's dynamic engine-error
     * notices keep riding [InformUser].
     */
    data class InformUserKey(val message: PlayerVideoMessage) : SessionEvent

    /** Media playback reached its end (autoplay/close policy stays VM-side). */
    data object PlaybackEnded : SessionEvent

    /** The session asks the player screen to close. */
    data object ClosePlayerRequested : SessionEvent

    /** Pass-out protection triggered a pause. */
    data object PassOutPause : SessionEvent

    /**
     * The "Still watching?" confirm prompt (feature 1.3) — the hours arm:
     * the pass-out protection tripped while the still-watching mode includes
     * HOURS, so the silent pause arrives bundled with the confirm overlay
     * (the engine is already paused; the overlay's Continue resumes).
     * The episode arm is raised by the ViewModel's end-of-playback gate,
     * which raises the same overlay directly.
     */
    data class StillWatchingPrompt(val reason: StillWatchingReason) : SessionEvent
}

// [SessionPositionStore] moved to :shared:core:player-contract (SAME package,
// zero consumer import churn — the PlayerLifecycleCallbacks precedent) so the
// shared test-fixtures module's FakePositionStore can implement it through
// that module's existing player-contract edge. [SavedStateHandlePositionStore]
// below stays here: it is the production implementation and touches the
// SavedStateHandle type.

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

/**
 * The owner-funnel lambda bundle (the successor of the deleted wiring's
 * WiringHostLambdas, shrunk to the entries that are GENUINELY ViewModel-owned:
 * the transport funnels — seek/step/route/resume and the resume-skip — and
 * the end-of-playback policy dispatch. Everything the wiring used to call
 * back into the VM for (the load funnel, the remote-play routing, the
 * per-item hydration, the teardown halves) is session-internal since the C6
 * collapse. Built at the VM from its private handlers; STORED while the
 * session is still under construction but only INVOKED from [PlaybackSession.arm]
 * onward, so no lambda can observe an uninitialized VM field — the same
 * lazy-callback discipline the wiring lambdas always applied.
 */
internal data class SessionHostLambdas(
    val seekTo: (positionMs: Long, userInitiated: Boolean) -> Unit,
    val seekByStep: (direction: Int) -> Unit,
    val routedPlay: (play: Boolean) -> Unit,
    val resumePlayback: () -> Unit,
    val applyResumeSkip: (engine: MediaEngine) -> Unit,
    val autoSkipSegment: (segment: MediaSegment) -> Unit,
    val onEndedWithNoNext: () -> Unit,
    val handlePlaybackEnded: () -> Unit,
)
