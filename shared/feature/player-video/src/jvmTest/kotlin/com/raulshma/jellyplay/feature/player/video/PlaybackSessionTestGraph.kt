package com.raulshma.jellyplay.feature.player.video

import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.core.data.playback.PlaybackIdentity
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.playback.SleepCountdown
import com.raulshma.jellyplay.core.data.playback.SleepCountdownClock
import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.testfixtures.FakeMediaEngine
import com.raulshma.jellyplay.core.testfixtures.FakePositionStore
import com.raulshma.jellyplay.core.ui.viewmodel.StateFlowHandle
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayController
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * The shared construction harness for the [PlaybackSession] behavioral suites
 * ([PlaybackSessionLifecycleTest] / [PlaybackSessionReportingTest]) after the
 * C6 wiring collapse made the session its own composition root: the session
 * builds its whole collaborator graph internally, so the harness supplies the
 * constructor dependencies the same way the production
 * [VideoPlayerViewModelTestHarness] does — relaxed mocks at every
 * repository/store seam with REAL StateFlows wherever a construction-site
 * lambda reads one, and the session's engine-room collaborators
 * (manager/reporter/pipeline/media-session-controller/hooks) substituted
 * through the nullable test overrides so each suite keeps its recording
 * doubles and its hook-ORDER pins.
 *
 * The ui-mirror seams the session used to receive as constructor lambdas
 * (cinema-intro state, playhead seed, playback-mode mirror, incognito gate…)
 * are session-derived over the [handles] now — the suites observe them by
 * collecting the real handle flows (equivalent recording strength), flip the
 * incognito gate through [PlaybackSession.cachedAggregate], and inject the
 * identity `directPlayFallbackNotice` where the fallback chain is exercised.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class PlaybackSessionTestGraph private constructor(
    /**
     * The session's injected scope. Scheduler-linked (the runTest scheduler)
     * for the suites that need virtual time; a plain Unconfined scope for the
     * scheduler-less reporting suite — see the two secondary constructors.
     */
    val sessionScope: CoroutineScope,
    /** The caller's recording hooks double (the session's `hooksOverride`). */
    val hooks: SessionLifecycleHooks,
    initialItem: String?,
    initialPlaySessionId: String?,
) {
    /** Scheduler-linked variant: virtual time (watchdog, seek coalescing) works. */
    constructor(
        testScope: TestScope,
        hooks: SessionLifecycleHooks,
        initialItem: String?,
        initialPlaySessionId: String?,
    ) : this(
        sessionScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScope.testScheduler)),
        hooks = hooks,
        initialItem = initialItem,
        initialPlaySessionId = initialPlaySessionId,
    )

    /** Scheduler-less variant: plain Unconfined session scope (real-time suites). */
    constructor(
        hooks: SessionLifecycleHooks,
        initialItem: String?,
        initialPlaySessionId: String?,
    ) : this(
        sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        hooks = hooks,
        initialItem = initialItem,
        initialPlaySessionId = initialPlaySessionId,
    )

    /** The injected wall clock's current reading; reset per build. */
    var nowMs: Long = 1_000_000L

    val engine = FakeMediaEngine().apply {
        durationValue = 100_000L
        advanceTo(30_000L)
    }

    val sessionStateFlow = MutableStateFlow(
        PlayerSessionState(
            currentItemId = initialItem,
            playSessionId = initialPlaySessionId,
            title = "Test Movie",
            subtitle = "2024",
        ),
    )
    val engineFlow = MutableStateFlow<MediaEngine?>(engine)

    val playerSessionManager = mockk<PlayerSessionManager>(relaxed = true).apply {
        every { sessionState } returns sessionStateFlow
        every { engineFlow } returns this@PlaybackSessionTestGraph.engineFlow
        // Qualified on purpose: inside `apply` the mock's own `engine`
        // shadows the harness field, and an unqualified read here would call
        // the mock during recording (no answer yet — MockKAnswerScope boom).
        every { engine } returns this@PlaybackSessionTestGraph.engine
    }

    val playbackRepository = mockk<com.raulshma.jellyplay.core.data.repository.PlaybackRepository>(relaxed = true)
    val offlinePlaybackFacade = mockk<com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade>(relaxed = true)
    val playbackStore = mockk<com.raulshma.jellyplay.core.datastore.playback.PlaybackStore>(relaxed = true)
    val adaptiveBitrateManager = mockk<com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager>(relaxed = true).apply {
        coEvery { resolveMaxBitrate(any()) } returns 8_000_000L
    }
    val progressReporter = mockk<PlaybackProgressReporter>(relaxed = true)
    val mediaSessionController = mockk<MediaSessionController>(relaxed = true)
    val mediaRepository = mockk<com.raulshma.jellyplay.core.data.repository.MediaRepository>(relaxed = true)
    val pipeline = mockk<SessionLoadPipeline>(relaxed = true)
    val positionStore = FakePositionStore()

    // ── The session's constructor dependencies (relaxed, arm-free) ──────────

    private val aggregateFlow = MutableStateFlow(VideoPlayerAggregate())
    private val aggregateStore = mockk<com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore>(relaxed = true).apply {
        every { aggregate } returns aggregateFlow
        every { aggregateRaw } returns aggregateFlow
    }

    private val engineStore = mockk<com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore>(relaxed = true).apply {
        every { playerEngine } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.engine.PlayerEngineSlice())
    }
    private val subtitleStore = mockk<com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore>(relaxed = true).apply {
        every { subtitle } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice())
    }
    private val securityStore = mockk<com.raulshma.jellyplay.core.datastore.security.SecurityStore>(relaxed = true)
    private val syncPlayCastStore = mockk<com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore>(relaxed = true).apply {
        every { syncPlayCast } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice())
    }
    private val audioStore = mockk<com.raulshma.jellyplay.core.datastore.audio.AudioStore>(relaxed = true).apply {
        every { audio } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.audio.AudioSlice())
    }
    private val audioEffectsStore = mockk<com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore>(relaxed = true).apply {
        every { audioEffects } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice())
    }
    private val videoPlayerStore = mockk<com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore>(relaxed = true).apply {
        every { videoPlayer } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice())
    }
    private val downloadsStore = mockk<com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore>(relaxed = true).apply {
        every { downloads } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice())
    }
    private val appearanceStore = mockk<com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore>(relaxed = true).apply {
        every { appearance } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice())
    }
    private val networkOfflineStore = mockk<com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore>(relaxed = true).apply {
        every { networkOffline } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice())
    }
    private val volumeProfileStore = mockk<com.raulshma.jellyplay.core.datastore.volume.VolumeProfileStore>(relaxed = true).apply {
        every { volumeProfile } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.volume.VolumeProfileSlice())
    }

    val stores = PlayerStores(
        aggregateStore = aggregateStore,
        engine = engineStore,
        subtitleLanguage = subtitleStore,
        playback = playbackStore,
        audio = audioStore,
        audioEffects = audioEffectsStore,
        videoPlayer = videoPlayerStore,
        security = securityStore,
        syncPlayCast = syncPlayCastStore,
        downloads = downloadsStore,
        appearance = appearanceStore,
        networkOffline = networkOfflineStore,
        volumeProfile = volumeProfileStore,
    )

    private val networkMonitor = mockk<com.raulshma.jellyplay.core.data.network.NetworkMonitor>(relaxed = true).apply {
        every { isMetered } returns MutableStateFlow(false)
    }
    private val activePlayerController = mockk<ActivePlayerController>(relaxed = true).apply {
        every { engine } returns null
        every { screenshotRequests } returns MutableSharedFlow()
    }
    private val pipController = mockk<com.raulshma.jellyplay.core.data.playback.PipController>(relaxed = true).apply {
        every { isInPipMode } returns MutableStateFlow(false)
        every { pipDismissed } returns MutableStateFlow(false)
    }
    private val videoMiniPlayerState = mockk<com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState>(relaxed = true).apply {
        every { tryReclaimMediaEngine(any()) } returns null
    }
    private val offlineModeManager = mockk<com.raulshma.jellyplay.core.data.offline.OfflineModeManager>(relaxed = true).apply {
        every { isOffline } returns false
        every { networkStatus } returns MutableStateFlow(com.raulshma.jellyplay.core.model.NetworkStatus.Online)
    }
    private val syncPlayManager = mockk<com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager>(relaxed = true).apply {
        every { isInSyncPlaySession } returns false
        every { currentGroup } returns null
        stubEmptyEvents()
    }
    private val jellyfinRemotePlayCastStrategy = mockk<JellyfinRemotePlayCastStrategy>(relaxed = true).apply {
        every { isConnected } returns MutableStateFlow(false)
    }
    private val itemPlaybackPreferenceRepository =
        mockk<com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository>(relaxed = true).apply {
            // Resolve to "no stored preference": a relaxed mock would return a
            // mocked ItemPlaybackPreference whose enum props clobber the
            // controller seeds under test.
            coEvery { get(any(), any()) } returns null
        }

    val handles = PlayerStateHandles(
        uiState = StateFlowHandle(MutableStateFlow(VideoPlayerUiState())),
        positionMs = MutableStateFlow(0L),
        durationMs = MutableStateFlow(0L),
        videoStats = MutableStateFlow(com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats()),
        resumeReminder = MutableSharedFlow(extraBufferCapacity = 1),
        closePlayer = Channel(Channel.BUFFERED),
        passOutEvents = Channel(Channel.BUFFERED),
    )

    /** The uiState the session's mirror seams write through (test-observable). */
    val uiState get() = handles.uiState

    private val host = SessionHostLambdas(
        seekTo = { _, _ -> },
        seekByStep = { _ -> },
        routedPlay = { _ -> },
        resumePlayback = {},
        applyResumeSkip = {},
        autoSkipSegment = {},
        onEndedWithNoNext = {},
        handlePlaybackEnded = {},
    )

    val session = PlaybackSession(
        scope = sessionScope,
        clock = { nowMs },
        playerSessionManagerOverride = playerSessionManager,
        progressReporterOverride = progressReporter,
        sessionLoadPipelineOverride = pipeline,
        mediaSessionControllerOverride = mediaSessionController,
        hooksOverride = hooks,
        // Identity: the fallback-chain tests capture the RAW error text.
        directPlayFallbackNotice = { it },
        platform = SessionTestPlatform,
        stores = stores,
        imageUrlProvider = mockk(relaxed = true),
        itemPlaybackPreferenceRepository = itemPlaybackPreferenceRepository,
        castManager = mockk(relaxed = true),
        jellyfinRemotePlayCastStrategy = jellyfinRemotePlayCastStrategy,
        syncPlayManager = syncPlayManager,
        adaptiveBitrateManager = adaptiveBitrateManager,
        networkMonitor = networkMonitor,
        activePlayerController = activePlayerController,
        pipController = pipController,
        videoMiniPlayerState = videoMiniPlayerState,
        sleepCountdown = SleepCountdown(SleepCountdownClock.perTick(1_000L)),
        userMessageBus = NoOpPlayerVideoMessageBus,
        savedStateHandle = SavedStateHandle(),
        userDataMutator = mockk(relaxed = true),
        playbackSourceResolver = mockk<PlaybackSourceResolver>(relaxed = true).apply {
            coEvery { resolveUsableDownload(any()) } returns null
        },
        nowPlayingReporter = mockk(relaxed = true),
        subtitleSources = PlayerSubtitleSources(
            subtitleProviderRepository = mockk(relaxed = true),
            streamingSubtitleStore = noOpStreamingSubtitleStore(),
            subtitlePreviewRepository = mockk(relaxed = true),
            fontProvider = NoOpFontProvider,
        ),
        offlineSources = PlayerOfflineSources(
            downloadRepository = mockk(relaxed = true),
            offlineRepository = mockk(relaxed = true),
            offlineModeManager = offlineModeManager,
        ),
        sessionStack = PlayerSessionStackSources(
            playbackIdentity = mockk<PlaybackIdentity>(relaxed = true),
            playerLifecycleManager = PlayerLifecycleManager(playbackStore),
            playerEngineFactory = object : com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory {
                override suspend fun create(playerType: PlayerType): MediaEngine =
                    error("no engine creation in the session suites (the pipeline is a mock)")
            },
            mediaSessionFactory = NoOpMediaSessionFactory,
        ),
        itemContent = PlayerItemContentSources(
            libraryApiClient = mockk(relaxed = true),
            episodeCatalogue = mockk(relaxed = true),
            lyricsRepository = mockk(relaxed = true),
        ),
        handles = handles,
        host = host,
        playbackStore = playbackStore,
        playbackRepository = playbackRepository,
        offlinePlaybackFacade = offlinePlaybackFacade,
        mediaRepository = mediaRepository,
        positionStoreOverride = positionStore,
    )

    /** The harness's [VideoPlayerPlatform]: every factory member returns the jvmMain no-ops. */
    private object SessionTestPlatform : VideoPlayerPlatform {
        override fun isLowRamDevice(): Boolean = false
        override fun queryFileSizeBytes(uri: String): Long = 0L
        override fun readBytes(uri: String): ByteArray = ByteArray(0)
        override val offlineMediaProbe: OfflineMediaProbe = object : OfflineMediaProbe {
            override fun extractDurationMs(path: String): Long? = null
            override fun mapContainerToMime(container: String?): String? = null
        }
        override fun createTrickplayController(playbackRepository: com.raulshma.jellyplay.core.data.repository.PlaybackRepository): TrickplayController =
            NoOpTrickplayController
        override fun createCastController(
            playbackRepository: com.raulshma.jellyplay.core.data.repository.PlaybackRepository,
            imageUrlProvider: com.raulshma.jellyplay.core.data.util.ImageUrlProvider,
            adaptiveBitrateManager: com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager,
            syncPlayCastStore: com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore,
            getEngine: () -> MediaEngine?,
            getCurrentPlaybackMode: () -> com.raulshma.jellyplay.core.model.PlaybackMode,
            getSessionState: () -> PlayerSessionState,
        ): PlayerCastController = NoOpPlayerCastController()
        override fun createBecomingNoisy(getEngine: () -> MediaEngine?, isResumeOnPlugEnabled: () -> Boolean): VideoPlayerAudio = NoOpVideoPlayerAudio
    }
}
