package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.NowPlayingReporter
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.PlaybackResolution
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.LoadEpochEngine
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSessionManagerLoadPhaseTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var detailFeed: DetailFeed
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var playbackSourceResolver: com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
    private lateinit var aggregateStore: VideoPlayerAggregateStore
    private lateinit var playerLifecycleManager: PlayerLifecycleManager
    private lateinit var adaptiveBitrateManager: AdaptiveBitrateManager
    private lateinit var playbackStore: com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
    private lateinit var offlineModeManager: com.raulshma.jellyplay.core.data.offline.OfflineModeManager
    /** The relaxed mockk — the verification target for delegated engine calls. */
    private lateinit var mockEngine: MediaEngine
    /** What the factory hands out: the mock plus the chassis engines' epoch capability. */
    private lateinit var sessionEngine: MediaEngine
    private lateinit var enginePlaybackStateFlow: MutableStateFlow<EnginePlaybackState>
    private lateinit var engineFactory: PlayerEngineFactory
    private lateinit var sessionManager: PlayerSessionManager

    @Before
    fun setUp() {
        detailFeed = mockk(relaxed = true)
        playbackRepository = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        playbackSourceResolver = mockk(relaxed = true)
        aggregateStore = mockk(relaxed = true)
        playerLifecycleManager = mockk(relaxed = true)
        adaptiveBitrateManager = mockk(relaxed = true)
        offlineModeManager = mockk(relaxed = true)
        every { offlineModeManager.networkStatus } returns MutableStateFlow(NetworkStatus.Online)
        every { offlineModeManager.isOffline } returns false

        every { aggregateStore.aggregate } returns
            MutableStateFlow(VideoPlayerAggregate(playback = PlaybackSlice(preferredPlayer = PlayerType.EXO_PLAYER)))
        every { aggregateStore.aggregateRaw } returns flowOf(
            VideoPlayerAggregate(playback = PlaybackSlice(preferredPlayer = PlayerType.EXO_PLAYER)),
        )

        sessionManager = buildSessionManager(CoroutineScope(testDispatcher + SupervisorJob()))
    }

    /**
     * One construction site for the manager graph. Tests needing a different
     * dispatch shape (the readiness-watcher handshake pins drive a QUEUED
     * dispatcher — the Unconfined default would run the watcher at arm time
     * and mask the race) build their own manager over the shared mocks.
     */
    private fun buildSessionManager(scope: CoroutineScope): PlayerSessionManager {
        enginePlaybackStateFlow = MutableStateFlow(EnginePlaybackState.IDLE)
        mockEngine = mockk(relaxed = true) {
            every { playbackState } returns enginePlaybackStateFlow
        }
        // The real chassis engines implement [LoadEpochEngine] (bumped first
        // in load()); the session watcher's stale-baseline drop reads it, so
        // the double here must carry it too for the reuse-path pins.
        sessionEngine = EpochCapableEngine(mockEngine)
        playbackStore = mockk(relaxed = true)
        every { playbackStore.playback } returns MutableStateFlow(PlaybackSlice())

        engineFactory = mockk {
            coEvery { create(any()) } returns sessionEngine
        }

        return PlayerSessionManager(
            scope = scope,
            nowPlayingReporter = NowPlayingReporter(),
            detailFeed = detailFeed,
            playbackRepository = playbackRepository,
            imageUrlProvider = mockk(relaxed = true),
            playbackIdentity = mockk(relaxed = true),
            offlineRepository = offlineRepository,
            aggregateStore = aggregateStore,
            playerLifecycleManager = playerLifecycleManager,
            pipController = mockk<PipController>(relaxed = true),
            adaptiveBitrateManager = adaptiveBitrateManager,
            videoPerformanceResolver = testVideoPerformanceResolver(),
            playbackCapResolver = com.raulshma.jellyplay.core.data.playback.PlaybackCapResolver(
                adaptiveBitrateManager = adaptiveBitrateManager,
                performanceResolver = testVideoPerformanceResolver(),
                playbackStore = playbackStore,
            ),
            playerEngineFactory = engineFactory,
            playbackSourceResolver = playbackSourceResolver,
            sessionSubtitleSources = SessionSubtitleSources(
                streamingSubtitleStore = noOpStreamingSubtitleStore(),
                downloadRepository = downloadRepository,
                playbackRepository = playbackRepository,
                addExternalSubtitle = { },
                getExternalSubtitles = { null },
                getCurrentItemId = { null },
                getCurrentPlayMethod = { com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_PLAY },
                matchPlayingMediaSource = { null },
                getEngineCapabilities = { null },
            ),
            offlineMediaProbe = mockk(relaxed = true),
            offlineModeManager = offlineModeManager,
            userMessageBus = object : PlayerVideoMessageBus {
                override fun info(message: String) {}
                override fun error(message: String) {}
                override fun info(message: PlayerVideoMessage) {}
                override fun error(message: PlayerVideoMessage) {}
            },
        )
    }

    @Test
    fun testProgressiveLoadReachesPrimingBufferThenSettlesOnEngineReady() = runTest(testDispatcher) {
        val dummyDetail = MediaDetail(
            item = MediaItem(id = "item-123", name = "Test Movie", mediaType = MediaType.MOVIE),
            mediaSources = listOf(MediaSource(id = "src-1", name = "1080p")),
        )
        coEvery { detailFeed.getMediaDetail("item-123", force = false) } returns Result.success(dummyDetail)
        coEvery { playbackRepository.resolvePlayable(any()) } returns PlaybackResolution.StaticFallback("https://example.com/stream.m3u8")

        sessionManager.loadMedia(PlaybackSource.Auto("item-123", null), startPositionTicks = 0L)

        // With mock engine starting at IDLE, load media arms the watcher and enters PrimingBuffer
        val stateDuringPriming = sessionManager.sessionState.value
        assertTrue("Expected PrimingBuffer phase, got ${stateDuringPriming.loadPhase}", stateDuringPriming.loadPhase is LoadPhase.Loading.PrimingBuffer)
        assertFalse(stateDuringPriming.isReady)

        // Engine transitions to READY -> veil can lift, phase becomes Ready.Settled
        enginePlaybackStateFlow.value = EnginePlaybackState.READY

        val stateSettled = sessionManager.sessionState.value
        assertTrue("Expected Ready phase, got ${stateSettled.loadPhase}", stateSettled.loadPhase is LoadPhase.Ready)
        assertTrue(stateSettled.isReady)

        val settled = stateSettled.loadPhase as LoadPhase.Ready.Settled
        assertTrue("Settled should have stage latencies", settled.stageLatenciesMs.isNotEmpty())
        assertTrue(settled.stageLatenciesMs.containsKey("Preparing"))
        assertTrue(settled.stageLatenciesMs.containsKey("FetchingMetadata"))
        assertTrue(settled.stageLatenciesMs.containsKey("ResolvingStream"))
        assertTrue(settled.stageLatenciesMs.containsKey("InitializingEngine"))
        assertTrue(settled.stageLatenciesMs.containsKey("PrimingBuffer"))
    }

    @Test
    fun testAwaitLoadReadyOrFailedSuspendsUntilEngineReady() = runTest(testDispatcher) {
        val dummyDetail = MediaDetail(
            item = MediaItem(id = "item-456", name = "Test Series", mediaType = MediaType.EPISODE),
            mediaSources = listOf(MediaSource(id = "src-2", name = "720p")),
        )
        coEvery { detailFeed.getMediaDetail("item-456", force = false) } returns Result.success(dummyDetail)
        coEvery { playbackRepository.resolvePlayable(any()) } returns PlaybackResolution.StaticFallback("https://example.com/stream.mp4")

        sessionManager.loadMedia(PlaybackSource.Auto("item-456", null), startPositionTicks = 0L)

        // Coroutine awaiting ready or failed
        val awaitJob = async {
            sessionManager.awaitLoadReadyOrFailed()
        }

        assertFalse(awaitJob.isCompleted)

        // Fire engine ready
        enginePlaybackStateFlow.value = EnginePlaybackState.READY

        val resultPhase = awaitJob.await()
        assertTrue(resultPhase is LoadPhase.Ready)
        assertTrue(resultPhase.isReady)
    }

    @Test
    fun testFailureCapturesStageAndLatencies() = runTest(testDispatcher) {
        coEvery { detailFeed.getMediaDetail("item-err", force = false) } returns Result.failure(RuntimeException("Network unreachable"))

        sessionManager.loadMedia(PlaybackSource.Auto("item-err", null), startPositionTicks = 0L)

        val state = sessionManager.sessionState.value
        assertFalse(state.isReady)
        assertTrue(state.loadPhase is LoadPhase.Failed)

        val failed = state.loadPhase as LoadPhase.Failed
        assertTrue(failed.message.isNotEmpty())
        assertEquals("FetchingMetadata", failed.failedStage)
        assertTrue(failed.totalElapsedMs >= 0L)
    }

    /**
     * The settle gate's expiry is TERMINATING, never abandoning: a wait that
     * expires with the load still Loading fails the attempt (land Failed) and
     * stops the engine, instead of returning a Loading phase while the active
     * engine and readiness watcher keep running with nobody left to complete
     * the load.
     */
    @Test
    fun awaitLoadReadyOrFailed_expiryFailsTheStalledLoadAndStopsTheEngine() = runTest(testDispatcher) {
        val dummyDetail = MediaDetail(
            item = MediaItem(id = "item-stall", name = "Stalled", mediaType = MediaType.MOVIE),
            mediaSources = listOf(MediaSource(id = "src-3", name = "720p")),
        )
        coEvery { detailFeed.getMediaDetail("item-stall", force = false) } returns Result.success(dummyDetail)
        coEvery { playbackRepository.resolvePlayable(any()) } returns
            PlaybackResolution.StaticFallback("https://example.com/stall.mp4")

        sessionManager.loadMedia(PlaybackSource.Auto("item-stall", null), startPositionTicks = 0L)
        assertTrue(sessionManager.sessionState.value.loadPhase is LoadPhase.Loading.PrimingBuffer)

        val awaitJob = async { sessionManager.awaitLoadReadyOrFailed(5_000) }
        assertFalse(awaitJob.isCompleted)

        advanceTimeBy(5_000)
        val result = awaitJob.await()
        assertTrue("the expired wait fails the stalled load", result is LoadPhase.Failed)
        assertTrue(sessionManager.sessionState.value.loadPhase is LoadPhase.Failed)
        verify(exactly = 1) { mockEngine.stop() }
    }

    /**
     * The readiness-watcher handshake's reason to exist: a cached same-engine
     * load whose whole BUFFERING→READY ladder lands inside one dispatch
     * window. The intermediate emission is conflated away before the watcher
     * processes anything and the flow value ends where the reused engine's
     * baseline sat (READY) — the watcher must still settle THIS load's READY.
     * Built on a QUEUED (production-shaped) dispatcher: the Unconfined
     * default would run the watcher synchronously at arm time and mask the
     * race.
     */
    @Test
    fun cachedSameEngineLoad_conflatedIntermediate_stillSettlesOnTheNewReady() = runTest {
        val queued = StandardTestDispatcher(testScheduler)
        val manager = buildSessionManager(CoroutineScope(queued + SupervisorJob()))

        coEvery { detailFeed.getMediaDetail("item-a", force = false) } returns Result.success(
            MediaDetail(item = MediaItem(id = "item-a", name = "First", mediaType = MediaType.MOVIE)),
        )
        coEvery { playbackRepository.resolvePlayable(any()) } returns
            PlaybackResolution.StaticFallback("https://example.com/a.mp4")
        manager.loadMedia(PlaybackSource.Auto("item-a", null), startPositionTicks = 0L)
        enginePlaybackStateFlow.value = EnginePlaybackState.READY
        runCurrent()
        assertTrue("precondition: the first load settled", manager.sessionState.value.isReady)

        // The cached same-engine load: the unchanged player type reuses the
        // live engine, whose flow still rests at READY — the new load's
        // baseline.
        coEvery { detailFeed.getMediaDetail("item-b", force = false) } returns Result.success(
            MediaDetail(item = MediaItem(id = "item-b", name = "Second", mediaType = MediaType.MOVIE)),
        )
        manager.loadMedia(PlaybackSource.Auto("item-b", null), startPositionTicks = 0L)
        assertTrue(manager.sessionState.value.loadPhase is LoadPhase.Loading)

        // This load's transitions land back-to-back: the watcher never
        // observes BUFFERING; the flow's value returns to READY.
        enginePlaybackStateFlow.value = EnginePlaybackState.BUFFERING
        enginePlaybackStateFlow.value = EnginePlaybackState.READY

        // The watcher consumes the request epoch (the handshake proves it was
        // read before eng.load bumped it), then the bounded baseline grace
        // expires with no departure from the stale READY — the conflated
        // ladder — and the settlement reads THIS load's READY.
        runCurrent()
        advanceTimeBy(600)
        runCurrent()
        assertTrue(
            "the new load settles on its own READY even with the intermediate skipped",
            manager.sessionState.value.isReady,
        )
        assertEquals("item-b", manager.sessionState.value.currentItemId)
    }

    /**
     * A retry after a priming-timeout failure starts a FRESH load lifecycle:
     * the engine swap restores Loading (onEngineReady only settles a Loading
     * phase, so without the restore the replacement's READY would land on a
     * forever-Failed session), restarts the priming watchdog from scratch,
     * and the replacement engine's READY settles the load Ready again.
     */
    @Test
    fun retryAfterPrimingTimeout_restoresTheLoadingLifecycleAndSettlesOnTheReplacementReady() = runTest {
        // The manager's scope shares THIS test's scheduler so the watchdog
        // ladder runs on the same virtual timeline the test advances.
        val manager = buildSessionManager(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()))
        val dummyDetail = MediaDetail(
            item = MediaItem(id = "item-retry", name = "Retry", mediaType = MediaType.MOVIE),
            mediaSources = listOf(MediaSource(id = "src-4", name = "1080p")),
        )
        coEvery { detailFeed.getMediaDetail("item-retry", force = false) } returns Result.success(dummyDetail)
        coEvery { playbackRepository.resolvePlayable(any()) } returns
            PlaybackResolution.StaticFallback("https://example.com/retry.mp4")

        manager.loadMedia(PlaybackSource.Auto("item-retry", null), startPositionTicks = 0L)
        assertTrue(manager.sessionState.value.loadPhase is LoadPhase.Loading.PrimingBuffer)

        // The priming watchdog expires: the load lands Failed and the engine
        // stops. (The deadline lands exactly on the 20 s mark — runCurrent
        // drains the task scheduled there.)
        advanceTimeBy(20_000)
        runCurrent()
        assertTrue(manager.sessionState.value.loadPhase is LoadPhase.Failed)
        verify(exactly = 1) { mockEngine.stop() }

        // The error-dialog retry swaps the engine.
        manager.reloadWithEngine(PlayerType.EXO_PLAYER, currentPositionMs = 0L)
        assertTrue(
            "a retry after a priming timeout restores the Loading lifecycle",
            manager.sessionState.value.loadPhase is LoadPhase.Loading.PrimingBuffer,
        )

        // The replacement engine's READY settles the restored load.
        enginePlaybackStateFlow.value = EnginePlaybackState.READY
        runCurrent()
        assertTrue(manager.sessionState.value.isReady)
    }

    /**
     * The replacement flow invalidates the OUTGOING watchdog before its first
     * suspension: a PrimingBuffer deadline expiring mid-swap (here: inside
     * the replacement factory's suspension window) can neither land Failed
     * nor stop() the replacement engine. The replacement re-arms its own
     * fresh ladder and settles Ready.
     */
    @Test
    fun engineReplacement_oldDeadlineExpiringDuringTheSwap_doesNotFailTheReplacementAttempt() = runTest {
        val manager = buildSessionManager(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()))
        val dummyDetail = MediaDetail(
            item = MediaItem(id = "item-swap", name = "Swap", mediaType = MediaType.MOVIE),
            mediaSources = listOf(MediaSource(id = "src-5", name = "1080p")),
        )
        coEvery { detailFeed.getMediaDetail("item-swap", force = false) } returns Result.success(dummyDetail)
        coEvery { playbackRepository.resolvePlayable(any()) } returns
            PlaybackResolution.StaticFallback("https://example.com/swap.mp4")

        // First creation is immediate; the REPLACEMENT's create suspends
        // 10 s — spanning the outgoing load's 20 s PrimingBuffer deadline.
        var creates = 0
        coEvery { engineFactory.create(any()) } coAnswers {
            if (creates++ == 0) sessionEngine else {
                delay(10_000)
                sessionEngine
            }
        }

        manager.loadMedia(PlaybackSource.Auto("item-swap", null), startPositionTicks = 0L)
        assertTrue(manager.sessionState.value.loadPhase is LoadPhase.Loading.PrimingBuffer)

        // 15 s in, the old deadline is 5 s away. The swap begins and its
        // factory suspension carries the deadline past its expiry.
        advanceTimeBy(15_000)
        assertTrue(manager.sessionState.value.loadPhase is LoadPhase.Loading)

        val reloadJob = async { manager.reloadWithEngine(PlayerType.EXO_PLAYER, currentPositionMs = 0L) }
        advanceTimeBy(10_000)
        // The create's 10 s suspension ends exactly on this boundary.
        runCurrent()
        reloadJob.join()

        // The replacement loaded, re-armed its own ladder, and the watcher
        // parks on the fresh engine's IDLE baseline — the expired OUTGOING
        // deadline neither failed the attempt nor stopped the (shared)
        // replacement engine. Its READY now settles the load.
        enginePlaybackStateFlow.value = EnginePlaybackState.READY
        runCurrent()
        assertTrue(
            "the replacement attempt survives the old deadline",
            manager.sessionState.value.isReady,
        )
        verify(exactly = 0) { mockEngine.stop() }
    }

    /**
     * The relaxed mock engine plus the chassis engines' [LoadEpochEngine]
     * capability (bumped first in load), so the readiness watcher's
     * stale-baseline drop exercises the request-aware path.
     */
    private class EpochCapableEngine(private val relaxed: MediaEngine) :
        MediaEngine by relaxed, LoadEpochEngine {
        private val _loadEpoch = MutableStateFlow(0L)
        override val loadEpoch: StateFlow<Long> = _loadEpoch.asStateFlow()
        override fun load(request: PlaybackRequest) {
            _loadEpoch.value += 1
            relaxed.load(request)
        }
    }
}
