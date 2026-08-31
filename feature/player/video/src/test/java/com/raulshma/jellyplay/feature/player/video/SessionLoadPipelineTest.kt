package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayMethod
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
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The first written record of the session-load ordering
 * constraints that used to live unwritten inside `initializeInternal`'s inlined
 * ~15-stage coroutine. A fake [SessionLoadOutputs] + recording [SessionLoadHooks]
 * capture the invocation order; the collaborators ([PlayerSessionManager],
 * [MediaRepository], stores) are stubbed.
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
        cinemaGate: Boolean = false,
        offlineResumeTicks: Long = 0L,
    ) = SessionLoadHooks(
        reconcileSyncPlayQueue = { _, _, _ -> stages += "reconcileSyncPlayQueue" },
        shouldAttemptCinemaMode = { _, _, _ -> cinemaGate },
        beginCinemaMode = { _, _ -> stages += "beginCinemaMode" },
        resolveOfflineResumeTicks = { _, _ ->
            stages += "resolveOfflineResumeTicks"
            offlineResumeTicks
        },
        onSessionPrefsApplied = { stages += "onSessionPrefsApplied" },
        restoreRememberedMuted = { stages += "restoreRememberedMuted" },
        onItemHydrated = { _, _ -> stages += "onItemHydrated" },
        createMediaSession = { _, _, _ -> stages += "createMediaSession" },
        applyMediaDetail = { stages += "applyMediaDetail" },
        initializeTrickplay = { _, _ -> stages += "initializeTrickplay" },
        reportPlaybackStart = { _, _, _ -> stages += "reportPlaybackStart" },
        startPositionTracking = { stages += "startPositionTracking" },
        startProgressReporting = { stages += "startProgressReporting" },
        fetchMediaSegments = { stages += "fetchMediaSegments" },
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
        loadMediaBlock: CompletableDeferred<Unit>? = null,
    ): SessionLoadPipeline {
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

        val mediaRepository = mockk<MediaRepository>(relaxed = true)
        coEvery { mediaRepository.getIntros(any()) } returns Result.success(intros)

        val aggregateStore = mockk<VideoPlayerAggregateStore>(relaxed = true)
        every { aggregateStore.aggregate } returns MutableStateFlow(VideoPlayerAggregate())
        every { aggregateStore.aggregateRaw } returns flowOf(VideoPlayerAggregate())

        val networkOfflineStore = mockk<NetworkOfflineStore>(relaxed = true)
        every { networkOfflineStore.networkOffline } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice()
        )

        return SessionLoadPipeline(
            sessionManager = sessionManager,
            mediaRepository = mediaRepository,
            aggregateStore = aggregateStore,
            networkOfflineStore = networkOfflineStore,
            outputs = RecordingOutputs(stages),
            hooks = hooks,
        )
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

        assertEquals(
            listOf(
                "reconcileSyncPlayQueue",
                "onPrefsProjected",
                "onSessionPrefsApplied",
                "restoreRememberedMuted",
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
                "fetchMediaSegments",
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
            hooks = recordingHooks(stages, cinemaGate = true),
            intros = listOf(intro),
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

    @Test
    fun loadFailure_stillLiftsLoadingScreen() = runTest {
        val sessionManager = mockk<PlayerSessionManager>(relaxed = true)
        every { sessionManager.sessionState } returns MutableStateFlow(PlayerSessionState())
        coEvery { sessionManager.loadMedia(any(), any(), any()) } throws
            RuntimeException("playback info failed")

        val mediaRepository = mockk<MediaRepository>(relaxed = true)
        coEvery { mediaRepository.getIntros(any()) } returns Result.success(emptyList())
        val aggregateStore = mockk<VideoPlayerAggregateStore>(relaxed = true)
        every { aggregateStore.aggregate } returns MutableStateFlow(VideoPlayerAggregate())
        every { aggregateStore.aggregateRaw } returns flowOf(VideoPlayerAggregate())
        val networkOfflineStore = mockk<NetworkOfflineStore>(relaxed = true)
        every { networkOfflineStore.networkOffline } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice()
        )

        val pipeline = SessionLoadPipeline(
            sessionManager = sessionManager,
            mediaRepository = mediaRepository,
            aggregateStore = aggregateStore,
            networkOfflineStore = networkOfflineStore,
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
