package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported from the legacy Android test of the same name (the migration dropped
 * it; the class under test is commonMain-pure so the file is verbatim JVM).
 * Includes upstream v0.10.6 additions: the `isReady`-gate staging and the
 * `loadMediaFailure_stopsAfterLoadMedia_withoutOutcome` pin (#146).
 *
 * The first written record of the session-load ordering
 * constraints that used to live unwritten inside `initializeInternal`'s inlined
 * ~15-stage coroutine. A fake [SessionLoadOutputs] + recording [SessionLoadHooks]
 * capture the invocation order; the collaborators ([PlayerSessionManager],
 * [LibraryApiClient], stores) are stubbed.
 *
 * Since the [VideoSessionHost] deletion, the three stage bodies with real
 * logic are pipeline members, so their behavior pins moved here too (from the
 * deleted VideoSessionHostTest): the segments fetch's offline-first
 * precedence, the cinema gate's five vetoes, and the remembered-muted
 * restore's mirror-then-engine order.
 *
 * Constraints pinned:
 *  - `loadMedia` runs BEFORE per-item hydration, and the hydration reads a
 *    re-snapshotted aggregate, not the cold-start one;
 *  - `resolveOfflineResumeTicks` runs before `loadMedia` and its result feeds
 *    `loadMedia`'s start ticks;
 *  - a cancelled in-flight load lifts the loading screen (finally) BEFORE the
 *    next load's stages run — the cancel-before-release ordering the VM
 *    performs with `loadJob?.cancel()` ahead of `releaseInternals()`;
 *  - the cinema gate early-returns after `beginCinemaMode` WITHOUT loading the
 *    main feature, and still lifts the loading screen;
 *  - the loading screen lifts on failure too (the `finally` guarantee).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionLoadPipelineTest {

    private val stages = mutableListOf<String>()

    private class RecordingOutputs(val stages: MutableList<String>) : SessionLoadOutputs {
        override fun onPrefsProjected(ui: VideoPlayerUiState.() -> VideoPlayerUiState) {
            stages += "onPrefsProjected"
        }

        override fun onInitializing(visible: Boolean) {
            stages += "onInitializing($visible)"
        }

        override fun onDurationSeeded(runtimeMs: Long) {
            stages += "onDurationSeeded($runtimeMs)"
        }

        override fun onPlayheadSeeded(startPositionTicks: Long) {
            stages += "onPlayheadSeeded($startPositionTicks)"
        }

        override fun onStreamUrlResolved(url: String) {
            stages += "onStreamUrlResolved"
        }
    }

    private fun recordingHooks(
        stages: MutableList<String>,
        offlineResumeTicks: Long = 0L,
    ) = SessionLoadHooks(
        reconcileSyncPlayQueue = { _, _, _ -> stages += "reconcileSyncPlayQueue" },
        beginCinemaMode = { _, _ -> stages += "beginCinemaMode" },
        resolveOfflineResumeTicks = { _, _ ->
            stages += "resolveOfflineResumeTicks"
            offlineResumeTicks
        },
        onSessionPrefsApplied = { stages += "onSessionPrefsApplied" },
        onItemHydrated = { _, _ -> stages += "onItemHydrated" },
        createMediaSession = { _, _, _ -> stages += "createMediaSession" },
        applyMediaDetail = { stages += "applyMediaDetail" },
        initializeTrickplay = { _, _ -> stages += "initializeTrickplay" },
        reportPlaybackStart = { _, _, _ -> stages += "reportPlaybackStart" },
        startPositionTracking = { stages += "startPositionTracking" },
        startProgressReporting = { stages += "startProgressReporting" },
        fetchAdjacentEpisodes = { stages += "fetchAdjacentEpisodes" },
        loadSeriesEpisodes = { stages += "loadSeriesEpisodes" },
        onOutcome = { outcome -> stages += "onOutcome($outcome)" },
    )

    /** Item detail with a 20-minute runtime so the duration-seed stage fires. */
    private fun detail(runtimeTicks: Long? = 1_200_000L * 10_000L) = MediaDetail(
        item = MediaItem(
            id = "item-1",
            name = "Test Movie",
            mediaType = MediaType.MOVIE,
            runTimeTicks = runtimeTicks,
        ),
    )

    private fun pipeline(
        stages: MutableList<String>,
        hooks: SessionLoadHooks,
        // isReady = true models a loadMedia that produced a playable session;
        // the failure early-return is pinned by loadMediaFailure_stopsAfterLoadMedia.
        sessionState: PlayerSessionState = PlayerSessionState(
            currentItemId = "item-1",
            mediaDetail = detail(),
            title = "Test Movie",
            playMethod = PlayMethod.DIRECT_PLAY,
            streamUrl = "https://jellyfin/stream",
            isReady = true,
        ),
        intros: List<MediaItem> = emptyList(),
        aggregate: VideoPlayerAggregate = VideoPlayerAggregate(),
        inSyncPlay: Boolean = false,
        detailHolder: MediaDetail? = null,
        loadMediaBlock: CompletableDeferred<Unit>? = null,
    ): Fixture {
        val sessionManager = mockk<PlayerSessionManager>(relaxed = true)
        every { sessionManager.sessionState } returns MutableStateFlow(sessionState)
        if (loadMediaBlock != null) {
            coEvery { sessionManager.loadMedia(any(), any(), any()) } coAnswers {
                stages += "loadMedia"
                loadMediaBlock.await()
            }
        } else {
            coEvery { sessionManager.loadMedia(any(), any(), any()) } coAnswers {
                stages += "loadMedia"
            }
        }

        val libraryApiClient = mockk<LibraryApiClient>(relaxed = true)
        coEvery { libraryApiClient.getIntros(any()) } returns Result.success(intros)

        val aggregateStore = mockk<VideoPlayerAggregateStore>(relaxed = true)
        every { aggregateStore.aggregate } returns MutableStateFlow(aggregate)
        every { aggregateStore.aggregateRaw } returns flowOf(aggregate)

        val networkOfflineStore = mockk<NetworkOfflineStore>(relaxed = true)
        every { networkOfflineStore.networkOffline } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice()
        )

        val offlinePlaybackFacade = mockk<OfflinePlaybackFacade>(relaxed = true)
        coEvery { offlinePlaybackFacade.loadSegments(any()) } returns null

        val syncPlayManager = mockk<SyncPlayManager>(relaxed = true)
        every { syncPlayManager.isInSyncPlaySession } returns inSyncPlay

        val playbackRepository = mockk<PlaybackRepository>(relaxed = true)
        coEvery { playbackRepository.getMediaSegments(any()) } returns Result.success(emptyList())

        val pipeline = SessionLoadPipeline(
            sessionManager = sessionManager,
            libraryApiClient = libraryApiClient,
            aggregateStore = aggregateStore,
            networkOfflineStore = networkOfflineStore,
            offlinePlaybackFacade = offlinePlaybackFacade,
            syncPlayManager = syncPlayManager,
            getMediaDetail = { detailHolder },
            playbackRepository = playbackRepository,
            setMutedMirror = { muted ->
                stages += "mutedMirror($muted)"
            },
            onSegmentsFetched = { segments ->
                stages += "onSegmentsFetched(${segments.size})"
            },
            outputs = RecordingOutputs(stages),
            hooks = hooks,
        )
        return Fixture(pipeline, offlinePlaybackFacade, playbackRepository, sessionManager)
    }

    /** The pipeline plus the mocks the member-behavior pins arrange per test. */
    private class Fixture(
        val pipeline: SessionLoadPipeline,
        val offlinePlaybackFacade: OfflinePlaybackFacade,
        val playbackRepository: PlaybackRepository,
        val sessionManager: PlayerSessionManager,
    ) {
        operator fun component1() = pipeline
        operator fun component2() = offlinePlaybackFacade
        operator fun component3() = playbackRepository

        /** Delegates to the pipeline's start so the existing call sites keep reading `pipeline.start(...)`. */
        fun start(scope: CoroutineScope, request: LoadRequest) = pipeline.start(scope, request)
    }

    private fun request() = LoadRequest(
        itemId = "item-1",
        mediaSourceId = null,
        startPositionTicks = 0L,
        allowCinemaMode = true,
        subtitleStreamIndex = null,
        audioStreamIndex = null,
    )

    @Test
    fun happyPath_runsStagesInOrder() = runTest {
        val pipeline = pipeline(
            stages = stages,
            hooks = recordingHooks(stages, offlineResumeTicks = 5L * 60L * 10_000L),
        )

        pipeline.start(this, request()).join()
        runCurrent()

        // The segments fetch launches fire-and-forget on the caller's scope
        // (it must survive a load-job cancellation); the spine's
        // `coroutineScope` yield at the episode-fetch stage lets it land
        // exactly where the former hook fired — between the tracking starts
        // and the episode fetches.
        assertEquals(
            listOf(
                "reconcileSyncPlayQueue",
                "onPrefsProjected",
                "onSessionPrefsApplied",
                "resolveOfflineResumeTicks",
                "onPlayheadSeeded(3000000)",
                "loadMedia",
                "onItemHydrated",
                "onStreamUrlResolved",
                "createMediaSession",
                "onDurationSeeded(1200000)",
                "applyMediaDetail",
                "onInitializing(false)",
                "initializeTrickplay",
                "reportPlaybackStart",
                "startPositionTracking",
                "startProgressReporting",
                "onSegmentsFetched(0)",
                "fetchAdjacentEpisodes",
                "loadSeriesEpisodes",
                "onOutcome(Completed)",
                // The finally-side lift is a no-op second lift on the happy path.
                "onInitializing(false)",
            ),
            stages,
        )
    }

    /**
     * The playhead must be seeded from the RESOLVED start ticks (offline
     * mirror included) before the engine loads and before the loading screen
     * lifts — otherwise zero-tick entries paint the seek bar at 0 and jump to
     * the resume position on the engine's first tick.
     */
    @Test
    fun playheadSeed_usesResolvedTicks_beforeLoadMediaAndLift() = runTest {
        val resolvedTicks = 7L * 60L * 10_000L
        val pipeline = pipeline(
            stages = stages,
            hooks = recordingHooks(stages, offlineResumeTicks = resolvedTicks),
        )

        pipeline.start(this, request()).join()
        runCurrent()

        assertEquals("onPlayheadSeeded(4200000)", stages.single { it.startsWith("onPlayheadSeeded") })
        assertTrue(stages.indexOf("resolveOfflineResumeTicks") < stages.indexOf("onPlayheadSeeded(4200000)"))
        assertTrue(stages.indexOf("onPlayheadSeeded(4200000)") < stages.indexOf("loadMedia"))
        assertTrue(stages.indexOf("onPlayheadSeeded(4200000)") < stages.indexOf("onInitializing(false)"))
    }

    @Test
    fun loadMedia_runsBeforeHydration_andReceivesResolvedStartTicks() = runTest {
        val resolvedTicks = 5L * 60L * 10_000L
        val pipeline = pipeline(
            stages = stages,
            hooks = recordingHooks(stages, offlineResumeTicks = resolvedTicks),
        )

        pipeline.start(this, request()).join()
        runCurrent()

        assertTrue(stages.indexOf("loadMedia") < stages.indexOf("onItemHydrated"))
        assertTrue(stages.indexOf("resolveOfflineResumeTicks") < stages.indexOf("loadMedia"))
    }

    /**
     * The cancel-before-release ordering the VM performs with
     * `loadJob?.cancel()` ahead of `releaseInternals()`: a cancelled in-flight
     * load must run its `finally` (loading-screen lift) to completion before
     * the replacement load's stages begin.
     */
    @Test
    fun cancelledLoad_liftsLoadingScreen_beforeReplacementLoadRuns() = runTest {
        val blocked = CompletableDeferred<Unit>()
        val pipeline = pipeline(
            stages = stages,
            hooks = recordingHooks(stages),
            loadMediaBlock = blocked,
        )

        val first = pipeline.start(this, request())
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("loadMedia", stages.last())

        // The VM's initializeInternal cancels the in-flight load before
        // releasing internals and starting the replacement.
        first.cancel()
        runCurrent()
        val liftAfterCancel = stages.indexOf("onInitializing(false)")
        assertTrue("cancelled load must lift the loading screen in finally", liftAfterCancel >= 0)

        blocked.complete(Unit)
        pipeline.start(this, request()).join()
        runCurrent()

        // The cancelled load's lift precedes every stage of the second load.
        val secondLoadMedia = stages.indexOfLast { it == "loadMedia" }
        assertTrue(secondLoadMedia > liftAfterCancel)
        // Only one completed spine — the cancelled one never reached hydration.
        assertEquals(1, stages.count { it == "onItemHydrated" })
    }

    @Test
    fun cinemaGate_earlyReturn_beginsIntroWithoutLoadingMainFeature() = runTest {
        val intro = MediaItem(id = "intro-1", name = "Intro", mediaType = MediaType.MOVIE)
        val pipeline = pipeline(
            stages = stages,
            hooks = recordingHooks(stages),
            intros = listOf(intro),
            aggregate = VideoPlayerAggregate(videoPlayer = VideoPlayerSlice(cinemaModeEnabled = true)),
        )

        pipeline.start(this, request()).join()
        runCurrent()

        assertTrue("beginCinemaMode must run", "beginCinemaMode" in stages)
        assertFalse("cinema early return must NOT loadMedia the main item", "loadMedia" in stages)
        assertFalse("resolveOfflineResumeTicks must not run past the gate", "resolveOfflineResumeTicks" in stages)
        assertEquals("onOutcome(CinemaIntro(introItemId=intro-1))", stages.last { it.startsWith("onOutcome") })
        // finally guarantee: the loading screen lifts even on the early return.
        assertTrue(stages.contains("onInitializing(false)"))
    }

    // ── shouldAttemptCinemaMode: the five vetoes (ported from the deleted
    //    VideoSessionHostTest when the gate became a pipeline member) ────────

    @Test
    fun cinemaGate_vetoesNonFreshStartsSyncPlayExternalAndPrefOff() = runTest {
        val armed = VideoPlayerAggregate(videoPlayer = VideoPlayerSlice(cinemaModeEnabled = true))
        val intro = MediaItem(id = "intro-1", name = "Intro", mediaType = MediaType.MOVIE)
        val hooks = recordingHooks(stages)

        // A resume position vetoes the pre-roll.
        pipeline(stages = stages, hooks = hooks, intros = listOf(intro), aggregate = armed).start(
            this, request().copy(startPositionTicks = 1L),
        ).join()
        runCurrent()
        assertFalse("a resume position vetoes the pre-roll", "beginCinemaMode" in stages)

        // SyncPlay group pacing vetoes the pre-roll.
        stages.clear()
        pipeline(stages = stages, hooks = hooks, intros = listOf(intro), aggregate = armed, inSyncPlay = true)
            .start(this, request()).join()
        runCurrent()
        assertFalse("SyncPlay group pacing vetoes the pre-roll", "beginCinemaMode" in stages)

        // The pref off vetoes the pre-roll.
        stages.clear()
        pipeline(
            stages = stages, hooks = hooks, intros = listOf(intro),
            aggregate = VideoPlayerAggregate(videoPlayer = VideoPlayerSlice(cinemaModeEnabled = false)),
        ).start(this, request()).join()
        runCurrent()
        assertFalse("the pref off vetoes the pre-roll", "beginCinemaMode" in stages)

        // An external player vetoes the pre-roll.
        stages.clear()
        pipeline(
            stages = stages, hooks = hooks, intros = listOf(intro),
            aggregate = armed.copy(playback = armed.playback.copy(preferredPlayer = PlayerType.EXTERNAL)),
        ).start(this, request()).join()
        runCurrent()
        assertFalse("an external player vetoes the pre-roll", "beginCinemaMode" in stages)
    }

    // ── fetchMediaSegments: offline-first precedence (ported from the deleted
    //    VideoSessionHostTest when the fetch became a pipeline member) ───────

    @Test
    fun fetchMediaSegments_prefersTheOfflineBundle_beforeAnyServerRoundTrip() = runTest {
        val (pipeline, offline, server) = pipeline(stages = stages, hooks = recordingHooks(stages))
        val local = listOf(mockk<MediaSegment>(), mockk<MediaSegment>())
        coEvery { offline.loadSegments("item-1") } returns local

        pipeline.fetchMediaSegments(this, "item-1")
        runCurrent()

        // Under the test scheduler the fetch ran: the local bundle won, the
        // server was never asked.
        assertEquals(listOf("onSegmentsFetched(2)"), stages.filter { it.startsWith("onSegmentsFetched") })
        coVerify(exactly = 0) { server.getMediaSegments(any()) }
    }

    @Test
    fun fetchMediaSegments_fallsBackToTheServer_whenNoBundleShips() = runTest {
        val (pipeline, offline, server) = pipeline(stages = stages, hooks = recordingHooks(stages))
        val segments = listOf(mockk<MediaSegment>())
        coEvery { offline.loadSegments("item-1") } returns null
        coEvery { server.getMediaSegments("item-1") } returns Result.success(segments)

        pipeline.fetchMediaSegments(this, "item-1")
        runCurrent()

        assertEquals(listOf("onSegmentsFetched(1)"), stages.filter { it.startsWith("onSegmentsFetched") })
    }

    // ── restoreRememberedMuted: the mirror-then-engine order (ported from the
    //    deleted VideoSessionHostTest when the restore became a pipeline
    //    member) ──────────────────────────────────────────────────────────────

    @Test
    fun restoreRememberedMuted_whenArmed_mirrorsThenMutesTheEngine_inSpinePosition() = runTest {
        val armed = VideoPlayerAggregate(
            videoPlayer = VideoPlayerSlice(videoRememberMuted = true, videoMuted = true),
        )
        val fixture = pipeline(stages = stages, hooks = recordingHooks(stages), aggregate = armed)
        val engine = mockk<MediaEngine>(relaxed = true)
        every { fixture.sessionManager.engine } returns engine

        fixture.pipeline.start(this, request()).join()
        runCurrent()

        // EXACT order: the uiState mirror write first, the engine command
        // second, both at stage 3 — after session-pref application, before the
        // spine completes.
        val mirrorIndex = stages.indexOf("mutedMirror(true)")
        assertTrue(mirrorIndex > stages.indexOf("onSessionPrefsApplied"))
        assertTrue(mirrorIndex < stages.indexOf("onOutcome(Completed)"))
        verify(exactly = 1) { engine.setMuted(true) }
    }

    @Test
    fun restoreRememberedMuted_isGatedOnRememberMutedAndMuted() = runTest {
        val pipeline = pipeline(
            stages = stages,
            hooks = recordingHooks(stages),
            aggregate = VideoPlayerAggregate(videoPlayer = VideoPlayerSlice(videoRememberMuted = true)),
        )

        pipeline.start(this, request()).join()
        runCurrent()

        assertTrue(
            "the gate holds: remember-muted without muted restores nothing",
            stages.none { it.startsWith("mutedMirror") },
        )
    }

    @Test
    fun loadFailure_stillLiftsLoadingScreen() = runTest {
        val sessionManager = mockk<PlayerSessionManager>(relaxed = true)
        every { sessionManager.sessionState } returns MutableStateFlow(PlayerSessionState())
        coEvery { sessionManager.loadMedia(any(), any(), any()) } throws
            RuntimeException("playback info failed")

        val libraryApiClient = mockk<LibraryApiClient>(relaxed = true)
        coEvery { libraryApiClient.getIntros(any()) } returns Result.success(emptyList())
        val aggregateStore = mockk<VideoPlayerAggregateStore>(relaxed = true)
        every { aggregateStore.aggregate } returns MutableStateFlow(VideoPlayerAggregate())
        every { aggregateStore.aggregateRaw } returns flowOf(VideoPlayerAggregate())
        val networkOfflineStore = mockk<NetworkOfflineStore>(relaxed = true)
        every { networkOfflineStore.networkOffline } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice()
        )

        val pipeline = SessionLoadPipeline(
            sessionManager = sessionManager,
            libraryApiClient = libraryApiClient,
            aggregateStore = aggregateStore,
            networkOfflineStore = networkOfflineStore,
            offlinePlaybackFacade = mockk(relaxed = true),
            syncPlayManager = mockk(relaxed = true),
            getMediaDetail = { null },
            playbackRepository = mockk(relaxed = true),
            setMutedMirror = { },
            onSegmentsFetched = { },
            outputs = RecordingOutputs(stages),
            hooks = recordingHooks(stages),
        )

        // Mirror the VM's real scope shape (viewModelScope = SupervisorJob):
        // the failed load must not take down sibling collectors, and its
        // exception is routed to the handler rather than the test framework.
        val vmLikeScope = CoroutineScope(
            coroutineContext + SupervisorJob() +
                CoroutineExceptionHandler { _, _ -> /* the finally guarantee is the assertion */ }
        )

        runCatching { pipeline.start(vmLikeScope, request()).join() }
        runCurrent()

        assertFalse("no hydration after a failed load", "onItemHydrated" in stages)
        assertEquals("onInitializing(false)", stages.last())
    }

    /**
     * A loadMedia that reports its own failure (offline gate, detail-fetch
     * miss, vanished offline file) leaves `isReady = false`. The spine must
     * stop there — no media session, no playback-START report, no tracking —
     * instead of ghosting a session for an item that never started (#146).
     */
    @Test
    fun loadMediaFailure_stopsAfterLoadMedia_withoutOutcome() = runTest {
        val pipeline = pipeline(
            stages = stages,
            hooks = recordingHooks(stages),
            sessionState = PlayerSessionState(currentItemId = "item-1", isReady = false),
        )

        pipeline.start(this, request()).join()
        runCurrent()

        assertTrue("loadMedia must run", "loadMedia" in stages)
        assertFalse("no hydration after a failed load", "onItemHydrated" in stages)
        assertFalse("no media session for a failed load", "createMediaSession" in stages)
        assertFalse("no start report for a failed load", "reportPlaybackStart" in stages)
        assertFalse("failed load is not Completed", stages.any { it.startsWith("onOutcome") })
        // finally guarantee: the loading veil still lifts.
        assertEquals("onInitializing(false)", stages.last())
    }
}
