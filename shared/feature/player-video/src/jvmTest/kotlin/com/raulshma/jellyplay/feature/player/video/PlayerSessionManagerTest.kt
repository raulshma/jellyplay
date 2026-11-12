package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.data.playback.NowPlayingReporter
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflinePlaybackPreference
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlaybackResolution
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.ResolvedPlayback
import com.raulshma.jellyplay.core.model.StreamingQuality
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * Pure-JVM port of the legacy Android test of the same name (the migration
 * dropped the Robolectric harness; the `Context`/`Uri.fromFile` mocks it
 * needed do not exist in common code — [fileUriString] builds real
 * `file://` URIs from real temp files, so no static mocking remains).
 *
 * This file carries the upstream v0.10.6 additions: the #146 offline-gate
 * suite. The constructor grows the two upstream seams — [OfflineModeManager]
 * (fail-fast gate) and [PlayerVideoMessageBus] (feedback) — mirrored here by
 * a recording fake bus so the failLoad contract ("the state write and the
 * feedback emission cannot drift apart") is pinned end to end.
 *
 * Uses [PlayerType.EXTERNAL] to short-circuit engine creation — both
 * `loadOffline` and `loadOnline` return immediately after the EXTERNAL check,
 * letting us verify dispatch decisions without instantiating a real engine.
 * That dodge is also how the #198 regression (the deleted `initializeEngine`
 * call in `loadOffline`) slipped through: the EXTERNAL paths never exercise
 * engine creation, so the #198 section wires mocked engines instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSessionManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    /** Records [PlayerVideoMessageBus.error] emissions — the test's feedback probe. */
    private class RecordingMessageBus : PlayerVideoMessageBus {
        val errors = mutableListOf<String>()
        val infos = mutableListOf<String>()
        val typedInfos = mutableListOf<PlayerVideoMessage>()

        override fun info(message: String) {
            infos += message
        }

        override fun error(message: String) {
            errors += message
        }

        override fun info(message: PlayerVideoMessage) {
            typedInfos += message
        }
        override fun error(message: PlayerVideoMessage) {
            typedInfos += message
        }
    }

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
    private lateinit var messageBus: RecordingMessageBus
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
        val pipController = mockk<PipController>(relaxed = true)
        adaptiveBitrateManager = mockk(relaxed = true)
        // Relaxed mock: isOffline defaults to false (online), matching the
        // historical no-gate behaviour the existing tests were written against.
        offlineModeManager = mockk(relaxed = true)
        // The Auto resolution reads the same flow the offline gate does —
        // default it to Online so the historical tests resolve unchanged.
        every { offlineModeManager.networkStatus } returns MutableStateFlow(NetworkStatus.Online)
        messageBus = RecordingMessageBus()

        // Default: EXTERNAL player to avoid real engine instantiation in unit tests.
        every { aggregateStore.aggregate } returns
            MutableStateFlow(VideoPlayerAggregate(playback = PlaybackSlice(preferredPlayer = PlayerType.EXTERNAL)))
        // The load path awaits the raw (hydrated) flow rather than the StateFlow's
        // cold-start initial value, so stub it with the same single emission.
        every { aggregateStore.aggregateRaw } returns flowOf(
            VideoPlayerAggregate(playback = PlaybackSlice(preferredPlayer = PlayerType.EXTERNAL)),
        )

        // Default: PlaybackInfo resolution yields nothing, so loadOnline
        // falls through the repository's static fold (the static direct
        // URL; PlayMethod.DIRECT_PLAY).
        coEvery {
            playbackRepository.resolvePlayable(any())
        } returns PlaybackResolution.StaticFallback("")

        // Issue #186 §5.7: the HIGH-default resolver keeps the profile fold
        // identity for the pre-profile pins below; the LOW-pinning §5.14
        // tests swap their own manager in (see lowTierSessionManager).
        playbackStore = mockk(relaxed = true)
        every { playbackStore.playback } returns MutableStateFlow(PlaybackSlice())

        sessionManager = PlayerSessionManager(
            scope = CoroutineScope(testDispatcher + SupervisorJob()),
            nowPlayingReporter = NowPlayingReporter(),
            detailFeed = detailFeed,
            playbackRepository = playbackRepository,
            imageUrlProvider = mockk(relaxed = true),
            playbackIdentity = mockk(relaxed = true),
            offlineRepository = offlineRepository,
            aggregateStore = aggregateStore,
            playerLifecycleManager = playerLifecycleManager,
            pipController = pipController,
            adaptiveBitrateManager = adaptiveBitrateManager,
            videoPerformanceResolver = testVideoPerformanceResolver(),
            playbackCapResolver = com.raulshma.jellyplay.core.data.playback.PlaybackCapResolver(
                adaptiveBitrateManager = adaptiveBitrateManager,
                performanceResolver = testVideoPerformanceResolver(),
                playbackStore = playbackStore,
            ),
            // EXTERNAL short-circuits before any engine is built, so the
            // factory is never invoked on these paths.
            playerEngineFactory = mockk(relaxed = true),
            playbackSourceResolver = playbackSourceResolver,
            sessionSubtitleSources = sessionSubtitleSources(),
            offlineMediaProbe = mockk(relaxed = true),
            offlineModeManager = offlineModeManager,
            userMessageBus = messageBus,
        )
    }

    // ── Offline gate ───────────────────────────────────────────

    @Test
    fun loadMedia_offlineMode_blocksOnlineResolutionInsteadOfDeadAir() = runTest(testDispatcher) {
        val itemId = "item-movie"
        every { offlineModeManager.isOffline } returns true
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns null

        sessionManager.loadMedia(PlaybackSource.Auto(itemId, null), startPositionTicks = 0L)

        val state = sessionManager.sessionState.value
        assertFalse(state.isReady)
        // The whole point: no network stage runs while offline.
        coVerify(exactly = 0) { detailFeed.getMediaDetail(any(), force = false) }
        // failLoad contract: the state write and the feedback emission carry
        // the SAME message — comparing them needs no resource literal here.
        assertEquals(1, messageBus.errors.size)
        // The reason lands in the load PHASE (declared v0.11.10 fix: it no
        // longer masquerades as the media title — the projector mirrors
        // `title` into the player chrome, and the pause-details overlay gate
        // reads the title's non-blankness).
        val failure = state.loadPhase as LoadPhase.Failed
        assertEquals(messageBus.errors.single(), failure.message)
        assertEquals("", state.title)
    }

    @Test
    fun loadMedia_offlineMode_stillPlaysLocalDownloads() = runTest(testDispatcher) {
        val tempFile = Files.createTempFile("test-video", ".mp4").toFile()
        tempFile.deleteOnExit()
        val itemId = "item-movie"
        every { offlineModeManager.isOffline } returns true
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns
            downloadItem(itemId, tempFile.absolutePath)
        coEvery { offlineRepository.getOfflineItem(itemId) } returns offlineMediaItem(itemId)

        sessionManager.loadMedia(PlaybackSource.Auto(itemId, null), startPositionTicks = 0L)

        val state = sessionManager.sessionState.value
        assertEquals("Offline", state.playMethodString)
        assertTrue(state.isReady)
        coVerify(exactly = 0) { detailFeed.getMediaDetail(any(), force = false) }
    }

    // ── Offline-source preference (prefer downloaded vs streaming) ────

    /**
     * Re-stubs the hydrated aggregate with [preference] — the read the Auto
     * resolution consults before dispatching (see
     * [PlayerSessionManager.loadMedia]).
     */
    private fun stubAggregatePreference(preference: OfflinePlaybackPreference) {
        val agg = VideoPlayerAggregate(
            playback = PlaybackSlice(
                preferredPlayer = PlayerType.EXTERNAL,
                offlinePlaybackPreference = preference,
            ),
        )
        every { aggregateStore.aggregate } returns MutableStateFlow(agg)
        every { aggregateStore.aggregateRaw } returns flowOf(agg)
    }

    @Test
    fun loadMedia_preferStreaming_onlinePlaysServerCopyDespiteUsableDownload() = runTest(testDispatcher) {
        val tempFile = Files.createTempFile("test-video", ".mp4").toFile()
        tempFile.deleteOnExit()
        val itemId = "item-movie"
        stubAggregatePreference(OfflinePlaybackPreference.PREFER_STREAMING)
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns
            downloadItem(itemId, tempFile.absolutePath)
        coEvery { detailFeed.getMediaDetail(itemId, force = false) } returns Result.success(
            MediaDetail(item = MediaItem(id = itemId, name = "Test Movie", mediaType = MediaType.MOVIE)),
        )

        sessionManager.loadMedia(PlaybackSource.Auto(itemId, null), startPositionTicks = 0L)

        // The usable download was passed over: the server copy is fetched
        // and the session never takes the "Offline" play method.
        coVerify(exactly = 1) { detailFeed.getMediaDetail(itemId, force = false) }
        assertFalse(sessionManager.sessionState.value.playMethodString == "Offline")
    }

    @Test
    fun loadMedia_preferStreaming_offlineNetworkStillPlaysDownload() = runTest(testDispatcher) {
        val tempFile = Files.createTempFile("test-video", ".mp4").toFile()
        tempFile.deleteOnExit()
        val itemId = "item-movie"
        stubAggregatePreference(OfflinePlaybackPreference.PREFER_STREAMING)
        // No reachable server — PREFER_STREAMING must not strand the load on
        // the (gated) online path; the stored file is the only thing that
        // can play.
        every { offlineModeManager.networkStatus } returns MutableStateFlow(NetworkStatus.Offline)
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns
            downloadItem(itemId, tempFile.absolutePath)
        coEvery { offlineRepository.getOfflineItem(itemId) } returns offlineMediaItem(itemId)

        sessionManager.loadMedia(PlaybackSource.Auto(itemId, null), startPositionTicks = 0L)

        val state = sessionManager.sessionState.value
        assertEquals("Offline", state.playMethodString)
        assertTrue(state.isReady)
        coVerify(exactly = 0) { detailFeed.getMediaDetail(any(), force = false) }
    }

    @Test
    fun loadOnline_detailFetchFailure_surfacesFeedbackAndIsNotReady() = runTest(testDispatcher) {
        val itemId = "item-movie"
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns null
        coEvery { detailFeed.getMediaDetail(itemId, force = false) } returns
            Result.failure(java.io.IOException("dead air"))

        sessionManager.loadMedia(PlaybackSource.Auto(itemId, null), startPositionTicks = 0L)

        val state = sessionManager.sessionState.value
        assertFalse(state.isReady)
        // The name's promise: feedback actually reached the bus, and the state
        // write and the emission carry the same message (failLoad contract) —
        // in the load phase, not `title` (see the offline-gate test above).
        assertEquals(1, messageBus.errors.size)
        assertEquals(messageBus.errors.single(), (state.loadPhase as LoadPhase.Failed).message)
        assertEquals("", state.title)
    }

    @Test
    fun loadOffline_missingFile_convergesOnFailLoad_withFeedback() = runTest(testDispatcher) {
        // PlaybackSource.Offline trusts its path by contract ("callers should
        // have already verified the download is usable") — the vanished-file
        // gate inside loadOffline is the TOCTOU belt when the file disappears
        // between that verification and the open.
        val tempFile = Files.createTempFile("test-video", ".mp4").toFile()
        tempFile.delete()

        sessionManager.loadMedia(
            PlaybackSource.Offline("item-movie", tempFile.absolutePath),
            startPositionTicks = 0L,
        )

        val state = sessionManager.sessionState.value
        // Declared v0.11.10 fix: the former inline arm wrote the reason into
        // `title` ONLY — no bus emission, so no error dialog ever explained
        // the dead air. It now converges on failLoad like every other path.
        assertFalse(state.isReady)
        assertEquals(1, messageBus.errors.size)
        assertEquals(messageBus.errors.single(), (state.loadPhase as LoadPhase.Failed).message)
        assertEquals("", state.title)
    }

    // ── #198: offline downloads must reach the engine ─────────────────

    /**
     * Builds a manager pinned to [preferredPlayer] with a mocked engine per
     * type — the #198 harness. Every engine's `load` is recorded into
     * [loadedRequests], so the tests can pin that the offline load path
     * actually issues a request (the v0.11.9 regression flipped the session
     * to Ready without ever building an engine) and that switching engines
     * over a live offline session re-loads it. Everything else mirrors
     * [clientCertSessionManager].
     */
    private fun offlineEngineSessionManager(
        preferredPlayer: PlayerType,
        loadedRequests: MutableList<com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest>,
        vararg engines: Pair<PlayerType, com.raulshma.jellyplay.feature.player.video.engine.MediaEngine>,
    ): PlayerSessionManager {
        val agg = VideoPlayerAggregate(playback = PlaybackSlice(preferredPlayer = preferredPlayer))
        every { aggregateStore.aggregate } returns MutableStateFlow(agg)
        every { aggregateStore.aggregateRaw } returns flowOf(agg)
        engines.forEach { (_, engine) ->
            every { engine.load(any()) } answers {
                loadedRequests += firstArg<com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest>()
            }
        }
        val factory = io.mockk.mockk<com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory> {
            engines.forEach { (type, engine) -> coEvery { create(type) } returns engine }
        }

        return PlayerSessionManager(
            scope = CoroutineScope(testDispatcher + SupervisorJob()),
            nowPlayingReporter = NowPlayingReporter(),
            detailFeed = detailFeed,
            playbackRepository = playbackRepository,
            imageUrlProvider = mockk(relaxed = true),
            playbackIdentity = mockk(relaxed = true),
            offlineRepository = offlineRepository,
            aggregateStore = aggregateStore,
            playerLifecycleManager = playerLifecycleManager,
            pipController = mockk(relaxed = true),
            adaptiveBitrateManager = adaptiveBitrateManager,
            videoPerformanceResolver = testVideoPerformanceResolver(),
            playbackCapResolver = com.raulshma.jellyplay.core.data.playback.PlaybackCapResolver(
                adaptiveBitrateManager = adaptiveBitrateManager,
                performanceResolver = testVideoPerformanceResolver(),
                playbackStore = playbackStore,
            ),
            playerEngineFactory = factory,
            playbackSourceResolver = playbackSourceResolver,
            sessionSubtitleSources = sessionSubtitleSources(),
            offlineMediaProbe = mockk(relaxed = true),
            offlineModeManager = offlineModeManager,
            userMessageBus = messageBus,
        )
    }

    @Test
    fun loadOffline_createsTheEngineAndHandsItTheLocalFileRequest() = runTest(testDispatcher) {
        val tempFile = Files.createTempFile("test-video", ".mp4").toFile()
        tempFile.deleteOnExit()
        val itemId = "item-movie"
        // Offline mode: deterministic (no background sidecar catch-up fetch).
        every { offlineModeManager.isOffline } returns true
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns
            downloadItem(itemId, tempFile.absolutePath)
        coEvery { offlineRepository.getOfflineItem(itemId) } returns offlineMediaItem(itemId)

        val loaded = mutableListOf<com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest>()
        val manager = offlineEngineSessionManager(
            preferredPlayer = PlayerType.MPV,
            loadedRequests = loaded,
            PlayerType.MPV to mockk(relaxed = true),
        )

        manager.loadMedia(PlaybackSource.Auto(itemId, null), startPositionTicks = 15_000_000L)

        // The #198 regression: the session flipped Ready with dead air — no
        // engine was ever built, no request ever issued. The local file must
        // reach the engine as a real load.
        assertEquals(1, loaded.size)
        val request = loaded.single()
        assertEquals(fileUriString(tempFile), request.uri)
        // ticks / 10_000, the plan's single fold.
        assertEquals(1_500L, request.startPositionMs)
        assertEquals(PlayMethod.DIRECT_PLAY, request.playMethod)

        val state = manager.sessionState.value
        assertTrue(state.isReady)
        assertTrue(state.isOffline)
        assertEquals("Offline", state.playMethodString)
        assertEquals(0, messageBus.errors.size)
    }

    @Test
    fun loadOffline_engineSwitch_replaysTheOfflineRequestOnTheNewEngine() = runTest(testDispatcher) {
        val tempFile = Files.createTempFile("test-video", ".mp4").toFile()
        tempFile.deleteOnExit()
        val itemId = "item-movie"
        every { offlineModeManager.isOffline } returns true
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns
            downloadItem(itemId, tempFile.absolutePath)
        coEvery { offlineRepository.getOfflineItem(itemId) } returns offlineMediaItem(itemId)

        val loaded = mutableListOf<com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest>()
        val manager = offlineEngineSessionManager(
            preferredPlayer = PlayerType.MPV,
            loadedRequests = loaded,
            PlayerType.MPV to mockk(relaxed = true),
            PlayerType.EXO_PLAYER to mockk(relaxed = true),
        )
        manager.loadMedia(PlaybackSource.Auto(itemId, null), startPositionTicks = 0L)
        assertEquals(1, loaded.size)
        loaded.clear()

        // The #198 reporter's recovery attempt: switching engines mid-session
        // must replay the offline request on the new engine. Before the fix
        // `lastPlaybackRequest` stayed null for offline sessions, so
        // reloadWithEngine silently returned — no switch could ever recover.
        manager.reloadWithEngine(PlayerType.EXO_PLAYER, currentPositionMs = 30_000L)

        assertEquals(1, loaded.size)
        assertEquals(fileUriString(tempFile), loaded.single().uri)
        assertEquals(30_000L, loaded.single().startPositionMs)
    }

    // ── Reload paths: the isDirectPlayForced badge contract ───────────

    /**
     * Loads an online EXTERNAL session end to end so the session state carries
     * the item/source the reload paths re-resolve against, while the EXTERNAL
     * player type keeps every engine touchpoint out of the test (a reload's
     * engine swap no-ops without a prior [PlaybackRequest]).
     */
    private suspend fun loadOnlineSession(
        itemId: String = "item-movie",
        sourceId: String = "ms-1",
    ) {
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns null
        coEvery { detailFeed.getMediaDetail(itemId, force = false) } returns Result.success(
            MediaDetail(
                item = MediaItem(id = itemId, name = "Test Movie", mediaType = MediaType.MOVIE),
                mediaSources = listOf(MediaSource(id = sourceId, name = "Test Source")),
            ),
        )
        sessionManager.loadMedia(PlaybackSource.Online(itemId, sourceId), startPositionTicks = 0L)
    }

    private fun stubResolve(streamUrl: String, playSessionId: String) {
        coEvery {
            playbackRepository.resolvePlayable(any())
        } returns PlaybackResolution.Resolved(
            ResolvedPlayback(
                mediaSourceId = "ms-1",
                streamUrl = streamUrl,
                playMethod = PlayMethod.DIRECT_PLAY,
                playSessionId = playSessionId,
                maxStreamingBitrate = null,
            ),
        )
    }

    @Test
    fun reloadPlayback_reEvaluatesTheForcedBadge_fromTheRequestedMode() = runTest(testDispatcher) {
        loadOnlineSession()
        stubResolve(streamUrl = "https://stream/forced", playSessionId = "psid-forced")

        sessionManager.reloadPlayback(PlaybackMode.FORCE_DIRECT_PLAY, StreamingQuality.AUTO, 5_000L)

        assertTrue(sessionManager.sessionState.value.isDirectPlayForced)
        assertEquals("psid-forced", sessionManager.sessionState.value.playSessionId)

        // The badge is re-evaluated on every mode/quality reload — a plain
        // AUTO reload clears it again.
        stubResolve(streamUrl = "https://stream/auto", playSessionId = "psid-auto")
        sessionManager.reloadPlayback(PlaybackMode.AUTO, StreamingQuality.AUTO, 6_000L)

        assertFalse(sessionManager.sessionState.value.isDirectPlayForced)
    }

    @Test
    fun reloadForStreamChange_preservesTheForcedBadge() = runTest(testDispatcher) {
        loadOnlineSession()
        stubResolve(streamUrl = "https://stream/forced", playSessionId = "psid-forced")

        sessionManager.reloadPlayback(PlaybackMode.FORCE_DIRECT_PLAY, StreamingQuality.AUTO, 5_000L)
        assertTrue(sessionManager.sessionState.value.isDirectPlayForced)

        // The stream-index re-POST is orthogonal to the badge: it must neither
        // clear nor re-derive it — the historical divergence, now the declared
        // markForced=false branch of the shared reload ladder.
        sessionManager.reloadForStreamChange(
            MediaStreamSelection(audioStreamIndex = 1, subtitleStreamIndex = null),
            6_000L,
        )

        assertTrue(sessionManager.sessionState.value.isDirectPlayForced)
    }

    // ── Client certificate × engine selection ────────────────

    /**
     * Builds a manager whose preferred engine and client-TLS read are the two
     * variables under test (everything else mirrors [setUp]). The factory
     * hands out [engine] for [playerType] and every `load` onto it is
     * recorded, so the tests can pin BOTH halves of the contract: the
     * VLC notice emission AND that the engines' [PlaybackRequest] carries
     * (or omits) the TLS file paths.
     */
    private fun clientCertSessionManager(
        playerType: PlayerType,
        clientTls: com.raulshma.jellyplay.core.model.PlaybackTls?,
        engine: com.raulshma.jellyplay.feature.player.video.engine.MediaEngine,
        loadedRequests: MutableList<com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest>,
    ): PlayerSessionManager {
        val agg = VideoPlayerAggregate(playback = PlaybackSlice(preferredPlayer = playerType))
        every { aggregateStore.aggregate } returns MutableStateFlow(agg)
        every { aggregateStore.aggregateRaw } returns flowOf(agg)

        every { engine.load(any()) } answers {
            loadedRequests += firstArg<com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest>()
        }
        val factory = io.mockk.mockk<com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory> {
            coEvery { create(playerType) } returns engine
        }
        val identity = mockk<com.raulshma.jellyplay.core.data.playback.PlaybackIdentity>(relaxed = true)
        every { identity.clientTls() } returns clientTls

        return PlayerSessionManager(
            scope = CoroutineScope(testDispatcher + SupervisorJob()),
            nowPlayingReporter = NowPlayingReporter(),
            detailFeed = detailFeed,
            playbackRepository = playbackRepository,
            imageUrlProvider = mockk(relaxed = true),
            playbackIdentity = identity,
            offlineRepository = offlineRepository,
            aggregateStore = aggregateStore,
            playerLifecycleManager = playerLifecycleManager,
            pipController = mockk(relaxed = true),
            adaptiveBitrateManager = adaptiveBitrateManager,
            videoPerformanceResolver = testVideoPerformanceResolver(),
            playbackCapResolver = com.raulshma.jellyplay.core.data.playback.PlaybackCapResolver(
                adaptiveBitrateManager = adaptiveBitrateManager,
                performanceResolver = testVideoPerformanceResolver(),
                playbackStore = playbackStore,
            ),
            playerEngineFactory = factory,
            playbackSourceResolver = playbackSourceResolver,
            sessionSubtitleSources = sessionSubtitleSources(),
            offlineMediaProbe = mockk(relaxed = true),
            offlineModeManager = offlineModeManager,
            userMessageBus = messageBus,
        )
    }

    /** Loads one online session against [manager] with a minimal playable detail. */
    private suspend fun loadOneOnlineSession(manager: PlayerSessionManager) {
        val itemId = "item-movie"
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns null
        coEvery { detailFeed.getMediaDetail(itemId, force = false) } returns Result.success(
            MediaDetail(
                item = MediaItem(id = itemId, name = "Test Movie", mediaType = MediaType.MOVIE),
                mediaSources = listOf(MediaSource(id = "ms-1", name = "Test Source")),
            ),
        )
        manager.loadMedia(PlaybackSource.Online(itemId, "ms-1"), startPositionTicks = 0L)
    }

    private val activeTls = com.raulshma.jellyplay.core.model.PlaybackTls(
        clientCertificatePath = "/certs/client.crt",
        clientKeyPath = "/certs/client.key",
        caPath = "/certs/server-ca.pem",
    )

    @Test
    fun libVlcLoad_withActiveClientCertificate_notifiesOnceAndDoesNotBlock() = runTest(testDispatcher) {
        val loaded = mutableListOf<com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest>()
        val manager = clientCertSessionManager(
            playerType = PlayerType.LIBVLC,
            clientTls = activeTls,
            engine = mockk(relaxed = true),
            loadedRequests = loaded,
        )

        loadOneOnlineSession(manager)

        // The documented-unsupported notice fires exactly once (the typed
        // resource-key message); the load itself proceeds (not blocked — the
        // server decides whether a certificate-less VLC connection lives).
        assertEquals(listOf(PlayerVideoMessage.VlcClientCertUnsupported), messageBus.typedInfos)
        assertEquals(1, loaded.size)
        assertEquals(activeTls, loaded.single().requestSpecific?.tls)

        // Second load of another item: still one notice (one-time per session
        // manager), still loading.
        loadOneOnlineSession(manager)
        assertEquals(1, messageBus.typedInfos.size)
        assertEquals(2, loaded.size)
    }

    @Test
    fun libVlcLoad_withoutClientCertificate_isSilent() = runTest(testDispatcher) {
        val loaded = mutableListOf<com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest>()
        val manager = clientCertSessionManager(
            playerType = PlayerType.LIBVLC,
            clientTls = null,
            engine = mockk(relaxed = true),
            loadedRequests = loaded,
        )

        loadOneOnlineSession(manager)

        assertEquals(0, messageBus.infos.size)
        assertEquals(1, loaded.size)
        assertEquals(null, loaded.single().requestSpecific?.tls)
    }

    @Test
    fun mpvLoad_withActiveClientCertificate_isSilentAndCarriesTheTlsPaths() = runTest(testDispatcher) {
        val loaded = mutableListOf<com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest>()
        val manager = clientCertSessionManager(
            playerType = PlayerType.MPV,
            clientTls = activeTls,
            engine = mockk(relaxed = true),
            loadedRequests = loaded,
        )

        loadOneOnlineSession(manager)

        // mpv CONSUMES the certificate (via the tls-* options) — no notice.
        assertEquals(0, messageBus.infos.size)
        assertEquals(1, loaded.size)
        assertEquals(activeTls, loaded.single().requestSpecific?.tls)
    }

    // ── §5.14: the LOW profile's caps reach every §5.7 path ─────────────

    /**
     * A manager pinned to the LOW tier (the §5.7/§5.8 seam under test):
     * AUTO playback mode, a HIGH preload-buffer preference (the §5.9 clamp
     * has room to bite) and a 20 Mbps network cap the LOW ceiling (8 Mbps /
     * 1080p / Medium buffer) must fold under. Everything else mirrors
     * [clientCertSessionManager].
     */
    private fun lowTierSessionManager(
        engine: com.raulshma.jellyplay.feature.player.video.engine.MediaEngine,
        loadedRequests: MutableList<com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest>,
    ): PlayerSessionManager {
        val agg = VideoPlayerAggregate(
            playback = PlaybackSlice(preferredPlayer = PlayerType.MPV, playbackMode = PlaybackMode.AUTO),
            videoPlayer = VideoPlayerAggregate().videoPlayer.copy(
                videoPreloadBufferSize = com.raulshma.jellyplay.core.model.PreloadBufferSize.HIGH,
            ),
        )
        every { aggregateStore.aggregate } returns MutableStateFlow(agg)
        every { aggregateStore.aggregateRaw } returns flowOf(agg)
        // The network/user ladder above the profile ceiling: an AUTO quality
        // (no height cap) and a 20 Mbps effective cap — both stricter-folded
        // by LOW's 1080p / 8 Mbps.
        every { adaptiveBitrateManager.resolveEffectiveMaxBitrate(any()) } returns 20_000_000L
        every { adaptiveBitrateManager.effectiveQuality(any(), any()) } returns StreamingQuality.AUTO
        every { adaptiveBitrateManager.resolveMaxBitrate(any()) } returns 20_000_000L

        every { engine.load(any()) } answers {
            loadedRequests += firstArg<com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest>()
        }
        val factory = io.mockk.mockk<com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory> {
            coEvery { create(PlayerType.MPV) } returns engine
        }
        val lowResolver = testVideoPerformanceResolver(
            tier = com.raulshma.jellyplay.core.model.DevicePerformanceTier.LOW,
        )

        return PlayerSessionManager(
            scope = CoroutineScope(testDispatcher + SupervisorJob()),
            nowPlayingReporter = NowPlayingReporter(),
            detailFeed = detailFeed,
            playbackRepository = playbackRepository,
            imageUrlProvider = mockk(relaxed = true),
            playbackIdentity = mockk(relaxed = true),
            offlineRepository = offlineRepository,
            aggregateStore = aggregateStore,
            playerLifecycleManager = playerLifecycleManager,
            pipController = mockk(relaxed = true),
            adaptiveBitrateManager = adaptiveBitrateManager,
            videoPerformanceResolver = lowResolver,
            playbackCapResolver = com.raulshma.jellyplay.core.data.playback.PlaybackCapResolver(
                adaptiveBitrateManager = adaptiveBitrateManager,
                performanceResolver = lowResolver,
                playbackStore = playbackStore,
            ),
            playerEngineFactory = factory,
            playbackSourceResolver = playbackSourceResolver,
            sessionSubtitleSources = sessionSubtitleSources(),
            offlineMediaProbe = mockk(relaxed = true),
            offlineModeManager = offlineModeManager,
            userMessageBus = messageBus,
        )
    }

    @Test
    fun `LOW profile caps the resolve request and the engine load request identically`() = runTest(testDispatcher) {
        val loaded = mutableListOf<com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest>()
        val resolveRequests = mutableListOf<com.raulshma.jellyplay.core.model.PlaybackResolveRequest>()
        coEvery { playbackRepository.resolvePlayable(any()) } coAnswers {
            resolveRequests += firstArg<com.raulshma.jellyplay.core.model.PlaybackResolveRequest>()
            PlaybackResolution.StaticFallback("https://stream/low")
        }
        val manager = lowTierSessionManager(engine = mockk(relaxed = true), loadedRequests = loaded)

        loadOneOnlineSession(manager)

        // The §5.7 resolve request: the profile ceiling min-folds the network
        // cap (20 Mbps → 8 Mbps) and the AUTO quality height (none → 1080p).
        val resolve = resolveRequests.single()
        assertEquals(8_000_000L, resolve.maxStreamingBitrateBits)
        assertEquals(1_080, resolve.maxVideoHeight)

        // §5.14's "external handoff uses the same capped values": the engine
        // load request applies the SAME capped bitrate (AUTO mode) and the
        // §5.9 buffer ceiling (user HIGH 50/120 s → the LOW profile's Medium
        // 25/50 s).
        val request = loaded.single()
        assertEquals(8_000_000, request.maxVideoBitrate)
        assertEquals(25_000, request.minBufferMs)
        assertEquals(50_000, request.maxBufferMs)
    }

    @Test
    fun `LOW profile caps the user-quality reload bitrate`() = runTest(testDispatcher) {
        val loaded = mutableListOf<com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest>()
        val resolveRequests = mutableListOf<com.raulshma.jellyplay.core.model.PlaybackResolveRequest>()
        coEvery { playbackRepository.resolvePlayable(any()) } coAnswers {
            resolveRequests += firstArg<com.raulshma.jellyplay.core.model.PlaybackResolveRequest>()
            PlaybackResolution.StaticFallback("https://stream/low")
        }
        val manager = lowTierSessionManager(engine = mockk(relaxed = true), loadedRequests = loaded)
        loadOneOnlineSession(manager)
        resolveRequests.clear()

        // The user picks 4K in the in-player sheet: the quality's own ladder
        // (no cap) under the LOW ceiling → 8 Mbps.
        manager.reloadPlayback(
            PlaybackMode.AUTO,
            com.raulshma.jellyplay.core.model.StreamingQuality.UHD_4K,
            5_000L,
        )

        assertEquals(8_000_000L, resolveRequests.single().maxStreamingBitrateBits)
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /**
     * The extracted subtitle-sourcing collaborator (the session's constructor
     * now takes it instead of the bare sourcing dependencies), wired to the
     * same test doubles. The EXTERNAL-player paths under test short-circuit
     * before any side-load, so inert seams suffice — the collaborator itself
     * is exercised through the session's public load behavior, unchanged.
     */
    private fun sessionSubtitleSources() = SessionSubtitleSources(
        streamingSubtitleStore = noOpStreamingSubtitleStore(),
        downloadRepository = downloadRepository,
        playbackRepository = playbackRepository,
        addExternalSubtitle = { },
        getExternalSubtitles = { null },
        getCurrentItemId = { null },
        getCurrentPlayMethod = { PlayMethod.DIRECT_PLAY },
        matchPlayingMediaSource = { null },
        getEngineCapabilities = { null },
    )

    private fun downloadItem(
        itemId: String,
        path: String,
        status: DownloadStatus = DownloadStatus.COMPLETED,
    ) = DownloadItem(
        id = "dl-$itemId",
        mediaItemId = itemId,
        name = "Test Movie",
        mediaType = MediaType.MOVIE,
        downloadPath = path,
        downloadUrl = "http://example.com/movie",
        totalSizeBytes = 1_000_000L,
        downloadedBytes = 1_000_000L,
        status = status,
    )

    private fun offlineMediaItem(itemId: String) = OfflineMediaItem(
        id = itemId,
        name = "Test Movie",
        mediaType = MediaType.MOVIE,
        overview = "A test movie for unit testing.",
        runTimeTicks = 3_600_000 * 10_000L,
    )
}
