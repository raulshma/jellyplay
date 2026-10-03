package com.raulshma.jellyplay.feature.player.video

import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.playback.SleepCountdown
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.playback.dischargePipDismissal
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackFocus
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackSurfaceId
import com.raulshma.jellyplay.core.data.playback.focus.VideoFocusPolicyInput
import com.raulshma.jellyplay.core.data.playback.focus.claimOnPlayEdge
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.data.repository.LyricsRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.mediaRuleContentType
import com.raulshma.jellyplay.core.ui.viewmodel.StateFlowHandle
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.chrome.mirrorPlaying
import com.raulshma.jellyplay.feature.player.video.state.ReadySubtitleHint
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayController
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayPreparation
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_direct_play_fallback
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_error_next_episode_load
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * The video player's composition builder — the ONE place the player
 * collaborator graph is constructed and armed. Extracted from
 * [VideoPlayerViewModel] (whose body had grown to ~1,400 lines of in-class
 * collaborator wiring) and the deleted [VideoSessionHost] pass-through layer
 * (whose 15 collaborator parameters + 21 command lambdas + delegate
 * overrides existed only because the host was BUILT before the session it
 * wired).
 *
 * ## Phase 1 — construct
 *
 * The class body constructs every collaborator exactly once, in dependency
 * order, with their wiring lambdas attached at their construction sites —
 * the same one-line bodies [VideoSessionHost] used to collect behind its
 * delegate overrides. Collaborators that mutually reference each other's
 * wiring (the seven construction cycles that previously forced five
 * "load-bearing" explicit-type annotations on the ViewModel) are broken with
 * LATE BINDING, not reordering — reordering cannot break a cycle because
 * each edge needs the other side to exist first:
 *
 *  - `mediaContentProjector` ↔ `mediaDetailProjection` (applyDetail): the
 *    projector's lambda reads [mediaDetailProjectionRef];
 *  - `progressReporter` ↔ `playbackSession` (the play-session id resolver +
 *    the position persist): the reporter's lambdas read [playbackSessionRef];
 *  - the session stack: `playbackSession` takes [sessionLoadPipeline], which
 *    takes this builder as its `outputs`/`hooks` — the builder's interface
 *    overrides are METHODS (evaluated at call time), so the former host
 *    pass-through layer dissolves without a cycle; the load-hooks bundle
 *    ([loadHooks]) is the one lambda-shaped edge left, and it reads
 *    [playbackSessionRef] (the cinema handoff, the start report) and
 *    [episodeContinuationRef] (the episode fetches);
 *  - `trackSelectionHelper` ↔ `playbackPreferenceWriter`
 *    (persistRememberedTrack): the helper's lambda reads
 *    [playbackPreferenceWriterRef];
 *  - `effects`/`render`/`subtitleStyleController`/`prefsFanout` ↔
 *    `engineConfigSync` (the config-dirty triggers): their lambdas read
 *    [engineConfigSyncRef];
 *  - `sessionSubtitleSources` ↔ `playerSessionManager` (cycle 7: the
 *    session's subtitle sourcing is a session CONSTRUCTOR argument, so it is
 *    declared before the session and its wiring lambdas — the
 *    addExternalSubtitle seam and the session-state reads the sourcing bodies
 *    call back through — read [playerSessionManagerRef]).
 *
 * Each `...Ref` slot is declared unset (below) and bound ONCE in [arm];
 * the wiring lambdas that read the slots run only from session/engine call
 * chains — long after `arm` — so the reads never observe the unbound state.
 * With the back-references behind explicitly-typed slots, no property's
 * type inference flows through another's initializer anymore: the explicit
 * annotations were load-bearing only while the mutual references were
 * DIRECT, and that shape is now structurally impossible.
 *
 * ## Phase 2 — arm
 *
 * [arm] binds the five slots, then registers every collector the
 * ViewModel's former `init` block launched (the session-event forwarder,
 * the aggregate-prefs fan-out, the engine-attach choreography, the mirrors)
 * in the SAME order the inline block used. The ViewModel calls `arm` from
 * its `init` immediately after constructing this builder, so subscription
 * timing relative to the first session/engine emission is unchanged.
 *
 * ## The VM boundary
 *
 * The builder owns the collaborator graph and the uiState-write lambdas;
 * the ViewModel keeps the user-intent funnel (`onEvent` + its handlers),
 * the state-holder construction, and the expose-only flows. Everything the
 * wiring needs FROM the ViewModel's funnel arrives through [Host] — a
 * narrow call-back seam the VM implements (as a private adapter object, so
 * the VM's public/internal member surface — and with it the ownership
 * ratchet — is unchanged). `Host` is STORED in phase 1 but only INVOKED
 * from `arm` onward; no phase-1 initializer may call through it.
 *
 * The VM-bound members of the former [VideoSessionHost] landed here as the
 * [SessionLifecycleHooks] / [SessionLoadOutputs] implementations below; the
 * host's four real behavior funs moved to where their halves already lived:
 * `fetchMediaSegments` / `shouldAttemptCinemaMode` /
 * `restoreRememberedMuted` into [SessionLoadPipeline] (the load spine that
 * owns WHEN each of those stages runs), and `reportPlaybackStart` into
 * [PlaybackSession] (beside its stop-report twin and the canonical
 * play-session id resolver).
 */
