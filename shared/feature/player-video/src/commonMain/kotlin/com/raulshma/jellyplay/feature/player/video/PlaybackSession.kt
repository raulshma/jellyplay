package com.raulshma.jellyplay.feature.player.video

import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.core.concurrency.TaskBundle
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.SleepCountdown
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.playback.VideoQueueController
import com.raulshma.jellyplay.core.data.playback.dischargePipDismissal
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackFocus
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackSurfaceId
import com.raulshma.jellyplay.core.data.playback.focus.VideoFocusPolicyInput
import com.raulshma.jellyplay.core.data.playback.focus.claimOnPlayEdge
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.playback.NowPlayingReporter
import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.util.ArtworkUrls
import com.raulshma.jellyplay.core.data.util.DefaultArtworkUrls
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
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
import com.raulshma.jellyplay.feature.player.video.session.playbackHandledFingerprint
import com.raulshma.jellyplay.feature.player.video.state.InputBindingToggleController
import com.raulshma.jellyplay.feature.player.video.state.ReadySubtitleHint
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayController
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayPreparation
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_direct_play_fallback
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_delete_offer_failed
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

private const val LOAD = "PlaybackSession.load"

/** How long the "Skipped …" segment-skip confirmation caption stays up. */
private const val SKIPPED_SEGMENT_NOTICE_MS = 2_500L

/**
 * Up Next card + manual queue trace (the queue mirror's VM-side half, the
 * card's overlay flag, the end-of-playback autoplay decision): one filter,
 * `adb logcat -s UpNext UpNextQueue`, covers the whole chain.
 */
private const val UP_NEXT_LOG_TAG = "UpNext"

