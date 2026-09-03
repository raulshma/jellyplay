package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.ResolvedPlayback
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.feature.player.video.engine.EngineError
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.FakeMediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Session-lifecycle behaviors of [PlaybackSession] that its sibling suites
 * do not pin ([PlaybackSessionReportingTest] owns the reporting/persist/
 * release surface; [PlaybackSessionResumeTicksTest] owns the pure resume
 * resolver). This suite covers:
 *
 *  1. `initialize`'s ordered choreography — hook sequence, latch resets,
 *     remote-routing and same-item short-circuit early-returns, the
 *     single-flight load-job cancel, the SyncPlay conditional re-attach, the
 *     disposed-coordinator re-arm, and the process-death play-session-id
 *     restore;
 *  2. the mini-player reclaim BODY ([PlaybackSession.loadReclaimedEngine]);
 *  3. the retry/reload paths ([PlaybackSession.retryWithEngine] /
 *     [PlaybackSession.retryPlayback] / [PlaybackSession.reloadForStreamChange]);
 *  4. the engine-decision fan-out executed session-side (engine errors →
 *     [SessionEvent.ShowError], the FORCE_DIRECT_PLAY fallback chain, ENDED,
 *     and the released-session guards);
 *  5. Cinema Mode sequencing ([PlaybackSession.beginCinemaMode] /
 *     [PlaybackSession.advanceCinemaIntro]);
 *  6. the coalesced seek-mirror write on a virtual clock.
 *
 * Conventions match [PlaybackSessionReportingTest]: the session's injected
 * scope is unconfined on the test scheduler (synchronous launches + a virtual
 * clock for the 500 ms seek coalescing), repositories are relaxed mocks,
 * VM-facing seams are recording fakes. The session-owned
 * [PlaybackSession.releaseScope] (a real IO scope) is cancelled in the
 * per-test teardown via [PlaybackSession.onOwnerCleared].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionLifecycleTest {

    private lateinit var session: PlaybackSession
    private lateinit var playerSessionManager: PlayerSessionManager
    private lateinit var sessionStateFlow: MutableStateFlow<PlayerSessionState>
    private lateinit var engineFlow: MutableStateFlow<MediaEngine?>
    private lateinit var engine: FakeMediaEngine
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var offlinePlaybackFacade: OfflinePlaybackFacade
    private lateinit var playbackStore: PlaybackStore
    private lateinit var adaptiveBitrateManager: AdaptiveBitrateManager
    private lateinit var progressReporter: PlaybackProgressReporter
    private lateinit var mediaSessionController: MediaSessionController
    private lateinit var pipeline: SessionLoadPipeline
    private lateinit var hooks: RecordingHooks
    private lateinit var positionStore: FakePositionStore
    private lateinit var mediaRepository: MediaRepository
    private lateinit var sessionScope: CoroutineScope

    private val uiPlaybackModes = mutableListOf<PlaybackMode>()
    private val pendingStreams = mutableListOf<MediaStreamSelection?>()
    private val cinemaStates = mutableListOf<CinemaIntroUiState?>()
    private val seededPlayheadMs = mutableListOf<Long>()
    private val events = mutableListOf<SessionEvent>()
    private val startedRequests = mutableListOf<LoadRequest>()

    private var wasInSyncPlay = false
    private var playbackMode: PlaybackMode = PlaybackMode.AUTO

    private fun TestScope.buildSession(currentItemId: String? = null, playSessionId: String? = null) {
        sessionScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        engine = FakeMediaEngine().apply {
            durationValue = 100_000L
            advanceTo(30_000L)
        }
        sessionStateFlow = MutableStateFlow(
            PlayerSessionState(
                currentItemId = currentItemId,
                playSessionId = playSessionId,
                title = "Test Movie",
                subtitle = "2024",
            ),
        )
        engineFlow = MutableStateFlow<MediaEngine?>(engine)
        playerSessionManager = mockk(relaxed = true)
        every { playerSessionManager.sessionState } returns sessionStateFlow
        every { playerSessionManager.engineFlow } returns engineFlow
        every { playerSessionManager.engine } returns engine
        playbackRepository = mockk(relaxed = true)
        offlinePlaybackFacade = mockk(relaxed = true)
        playbackStore = mockk(relaxed = true)
        adaptiveBitrateManager = mockk(relaxed = true)
        every { adaptiveBitrateManager.resolveMaxBitrate(any()) } returns 8_000_000L
        progressReporter = mockk(relaxed = true)
        mediaSessionController = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        hooks = RecordingHooks(
            routeToRemote = { false },
            reclaimEngine = { null },
            syncPlayProbe = { wasInSyncPlay },
        )
        positionStore = FakePositionStore()
        pipeline = mockk(relaxed = true)
        every { pipeline.start(any(), any()) } answers {
            startedRequests += secondArg<LoadRequest>()
            hooks.calls += "startPipeline"
            Job()
        }

        session = PlaybackSession(
            scope = sessionScope,
            playerSessionManager = playerSessionManager,
            progressReporter = progressReporter,
            sessionLoadPipeline = pipeline,
            hooks = hooks,
            mediaSessionController = mediaSessionController,
            playbackStore = playbackStore,
            adaptiveBitrateManager = adaptiveBitrateManager,
            playbackRepository = playbackRepository,
            offlinePlaybackFacade = offlinePlaybackFacade,
            mediaRepository = mediaRepository,
            setCinemaIntroState = { cinemaStates += it },
            seedDisplayedPositionMs = { seededPlayheadMs += it },
            positionStore = positionStore,
            getStreamingQuality = { StreamingQuality.AUTO },
            setUiPlaybackMode = { uiPlaybackModes += it },
            getIncognitoModeEnabled = { false },
            setPendingStreams = { pendingStreams += it },
            getPlaybackMode = { playbackMode },
            directPlayFallbackNotice = { it },
            passOutHours = flowOf(0),
            onEngineEventCoordinatorRearmed = { hooks.calls += "coordinatorRearmed" },
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            session.events.collect { events += it }
        }
        testScheduler.runCurrent()
    }

    @AfterTest
    fun tearDown() {
        if (this::session.isInitialized) session.onOwnerCleared()
        if (this::sessionScope.isInitialized) sessionScope.cancel()
    }

    private fun request(
        itemId: String = "item-9",
        startPositionTicks: Long = 0L,
        allowCinemaMode: Boolean = true,
    ) = LoadRequest(
        itemId = itemId,
        mediaSourceId = "ms-$itemId",
        startPositionTicks = startPositionTicks,
        allowCinemaMode = allowCinemaMode,
        subtitleStreamIndex = null,
        audioStreamIndex = null,
    )

    // ── 1. initialize choreography ───────────────────────────────────────────

    @Test
    fun initialize_runsHooksInOrder_andStartsThePipeline() = runTest {
        buildSession() // no current item: the outgoing stop-report is a no-op

        session.initialize(
            LoadRequest(
                itemId = "item-9",
                mediaSourceId = "ms-9",
                startPositionTicks = 30_000_000L,
                allowCinemaMode = true,
                subtitleStreamIndex = 2,
                audioStreamIndex = 1,
            ),
        )

        assertEquals(
            listOf(
                "rearmTransports",
                "resetForNewItem",
                "routeToRemotePlaySession",
                "wasInSyncPlay",
                "tryReclaimMiniPlayer",
                "releaseMiniPlayerState",
                "releaseInternalsVmPart",
                "clearTrickplay",
                "startPipeline",
            ),
            hooks.calls,
            "the VM-bound hook order is fixed (B3 split: VM teardown half after the session's)",
        )
        // The pending selection carries the request's stream indices.
        assertEquals(
            listOf(MediaStreamSelection(audioStreamIndex = 1, subtitleStreamIndex = 2)),
            hooks.resetSelections,
        )
        // The session-owned teardown half ran synchronously too.
        verify(exactly = 1) { mediaSessionController.release() }
        verify(exactly = 1) { playerSessionManager.release() }
        // The pipeline receives the ORIGINAL request object, verbatim.
        assertEquals(
            listOf(
                LoadRequest(
                    itemId = "item-9",
                    mediaSourceId = "ms-9",
                    startPositionTicks = 30_000_000L,
                    allowCinemaMode = true,
                    subtitleStreamIndex = 2,
                    audioStreamIndex = 1,
                ),
            ),
            startedRequests.toList(),
        )
        assertFalse(session.released, "initialize clears the released latch")
    }

    @Test
    fun initialize_resetsTheSeekStopAndReleaseLatches() = runTest {
        buildSession()
        session.lastSeekPositionMs = 42_000L
        session.lastSeekTimestamp = System.currentTimeMillis()
        session.stopReportedForSession = "server-old"
        session.released = true

        session.initialize(request())

        assertNull(session.lastSeekPositionMs)
        assertEquals(0L, session.lastSeekTimestamp)
        assertNull(session.stopReportedForSession)
        assertFalse(session.released)
    }

    @Test
    fun initialize_cancelsTheInFlightLoad_latestLoadWins() = runTest {
        buildSession()
        val firstLoad = CompletableDeferred<Unit>()
        val firstJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            firstLoad.await()
        }
        startedRequests.clear()
        hooks.calls.clear()
        every { pipeline.start(any(), any()) } answers {
            startedRequests += secondArg<LoadRequest>()
            firstJob
        }
        session.initialize(request(itemId = "item-1"))
        assertFalse(firstJob.isCancelled)

        // A second initialize (new item) must cancel the first load's job
        // before launching its own — no interleaved network/teardown effects.
        session.initialize(request(itemId = "item-2"))

        assertTrue(firstJob.isCancelled)
        // Both loads reached the pipeline (the first was cancelled after
        // starting); the LATEST request is the one that owns the session.
        assertEquals("item-2", startedRequests.last().itemId)
    }

    @Test
    fun initialize_remoteRoutingEarlyReturn_completesWithoutAnyLoad() = runTest {
        buildSession()
        hooks.routeToRemote = { true }

        val job = session.initialize(request())

        assertTrue(job.isCompleted, "the routing early-return yields a completed no-load job")
        assertEquals(
            listOf("rearmTransports", "resetForNewItem", "routeToRemotePlaySession"),
            hooks.calls,
            "no teardown, no reclaim, no pipeline start may run behind the routing gate",
        )
        assertTrue(startedRequests.isEmpty())
    }

    @Test
    fun initialize_sameItemLiveWithZeroStartPosition_shortCircuits() = runTest {
        buildSession(currentItemId = "item-1", playSessionId = "server-1")
        engine.playbackState.value = EnginePlaybackState.READY
        engine.advanceTo(0L)

        val job = session.initialize(request(itemId = "item-1"))

        assertTrue(job.isCompleted)
        assertTrue(startedRequests.isEmpty())
    }

    @Test
    fun initialize_sameItemWithProgress_reloads() = runTest {
        buildSession(currentItemId = "item-1", playSessionId = "server-1")
        engine.playbackState.value = EnginePlaybackState.READY
        engine.advanceTo(5_000L)

        session.initialize(request(itemId = "item-1"))

        assertEquals(listOf(request(itemId = "item-1")), startedRequests.toList())
    }

    @Test
    fun initialize_sameItemEnded_reloads() = runTest {
        buildSession(currentItemId = "item-1", playSessionId = "server-1")
        engine.playbackState.value = EnginePlaybackState.ENDED

        session.initialize(request(itemId = "item-1"))

        assertEquals(1, startedRequests.size)
    }

    @Test
    fun initialize_sameItemWithAnExplicitStartPosition_reloads() = runTest {
        buildSession(currentItemId = "item-1", playSessionId = "server-1")
        engine.playbackState.value = EnginePlaybackState.READY
        engine.advanceTo(0L)

        session.initialize(request(itemId = "item-1", startPositionTicks = 60_000_000L))

        assertEquals(1, startedRequests.size)
    }

    @Test
    fun initialize_stopReportsTheOutgoingSession_beforeTeardown() = runTest {
        buildSession(currentItemId = "item-1", playSessionId = "server-1")
        engine.advanceTo(30_000L)

        session.initialize(request(itemId = "item-2"))

        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped("item-1", "server-1", 300_000_000L)
        }
        assertEquals(1, startedRequests.size)
    }

    @Test
    fun initialize_reattachesSyncPlay_onlyWhenItWasActive() = runTest {
        buildSession()
        wasInSyncPlay = true

        session.initialize(request())

        assertTrue(
            hooks.calls.indexOf("reattachSyncPlay") in 0 until hooks.calls.indexOf("startPipeline"),
            "the SyncPlay re-attach rides the load prefix, before the pipeline starts",
        )

        // The negative branch: a non-SyncPlay entry never re-attaches.
        wasInSyncPlay = false
        session.initialize(request(itemId = "item-3"))
        assertEquals(1, hooks.calls.count { it == "reattachSyncPlay" })
    }

    @Test
    fun initialize_afterADisposedCoordinator_reArmsItAndPokesTheVm() = runTest {
        buildSession()
        session.engineEventCoordinator.dispose()
        assertTrue(session.engineEventCoordinator.disposed)

        session.initialize(request())

        assertFalse(
            session.engineEventCoordinator.disposed,
            "the Activity-scoped VM is reused across media — every load re-arms the coordinator",
        )
        assertTrue("coordinatorRearmed" in hooks.calls)
    }

    @Test
    fun initialize_restoresTheSavedPlaySessionId_forTheSameItem_only() = runTest {
        buildSession()
        positionStore.savedItemIdValue = "item-9"
        positionStore.savedPlaySessionIdValue = "srv-9"

        session.initialize(request(itemId = "item-9"))
        assertEquals("srv-9", session.playSessionId)

        // A different item must allocate a FRESH local session id.
        session.initialize(request(itemId = "item-8"))
        assertNotEquals("srv-9", session.playSessionId)
    }

    // ── 2. Mini-player reclaim body ──────────────────────────────────────────

    private fun detail(itemId: String) = MediaDetail(
        item = MediaItem(id = itemId, name = "Reclaimed", mediaType = MediaType.MOVIE),
    )

    @Test
    fun initialize_reclaimGate_bindsTheReclaimedEngine_withoutThePipeline() = runTest {
        buildSession(currentItemId = "item-1", playSessionId = "server-1")
        sessionStateFlow.value = sessionStateFlow.value.copy(title = "Mini Title", subtitle = "Sub")
        val reclaimedEngine = FakeMediaEngine()
        hooks.reclaimEngine = { reclaimedEngine }
        coEvery { mediaRepository.getMediaDetail("item-1", any()) } returns
            Result.success(detail("item-1"))

        session.initialize(request(itemId = "item-1"))

        assertEquals(
            listOf(
                "rearmTransports",
                "resetForNewItem",
                "routeToRemotePlaySession",
                "wasInSyncPlay",
                "tryReclaimMiniPlayer",
                "onMiniPlayerReclaimed",
                // Post-bind hydration of the reclaimed item (offline-mirror
                // resume state) — part of the reclaim body, still no pipeline.
                "hydrateReclaimedItem",
            ),
            hooks.calls,
            "the reclaim routing skips the full teardown and the pipeline; the veil lifts synchronously",
        )
        assertTrue(startedRequests.isEmpty())
        verify { playerSessionManager.bindReclaimedEngine(reclaimedEngine, "item-1", detail("item-1")) }
        verify(exactly = 1) { mediaSessionController.createForItem("item-1", "Mini Title", "Sub") }
        verify(exactly = 1) { progressReporter.startPositionTracking() }
        verify(exactly = 1) { progressReporter.startProgressReporting() }
        assertTrue("hydrateReclaimedItem" in hooks.calls)
    }

    @Test
    fun initialize_reclaimGate_detailFailure_skipsHydration() = runTest {
        buildSession()
        hooks.reclaimEngine = { FakeMediaEngine() }
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns
            Result.failure(RuntimeException("offline"))

        session.initialize(request(itemId = "item-1"))

        assertTrue(startedRequests.isEmpty())
        assertFalse("hydrateReclaimedItem" in hooks.calls)
        verify(exactly = 0) { playerSessionManager.bindReclaimedEngine(any(), any(), any()) }
    }

    // ── 3. Retry / reload paths ──────────────────────────────────────────────

    @Test
    fun retryWithEngine_resolvesTheBitrate_swapsTheEngine_andPersistsTheChoice() = runTest {
        buildSession(currentItemId = "item-1", playSessionId = "server-1")
        engine.advanceTo(12_000L)

        session.retryWithEngine(
            playerType = PlayerType.MPV,
            playbackSpeed = 1.5f,
            streamingQuality = StreamingQuality.AUTO,
        )

        coVerify(exactly = 1) { playbackStore.setPreferredPlayer(PlayerType.MPV) }
        coVerify(exactly = 1) {
            playerSessionManager.reloadWithEngine(PlayerType.MPV, 12_000L, 1.5f, 8_000_000)
        }
        verify(exactly = 1) { progressReporter.cancelJobs() }
        verify(exactly = 1) { mediaSessionController.release() }
        // The reload rebuild: media session + position/progress tracking.
        verify(exactly = 1) { mediaSessionController.createForItem("item-1", "Test Movie", "2024") }
        verify(exactly = 1) { progressReporter.startPositionTracking() }
        verify(exactly = 1) { progressReporter.startProgressReporting() }
    }

    @Test
    fun retryPlayback_keepsTheStoredEngineChoice() = runTest {
        buildSession(currentItemId = "item-1", playSessionId = "server-1")
        engine.advanceTo(12_000L)

        session.retryPlayback(
            playbackSpeed = 1.0f,
            streamingQuality = StreamingQuality.AUTO,
            preferredPlayerType = PlayerType.EXO_PLAYER,
        )

        coVerify(exactly = 0) { playbackStore.setPreferredPlayer(any()) }
        coVerify(exactly = 1) {
            playerSessionManager.reloadWithEngine(PlayerType.EXO_PLAYER, 12_000L, 1.0f, 8_000_000)
        }
    }

    @Test
    fun reloadForStreamChange_withoutAnEngine_isNoOp() = runTest {
        buildSession()
        // The session guards on the manager's engine handle, not the flow.
        every { playerSessionManager.engine } returns null

        session.reloadForStreamChange(MediaStreamSelection(audioStreamIndex = 0, subtitleStreamIndex = null))

        coVerify(exactly = 0) { playerSessionManager.reloadForStreamChange(any(), any()) }
        assertTrue(pendingStreams.isEmpty())
    }

    @Test
    fun reloadForStreamChange_delegatesWithTheFreshSeekPosition() = runTest {
        buildSession(currentItemId = "item-1", playSessionId = "server-1")
        session.seekPersisted(45_000L)
        val selection = MediaStreamSelection(audioStreamIndex = null, subtitleStreamIndex = 3)

        session.reloadForStreamChange(selection)

        // The pending selection is seeded BEFORE the reload so the ladder can
        // restore the pick on the new engine's first track emissions.
        assertEquals(listOf<MediaStreamSelection?>(selection), pendingStreams)
        coVerify(exactly = 1) { playerSessionManager.reloadForStreamChange(selection, 45_000L) }
    }

    // ── 4. Decision fan-out executed session-side ────────────────────────────

    @Test
    fun engineError_surfacesAsAShowErrorEvent_carryingTheRetryVerdict() = runTest {
        buildSession()

        engine.errorEmissions.tryEmit(EngineError.Network(null))
        testScheduler.runCurrent()

        val event = events.single() as SessionEvent.ShowError
        assertEquals(EngineError.Network(null).message, event.error)
        assertTrue(event.retryable)
        assertFalse(event.clearBuffering)
    }

    @Test
    fun engineError_afterRelease_isDropped() = runTest {
        buildSession()
        session.released = true

        engine.errorEmissions.tryEmit(EngineError.Render(null))
        testScheduler.runCurrent()

        assertTrue(events.isEmpty())
    }

    @Test
    fun playbackEnded_emitsTheEvent_untilRelease() = runTest {
        buildSession()

        engine.playbackState.value = EnginePlaybackState.ENDED
        testScheduler.runCurrent()
        assertEquals(listOf<SessionEvent>(SessionEvent.PlaybackEnded), events)

        // After release, the same decision class is dropped (the error path's
        // guard applies to ENDED too).
        session.released = true
        engine.playbackState.value = EnginePlaybackState.IDLE
        testScheduler.runCurrent()
        engine.playbackState.value = EnginePlaybackState.ENDED
        testScheduler.runCurrent()
        assertEquals(1, events.size)
    }

    @Test
    fun watchdogTimeout_surfacesAShowErrorThatClearsBuffering() = runTest {
        buildSession()

        engine.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(20_000L)
        testScheduler.runCurrent()

        val event = events.single() as SessionEvent.ShowError
        assertEquals(EngineError.Timeout().message, event.error)
        assertTrue(event.retryable)
        assertTrue(event.clearBuffering, "the start-up watchdog must lift the stuck buffering spinner")
    }

    @Test
    fun forcedDirectPlayError_runsTheTranscodeFallbackChain() = runTest {
        buildSession(currentItemId = "item-1", playSessionId = "server-1")
        engine.advanceTo(15_000L)
        playbackMode = PlaybackMode.FORCE_DIRECT_PLAY
        coEvery {
            playerSessionManager.reloadPlayback(any(), any(), any(), any())
        } returns resolved(PlayMethod.TRANSCODE)

        engine.errorEmissions.tryEmit(EngineError.Decoder("h264", null))
        testScheduler.runCurrent()

        // Decision 1: the one-shot fallback notice from the coordinator. The
        // session's injected notice lambda is the identity here (the VM owns
        // the localized template), so the decision carries the raw error text.
        val inform = events.single() as SessionEvent.InformUser
        assertEquals(EngineError.Decoder("h264", null).message, inform.message)

        // Decision 2 executed session-side: persist FORCE_TRANSCODE,
        // stop-report the failed session, and re-resolve at the engine position.
        assertEquals(listOf(PlaybackMode.FORCE_TRANSCODE), uiPlaybackModes)
        coVerify(exactly = 1) { playbackStore.setPlaybackMode(PlaybackMode.FORCE_TRANSCODE) }
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped("item-1", "server-1", 150_000_000L)
        }
        coVerify(exactly = 1) {
            playerSessionManager.reloadPlayback(
                PlaybackMode.FORCE_TRANSCODE,
                StreamingQuality.AUTO,
                15_000L,
                null,
            )
        }
        verify(exactly = 1) { mediaSessionController.createForItem("item-1", "Test Movie", "2024") }
    }

    private fun resolved(playMethod: PlayMethod) = ResolvedPlayback(
        mediaSourceId = "ms-1",
        streamUrl = "https://jellyfin/stream",
        playMethod = playMethod,
        playSessionId = "server-2",
        maxStreamingBitrate = null,
    )

    // ── 5. Cinema Mode sequencing ────────────────────────────────────────────

    private fun intro(id: String, name: String) = MediaItem(
        id = id,
        name = name,
        mediaType = MediaType.EPISODE,
    )

    @Test
    fun beginCinemaMode_publishesTheIntroUiState_andLoadsTheFirstIntro() = runTest {
        buildSession()
        val intros = listOf(intro("intro-1", "Previously On"), intro("intro-2", "Second"))

        session.beginCinemaMode(intros, request(itemId = "main"))

        assertEquals(
            listOf<CinemaIntroUiState?>(CinemaIntroUiState(title = "Previously On", currentIndex = 1, totalCount = 2)),
            cinemaStates,
        )
        coVerify(exactly = 1) { playerSessionManager.loadMedia("intro-1", null, 0L) }
    }

    @Test
    fun beginCinemaMode_blankIntroName_fallsBackToTheGenericLabel() = runTest {
        buildSession()

        session.beginCinemaMode(listOf(intro("intro-1", "  ")), request(itemId = "main"))

        assertEquals(
            listOf<CinemaIntroUiState?>(CinemaIntroUiState(title = "Intro", currentIndex = 1, totalCount = 1)),
            cinemaStates,
        )
    }

    @Test
    fun advanceCinemaIntro_movesToTheNextIntro() = runTest {
        buildSession()
        val intros = listOf(intro("intro-1", "One"), intro("intro-2", "Two"))
        session.beginCinemaMode(intros, request(itemId = "main"))
        cinemaStates.clear()

        session.advanceCinemaIntro()

        assertEquals(
            listOf<CinemaIntroUiState?>(CinemaIntroUiState(title = "Two", currentIndex = 2, totalCount = 2)),
            cinemaStates,
        )
        coVerify(exactly = 1) { playerSessionManager.loadMedia("intro-2", null, 0L) }
        coVerify(exactly = 0) { pipeline.start(any(), any()) }
    }

    @Test
    fun advanceCinemaIntro_exhausted_resumesTheMainFeature_withoutCinema() = runTest {
        buildSession()
        session.beginCinemaMode(listOf(intro("intro-1", "One")), request(itemId = "main"))

        session.advanceCinemaIntro()

        // The intro ui state clears BEFORE the recursive initialize so the
        // re-entrant load cannot re-enter cinema mode.
        assertEquals(
            listOf<CinemaIntroUiState?>(
                CinemaIntroUiState(title = "One", currentIndex = 1, totalCount = 1),
                null,
            ),
            cinemaStates,
        )
        // Two cancelJobs: the explicit pre-resume cancel, plus the one inside
        // the re-entrant initialize's session teardown half.
        verify(exactly = 2) { progressReporter.cancelJobs() }
        assertEquals(1, startedRequests.size)
        val main = startedRequests.single()
        assertEquals("main", main.itemId)
        assertEquals("ms-main", main.mediaSourceId)
        assertFalse(main.allowCinemaMode, "the resumed main feature must not re-enter cinema mode")
    }

    @Test
    fun advanceCinemaIntro_withoutAnActiveContext_isNoOp() = runTest {
        buildSession()

        session.advanceCinemaIntro()

        assertTrue(cinemaStates.isEmpty())
        coVerify(exactly = 0) { playerSessionManager.loadMedia(any(), any(), any()) }
    }

    // ── 6. Playhead seeding + seek coalescing ────────────────────────────────

    @Test
    fun preSeedPlayhead_seedsTheDisplayOnlyForPositiveTicks() = runTest {
        buildSession()

        session.preSeedPlayhead(0L)
        session.preSeedPlayhead(30_000_000L)

        assertEquals(listOf<Long>(3_000L), seededPlayheadMs)
    }

    @Test
    fun seekPersisted_snapshotsImmediately_andCoalescesTheOfflineMirrorWrite() = runTest {
        buildSession(currentItemId = "item-1", playSessionId = "server-1")

        // Two rapid scrub targets within the 500 ms quiet window.
        session.seekPersisted(10_000L)
        session.seekPersisted(20_000L)

        // The process-death snapshot is synchronous per seek...
        assertEquals(
            listOf(10_000L, 20_000L),
            positionStore.persists.map { it.positionMs },
        )
        // ...but the offline DB mirror is coalesced to ONE write (the last
        // target) after the quiet period.
        coVerify(exactly = 0) { offlinePlaybackFacade.recordProgress(any(), any(), any(), any()) }
        testScheduler.advanceTimeBy(600L)
        testScheduler.runCurrent()
        coVerify(exactly = 1) {
            offlinePlaybackFacade.recordProgress("item-1", 200_000_000L, 20.0, false)
        }
    }

    @Test
    fun seekPersisted_withoutACurrentItem_persistsNothing() = runTest {
        buildSession()

        session.seekPersisted(10_000L)

        assertTrue(positionStore.persists.isEmpty())
        coVerify(exactly = 0) { offlinePlaybackFacade.recordProgress(any(), any(), any(), any()) }
    }

    // ── Fakes ────────────────────────────────────────────────────────────────

    /** Records the VM-bound hook invocations the session makes. */
    private class RecordingHooks(
        var routeToRemote: (LoadRequest) -> Boolean,
        var reclaimEngine: (String) -> MediaEngine?,
        // Named differently from the override: a same-named property makes
        // `wasInSyncPlay()` inside the override resolve to the method itself
        // (infinite recursion, StackOverflowError).
        var syncPlayProbe: () -> Boolean,
    ) : SessionLifecycleHooks {
        val calls = mutableListOf<String>()
        val resetSelections = mutableListOf<MediaStreamSelection>()

        override fun rearmTransports() { calls += "rearmTransports" }

        override fun resetForNewItem(selection: MediaStreamSelection) {
            calls += "resetForNewItem"
            resetSelections += selection
        }

        override fun routeToRemotePlaySession(request: LoadRequest): Boolean {
            calls += "routeToRemotePlaySession"
            return routeToRemote(request)
        }

        override fun tryReclaimMiniPlayer(itemId: String): MediaEngine? {
            calls += "tryReclaimMiniPlayer"
            return reclaimEngine(itemId)
        }

        override fun onMiniPlayerReclaimed() { calls += "onMiniPlayerReclaimed" }

        override fun hydrateReclaimedItem(itemId: String, detail: MediaDetail) {
            calls += "hydrateReclaimedItem"
        }

        override fun releaseMiniPlayerState() { calls += "releaseMiniPlayerState" }

        override fun releaseInternalsVmPart() { calls += "releaseInternalsVmPart" }

        override fun clearTrickplay() { calls += "clearTrickplay" }

        override fun reattachSyncPlay() { calls += "reattachSyncPlay" }

        override fun wasInSyncPlay(): Boolean {
            calls += "wasInSyncPlay"
            return syncPlayProbe()
        }
    }

    private data class PersistCall(
        val itemId: String,
        val positionMs: Long,
        val playSessionId: String,
        val nowMs: Long,
    )

    /** Recording [SessionPositionStore]: captures persists, serves saved values. */
    private class FakePositionStore : SessionPositionStore {
        val persists = mutableListOf<PersistCall>()
        var savedItemIdValue: String? = null
        var savedPositionMsValue: Long? = null
        var savedPersistedAtValue: Long? = null
        var savedPlaySessionIdValue: String? = null

        override fun persist(itemId: String, positionMs: Long, playSessionId: String, nowMs: Long) {
            persists += PersistCall(itemId, positionMs, playSessionId, nowMs)
        }

        override fun savedItemId(): String? = savedItemIdValue
        override fun savedPositionMs(): Long? = savedPositionMsValue
        override fun savedPersistedAtMs(): Long? = savedPersistedAtValue
        override fun savedPlaySessionId(): String? = savedPlaySessionIdValue
    }
}