internal class PlayerWiring(
    /** The ViewModel's scope (viewModelScope) — every collector and launch uses it. */
    private val scope: CoroutineScope,
    // ── ViewModel constructor collaborators ──────────────────────────────────
    //
    //    The collaborators the wiring body touches directly stay explicit;
    //    the groups that existed only to construct ONE internally-built
    //    module cluster are bundled at the call site ([PlayerStores]'s
    //    construction-bundle pattern — the composition surface stays flat
    //    without re-widening this constructor per collaborator):
    //    [PlayerSubtitleSources] (the subtitle/track content sources),
    //    [PlayerOfflineSources] (the offline/download availability trio),
    //    [PlayerSessionStackSources] (what the session stack is built from)
    //    and [PlayerItemContentSources] (the item-attached content reads),
    //    plus [PlayerStateHandles] (the ViewModel's state holders the wiring
    //    lambdas write through).
    private val platform: VideoPlayerPlatform,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val offlinePlaybackFacade: OfflinePlaybackFacade,
    private val playbackSourceResolver: com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver,
    private val itemPlaybackPreferenceRepository: ItemPlaybackPreferenceRepository,
    private val stores: PlayerStores,
    private val castManager: CastManager,
    private val syncPlayManager: SyncPlayManager,
    private val adaptiveBitrateManager: AdaptiveBitrateManager,
    private val networkMonitor: NetworkMonitor,
    private val activePlayerController: ActivePlayerController,
    private val pipController: PipController,
    private val videoMiniPlayerState: VideoMiniPlayerState,
    private val sleepCountdown: SleepCountdown,
    private val userMessageBus: PlayerVideoMessageBus,
    private val savedStateHandle: SavedStateHandle,
    private val userDataMutator: com.raulshma.jellyplay.core.data.repository.UserDataMutator,
    private val nowPlayingReporter: com.raulshma.jellyplay.core.data.playback.NowPlayingReporter,
    // ── The construction bundles ─────────────────────────────────────────────
    private val subtitleSources: PlayerSubtitleSources,
    private val offlineSources: PlayerOfflineSources,
    private val sessionStack: PlayerSessionStackSources,
    private val itemContent: PlayerItemContentSources,
    private val handles: PlayerStateHandles,
    /** The ViewModel funnel seam — stored in phase 1, invoked from [arm] onward. */
    private val host: Host,
) : SessionLoadOutputs, SessionLifecycleHooks {

    // ── The state-holder bundle, unpacked ────────────────────────────────────
    //
    //    One-line aliases restoring the historical member names, so every
    //    wiring lambda below reads exactly as it did when the holders were
    //    constructor parameters (the composition-test order pins and the
    //    phase-1 bodies are untouched by the bundling).

    /** The residual ui state bag — the wiring's narrow writes go through it. */
    private val uiState: StateFlowHandle<VideoPlayerUiState> get() = handles.uiState
    private val positionMs: MutableStateFlow<Long> get() = handles.positionMs
    private val durationMs: MutableStateFlow<Long> get() = handles.durationMs
    private val videoStats: MutableStateFlow<EngineVideoStats> get() = handles.videoStats
    private val resumeReminder: MutableSharedFlow<Long> get() = handles.resumeReminder
    private val closePlayer: Channel<Unit> get() = handles.closePlayer
    private val passOutEvents: Channel<String> get() = handles.passOutEvents

    /**
     * The ViewModel-owned behaviors the wiring calls back into: the transport
     * funnels ([seekTo]/[seekByStep]/[routedPlay]/[resumePlayback]), the load
     * funnel ([initialize]), the session-policy dispatch
     * ([autoSkipSegment]/[onEndedWithNoNext]/[handlePlaybackEnded]) and the
     * VM-owned lifecycle slices ([routeToRemotePlaySession] — the remote-play
     * strategy is a VM-only dependency — [releaseInternalsVmPart] with the
     * keepAcrossItems uiState rebuild, [onItemHydrated] and [release]).
     * Implementations are one-line delegations to the VM's private handlers;
     * the indirection exists so the VM's public/internal member surface (the
     * ownership ratchet) does not grow by the fourteen seam members.
     */
    internal interface Host {
        fun initialize(itemId: String, mediaSourceId: String?, startPositionTicks: Long)
        fun seekTo(positionMs: Long, userInitiated: Boolean)
        fun seekByStep(direction: Int)
        fun routedPlay(play: Boolean)
        fun resumePlayback()
        fun applyResumeSkip(engine: MediaEngine)
        fun autoSkipSegment(segment: MediaSegment)
        fun onEndedWithNoNext()
        fun handlePlaybackEnded()
        fun routeToRemotePlaySession(request: LoadRequest): Boolean
        fun releaseInternalsVmPart()
        fun onItemHydrated(itemId: String, hydratedAgg: VideoPlayerAggregate)
        fun release()
    }

    // ── Late-bound back-references (the broken construction cycles) ──────────
    //
    // Phase 1 declares these unset; [arm] binds each to the collaborator
    // constructed later in phase 1 — the ONE assignment each ever gets. The
    // wiring lambdas reading them run only from session/engine call chains,
    // long after [arm], so the unbound window is never observed. (KDoc on
    // each names the cycle it breaks; the class KDoc carries the full map.)

    /** Cycle 1: `mediaContentProjector.applyDetail` → the projection. */
    private lateinit var mediaDetailProjectionRef: MediaDetailProjection

    /** Cycles 2 + 3: the reporter's id/persist reads, the cinema handoff, the start report, the stream-change reload. */
    private lateinit var playbackSessionRef: PlaybackSession

    /** Cycle 3: the load hooks' episode fetches → the continuation controller. */
    private lateinit var episodeContinuationRef: EpisodeContinuationController

    /** Cycle 5: `trackSelectionHelper.persistRememberedTrack` → the writer. */
    private lateinit var playbackPreferenceWriterRef: ItemPlaybackPreferenceWriter

    /** Cycle 6: the config-dirty triggers (`effects`/`render`/style controller/prefs fan-out) → the sync. */
    private lateinit var engineConfigSyncRef: EngineConfigSync

    /**
     * Cycle 7: the session's subtitle sourcing. [sessionSubtitleSources] is
     * constructed BEFORE the session (the session takes it as a constructor
     * argument), so its wiring lambdas — the side-load mutation seam and the
     * session-state reads the moved sourcing bodies call back through — read
     * the session here. The lambdas run only from load chains, long after
     * [arm] binds this slot.
     */
    private lateinit var playerSessionManagerRef: PlayerSessionManager

    // ── Phase 1: collaborators (constructed once, in dependency order) ──────

    /**
     * The session's subtitle-sourcing collaborator (the load-spine sourcing
     * bodies extracted from [PlayerSessionManager], beside [SubtitleManager]):
     * the streaming-store / offline-manifest / server-stream side-load
     * builders plus the attach-new diff. Declared BEFORE the session — the
     * session takes it as a constructor argument — so its wiring lambdas read
     * the session through [playerSessionManagerRef] (cycle 7); they run only
     * from load chains, long after [arm].
     */
    private val sessionSubtitleSources = SessionSubtitleSources(
        streamingSubtitleStore = subtitleSources.streamingSubtitleStore,
        downloadRepository = offlineSources.downloadRepository,
        playbackRepository = playbackRepository,
        addExternalSubtitle = { playerSessionManagerRef.addExternalSubtitle(it) },
        getExternalSubtitles = { playerSessionManagerRef.currentExternalSubtitles },
        getCurrentItemId = { playerSessionManagerRef.sessionState.value.currentItemId },
        getCurrentPlayMethod = { playerSessionManagerRef.sessionState.value.playMethod },
        matchPlayingMediaSource = { detail ->
            playerSessionManagerRef.matchedMediaSource(detail, fallbackToFirst = true)
        },
        getEngineCapabilities = { playerSessionManagerRef.engine?.capabilities },
    )

    internal val playerSessionManager = PlayerSessionManager(
        scope = scope,
        mediaRepository = mediaRepository,
        playbackRepository = playbackRepository, imageUrlProvider = imageUrlProvider,
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

    internal val autoplayController = AutoPlayController()

    /** The platform trickplay controller handle; the load-time selection lives on [trickplayPreparation] below. */
    internal val trickplayManager = platform.createTrickplayController(playbackRepository)

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
        onMediaDetailRefreshed = { refresh -> mediaDetailProjectionRef.applyRefreshedDetail(refresh) },
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
        playbackRepository = playbackRepository, imageUrlProvider = imageUrlProvider,
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
            uiState.update { it.copy(media = update(it.media)) }
        },
        // Cycle 1's projector→projection edge: read through the late-bound
        // slot so neither initializer's type inference flows through the
        // other's construction (bound in [arm]).
        applyDetail = { detail -> mediaDetailProjectionRef.applyDetail(detail) },
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
        // collector had. The lambdas capture only stable handles (the
        // uiState handle, the session stack) and read later-declared
        // collaborators lazily — invoked only from the arm-phase collector,
        // long after those properties initialise.
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
     * long after construction. Cycle 1's projection→projector edge is the
     * DIRECT half (the projector is declared above), so no slot is needed
     * on this side.
     */
    private val mediaDetailProjection = MediaDetailProjection(
        scope = scope,
        lyricsRepository = itemContent.lyricsRepository,
        volumeProfileStore = stores.volumeProfile,
        setDetail = { detail -> mediaDetail = detail },
        setChapters = { chapters ->
            uiState.update { it.copy(chapters = chapters) }
        },
        onDetail = { detail, artworkUrl ->
            mediaContentProjector.onDetail(detail, artworkUrl)
        },
        artworkUrl = { itemId -> imageUrlProvider.getImageUrl(itemId, maxWidth = 400) },
        adoptSeasonOf = { detail -> episodeContinuation.adoptSeasonOf(detail) },
        onLyrics = { lines -> mediaContentProjector.onLyrics(lines) },
        onDetailRefreshed = { refresh -> mediaContentProjector.onDetailRefreshed(refresh) },
        getEngine = { playerSessionManager.engine },
    )

    internal val mediaSessionController = sessionStack.mediaSessionFactory.create(
        getEngine = { playerSessionManager.engine },
        getImageUrl = { itemId, maxWidth -> imageUrlProvider.getImageUrl(itemId = itemId, maxWidth = maxWidth) },
    )

    /**
     * The becoming-noisy auto-pause owner (headphone unplug → pause; the
     * focus half of the former audio-lifecycle moved into the PlaybackFocus
     * module at the video slice). Registered in [arm], released in
     * [performRelease]. [getEngine] is re-read on every broadcast so engine
     * swaps (retry/fallback) and teardown stay correct.
     */
    private val becomingNoisy = platform.createBecomingNoisy(
        getEngine = { playerSessionManager.engine },
    )

    // ── Cross-player exclusivity (the video focus slice, ADR-0004) ──────────
    // Both ride the platform aggregate seam (the VM's ctor line-ceiling
    // ratchet is why they are not VM ctor params): the module-owned
    // exclusivity authority — VIDEO claims ride the play edge (the music
    // manager's `onIsPlayingChanged` pattern), OS losses come back as surface
    // commands — and the VIDEO-family commandable surface singleton (null on
    // desktop, where the focus binding registers only the music surface and
    // the displaced-holder self-pause rides the claimState observer in
    // [arm]). The interface defaults are the vacuous NoopPlaybackFocus / null
    // pair headless harnesses get.
    private val playbackFocus: PlaybackFocus = platform.playbackFocus
    private val videoFocusSurface = platform.videoFocusSurface

    /**
     * The PiP-facing surface (the [SubtitlePreviewController] shape): the
     * transport registration behind the PiP window's remote actions and the
     * aspect/source-rect pushes. Re-armed from [arm] AND from the
     * `rearmTransports` session hook — see PipTransportController's KDoc for
     * why the re-arm must ride the load lifecycle. The dispatch lambdas route
     * through [Host] (the VM's transport funnels) or read
     * later-declared collaborators lazily (invoked long after construction).
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

    // Explicit type: the getPlaySessionId / onPositionPersisted lambdas below
    // read `playbackSession` through the late-bound cycle-2 slot; the reporter
    // is handed to the session as a constructor argument, so a direct read
    // would re-create the mutual construction the builder exists to remove.
    private val progressReporter: PlaybackProgressReporter = PlaybackProgressReporter(
        playbackRepository = playbackRepository,
        scope = scope,
        uiState = uiState,
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getPlaySessionId = {
            playerSessionManager.sessionState.value.playSessionId ?: playbackSessionRef.playSessionId
        },
        getResolvedPlayMethod = { playerSessionManager.sessionState.value.playMethod },
        getMediaEngine = { playerSessionManager.engine },
        getIncognitoModeEnabled = { cachedAggregate.videoPlayer.incognitoModeEnabled },
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
        onPositionPersisted = { positionMs -> playbackSessionRef.persistPlaybackPosition(positionMs, force = false) },
        onEnginePositionUpdate = { position, duration, _, stats ->
            this@PlayerWiring.positionMs.value = position
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
                host.initialize(itemId, null, positionTicks)
            } else {
                // Group-driven position sync, not a user seek — never clamped.
                host.seekTo(positionTicks / 10_000, userInitiated = false)
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
     * to their collaborator; the session-stack reads that would re-create a
     * construction cycle go through the late-bound slots. The order the
     * pipeline CALLS these in lives in [SessionLoadPipeline] (pinned by
     * SessionLoadPipelineTest) — this bundle owns only the bodies. The three
     * stage bodies with real logic (`fetchMediaSegments`,
     * `shouldAttemptCinemaMode`, `restoreRememberedMuted`) are NOT here —
     * they moved INTO the pipeline, whose spine owns when each stage runs.
     */
    private val loadHooks: SessionLoadHooks = SessionLoadHooks(
        reconcileSyncPlayQueue = { itemId, mediaSourceId, startPositionTicks ->
            syncPlay.reconcileQueueForItem(itemId, mediaSourceId, startPositionTicks)
        },
        // Cycle 3's cinema handoff: the sequencing is session-owned (B4); the
        // late-bound slot breaks the bundle-before-session construction edge.
        beginCinemaMode = { intros, request -> playbackSessionRef.beginCinemaMode(intros, request) },
        resolveOfflineResumeTicks = { itemId, startPositionTicks ->
            playbackSourceResolver.resolveStartPositionTicks(itemId, startPositionTicks)
        },
        onSessionPrefsApplied = { agg ->
            autoplayController.setEnabled(agg.videoPlayer.videoAutoplayNext)
            autoplayController.setStillWatchingThreshold(agg.videoPlayer.stillWatchingEpisodeThreshold)
        },
        onItemHydrated = { itemId, hydratedAgg -> host.onItemHydrated(itemId, hydratedAgg) },
        createMediaSession = { itemId, title, subtitle ->
            mediaSessionController.createForItem(itemId, title, subtitle)
        },
        applyMediaDetail = { detail -> mediaDetailProjection.applyDetail(detail) },
        initializeTrickplay = { itemId, source ->
            // Trickplay selection + dispatch live in [TrickplayPreparation];
            // a non-null result means exactly one arm initialized the
            // controller, so this is the single uiState write the former
            // three inline arms produced between them.
            trickplayPreparation.prepare(itemId, source)?.let { info ->
                uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(trickplayInfo = info)) }
            }
        },
        reportPlaybackStart = { itemId, source, playMethod ->
            playbackSessionRef.reportPlaybackStart(itemId, source, playMethod)
        },
        startPositionTracking = { progressReporter.startPositionTracking() },
        startProgressReporting = { progressReporter.startProgressReporting() },
        fetchAdjacentEpisodes = { detail -> episodeContinuationRef.refreshAdjacent(detail) },
        loadSeriesEpisodes = { detail -> episodeContinuationRef.loadSeries(detail) },
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
     * outputs are this builder ([SessionLoadOutputs]); its VM-bound bodies
     * are [loadHooks]. Since the [VideoSessionHost] deletion, the three
     * stage bodies with real logic are pipeline members (see its KDoc).
     */
    private val sessionLoadPipeline = SessionLoadPipeline(
        sessionManager = playerSessionManager,
        mediaExtrasReads = itemContent.mediaExtrasReads,
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
     * Process-death resume-position persistence behind the
     * [SessionPositionStore] seam. The VM keeps the [SavedStateHandle]
     * constructor parameter SOLELY to build this store — every read/write of
     * the resume keys goes through the session.
     */
    private val sessionPositionStore: SessionPositionStore =
        SavedStateHandlePositionStore(savedStateHandle)

    /**
     * Teardown scope handed to [playbackSession] (injected, like the VM scope):
     * the final stop-report and the pending-seek join must outlive the
     * viewModelScope on clear(), so they launch here — IO dispatcher +
     * supervisor so one failing write cannot cancel the other. The owner
     * cancels the scope in its `onCleared` AFTER release(), preserving the
     * same cancel-after-release ordering the session's `onOwnerCleared` applied
     * back when the session built the scope internally.
     */
    internal val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Playback-session deep module (Stage B): owns the session-scoped latches
     * and bookkeeping (release flag, Stop-report dedup, seek + persist
     * positions, play-session id, load/seek jobs), the initialize sequence
     * driving the hooks above and the pipeline-start ownership
     * ([PlaybackSession.initialize]), the engine reload/retry paths plus the
     * [com.raulshma.jellyplay.feature.player.video.engine.EngineEventCoordinator]
     * (construction, re-arm, decision execution), and the reporting + release
     * surface (stop-reports, seek/position persistence through
     * [sessionPositionStore], the release split and the final stop-report on
     * the release scope). Every ui-state value the moved code needs is
     * supplied here as a parameter or getter/setter lambda — the session
     * never touches the ui state; its outcomes come back as [SessionEvent]s
     * collected by the arm-phase collector.
     */
    internal val playbackSession = PlaybackSession(
        scope = scope,
        releaseScope = releaseScope,
        playerSessionManager = playerSessionManager,
        progressReporter = progressReporter,
        sessionLoadPipeline = sessionLoadPipeline,
        hooks = this,
        mediaSessionController = mediaSessionController,
        playbackStore = stores.playback,
        adaptiveBitrateManager = adaptiveBitrateManager,
        playbackRepository = playbackRepository,
        offlinePlaybackFacade = offlinePlaybackFacade,
        mediaRepository = mediaRepository,
        setCinemaIntroState = { state ->
            uiState.update { it.copy(cinemaIntroState = state) }
        },
        seedDisplayedPositionMs = { seed -> positionMs.value = seed },
        positionStore = sessionPositionStore,
        getStreamingQuality = { uiState.value.uiPrefs.streamingQuality },
        setUiPlaybackMode = { mode ->
            uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(playbackMode = mode)) }
        },
        getIncognitoModeEnabled = { cachedAggregate.videoPlayer.incognitoModeEnabled },
        setPendingStreams = { selection ->
            trackSelectionHelper.setPendingStreams(selection)
        },
        getPlaybackMode = { uiState.value.uiPrefs.playbackMode },
        directPlayFallbackNotice = { errorText ->
            // KMP seam: compose-resources' suspend resolver replaces
            // context.getString; the lambda contract went suspend with it
            // (DetailStrings precedent) — EngineEventCoordinator invokes it
            // from its error-flow collector, already a coroutine.
            getString(Res.string.player_direct_play_fallback, errorText)
        },
        passOutHours = uiState.flow.map { it.uiPrefs.passOutProtectionHours }.distinctUntilChanged(),
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
     * events). The lambdas capture only stable handles and read
     * later-declared collaborators lazily — invoked long after construction,
     * the trackSelectionHelper.persistRememberedTrack pattern.
     */
    internal val episodeContinuation = EpisodeContinuationController(
        scope = scope,
        sessionState = playerSessionManager.sessionState,
        sessionEvents = playbackSession.events,
        episodeCatalogue = itemContent.episodeCatalogue,
        getDetail = { mediaDetail },
        getSeriesId = { mediaDetail?.item?.seriesId ?: uiState.value.media.seriesId },
        updateEpisodes = { update ->
            uiState.update { it.copy(episodes = update(it.episodes)) }
        },
        initializeItem = { itemId, startPositionTicks ->
            host.initialize(itemId, null, startPositionTicks)
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
        closePlayer = { this@PlayerWiring.closePlayer.trySend(Unit) },
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
     * The incognito gate the overflow's "Mark unwatched & exit" item hides
     * behind — a rare-flip pref read at render time, not a dedicated flow.
     * The VM's `incognitoModeEnabled` getter reads through this.
     */
    // @Volatile: written by the arm-phase aggregate collector, read
    // off-Main (the session's stop/start reports read the incognito gate
    // off-Main).
    @Volatile
    internal var cachedAggregate: VideoPlayerAggregate = VideoPlayerAggregate()

    /**
     * The single resolved media-detail holder — set by
     * [MediaDetailProjection.setDetail], cleared by the per-item teardown;
     * read by the cinema gate, the episode navigation and the PiP series
     * mirror. (The VM's former `@Volatile mediaDetail` field, same
     * discipline: written from launched coroutines, read cross-coroutine.)
     */
    @Volatile
    internal var mediaDetail: MediaDetail? = null

    private val playbackPreferenceResolver = ItemPlaybackPreferenceResolver(
        repository = itemPlaybackPreferenceRepository,
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getCurrentSeriesId = { playerSessionManager.sessionState.value.mediaDetail?.item?.seriesId },
        scope = scope,
    )

    internal val trackSelectionHelper = TrackSelectionHelper(
        engineStore = stores.engine,
        subtitleStore = stores.subtitleLanguage,
        getEngine = { playerSessionManager.engine },
        getMediaStreams = { uiState.value.media.mediaStreams },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getCurrentSeriesId = { playerSessionManager.sessionState.value.mediaDetail?.item?.seriesId },
        getPlayMethod = { playerSessionManager.sessionState.value.playMethod },
        onReloadForStreamChange = { selection ->
            // Cycle 2's stream-change reload edge: the reload is session-owned,
            // reached through the late-bound playbackSession slot (the
            // former VM method-indirection, now structural).
            playbackSessionRef.reloadForStreamChange(selection)
        },
        playbackPreferenceResolver = playbackPreferenceResolver,
        persistRememberedTrack = { type, track ->
            // Cycle 5's helper→writer edge: the write-side twin is declared
            // below; the late-bound slot keeps the two initializers from
            // inferring through each other (bound in [arm]).
            playbackPreferenceWriterRef.rememberTrack(type, track)
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
    // mandatory-refresh choreography (see its KDoc). Declared after
    // trackSelectionHelper (whose persistRememberedTrack lambda reads it
    // through the cycle-5 slot) while its own wiring reads that helper back
    // directly — the cycle is broken by the slot, so no explicit-type
    // annotation is needed here anymore.
    internal val playbackPreferenceWriter = ItemPlaybackPreferenceWriter(
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
     * rows and the session item-change re-resolution. The config-dirty
     * trigger reads [engineConfigSyncRef] (cycle 6 — the sync is declared
     * below). Engine-side application stays here: the controller only
     * reports a dirty config through `onConfigDirty`.
     */
    internal val render = RenderControls(
        scope = scope,
        getGlobalMpvConfig = { cachedAggregate.engine.mpvConfig },
        saveGlobalMpvConfig = { config -> stores.engine.setMpvConfig(config) },
        loadStoredRow = { prefScope, id -> itemPlaybackPreferenceRepository.get(prefScope, id) },
        saveRenderProfile = { overrides -> playbackPreferenceWriter.setRenderProfile(overrides) },
        clearStoredRenderProfile = { playbackPreferenceWriter.clearRenderProfile() },
        onConfigDirty = { engineConfigSyncRef.markDirty() },
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
     * (A7): style edits, per-item delay writes and their debounced engine
     * re-sync, the per-item dialogue-boost persist, and — folded back from
     * SubtitleFontController (the style edit it performed WAS a
     * [SubtitleStyleController.setStyle] call; no init-order coupling, so the
     * fold is pure) — the user-font install and the direct engine re-apply of
     * the current style. Step-1 shape of the recorded design — the state
     * stays in the VM's uiState mirrors, the controller writes through the
     * narrow lambdas below (no raw uiState handle crosses; the god-count
     * ratchet is untouched). The config triggers read [engineConfigSyncRef]
     * (cycle 6).
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
        syncEngineConfig = { engineConfigSyncRef.markDirty() },
        syncEngineConfigDebounced = { engineConfigSyncRef.markDirtyDebounced() },
        fontProvider = subtitleSources.fontProvider,
        getEngine = { playerSessionManager.engine },
    )

    /**
     * Owns the uniform engine-effect setters (night mode, audio delay,
     * decoder, passthrough, normalization, channel mix, bass, virtualizer,
     * reverb) and the [com.raulshma.jellyplay.feature.player.video.state.AudioEffectsState]
     * slice they mutate. Public VM methods delegate so the 27 test references
     * + the public API stay valid. Dialogue Boost, Equalizer, and Video
     * Effects stay inline because their state lives outside this controller
     * (per-item repo / VM field / cinema gate). Cycle 6's effects→sync edge
     * goes through the late-bound slot; the sync's own getEffectsState read
     * of `effects` is the direct (backward) half.
     */
    internal val effects = VideoEffectsController(
        scope = scope,
        audioStore = stores.audio,
        audioEffectsStore = stores.audioEffects,
        playbackStore = stores.playback,
        syncConfig = { engineConfigSyncRef.markDirty() },
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
     * builder-owned slices (the aggregate cache, the session state, the
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
     * collector, long after construction. The rebuild trigger reads
     * [engineConfigSyncRef] (cycle 6).
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
        rebuildEngineConfigIfRunning = { playerSessionManager.engine?.let { engineConfigSyncRef.markDirty() } },
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
     * Declared near the END of phase 1 — after every collaborator whose
     * wiring reads it through [engineConfigSyncRef]. Because every such read
     * is deferred (a lambda or a slot), the former VM needed a
     * `configSyncReady` construction gate to survive its init-block
     * collectors firing before this existed; in the two-phase shape no
     * collector is registered until [arm], so the gate is structurally
     * unnecessary and died with the move.
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
     * [TrackPreferenceFanout.onPreferenceResolved]; this builder only
     * registers the collector. Declared after [trackSelectionHelper] and
     * [engineConfigSync] — its lambdas read them, but only run from the
     * arm-phase collector, long after construction.
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
        rebuildEngineConfig = { engineConfigSyncRef.markDirty() },
        reapplyTracksFromEngine = { trackSelectionHelper.updateTracksFromEngine() },
    )

    /**
     * The displaced-holder self-pause collector's decision half (the reader's
     * observation pattern — the rationale lives on
     * [DisplacedHolderSelfPause]): which focus-claim states warrant pausing a
     * still-playing engine. The builder only registers the collector.
     */
    private val displacedHolderSelfPause = DisplacedHolderSelfPause(
        isEnginePlaying = { playerSessionManager.engine?.isPlaying?.value == true },
        pauseEngine = { playerSessionManager.engine?.pause() },
    )

    /** The render sheet's session-scoped state (sheet + deinterlace cycle). */
    private val sessionRender: SessionRenderState
        get() = render.state

    // ── Phase 2: arm ─────────────────────────────────────────────────────────

    /**
     * Binds the six late-bound slots (each to the collaborator phase 1
     * already constructed — the ONE assignment per slot anywhere), then
     * registers every collector the ViewModel's former `init` block
     * launched, in the same order. Called EXACTLY ONCE from the ViewModel's
     * `init`, immediately after this builder is constructed.
     */
    fun arm() {
        // The cycle slots: bound once, before any collector or load can run.
        playerSessionManagerRef = playerSessionManager
        mediaDetailProjectionRef = mediaDetailProjection
        playbackSessionRef = playbackSession
        episodeContinuationRef = episodeContinuation
        playbackPreferenceWriterRef = playbackPreferenceWriter
        engineConfigSyncRef = engineConfigSync

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
            playbackSession.events.collect { event ->
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
                host.release()
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
            playbackSession.sessionState.collect { session ->
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
     * (SyncPlay, PiP). Called once from [arm] and again through the
     * session's rearm callback when a disposed coordinator is re-created —
     * the VM is Activity-scoped and survives release() across media, so the
     * mirrors must be re-armed alongside it. Decision *execution* lives in
     * [PlaybackSession] (B2); its outcomes arrive as [SessionEvent]s.
     */
    private fun startEngineEventCoordinatorOutputs() {
        engineEventOutputsJob?.cancel()
        val coordinator = playbackSession.engineEventCoordinator
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
        // Playhead display pre-seed is session-owned since B4; the write
        // itself flows through the session's seedDisplayedPositionMs seam.
        playbackSessionRef.preSeedPlayhead(startPositionTicks)
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

    // ── SessionLifecycleHooks (the initialize/release lifecycle slices) ─────

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
        uiState.update { it.copy(autoplay = it.autoplay.copy(autoplayCancelled = false)) }
        // Coordinator fallback-latch reset — a pure latch flip that ran
        // between the (session-owned) seek-latch and Stop-dedup resets in
        // the old inlined body; bundled here with the other
        // synchronous-prefix writes.
        playbackSession.engineEventCoordinator.onNewItem()
        trackSelectionHelper.setPendingStreams(selection)
    }

    override fun routeToRemotePlaySession(request: LoadRequest): Boolean =
        host.routeToRemotePlaySession(request)

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
        uiState.update { it.copy(isInitializing = false) }
    }

    override fun hydrateReclaimedItem(itemId: String, detail: MediaDetail) {
        // Old loadReclaimedEngine-hook tail: the hydration fetches, in
        // their old order — segments (the pipeline's offline-first fetch,
        // shared with the load spine) then the episode fetches.
        sessionLoadPipeline.fetchMediaSegments(playbackSession.scope, itemId)
        episodeContinuation.refreshAdjacent(detail)
        episodeContinuation.loadSeries(detail)
    }

    override fun releaseMiniPlayerState() {
        videoMiniPlayerState.release()
    }

    override fun releaseInternalsVmPart() {
        host.releaseInternalsVmPart()
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

    // ── VM-funnel helpers ────────────────────────────────────────────────────

    /**
     * The immediate engine-config rebuild trigger (the former
     * `updateConfigWithUiState`): the VM's funnel handlers (per-item video
     * effects hydration) and the wiring's collaborators (render sheet,
     * style controller, effects controller, prefs fan-out) both route their
     * dirty-config reports through here.
     */
    internal fun markEngineConfigDirty() {
        engineConfigSyncRef.markDirty()
    }

    /** The drag-settling trigger (the former `updateConfigWithUiStateDebounced`). */
    internal fun markEngineConfigDirtyDebounced() {
        engineConfigSyncRef.markDirtyDebounced()
    }

    /**
     * The FULL teardown choreography behind the VM's `release()` dispose
     * hook (the former VM `performRelease` body, moved with the collaborator
     * graph it drives): the now-playing Stopped event, the collector/engine
     * teardown ordering, the audio-lifecycle + sleep-timer release, the
     * session-owned release split with the VM's post-internals steps as the
     * callback, then the pending-seek join and the final stop-report on the
     * release scope.
     */
    internal fun performRelease() {
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
        // Abandon the focus claim + unbind the surface target, and stop the
        // becoming-noisy receiver (all idempotent; safe if never registered).
        playbackFocus.release(PlaybackSurfaceId.VIDEO)
        videoFocusSurface?.unbind()
        becomingNoisy.release()
        sleepTimer.onRelease()
        // Full teardown (B3): the session owns the tail — snapshot of the
        // stop-report inputs, the releaseInternals split (session half, then
        // the VM's releaseInternalsVmPart half, back-to-back), the VM's
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
            // was armed on (the lambda holds the wiring through `launch`).
            playerSessionManager.engine?.onUserVolumeChange = null
        }
    }
}

/**
 * Construction-time bundles for [PlayerWiring] (the [PlayerStores] pattern:
 * a flat bundle widens here and at the DI/call site, never the builder's
 * constructor). Each groups the collaborators that existed only to construct
 * ONE internally-built module cluster — the builder body shows each member
 * flowing to its single consumer under its original receiving name.
 */

/** The subtitle/track content sources (subtitle search, side-load store, cue preview, user fonts). */
internal data class PlayerSubtitleSources(
    val subtitleProviderRepository: com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository,
    val streamingSubtitleStore: com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore,
    val subtitlePreviewRepository: com.raulshma.jellyplay.feature.player.video.subtitle.SubtitlePreviewRepository,
    val fontProvider: FontProvider,
)

/** The offline/download availability trio the session stack and subtitle gate read. */
internal data class PlayerOfflineSources(
    val downloadRepository: DownloadRepository,
    val offlineRepository: OfflineRepository,
    val offlineModeManager: com.raulshma.jellyplay.core.data.offline.OfflineModeManager,
)

/** What the session stack is built from: identity, lifecycle, and the two engine/media-session factories. */
internal data class PlayerSessionStackSources(
    val playbackIdentity: com.raulshma.jellyplay.core.data.playback.PlaybackIdentity,
    val playerLifecycleManager: PlayerLifecycleManager,
    val playerEngineFactory: com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory,
    val mediaSessionFactory: VideoMediaSessionFactory,
)

/** The item-attached content reads the player's modules consume: cinema intros, episodes, companion lyrics. */
internal data class PlayerItemContentSources(
    val mediaExtrasReads: com.raulshma.jellyplay.core.data.repository.MediaExtrasReads,
    val episodeCatalogue: com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue,
    val lyricsRepository: LyricsRepository,
)

/** The ViewModel state holders the wiring lambdas write through (see [PlayerWiring]'s unpacked aliases). */
internal data class PlayerStateHandles(
    val uiState: StateFlowHandle<VideoPlayerUiState>,
    val positionMs: MutableStateFlow<Long>,
    val durationMs: MutableStateFlow<Long>,
    val videoStats: MutableStateFlow<EngineVideoStats>,
    val resumeReminder: MutableSharedFlow<Long>,
    val closePlayer: Channel<Unit>,
    val passOutEvents: Channel<String>,
)