/**
 * One playback session's lifecycle — the player's DEEP MODULE and its
 * COMPOSITION ROOT: the collaborator graph is built here, inside the session,
 * in one deliberate construction pass (the class body's collaborator
 * section, in dependency order) and armed by [arm] (the collector
 * registrations run in one fun — there are no late-bound slots to bind,
 * because the construction cycles dissolved by OWNERSHIP:
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
 * The ViewModel keeps the user-intent funnel ([VideoPlayerViewModel.onEvent])
 * + its expose-only flows; the session owns the collaborator graph, the load
 * sequence, the engine-swap choreography, the reporting + release surface and
 * the single forwarder that maps [SessionEvent]s into the VM's sinks — and the
 * session-policy folds the forwarder and the progress reporter dispatch: the
 * end-of-media fold ([EndOfMediaDecision], with the #14 offer controller this
 * class constructs) and the segment-skip dispatch (with its "Skipped …"
 * notice).
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

    /**
     * TRUE when the teardown release about to run is caused by the HOST ITSELF
     * going away (the activity finishing behind the player — the user swiped
     * the app from recents mid-playback, or the system tore the task down),
     * FALSE for an in-app player close. The ledger reads it at the final stop
     * report to decide the restore prompt's handled-offer marker: an in-app
     * close is a deliberate "done for now" (persist, next launch stays
     * silent), a host exit is an INTERRUPTED session (skip, next launch
     * prompts). A probe, not a flag: the session is constructed once while
     * the answer belongs to the teardown moment, so the owner wires the
     * read. Default FALSE (persist) keeps bare constructions and the desktop
     * shell (whose in-app closes are the only teardowns it sees — a window
     * close kills the process before any teardown runs) on the persist side.
     */
    private val isHostTeardown: () -> Boolean = { false },

    /** Manual up-next queue (details screen "play next/last"); drained by the Up Next controller's queue-first advance. */
    val videoQueue: VideoQueueController = VideoQueueController(),

    /** "Play (with pre-rolls)": the one-shot override the ⋮ action arms; the [initialize] funnel consumes it into [LoadRequest.forceCinemaMode]. Null in the behavioral test graphs (can never force). */
    private val cinemaModeOverride: com.raulshma.jellyplay.core.data.playback.CinemaModeOverride? = null,

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

    // ── Constructor dependencies ──────────────────────────────────────────────

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
     * publishes loads through it; the end-of-media dispatch marks the Ended
     * event and the full teardown ([performRelease]) clears it — NOT the
     * per-item re-initialization, where the session manager's release also
     * runs.
     */
    private val nowPlayingReporter: NowPlayingReporter,
    /**
     * The #14 delete seams (can-delete capability + server delete + cache
     * invalidation), bundled for one slot; null in test graphs — the
     * post-playback delete offer never arms without it. (Formerly a ViewModel
     * constructor slot; [PostPlaybackActionsController] consumes it
     * session-side since the end-of-media fold moved in.)
     */
    private val postPlaybackDeleteSources: PostPlaybackDeleteSources? = null,
    private val subtitleSources: PlayerSubtitleSources,
    private val offlineSources: PlayerOfflineSources,
    private val sessionStack: PlayerSessionStackSources,
    private val itemContent: PlayerItemContentSources,
    private val handles: PlayerStateHandles,

    // ── Engine-room dependencies (kept injectable for the behavioral suites) ──

    private val playbackStore: PlaybackStore,
    /** Server playback telemetry (the session-owned Stop/Start reports). */
    private val playbackRepository: PlaybackRepository,
    /** Offline-mirror writes for the seek-coalesced + throttled position persists. */
    private val offlinePlaybackFacade: OfflinePlaybackFacade,
    /** The detail-read family seam — the mini-player reclaim body's media-detail fetch. */
    private val detailFeed: DetailFeed,
    /** The process-death resume-position persistence; production: the SavedStateHandle store. */
    positionStoreOverride: SessionPositionStore? = null,
) : SessionLoadOutputs, SessionLifecycleHooks {

    // ── The state-holder bundle, unpacked ────────────────────────────────────
    //
    //    Inferred aliases (no state-bag type name crosses this file — the
    //    migrated-controller guard): every session write goes through these
    //    narrow handles.

    private val uiState get() = handles.uiState
    private val positionMs get() = handles.positionMs
    private val durationMs get() = handles.durationMs
    private val videoStats get() = handles.videoStats
    private val resumeReminder get() = handles.resumeReminder
    private val closePlayer get() = handles.closePlayer
    private val passOutEvents get() = handles.passOutEvents

    /**
     * The artwork resolver over this session's [imageUrlProvider]: every
     * tagged artwork URL the session mints (the media slice's artwork the
     * detail apply publishes, the engine hand-off + media-session pins) rides
     * the item's image tags, so re-uploaded art mints a new cache key.
     */
    private val artworkUrls: ArtworkUrls = DefaultArtworkUrls(imageUrlProvider)

    // ── The ui-mirror seams (derived, not injected) ───────────────────────────
    //
    //    The values derived over the handles + the cached aggregate. The
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
    //    at their construction sites. Every construction cycle is
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
            detailFeed = detailFeed,
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
    // plumbing both players share: the EngineEventCoordinator's
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
     * the reload paths. The session is the single forwarder: the collector
     * below maps each event into the VM's sinks (the genuinely
     * VM-bound ones through [host]). `tryEmit`-only — a mid-teardown emission
     * never suspends (same contract as the coordinator's decision stream).
     */
    val events: SharedFlow<SessionEvent> = engineEventShell.events

    // Task slots for the session's cancel-and-replace choreographies. The
    // bundle owns only the slot bookkeeping; scope lifecycle (the injected
    // owner scope + releaseScope) stays with the session.
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
     * ordering relative to [engineFlow] collectors is unchanged.
     */
    val sessionState: StateFlow<PlayerSessionState> = playerSessionManager.sessionState

    /** Direct alias of the session manager's engine flow — same instance, no re-publish. */
    val engineFlow: StateFlow<MediaEngine?> = playerSessionManager.engineFlow

    /** One-shot engine read beside the [engineFlow] alias — the live value, no subscription. */
    internal val engine: MediaEngine? get() = playerSessionManager.engine

    /**
     * The next-item stream-cache pre-warm triggers (the manager's warm slot):
     * [warmNextEpisode] resolves the card's item into a [warmNextStream]
     * emission the byte-watcher consumes.
     */
    internal fun warmNextEpisode(itemId: String) {
        playerSessionManager.warmNextEpisode(itemId)
    }

    internal val warmNextStream: StateFlow<WarmNextStream?>
        get() = playerSessionManager.warmNextStream

    /**
     * The "what's playing" projection — the session-state fields non-owner
     * callers actually read (the VM's post-[playerSessionManager] surface).
     * One snapshot fun instead of per-field aliases, so a new field costs a
     * bundle entry here and nothing at the callers' seam. Snapshot at call
     * time, exactly like the two-level reads it replaces.
     */
    internal fun playbackFacts(): PlaybackFacts =
        playerSessionManager.sessionState.value.let { state ->
            PlaybackFacts(
                currentItemId = state.currentItemId,
                title = state.title,
                currentMediaSourceId = state.currentMediaSource?.id,
                playMethod = state.playMethod,
                isOffline = state.isOffline,
            )
        }

    /** The engines this platform can construct (the playback sheet's engine chips). */
    internal val availablePlayerTypes: List<PlayerType> get() = platform.availablePlayerTypes

    /**
     * The subtitle-language preference slice the track picker's
     * preferred-language display filter reads — picker presentation only;
     * automatic selection never consults it.
     */
    internal val subtitlePreferences: StateFlow<SubtitleSlice> get() =
        stores.subtitleLanguage.subtitle

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
        detailFeed = detailFeed,
    )

    internal val subtitles = SubtitleManager(
        contentGateway = platform,
        playbackRepository = playbackRepository,
        detailFeed = detailFeed,
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
     * the projector↔projection mutual reference flows one way.
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
        // projector; these fire from it with the same cadence/order. The
        // lambdas capture only stable handles and read
        // later-declared collaborators lazily — invoked only from the
        // collector registered in [arm], long after those properties
        // initialise.
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
        artworkUrl = { item -> artworkUrls.poster(item) },
        adoptSeasonOf = { detail -> upNext.adoptSeasonOf(detail) },
        getEngine = { playerSessionManager.engine },
    )

    /**
     * The becoming-noisy auto-pause owner (headphone unplug → pause; the
     * focus half lives in the PlaybackFocus module at the video slice) plus
     * its opt-in resume-on-headset-insert
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

    // ── Cross-player exclusivity (the video focus slice) ──────────────────────
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
     * lifecycle. The dispatch lambdas route through [transport] (the session's
     * transport router) or read later-declared collaborators lazily.
     */
    internal val pipTransport = PipTransportController(
        pipController = pipController,
        getEngine = { playerSessionManager.engine },
        routedPlay = { play -> transport.play(play) },
        seekByStep = { direction -> transport.step(direction) },
        playNextEpisode = { upNext.playNextManually() },
    )

    /** The session-owned transport routing module (ladders + local seek arm; wiring in TransportRouter.kt). */
    internal val transport = transportRouter(
        handles,
        stores,
        // The seek clamp's "Skipped …" notice — the SAME notice the
        // auto-skip arm raises (see [showSkippedSegmentNotice]).
        onSegmentSkippedNotice = ::showSkippedSegmentNotice,
    )

    /**
     * The session-owned engine-command funnel (wiring in EngineCommands.kt):
     * the DIRECT engine commands — the speed family with its hold-speed
     * state, the scrubbing bracket and the controls-visibility polling
     * cadence — that never ride [transport]'s routing ladder (they address
     * the decode surface, not the timeline ownership).
     */
    internal val engineCommands = engineCommands(handles)

    /** Bumped per notice so same-type consecutive skips re-trigger the UI. */
    private var skippedSegmentNoticeSeq: Long = 0

    /** The auto-clear job backing [showSkippedSegmentNotice]'s ~2.5 s window. */
    private var skippedSegmentNoticeJob: Job? = null

    /**
     * Raises the ephemeral "Skipped …" caption on the uiState and
     * re-arms the auto-clear. A notice replacing a live one restarts the
     * window; the caption's [SkippedSegmentNotice.seq] makes the same-type
     * consecutive case visible to the collector. Fired from BOTH segment-skip
     * arms — the transport clamp's local seek and the position-tick
     * auto-skip — so the notice has ONE home.
     */
    private fun showSkippedSegmentNotice(segmentType: MediaSegmentType) {
        uiState.update {
            it.copy(
                skippedSegmentNotice = SkippedSegmentNotice(
                    segmentType = segmentType,
                    seq = ++skippedSegmentNoticeSeq,
                ),
            )
        }
        skippedSegmentNoticeJob?.cancel()
        skippedSegmentNoticeJob = scope.launch {
            delay(SKIPPED_SEGMENT_NOTICE_MS)
            uiState.update { current ->
                // Only clear OUR notice — a newer one may have replaced it.
                if (current.skippedSegmentNotice?.seq == skippedSegmentNoticeSeq) {
                    current.copy(skippedSegmentNotice = null)
                } else {
                    current
                }
            }
        }
    }

    /** The session-owned pref write choreography (wiring in PlayerUiCommands.kt). */
    internal val uiCommands = playerUiCommands(handles, stores, playbackStore)

    /**
     * The "Still watching?" confirm overlay's prompt lifecycle (feature 1.3) —
     * the [UpNextController] shape: the prompt StateFlow and the
     * show/continue/stop/tick choreography live in [StillWatchingController];
     * the prompt is raised by its two session-side arms (the end-of-media
     * gate's episode arm in [handlePlaybackEnded] and the hours arm via
     * `SessionEvent.StillWatchingPrompt`) and the overlay's event arms
     * forward one-line through the session slice. Pure decisions:
     * [StillWatchingGate] / [StillWatchingPromptState].
     */
    internal val stillWatching = StillWatchingController(
        getCountdownSeconds = { uiState.value.autoplay.autoPlayCountdownSec },
        onUserInteraction = { autoplayController.onUserInteraction() },
        playNextEpisode = { upNext.playNextManually() },
        resumePlayback = { transport.resume() },
        pauseEngine = { playerSessionManager.engine?.pause() },
        cancelAutoplay = { upNext.cancelAutoplay() },
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
            getPlaySessionId = { positionLedger.currentPlaySessionId },
            getResolvedPlayMethod = { playerSessionManager.sessionState.value.playMethod },
            getMediaEngine = { playerSessionManager.engine },
            getIncognitoModeEnabled = getIncognitoModeEnabled,
            onAutoSkip = { segment -> autoSkipSegment(segment) },
            onPlaybackEndedNoNext = { onEndedWithNoNext() },
            onWatchedThresholdReached = { itemId ->
                // Forwarded into the episode-continuation controller declared
                // below — the lambda only runs long after construction, so its
                // (lazy) read of the not-yet-initialised property is safe.
                upNext.handleSmartDownloadCleanup(itemId)
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
                        // Silent mode: the player is not a detail surface —
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

    /**
     * The position/report family's module — constructed here, inside the
     * session root (the composition-root stance; never injected). Owns the
     * play-session id resolution, the seek latch and report position, the
     * stop-report pair with its dedup, the process-death persistence and the
     * lifecycle phase verbs (new-item reset, item-loaded bookkeeping, the
     * teardown bracket) whose pinned orders are private to it. The members
     * below on the session are thin delegates so the load pipeline, the
     * transports and the tests keep their surface.
     */
    internal val positionLedger = SessionPositionLedger(
        scope = scope,
        releaseScope = releaseScope,
        clock = clock,
        positionStore = positionStore,
        playbackRepository = playbackRepository,
        offlinePlaybackFacade = offlinePlaybackFacade,
        progressReporter = progressReporter,
        playerSessionManager = playerSessionManager,
        getIncognitoModeEnabled = getIncognitoModeEnabled,
        seedDisplayedPositionMs = { seed -> seedDisplayedPositionMs(seed) },
        isHostTeardown = isHostTeardown,
        persistHandledOffer = { itemId, finalPositionMs ->
            stores.playback.setSessionRestoreDismissal(playbackHandledFingerprint(itemId, finalPositionMs))
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
                transport.seekLocal(positionTicks / 10_000, false)
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
        // The per-item hydration body (its every collaborator is
        // session-internal): the videoFx mirror write, the engine-config
        // rebuild nudge and the style-controller hydration.
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
        fetchAdjacentEpisodes = { detail -> upNext.refreshAdjacent(detail) },
        loadSeriesEpisodes = { detail -> upNext.loadSeries(detail) },
        // No terminal-outcome action today; stated explicitly here so a
        // future consumer is a construction-site change, not a hidden
        // default somewhere else.
        onOutcome = { },
    )

    /**
     * The load spine: owns the ORDER of the load stages (SyncPlay
     * reconcile → prefs seed → muted restore → cinema gate → offline
     * resolution → loadMedia → hydration → media session → trickplay →
     * reports). Its uiState-shaped outputs are this session
     * ([SessionLoadOutputs]); its VM-bound bodies are [loadHooks]. The three
     * stage bodies with real logic are pipeline members (see its KDoc).
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
     * Up Next - the session module behind the queue-first advance, the
     * manual queue-row activation, the card + has-queued-next projections,
     * the season/episode browsing, adjacent discovery, previous/next
     * choreography with the single-flight latch, the "mark watched &
     * skip" / "mark unwatched & quit" overflow orchestration, the
     * autoplay-cancel wiring, the Up Next overlay's loading flag and the
     * smart-download cleanup ([UpNextController]); the screen reaches the
     * controller through the overlay/sheets session slices, the PiP
     * transport and the end-of-media decision call it directly here.
     * Declared after the engine room (the navigator latches onto the
     * session's events); lambdas read later-declared collaborators lazily.
     */
    internal val upNext = UpNextController(
        scope = scope,
        sessionState = playerSessionManager.sessionState,
        sessionEvents = events,
        episodeCatalogue = itemContent.episodeCatalogue,
        videoQueue = videoQueue,
        syncPlaySessionMirror = syncPlay.state.map { it.isInSyncPlaySession }.distinctUntilChanged(),
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
        // Binge controls (#17): the cap leaf as a flow (the reactive limit
        // verdict) and the after-finish keep-gate's pref arm (the same
        // hydrated-aggregate read the incognito gate uses).
        autoplayEpisodeLimit = stores.aggregateStore.aggregate
            .map { it.videoPlayer.autoplayEpisodeLimit }
            .distinctUntilChanged(),
        isShowDetailsAfterFinishEnabled = { cachedAggregate.videoPlayer.showNextEpisodeDetailsAfterFinish },
        isSmartDownloadsEnabled = { stores.downloads.downloads.value.smartDownloadsEnabled },
        getDurationMs = { uiState.value.duration },
        deleteDownload = { itemId -> offlinePlaybackFacade.deleteDownload(itemId) },
        notifySmartDownloadDeleted = { userMessageBus.info(PlayerVideoMessage.SmartDownloadDeleted) },
    )

    /**
     * #14 offer-delete-after-playback: the post-playback controller,
     * constructed here inside the session — every fact it reads is
     * session-owned (the session-state projection + the cached aggregate).
     * The [UpNextController] shape: every read is the narrow constructor
     * lambda below, NO uiState handle crosses; the screen reaches its offer
     * flow + commands through the overlay session slice
     * ([createSessionSlices]). The core:data delete seams arrive as one
     * nullable bundle ([PostPlaybackDeleteSources]); null (test graphs)
     * disables the feature. The offer arms only from the end-of-media
     * dispatch ([EndOfMediaDecision]'s OfferDelete rung — a cinema chain
     * continuing, an auto-advance, a still-watching prompt and the
     * sleep-timer intercept all route away before it) — and a passing
     * precheck HOLDS that end's close (the dialog lives in the player
     * screen); the hold resolves through Delete / Keep / the
     * permission-read release.
     */
    internal val postPlaybackActions = PostPlaybackActionsController(
        scope = scope,
        sources = postPlaybackDeleteSources,
        getMediaKind = { playerSessionManager.sessionState.value.mediaDetail?.item?.mediaType },
        getItemId = { playerSessionManager.sessionState.value.currentItemId },
        getItemTitle = { playerSessionManager.sessionState.value.title },
        getSeriesIdHint = { playerSessionManager.sessionState.value.mediaDetail?.item?.seriesId },
        isIncognito = { cachedAggregate.videoPlayer.incognitoModeEnabled },
        isOfflinePlayback = { playerSessionManager.sessionState.value.isOffline },
        offerForMovies = { cachedAggregate.playback.offerDeleteAfterPlaybackMovies },
        offerForEpisodes = { cachedAggregate.playback.offerDeleteAfterPlaybackEpisodes },
        closePlayer = { closePlayer.trySend(Unit) },
        closeAfterKeep = {
            // The end-of-media close, deferred until the offer resolved
            // ("Keep" or a failing permission read), routed through the same
            // [EndOfMediaDecision] fold as the natural end so the #17
            // after-finish keep gate is single-sited — but as the offer's
            // own RELEASE: the offer is not re-entered (this call IS its
            // resolution) and the cinema arm routed away before the offer
            // ever armed, so only the keep-gate rung is live. The gate is
            // re-evaluated LIVE: a card still holding the player outlives
            // the dismissed dialog.
            executeEndOfMedia(
                EndOfMediaDecision.resolve(
                    EndOfMediaDecision.Facts(
                        advance = null,
                        close = EndOfMediaDecision.CloseFacts(
                            cinemaChainActive = false,
                            offerResolved = true,
                            keepCardHolds = heldByAfterFinishCard(),
                        ),
                    ),
                ),
            )
        },
        notifyDeleteFailed = { title ->
            userMessageBus.error(
                getString(Res.string.player_video_delete_offer_failed, title),
            )
        },
    )

    /**
     * The incognito/prefs gate cache the session-owned reports read off-Main.
     * The aggregate collector registered in [arm] keeps it fresh.
     */
    // @Volatile: written by the aggregate collector registered in [arm], read
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
            // (severed by this property's explicit type) replaces the
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
     * The in-player input-binding quick toggle: flips one binding's enabled
     * flag — the uiState write moves the gate immediately (the detectors
     * resolve through the mapping), the flip persists through the store's
     * read-modify-write verb (a settings-editor write is never clobbered by
     * a stale whole-map write).
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
     * them — the global mpv/exo slice persist, the per-item/series render-profile
     * rows and the session item-change re-resolution. The config-dirty
     * trigger routes through this session's [markEngineConfigDirty] funnel.
     * Engine-side application stays in the sync: the controller only reports a
     * dirty config through `onConfigDirty`.
     */
    internal val render = RenderControls(
        scope = scope,
        getGlobalMpvConfig = { cachedAggregate.engine.mpvConfig },
        saveGlobalMpvConfig = { config -> stores.engine.setMpvConfig(config) },
        getGlobalExoConfig = { cachedAggregate.engine.exoPlayerConfig },
        saveGlobalExoConfig = { config -> stores.engine.setExoPlayerConfig(config) },
        getHdrFormatPreference = { cachedAggregate.playback.hdrFormatPreference },
        saveHdrFormatPreference = { preference -> playbackStore.setHdrFormatPreference(preference) },
        loadStoredRow = { prefScope, id -> itemPlaybackPreferenceRepository.get(prefScope, id) },
        saveRenderProfile = { overrides -> playbackPreferenceWriter.setRenderProfile(overrides) },
        clearStoredRenderProfile = { playbackPreferenceWriter.clearRenderProfile() },
        onConfigDirty = { markEngineConfigDirty() },
    )

    /**
     * Owns the AV-sync sheet's cue preview (external track load + embedded cue
     * accumulation + the sheet-visibility gate) — the `subtitlePreviewCues` /
     * `subtitlePreviewSource` / `previewSheetVisible` uiState cluster. The
     * engine-attach choreography feeds it the engine's cue list; track
     * selection pokes it eagerly.
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
     * Owns the subtitle-style + dialogue-boost + subtitle-delay choreography:
     * style edits, per-item delay writes and their debounced engine
     * re-sync, the per-item dialogue-boost persist, the user-font install and
     * the direct engine re-apply of the current style. The state stays in the
     * uiState mirrors, the controller writes through the narrow lambdas below
     * (no raw uiState handle crosses; the ownership guard is untouched). The
     * config triggers route through this session's [markEngineConfigDirty]
     * funnels (cycle 6).
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
     * slice they mutate. Public VM methods delegate so the public API stays
     * valid. Dialogue Boost, Equalizer, and Video Effects stay inline because
     * their state lives outside this controller (per-item repo / VM field /
     * cinema gate). Cycle 6's effects→sync edge routes through this session's
     * [markEngineConfigDirty] funnel; the sync's own getEffectsState read of
     * `effects` is the direct (backward) half.
     */
    internal val effects = VideoEffectsController(
        scope = scope,
        audioStore = stores.audio, audioEffectsStore = stores.audioEffects, playbackStore = playbackStore,
        syncConfig = { markEngineConfigDirty() }, syncConfigDebounced = { markEngineConfigDirtyDebounced() },
        persistEffectsGate = { playerSessionManager.sessionState.value.currentItemId?.takeIf { cinemaIntroContext == null } },
        persistVideoEffectsForItem = { itemId, fx -> scope.launch { stores.engine.setVideoEffectsForItem(itemId, fx) } },
    )

    /**
     * The ordered engine-attach choreography ([EngineAttachController],
     * beside the other controllers): everything the engineFlow collector
     * runs on every engine emission — the previous-collectors
     * cancel, the remote-control registry bind/clear, the style seed, the
     * capability mirror, the effects seed, the cast strategy re-pick, the
     * unsupported-audio-delay heads-up, the PiP next-action mirror, the
     * track-selection reset and the three per-engine fan-out collectors.
     * Declared AFTER every collaborator its wiring hands over (cast →
     * syncPlay → trackSelectionHelper → subtitlePreview →
     * subtitleStyleController → effects); the engineFlow collector registered
     * in [arm] is the single forwarding call, and the choreography's order is
     * jvmTest-pinned in EngineAttachControllerTest. The lambdas read only
     * session-owned slices (the aggregate cache, the session state, the
     * media-detail holder); the ONE ui-state write goes through the narrow
     * onEngineCapabilities lambda — the ownership guard is untouched.
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
        onStreamMetadataReset = { videoStats.value = EngineVideoStats() },
    )

    /**
     * The aggregate-prefs collector's side-effecting half: the seeds, the
     * engine-config rebuild triggers, the autoplay flip and the video
     * focus-policy push - the side-effecting half beside [settingsProjector]
     * (whose `project` stays the pure-projection half). Declared after every
     * collaborator its wiring reads; the lambdas run only from the collector
     * registered in [arm], long after construction. The rebuild trigger
     * routes through this session's [markEngineConfigDirty] funnel (cycle 6).
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
     * snapshot + the live-engine dispatch — both trigger paths: the
     * immediate rebuild (`markDirty`) and the drag-settling debounce
     * (`markDirtyDebounced`). The uiState bag never crosses (the ownership
     * guard unmoved) — this ONE sanctioned uiState site projects the slices;
     * `getEngine` is read at dispatch time, so a debounce settling after an
     * engine swap lands on the NEW engine.
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
     * [TrackPreferenceFanout.onPreferenceResolved]; the [arm] collector is
     * the only registration. Declared after [trackSelectionHelper] — its
     * lambdas read it, but only run from the [arm] collector, long after
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
     * still-playing engine. [arm] only registers the collector.
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
            // The artwork read runs at session-build time, so the tag resolves
            // against the detail the session holds THEN (the pipeline builds
            // the session after loadMedia, the rebind funnel against the live
            // session state) — same itemId, the item's own primary tag rides
            // along; without a matching detail the raw-id URL stands.
            getImageUrl = { itemId, maxWidth ->
                artworkUrls.posterFor(playerSessionManager.sessionState.value.mediaDetail?.item, itemId, maxWidth)
            },
        )

    /**
     * The reload family's module ([PlaybackReload]): ONE `reload(variant)`
     * verb carrying the five reload shapes' shared swap choreography.
     * Constructed like [positionLedger] — inside the session
     * root, never injected. The rebind tail stays session-owned (wired in as
     * a self-reference lambda, cycle 2's pattern): the cinema pre-rolls and
     * the mini-player reclaim adopt engines without going through
     * [PlaybackReload.reload] and still share it.
     */
    private val reloadCoordinator = PlaybackReload(
        scope = scope,
        playerSessionManager = playerSessionManager,
        positionLedger = positionLedger,
        progressReporter = progressReporter,
        mediaSessionController = mediaSessionController,
        playbackStore = playbackStore,
        adaptiveBitrateManager = adaptiveBitrateManager,
        setPendingStreams = setPendingStreams,
        setUiPlaybackMode = setUiPlaybackMode,
        rebindSessionTracking = { itemId -> rebindSessionTracking(itemId) },
    )


    /**
     * The lifecycle hooks the session calls on the owner's behalf —
     * production: the session itself (the owner's slices are
     * session-internal); tests: recording doubles.
     */
    private val lifecycleHooks: SessionLifecycleHooks get() = hooksOverride ?: this

    /**
     * The immediate engine-config rebuild trigger: the collaborators'
     * dirty-config reports (render sheet, style controller, effects
     * controller, prefs fan-out) route through this funnel — cycle 6's
     * ownership seam.
     */
    internal fun markEngineConfigDirty() {
        engineConfigSync.markDirty()
    }

    /** The drag-settling trigger. */
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
     * Job returned from [initialize] when a hook early-returns before any
     * load was launched (remote "Play On" routing, same-item short-circuit):
     * callers get a uniformly typed, already-finished handle instead of a
     * dangling active job.
     */
    private val noLoadJob: Job = Job().also { it.complete() }

    /**
     * The user-facing load funnel: clears the PiP event flags a process
     * singleton may have left set, resolves the process-death resume position,
     * then runs the session-owned [initialize] sequence. Also the entry the
     * session-internal collaborators use (SyncPlay group loads, next-episode
     * advances) — the SAME funnel the user's open action takes.
     */
    fun initialize(
        itemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
        subtitleStreamIndex: Int? = null,
        audioStreamIndex: Int? = null,
        forceStream: Boolean = false,
    ) {
        // Defensive: PipController is a process @Singleton whose one-shot event
        // flags outlive this Activity. A flag left set by an abnormally torn
        // down previous session must never greet the next load — the fresh
        // screen would react to it instantly and close. Legitimate
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
                // consumed exactly once — later loads (and advanceCinemaIntro's
                // direct request) follow the global pref again. See [LoadRequest.forceCinemaMode].
                forceCinemaMode = cinemaModeOverride?.consume() == true,
                subtitleStreamIndex = subtitleStreamIndex,
                audioStreamIndex = audioStreamIndex,
                forceStream = forceStream,
            )
        )
    }

    /**
     * The load sequence, in order:
     *
     * 1. `released = false`;
     * 2. [SessionLifecycleHooks.rearmTransports] (PiP transport re-arm)
     *    followed by the session-owned engine-event coordinator re-arm
     *    ([EngineSessionShell.reArm] — a no-op unless a previous release
     *    disposed the coordinator);
     * 3. [SessionLifecycleHooks.resetForNewItem] (autoplay reset,
     *    autoplay-cancelled clear, coordinator new-item latch, pending stream
     *    indices);
     * 4. the ledger's new-item reset ([SessionPositionLedger.resetForNewItem]:
     *     the seek latch + the Stop-dedup latch drop, one verb);
     * 5. remote-routing early-return via
     *     [SessionLifecycleHooks.routeToRemotePlaySession];
     * 6. same-item short-circuit via [shouldShortCircuitSameItemReload];
     * 7. [SessionLifecycleHooks.wasInSyncPlay] (SyncPlay flag read) followed
     *     by the outgoing session's stop-report ([reportCurrentPlaybackStopped],
     *     session-side, directly after the flag read);
     * 8. cancel any in-flight the load task slot;
     * 9. mini-player reclaim early-return: the GATE stays a hook
     *    ([SessionLifecycleHooks.tryReclaimMiniPlayer] — mini-player state
     *    knowledge), but the body ([loadReclaimedEngine]) is session-side
     *    veil lift via [SessionLifecycleHooks.onMiniPlayerReclaimed]
     *    (synchronously before the body launch), then detail fetch → engine
     *    bind → media session → tracking restart → hydration via
     *    [SessionLifecycleHooks.hydrateReclaimedItem];
     * 10. [SessionLifecycleHooks.releaseMiniPlayerState];
     * 11. per-item teardown, split into two back-to-back halves (same
     *     synchronous call chain, no dispatch hop between them — an
     *     interleaved recomposition could flash the outgoing item's rebuilt
     *     stale title): the session-owned half ([releaseInternalsSessionPart]:
     *     in-flight load cancel, reporter jobs, media-session release, PSM
     *     release, seek latches) FIRST, then
     *     [SessionLifecycleHooks.releaseInternalsVmPart] (loading-veil raise +
     *     the controller/ui-state teardown);
     * 12. the ledger's item-loaded verb ([SessionPositionLedger.onItemLoaded]):
     *     the process-death play-session restore/allocate, then the
     *     persistence-latch resets (the never-persisted sentinel back in
     *     place, the throttle window re-armed, the pending coalesced
     *     seek-mirror write dropped);
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
        // New item = new session: the ledger's new-item reset (the seek
        // latch + the Stop dedup latch, so the upcoming session's Stop can
        // be reported). Runs BEFORE the gates below on purpose — a routed
        // away or short-circuited intent still cleared the latches.
        positionLedger.resetForNewItem()

        // "Play On" routing: a connected Jellyfin remote session takes the
        // video instead of local playback. (Full rationale on the routing
        // hook.)
        if (lifecycleHooks.routeToRemotePlaySession(request)) return noLoadJob

        if (shouldShortCircuitSameItemReload(request.itemId, request.startPositionTicks)) return noLoadJob

        val wasInSyncPlay = lifecycleHooks.wasInSyncPlay()
        // Stop-report the outgoing session before anything is cancelled or
        // torn down — its old position, directly after the flag read (the
        // report is session-side; the hook is a pure flag read).
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
        // Per-item teardown, split: the session-owned half runs FIRST, then
        // the controller/ui-state half back-to-back from this same synchronous
        // chain. The play-session restore runs right after the teardown.
        releaseInternalsSessionPart()
        lifecycleHooks.releaseInternalsVmPart()
        // New session bookkeeping — the ledger's item-loaded verb: the
        // process-death play-session restore/allocate, then the
        // persistence-latch resets (the pinned order is the ledger's now).
        positionLedger.onItemLoaded(request.itemId)
        // The playhead is seeded by the load pipeline from the RESOLVED start
        // ticks (see onPlayheadSeeded) — seeding from the raw request ticks
        // missed the offline-mirror resume that resolution produces.
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
     * the reload/retry family ([reloadForMode], the [PlaybackReload]
     * coordinator's arms) and the mini-player reclaim ([loadReclaimedEngine]).
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
        reloadCoordinator.reload(ReloadVariant.FallbackToTranscode(fromPositionMs, quality, selection))
    }

    /**
     * Retry playback on a different engine, or switch the live session when
     * [sessionOnly] is true (preserving pause state and the saved default).
     * [playbackSpeed] and [streamingQuality] are supplied by the caller
     * wrapper from its ui-state mirror; the error-dialog clear stays
     * caller-side (a synchronous ui-state write). The choreography is the
     * reload module's ([PlaybackReload], the [ReloadVariant.RetryWithEngine]
     * arm); the sessionOnly → persist/restore delta folds in
     * [engineRetryVariant].
     */
    fun retryWithEngine(
        playerType: PlayerType,
        playbackSpeed: Float,
        streamingQuality: StreamingQuality,
        sessionOnly: Boolean = false,
    ) {
        reloadCoordinator.reload(
            engineRetryVariant(
                playerType = playerType,
                streamingQuality = streamingQuality,
                playbackSpeed = playbackSpeed,
                sessionOnly = sessionOnly,
                enginePaused = playerSessionManager.engine?.isPlaying?.value == false,
            ),
        )
    }

    /**
     * Same-engine retry for recoverable errors (Network, Render, or the
     * buffering watchdog timeout). Reloads the current engine at the current
     * position, mirroring [retryWithEngine] without changing engine.
     * [preferredPlayerType] selects the engine (the ui-state mirror of the
     * last-chosen engine, supplied by the caller wrapper) — the
     * [ReloadVariant.RetrySameEngine] arm never persists it and never
     * touches the pause state.
     */
    fun retryPlayback(
        playbackSpeed: Float,
        streamingQuality: StreamingQuality,
        preferredPlayerType: PlayerType,
    ) {
        reloadCoordinator.reload(
            ReloadVariant.RetrySameEngine(preferredPlayerType, streamingQuality, playbackSpeed),
        )
    }

    /**
     * Reload playback for the current item at the current position with a new
     * audio/subtitle stream [selection]. Used when the user picks a
     * server-origin audio or subtitle track during transcoded playback — mpv
     * cannot switch audio in-place on an HLS manifest, and embedded subs aren't
     * in the transcode, so the server must re-issue the stream with the chosen
     * index. The [ReloadVariant.StreamChange] arm.
     */
    fun reloadForStreamChange(selection: MediaStreamSelection) {
        reloadCoordinator.reload(ReloadVariant.StreamChange(selection))
    }

    /**
     * Switches the playing version (media source) of the current item at the
     * current position — the Version sheet's pick. The pending stream-index
     * hints are CLEARED first: indices of the previous version are
     * meaningless server-side (and would bake the old audio/sub choice into
     * the new version's re-POST), so the new version starts on its own
     * defaults. The [ReloadVariant.SourceSwitch] arm.
     */
    fun switchMediaSource(mediaSourceId: String) {
        reloadCoordinator.reload(ReloadVariant.SourceSwitch(mediaSourceId))
    }

    // ── Mini-player reclaim ───────────────────────────────────────────────────

    /**
     * Load coroutine behind the mini-player-reclaim routing early-return in
     * [initialize]: binds the already-playing reclaimed engine to the session
     * manager and rebuilds its session bookkeeping (media session, tracking,
     * segments, episodes). Playback is continuous — no load screen, no
     * [SessionLoadPipeline] run (the engine never reloads).
     *
     * The GATE ([SessionLifecycleHooks.tryReclaimMiniPlayer]) stays a hook;
     * the two uiState/controller-bound slices stay in the hook implementation:
     * the loading-veil lift ([SessionLifecycleHooks.onMiniPlayerReclaimed],
     * synchronously before the body launch) and the post-bind hydration
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
            val detailResult = detailFeed.getMediaDetail(itemId, force = false)
            val detail = detailResult.getOrNull()
            if (detail != null) {
                playerSessionManager.bindReclaimedEngine(reclaimed, itemId, detail)
                rebindSessionTracking(itemId)
                lifecycleHooks.hydrateReclaimedItem(itemId, detail)
            }
        }
    }

    // ── Cinema Mode pre-roll sequencing ──────────────────────────────────────

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
    // by the end-of-media + segment-skip dispatch (handlePlaybackEnded /
    // skipIntro / the reporter's end-of-media callback — all session-side)
    // and the setVideoEffects per-item persist gate, and cleared by the
    // teardown half — see [SessionLifecycleHooks.releaseInternalsVmPart].
    @Volatile
    internal var cinemaIntroContext: CinemaIntroContext? = null

    /**
     * Cinema Mode take-over: queues the pre-roll [intros] and loads the first
     * one. Invoked through the pipeline's `beginCinemaMode` hook — the
     * session owns the whole sequencing (context + loads + advance).
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

    // ── End-of-media + segment-skip dispatch ──────────────────────────────────
    //
    //    The two dispatches the session-event forwarder and the progress
    //    reporter arm — moved from the ViewModel, whose every fact and every
    //    effect target here is session-owned: the autoplay/sleep/still-
    //    watching controllers, the Up Next advance + #17 keep gate, the
    //    cinema chain, the position/duration flows, the uiState sinks and
    //    the #14 offer controller. The DECISION halves stay in their pure
    //    modules ([EndOfMediaDecision], SegmentSkipPolicy.kt) — only the
    //    composition lives here.

    /**
     * The ONE position-aware snapshot fold behind the segment-skip dispatch
     * ([dispatchSegmentSkip]): every fact the skip ladder consults, read from
     * a single snapshot that injects the live position/duration from the
     * dedicated high-frequency flows over the (stale) `currentPosition`/
     * `duration` fields stored on uiState itself, so the active segment and
     * its end ticks can never come from different reads (the pairing rule
     * [SegmentDispatchFacts] exists to enforce).
     */
    private fun segmentDispatchFacts(): SegmentDispatchFacts {
        val state = uiState.value.copy(
            currentPosition = positionMs.value,
            duration = durationMs.value,
        )
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

    /** The "Skip Intro" button / menu arm (the VM's event funnel forwards). */
    internal fun skipIntro() {
        dispatchSegmentSkip(SegmentSkipKind.INTRO)
    }

    /**
     * The overlay button press for the active segment (user-initiated — the
     * seek itself is the feedback, so no confirmation notice).
     */
    internal fun skipSegment(segment: MediaSegment) {
        executeSegmentSkip(segmentEndSeekTarget(uiState.value.segmentEndTicks(segment)), userInitiated = true)
    }

    /**
     * The position-tick auto-skip arm ([PlaybackProgressReporter]'s
     * `onAutoSkip`): not user-initiated (never clamped) and confirmed with
     * the "Skipped …" notice so an invisible automatic jump is explained.
     */
    private fun autoSkipSegment(segment: MediaSegment) {
        executeSegmentSkip(segmentEndSeekTarget(uiState.value.segmentEndTicks(segment)), userInitiated = false)
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
            // A LOCAL seek — the segment-skip dispatch never rides the
            // SyncPlay/cast ladder (the group/receiver own their timelines).
            is SegmentSkipTarget.SeekToPosition ->
                transport.seekLocal(target.positionMs, userInitiated)
            SegmentSkipTarget.SkipToNextEpisode -> upNext.playNextManually()
            SegmentSkipTarget.AdvanceCinemaIntro -> advanceCinemaIntro()
            SegmentSkipTarget.None -> Unit
        }
    }

    private fun handlePlaybackEnded() {
        // App-wide now-playing seam's Ended event (feature 4.2): fires before
        // the autoplay/advance decision so shell hooks see ended-then-started.
        nowPlayingReporter.markEnded()
        val autoplayNext = uiState.value.episodes.nextEpisode
        val queuedNext = upNext.hasQueuedNext.value
        Log.d(
            UP_NEXT_LOG_TAG,
            "ended: nextEpisode=${autoplayNext?.id} hasQueuedNext=$queuedNext " +
                "enabled=${autoplayController.enabled} " +
                "cancelled=${autoplayController.cancelled}",
        )
        // The whole end-of-media dispatch is the pure [EndOfMediaDecision]
        // fold — the six verdict modules are consulted HERE (the verdicts
        // arrive as data; the fold decides ORDER + composition), and
        // [executeEndOfMedia] runs the returned effects in order.
        executeEndOfMedia(
            EndOfMediaDecision.resolve(
                EndOfMediaDecision.Facts(
                    advance = EndOfMediaDecision.AdvanceFacts(
                        armed = autoplayController.shouldAutoPlayNext(
                            autoplayNext,
                            hasQueuedNext = queuedNext,
                        ),
                        // #17 binge controls: a reached autoplay-episode cap
                        // blocks the advance — the card renders manual instead
                        // (the overlay reads the cap verdict through the slice).
                        upNextAllows = upNext.canAutoAdvance(),
                        sleepTimerIntercepts = sleepTimer.interceptsAutoAdvance(),
                        promptStillWatching = autoplayController.shouldPromptStillWatching(
                            mode = cachedAggregate.videoPlayer.stillWatchingMode,
                            isInSyncPlaySession = uiState.value.isInSyncPlaySession,
                        ),
                    ),
                    close = endOfMediaCloseFacts(),
                ),
            ),
        )
    }

    /**
     * End-of-stream with nothing queued: a cinema-intro chain advances to its
     * next item, anything else closes the player. Fold of the progress
     * reporter's `onPlaybackEndedNoNext` callback — must not drift.
     *
     * The two #17/#14 end-of-media policy arms hang here, where playback has
     * CONCLUDED (no advance fired, no cinema chain continues, the prompt and
     * sleep-timer arms already routed away) — as [EndOfMediaDecision] close
     * rungs, dispatched through [executeEndOfMedia]:
     *  - #14: the offer-delete-after-playback evaluation (a natural end —
     *    this is reached from the engine-ENDED arm and the reporter's
     *    safety net, never a user close). A passing precheck HOLDS the close
     *    (the dialog lives in the player screen — closing first would kill
     *    it unseen); the hold resolves through the offer's Delete / Keep /
     *    the permission-read release, each re-consulting the keep gate below;
     *  - #17: the after-finish keep gate — `showNextEpisodeDetailsAfterFinish`
     *    + a next item + no auto-advance pending keeps the Up Next card up
     *    persistently (the position is frozen at the end, so the card's
     *    window stays open) instead of closing.
     */
    private fun onEndedWithNoNext() {
        // The no-next dispatch enters at the close rung: the reporter's
        // safety net only fires when no episode sibling and no queued head
        // exist, so the advance rungs are structurally behind it.
        executeEndOfMedia(
            EndOfMediaDecision.resolve(
                EndOfMediaDecision.Facts(
                    advance = null,
                    close = endOfMediaCloseFacts(),
                ),
            ),
        )
    }

    /**
     * The #17 after-finish keep gate, live-evaluated against the current
     * autoplay state — both end-of-media close paths evaluate it at their
     * entry, and the fold ([EndOfMediaDecision]) single-sites the
     * hold-vs-close decision over it.
     */
    private fun heldByAfterFinishCard(): Boolean =
        upNext.shouldKeepUpNextCardAfterFinish(
            autoplayEnabled = uiState.value.autoplay.videoAutoplayNext,
            autoplayCancelled = uiState.value.autoplay.autoplayCancelled,
        )

    /**
     * The close rungs' facts for a natural end — the reads both end-of-media
     * entries share, evaluated at dispatch time (the offer arm is the
     * natural-end entry; the offer's own release builds its own
     * [EndOfMediaDecision.CloseFacts] with the offer marked resolved).
     */
    private fun endOfMediaCloseFacts(): EndOfMediaDecision.CloseFacts =
        EndOfMediaDecision.CloseFacts(
            cinemaChainActive = cinemaIntroContext != null,
            offerResolved = false,
            keepCardHolds = heldByAfterFinishCard(),
        )

    /**
     * The end-of-media executor: runs the [EndOfMediaDecision] fold's
     * effects in order, one arm per effect — the arms are the behaviors the
     * former if-ladders inlined. [Effect.OfferDelete] is the one arm that
     * can end the dispatch early: the controller's ONE entry point resolves
     * ownership HERE — an armed offer holds the close (its Delete / Keep /
     * the permission-read release resolve it later); a suppressed precheck
     * releases the ladder's tail (keep-card / close), exactly the original
     * fall-through.
     */
    private fun executeEndOfMedia(effects: List<EndOfMediaDecision.Effect>) {
        effects.forEach { effect ->
            when (effect) {
                EndOfMediaDecision.Effect.SleepTimerIntercept ->
                    // End-of-episode sleep timer outranks the
                    // advance/still-watching gates; an explicit skip-credits
                    // press still wins (deliberate).
                    sleepTimer.triggerSleepTimerEndOfEpisode()
                EndOfMediaDecision.Effect.PromptStillWatching ->
                    // "Still watching?" gate (feature 1.3): confirm overlay
                    // replaces the advance; no answer stops autoplay.
                    stillWatching.show(StillWatchingReason.EPISODE_COUNT)
                EndOfMediaDecision.Effect.RecordStreak ->
                    autoplayController.recordAutoAdvance()
                EndOfMediaDecision.Effect.AutoAdvance ->
                    // The engine-end AUTO arm: the binge streak grows here
                    // (#17); manual play-next paths reset it instead.
                    upNext.advanceUnattended()
                EndOfMediaDecision.Effect.ChainCinema ->
                    advanceCinemaIntro()
                EndOfMediaDecision.Effect.OfferDelete ->
                    if (postPlaybackActions.onPlaybackEndedNaturally()) return
                EndOfMediaDecision.Effect.HoldForKeepCard -> Unit
                EndOfMediaDecision.Effect.Close -> closePlayer.trySend(Unit)
            }
        }
    }

    // ── Reporting + position persistence (delegates into [positionLedger]) ────

    fun getReportPositionMs(): Long = positionLedger.getReportPositionMs()

    fun reportCurrentPlaybackStopped() = positionLedger.reportCurrentPlaybackStopped()

    internal suspend fun reportPlaybackStart(itemId: String, source: MediaSource?, playMethod: PlayMethod) =
        positionLedger.reportPlaybackStart(itemId, source, playMethod)

    fun seekPersisted(positionMs: Long) = positionLedger.seekPersisted(positionMs)

    fun persistPlaybackPosition(positionMs: Long, force: Boolean) =
        positionLedger.persistPlaybackPosition(positionMs, force)

    fun resolveStartTicksAfterProcessDeath(itemId: String, startPositionTicks: Long): Long =
        positionLedger.resolveStartTicksAfterProcessDeath(itemId, startPositionTicks)

    fun preSeedPlayhead(startPositionTicks: Long) = positionLedger.preSeedPlayhead(startPositionTicks)

    // ── Release ──────────────────────────────────────────────────────────────

    /**
     * The session-owned half of the teardown. Runs FIRST on both teardown
     * paths — the per-item re-initialization (see [initialize]) and the full
     * release (see [release]) — immediately followed by the
     * controller/ui-state half
     * ([SessionLifecycleHooks.releaseInternalsVmPart]) from the same
     * synchronous call chain.
     */
    private fun releaseInternalsSessionPart() {
        sessionTasks.cancel(LOAD)
        progressReporter.cancelJobs()
        mediaSessionController.release()
        playerSessionManager.release()
        // New item / released session: drop the seek latch — the ledger's
        // teardown-half reset (runs on BOTH teardown paths, so it is its own
        // verb, not part of onItemLoaded/onTeardown).
        positionLedger.resetSeekLatch()
    }

    /**
     * Full session teardown — the session-owned half inside the ledger's
     * teardown bracket ([SessionPositionLedger.onTeardown]): the ledger
     * snapshots the stop-report inputs FIRST (its bracket's opening
     * statement — [releaseInternalsSessionPart] calls PSM release, which
     * clears the session state those values read), then runs this body:
     * [releaseInternalsSessionPart] + the teardown callback
     * ([vmTeardownAfterInternals] runs the post-internals release steps:
     * PiP transport reset, cast consumer release, engine-controller clear)
     * — the order is preserved. When the body returns, the ledger flushes
     * the pending coalesced seek-mirror write (joined on the release scope
     * so it survives the viewModelScope cancellation on clear()) and issues
     * the final Stop report, deduped through the ledger's stop latch.
     */
    fun release(vmTeardownAfterInternals: () -> Unit) {
        positionLedger.onTeardown {
            releaseInternalsSessionPart()
            lifecycleHooks.releaseInternalsVmPart()
            vmTeardownAfterInternals()
        }
    }

    /**
     * The FULL teardown behind the owner's `release()` dispose hook: the
     * now-playing Stopped event, the collector/engine teardown ordering, the
     * audio-lifecycle + sleep-timer release, the session-owned release split
     * with the post-internals steps as the callback, then the pending-seek
     * join and the final stop-report on the release scope. Idempotent
     * through the [released] latch.
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

    // ── Arm ──────────────────────────────────────────────────────────────────

    /**
     * Registers every collector the session needs, in order (the engine-event
     * mirrors FIRST, the session-event forwarder, the PiP transport
     * registration + dismissal discharge, the aggregate-prefs and
     * metered-network collectors, the SyncPlay start + session mirror, the
     * becoming-noisy registration, the video-focus binding, the
     * displaced-holder observer, the session-state fold, the preference
     * resolver, and the engine-attach choreography LAST). Called EXACTLY ONCE
     * from the ViewModel's `init`, immediately after the session is
     * constructed. There are no late-bound slots to bind — the construction
     * cycles dissolved into ownership seams (see the class KDoc).
     */
    internal fun arm() {
        // Subscribe the engine-event fan-out FIRST: the coordinator's mirrors
        // collector must be active before any initialize() can produce an
        // engine state change (subscription timing). The coordinator's
        // decision executor lives in the session and is subscribed there.
        startEngineEventCoordinatorOutputs()
        // Single forwarder for the session's outcomes: one collector maps
        // each [SessionEvent] into the VM's existing sinks — except the
        // end-of-media policy event, whose fold ([handlePlaybackEnded]) the
        // session owns outright (every fact and effect target is
        // session-owned).
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
                    SessionEvent.PlaybackEnded -> handlePlaybackEnded()
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
        // defensive latch clear) lives on the shared core:data
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
            onRestore = { playerSessionManager.engine?.let { transport.applyResumeSkip(it) } },
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
            // onSessionState is the whole fold body, VM-lifetime fold state
            // included (never reset per item, so it survives
            // releaseInternalsVmPart).
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
     * The VIDEO claim edge (the video focus slice), folded onto
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
        // The session-reclaim twin of the audio manager's
        // reclaimAudioSessionIfDisplaced: a video session refused at birth
        // (the manager guard releases an idle challenger against a then-
        // playing music session) is rebuilt here — the granted claim has
        // paused the music holder, so the floor is free and the evictor's
        // session is not ours. Identity-checked in the controller: an
        // already-ours-and-active session never rebuilds.
        if (isPlaying) {
            val sessionState = playerSessionManager.sessionState.value
            val itemId = sessionState.currentItemId
            if (itemId != null) {
                mediaSessionController.ensureActiveForItem(itemId, sessionState.title, sessionState.subtitle)
            }
        }
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
        // Coordinator fallback-latch reset — a pure latch flip bundled here
        // with the other synchronous-prefix writes.
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
        // BODY is session-side; this is the veil write, before the body
        // launch.
        uiState.update { it.copy(isInitializing = false) }
    }

    override fun hydrateReclaimedItem(itemId: String, detail: MediaDetail) {
        // The hydration fetches: segments (the pipeline's offline-first
        // fetch, shared with the load spine) then the episode fetches.
        sessionLoadPipeline.fetchMediaSegments(scope, itemId)
        upNext.refreshAdjacent(detail)
        upNext.loadSeries(detail)
    }

    override fun releaseMiniPlayerState() {
        videoMiniPlayerState.release()
    }

    override fun releaseInternalsVmPart() {
        // The controller/ui-state teardown half (every collaborator it
        // touches is session-internal).
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
        // initialize() runs this half on every item load. The full
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
        // stays in this half immediately before the uiState rebuild. The
        // uiState rebuild below already nulls cinemaIntroState implicitly
        // (fresh constructor).
        cinemaIntroContext = null

        // Residual reset: session + prefs-mirror fields only. Everything that
        // reset implicitly (track lists, subtitle search, sleep timer, audio
        // effects, SyncPlay display, A/B repeat) is now reset — or deliberately
        // not reset — by its owning controller above. The surviving leaves are
        // declared in [keepAcrossItems].
        uiState.update { it.keepAcrossItems() }
        // The episode-slice reset goes through the Up Next controller's
        // seam — the navigator is the slice's single writer.
        upNext.resetForItemSwitch()

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
        // fires directly after this read inside PlaybackSession.initialize.
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
 * re-attach flag). The session implements this interface ITSELF (the
 * production `hooks` argument is null); the behavioral suites substitute
 * recording doubles to pin the call ORDER without weakening the pins.
 */
internal interface SessionLifecycleHooks {
    /**
     * Re-arms the PiP transport bridge for a new load: release()
     * nulled it and `init` does not re-run on this Activity-scoped, reused
     * VM. The engine-event coordinator re-arm is session-owned and
     * [PlaybackSession.initialize] performs it directly after this hook.
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
     * Null when there is nothing to reclaim. The GATE only — the
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
     * invokes this synchronously BEFORE launching the body (the veil write's
     * original position).
     */
    fun onMiniPlayerReclaimed()

    /**
     * Post-bind hydration for a reclaimed mini-player engine: the segments,
     * adjacent-episodes and series-episodes fetches whose uiState writes and
     * collaborators keep them in the hook implementation. Called by the
     * session's reclaim body (after the engine bind, media session and
     * tracking restart).
     */
    fun hydrateReclaimedItem(itemId: String, detail: MediaDetail)

    /** Releases the mini-player state when its engine was not reclaimed. */
    fun releaseMiniPlayerState()

    /**
     * The controller/ui-state part of the per-item teardown, prefixed by the
     * loading-veil raise. Called back-to-back AFTER the session-owned teardown
     * half — the session cancels the in-flight load, reporter jobs, media
     * session and PSM, and clears the seek latches directly before this hook —
     * from the same synchronous call chain (no dispatch hop). Also invoked by
     * [PlaybackSession.release] on full teardown, where the veil raise is a
     * same-value write (the ui-state rebuild constructs a fresh state whose
     * `isInitializing` default is already true). The play-session id restore
     * is session-side.
     */
    fun releaseInternalsVmPart()

    /** Clears the trickplay cache for the outgoing item. */
    fun clearTrickplay()

    /** Re-attaches the SyncPlay bridge to the (surviving) group session. */
    fun reattachSyncPlay()

    /**
     * Reads whether playback was in a SyncPlay session before this load
     * (feeding the conditional [reattachSyncPlay] call). A pure flag read:
     * the outgoing session's stop-report is session-owned
     * ([PlaybackSession.initialize] fires
     * [PlaybackSession.reportCurrentPlaybackStopped] directly after this
     * read).
     */
    fun wasInSyncPlay(): Boolean
}

/**
 * Event surface a [PlaybackSession] exposes to the ViewModel: the
 * session-event forwarder is the single mapper, routing each event into the
 * existing sinks (the close-player channel, the uiState error fields, the
 * user-message bus, the pass-out event channel); the end-of-media policy
 * event ([PlaybackEnded]) arms the session's own fold
 * ([PlaybackSession.handlePlaybackEnded]).
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

    /** Media playback reached its end (the end-of-media fold is session-side). */
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
     * The episode arm is raised by the session's end-of-media gate
     * ([PlaybackSession.handlePlaybackEnded]), which raises the same overlay
     * directly.
     */
    data class StillWatchingPrompt(val reason: StillWatchingReason) : SessionEvent
}

// [SessionPositionStore] lives in :shared:core:player-contract so the
// shared test-fixtures module's FakePositionStore can implement it through
// that module's existing player-contract edge. [SavedStateHandlePositionStore]
// below stays here: it is the production implementation and touches the
// SavedStateHandle type.

/**
 * Production [SessionPositionStore]: a thin wrapper over the ViewModel's
 * [SavedStateHandle]. Key names and the write order (item id, position,
 * play-session id, persisted-at) are stable across app upgrades, so a
 * process-death restore keeps resolving.
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
 * The session's "what's playing" snapshot ([PlaybackSession.playbackFacts]):
 * the [PlayerSessionState] fields non-owner callers actually read, one value
 * instead of two-level `session → PSM → sessionState.value → field`
 * reach-throughs.
 */
internal data class PlaybackFacts(
    val currentItemId: String?,
    val title: String,
    val currentMediaSourceId: String?,
    val playMethod: PlayMethod,
    val isOffline: Boolean,
)
