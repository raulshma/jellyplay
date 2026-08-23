package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.ResolvedPlayback
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.feature.player.video.engine.EngineError
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.FakeMediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Direct-construction tests for [PlaybackSession] — the Stage-B deep module
 * (plan B5). The ViewModel is NOT involved: the session is built with a real
 * [PlayerSessionManager], a real [SessionLoadPipeline] and a real
 * [EngineEventCoordinator] behind spied [FakeMediaEngine]s.
 *
 * Harness lineage (per the plan):
 *  - the engine factory mock `every { create(any()) } … spyk(FakeMediaEngine())`
 *    comes from [VideoPlayerViewModelPolicyCharacterizationTest];
 *  - the real [PlayerSessionManager] over mocked repositories follows
 *    [PlayerSessionManagerTest] (online loads only — the offline path touches
 *    `Uri.fromFile` / `MediaMetadataRetriever`);
 *  - the pipeline's load-order surface is recorded through a
 *    `RecordingOutputs`-style fake plus recording [SessionLoadHooks], exactly
 *    like [SessionLoadPipelineTest].
 *
 * JVM-pure (no Robolectric): `underlyingPlayer` is always null so
 * [MediaSessionController.createForItem] early-returns before touching any
 * media3 type, and the module's `isReturnDefaultValues` makes the coordinator's
 * default `SystemClock` clock inert (the pass-out poller is disabled via
 * `passOutHours = 0` — see the class note on [SessionEvent.PassOutPause]).
 *
 * The [FakeMediaEngine.positionFlow] access-time-capture gap noted in the plan
 * does not bite here: nothing in this harness subscribes to `positionFlow`
 * (the progress reporter is a relaxed mock whose own behavior is pinned by
 * `PlaybackProgressReporterTest`, and the coordinator only collects
 * play-state / playback-state / error / subtitle flows).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionTest {

    // ── Recording doubles ────────────────────────────────────────────────────
    /** One [SessionPositionStore.persist] call. */
    private data class PersistCall(val itemId: String, val positionMs: Long, val playSessionId: String)

    /**
     * Recording [SessionPositionStore]: delegates to the production
     * [SavedStateHandlePositionStore] (a plain [SavedStateHandle] works in JVM
     * tests) so reads round-trip like production, while recording every
     * persist for write-through assertions.
     */
    private class RecordingPositionStore : SessionPositionStore {
        val persists = mutableListOf<PersistCall>()
        private val delegate = SavedStateHandlePositionStore(SavedStateHandle())

        override fun persist(itemId: String, positionMs: Long, playSessionId: String, nowMs: Long) {
            persists += PersistCall(itemId, positionMs, playSessionId)
            delegate.persist(itemId, positionMs, playSessionId, nowMs)
        }

        override fun savedItemId(): String? = delegate.savedItemId()
        override fun savedPositionMs(): Long? = delegate.savedPositionMs()
        override fun savedPersistedAtMs(): Long? = delegate.savedPersistedAtMs()
        override fun savedPlaySessionId(): String? = delegate.savedPlaySessionId()
    }

    /** Recording implementation of the VM-facing [SessionLifecycleHooks]. */
    private class RecordingHooks(
        val stages: MutableList<String>,
        var routeToRemote: Boolean = false,
        var reclaimEngine: MediaEngine? = null,
        var wasSyncPlay: Boolean = false,
    ) : SessionLifecycleHooks {
        override fun rearmTransports() {
            stages += "rearmTransports"
        }

        override fun resetForNewItem(selection: MediaStreamSelection) {
            stages += "resetForNewItem"
        }

        override fun routeToRemotePlaySession(request: LoadRequest): Boolean {
            if (routeToRemote) stages += "routeToRemote"
            return routeToRemote
        }

        override fun tryReclaimMiniPlayer(itemId: String): MediaEngine? {
            stages += "tryReclaimMiniPlayer"
            return reclaimEngine
        }

        override fun onMiniPlayerReclaimed() {
            stages += "onMiniPlayerReclaimed"
        }

        override fun hydrateReclaimedItem(itemId: String, detail: MediaDetail) {
            stages += "hydrateReclaimedItem"
        }

        override fun releaseMiniPlayerState() {
            stages += "releaseMiniPlayerState"
        }

        override fun releaseInternalsVmPart() {
            stages += "releaseInternalsVmPart"
        }

        override fun clearTrickplay() {
            stages += "clearTrickplay"
        }

        override fun reattachSyncPlay() {
            stages += "reattachSyncPlay"
        }

        override fun wasInSyncPlay(): Boolean = wasSyncPlay
    }

    /** `RecordingOutputs` pattern from [SessionLoadPipelineTest]. */
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

    /** Recording [SessionLoadHooks], mirroring [SessionLoadPipelineTest]. */
    private fun recordingLoadHooks(stages: MutableList<String>) = SessionLoadHooks(
        reconcileSyncPlayQueue = { _, _, _ -> stages += "reconcileSyncPlayQueue" },
        shouldAttemptCinemaMode = { _, _, _ -> false },
        beginCinemaMode = { _, _ -> stages += "beginCinemaMode" },
        resolveOfflineResumeTicks = { _, ticks ->
            stages += "resolveOfflineResumeTicks"
            ticks
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
        fetchAdjacentEpisodes = { _ -> stages += "fetchAdjacentEpisodes" },
        loadSeriesEpisodes = { _ -> stages += "loadSeriesEpisodes" },
        onOutcome = { outcome -> stages += "onOutcome($outcome)" },
    )

    // ── Harness ──────────────────────────────────────────────────────────────

    private class Harness(
        val session: PlaybackSession,
        val psm: PlayerSessionManager,
        val mediaRepository: MediaRepository,
        val playbackRepository: PlaybackRepository,
        val playbackStore: PlaybackStore,
        val progressReporter: PlaybackProgressReporter,
        val offlinePlaybackFacade: OfflinePlaybackFacade,
        val positionStore: RecordingPositionStore,
        val hooks: RecordingHooks,
        val stages: MutableList<String>,
        val createdEngines: MutableList<FakeMediaEngine>,
        val uiPlaybackModes: MutableList<PlaybackMode>,
        /** [PlayerSessionState] snapshots taken while the load was in flight. */
        val detailFetchStates: MutableList<PlayerSessionState>,
        val events: MutableList<SessionEvent>,
        val details: MutableMap<String, Result<MediaDetail>>,
        val resolvedSessions: MutableMap<Pair<String, PlaybackMode>, ResolvedPlayback>,
    ) {
        fun stubDetail(itemId: String) {
            details[itemId] = Result.success(detail(itemId))
        }

        fun failDetail(itemId: String) {
            details[itemId] = Result.failure(RuntimeException("detail fetch failed"))
        }

        fun transcodeResolution(itemId: String, playSessionId: String) {
            resolvedSessions[itemId to PlaybackMode.FORCE_TRANSCODE] = ResolvedPlayback(
                mediaSourceId = "",
                streamUrl = "https://jellyfin/$itemId/transcode",
                playMethod = PlayMethod.TRANSCODE,
                playSessionId = playSessionId,
                maxStreamingBitrate = null,
            )
        }

        val engine: FakeMediaEngine get() = psm.engine as FakeMediaEngine

        /** Waits for stop-reports/joins launched on the (real-IO) release scope. */
        suspend fun drainReleaseScope() {
            session.releaseScope.coroutineContext[Job]?.children?.toList()?.joinAll()
        }
    }

    /**
     * Builds the session with a real PSM (mocked repositories, online loads)
     * and a real pipeline, over [FakeMediaEngine] spies from a mocked factory.
     * The session scope shares the test scheduler on an Unconfined dispatcher
     * so every launch (decision fan-out, reloads, coalesced writes) runs
     * eagerly and `advanceTimeBy` still controls the delayed ones.
     */
    private fun TestScope.buildHarness(
        playbackMode: () -> PlaybackMode = { PlaybackMode.AUTO },
    ): Harness {
        val stages = mutableListOf<String>()
        val createdEngines = mutableListOf<FakeMediaEngine>()
        val uiPlaybackModes = mutableListOf<PlaybackMode>()
        val detailFetchStates = mutableListOf<PlayerSessionState>()
        val events = mutableListOf<SessionEvent>()
        val details = mutableMapOf<String, Result<MediaDetail>>()
        val resolvedSessions = mutableMapOf<Pair<String, PlaybackMode>, ResolvedPlayback>()
        // The session's collectors (decision fan-out, engine-event policies)
        // never complete on their own — parent the session scope to the
        // backgroundScope so runTest cancels them when the body finishes
        // instead of timing out on uncompleted children.
        val sessionScope = CoroutineScope(
            UnconfinedTestDispatcher(testScheduler) +
                SupervisorJob(backgroundScope.coroutineContext[Job])
        )

        val context = mockk<Context>(relaxed = true)
        every { context.getString(any<Int>()) } returns "Error loading media"

        var psmRef: PlayerSessionManager? = null
        val mediaRepository = mockk<MediaRepository>()
        coEvery { mediaRepository.getMediaDetail(any()) } coAnswers {
            val id = firstArg<String>()
            psmRef?.let { detailFetchStates += it.sessionState.value }
            details[id] ?: Result.failure(IllegalStateException("unstubbed item: $id"))
        }
        coEvery { mediaRepository.getIntros(any()) } returns Result.success(emptyList())

        val playbackRepository = mockk<PlaybackRepository>(relaxed = true)
        coEvery {
            playbackRepository.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            val itemId = firstArg<String>()
            val mode = arg<PlaybackMode>(6)
            resolvedSessions[itemId to mode] ?: ResolvedPlayback(
                mediaSourceId = "",
                streamUrl = "https://jellyfin/$itemId",
                playMethod = PlayMethod.DIRECT_PLAY,
                playSessionId = "srv-$itemId",
                maxStreamingBitrate = null,
            )
        }

        val playbackStore = mockk<PlaybackStore>(relaxed = true)
        val offlinePlaybackFacade = mockk<OfflinePlaybackFacade>(relaxed = true)
        val adaptiveBitrateManager = mockk<AdaptiveBitrateManager>(relaxed = true)
        val playbackSourceResolver = mockk<PlaybackSourceResolver>(relaxed = true)
        coEvery { playbackSourceResolver.resolveUsableDownload(any()) } returns null

        val aggregateStore = mockk<VideoPlayerAggregateStore>(relaxed = true)
        every { aggregateStore.aggregate } returns MutableStateFlow(VideoPlayerAggregate())
        every { aggregateStore.aggregateRaw } returns flowOf(VideoPlayerAggregate())
        val networkOfflineStore = mockk<NetworkOfflineStore>(relaxed = true)
        every { networkOfflineStore.networkOffline } returns MutableStateFlow(NetworkOfflineSlice())

        val factory = mockk<PlayerEngineFactory>()
        every { factory.create(any()) } answers { spyk(FakeMediaEngine()).also(createdEngines::add) }

        val psm = PlayerSessionManager(
            context = context,
            scope = sessionScope,
            mediaRepository = mediaRepository,
            playbackRepository = playbackRepository,
            downloadRepository = mockk(relaxed = true),
            offlineRepository = mockk(relaxed = true),
            aggregateStore = aggregateStore,
            playerLifecycleManager = PlayerLifecycleManager(playbackStore),
            pipController = PipController(),
            adaptiveBitrateManager = adaptiveBitrateManager,
            playerEngineFactory = factory,
            playbackSourceResolver = playbackSourceResolver,
            streamingSubtitleStore = noOpStreamingSubtitleStore(),
        )
        psmRef = psm

        // Real controller, but with no underlying player bound: createForItem
        // early-returns (its documented no-op) and release() is a no-op — no
        // media3 types are ever constructed in a JVM test.
        val mediaSessionController = MediaSessionController(
            context = context,
            sessionManager = mockk<PlaybackSessionManager>(relaxed = true),
            getPlayer = { null },
            getImageUrl = { _, _ -> "" },
        )

        val pipeline = SessionLoadPipeline(
            sessionManager = psm,
            mediaRepository = mediaRepository,
            aggregateStore = aggregateStore,
            networkOfflineStore = networkOfflineStore,
            outputs = RecordingOutputs(stages),
            hooks = recordingLoadHooks(stages),
        )

        val hooks = RecordingHooks(stages)
        val progressReporter = mockk<PlaybackProgressReporter>(relaxed = true)
        val positionStore = RecordingPositionStore()

        val session = PlaybackSession(
            scope = sessionScope,
            playerSessionManager = psm,
            progressReporter = progressReporter,
            sessionLoadPipeline = pipeline,
            hooks = hooks,
            mediaSessionController = mediaSessionController,
            playbackStore = playbackStore,
            adaptiveBitrateManager = adaptiveBitrateManager,
            playbackRepository = playbackRepository,
            offlinePlaybackFacade = offlinePlaybackFacade,
            mediaRepository = mediaRepository,
            setCinemaIntroState = { },
            seedDisplayedPositionMs = { },
            positionStore = positionStore,
            getStreamingQuality = { StreamingQuality.AUTO },
            setUiPlaybackMode = { uiPlaybackModes += it },
            getIncognitoModeEnabled = { false },
            setPendingStreams = { _ -> },
            getPlaybackMode = playbackMode,
            directPlayFallbackNotice = { error -> "Falling back to transcode: $error" },
            passOutHours = flowOf(0),
            onEngineEventCoordinatorRearmed = { stages += "coordinatorRearmed" },
        )

        // SharedFlow has no replay — subscribe before any emission under test.
        // Launched on the background scope: an always-active collector must
        // not keep the test coroutine's children uncompleted.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            session.events.collect { events += it }
        }

        return Harness(
            session = session,
            psm = psm,
            mediaRepository = mediaRepository,
            playbackRepository = playbackRepository,
            playbackStore = playbackStore,
            progressReporter = progressReporter,
            offlinePlaybackFacade = offlinePlaybackFacade,
            positionStore = positionStore,
            hooks = hooks,
            stages = stages,
            createdEngines = createdEngines,
            uiPlaybackModes = uiPlaybackModes,
            detailFetchStates = detailFetchStates,
            events = events,
            details = details,
            resolvedSessions = resolvedSessions,
        )
    }

    // ── 1. Initialize lifecycle ──────────────────────────────────────────────

    @Test
    fun initialize_runsHookSequence_drivesSessionStateToActive_returnsTrackedLoadJob() = runTest {
        val h = buildHarness()
        h.stubDetail("item-1")

        // The exposed session/engine flows are direct aliases of the PSM's —
        // no re-publish, no stateIn (dispatch ordering vs. engineFlow).
        assertSame(h.psm.sessionState, h.session.sessionState)
        assertSame(h.psm.engineFlow, h.session.engineFlow)

        val job = h.session.initialize(request("item-1"))
        assertSame(job, h.session.loadJob)
        job.join()

        // Loading → active: the in-flight snapshot (taken while the detail
        // fetch was resolving) shows the session loading; the final state is
        // ready with the server-issued session id.
        val loading = h.detailFetchStates.single()
        assertEquals("item-1", loading.currentItemId)
        assertFalse(loading.isReady)
        val state = h.psm.sessionState.value
        assertEquals("item-1", state.currentItemId)
        assertTrue(state.isReady)
        assertEquals("srv-item-1", state.playSessionId)
        assertEquals("Test Movie item-1", state.title)
        assertTrue(h.session.loadJob!!.isCompleted)
        assertEquals(1, h.createdEngines.size)
        assertSame(h.createdEngines.single(), h.psm.engine)

        // Hook ordering: synchronous prefix (transports, item reset, per-item
        // teardown) fully precedes the pipeline spine, and the SyncPlay
        // reattach does not fire for a non-SyncPlay session.
        fun idx(stage: String) = h.stages.indexOf(stage)
        assertTrue(idx("rearmTransports") < idx("reconcileSyncPlayQueue"))
        assertTrue(idx("resetForNewItem") < idx("releaseInternalsVmPart"))
        assertTrue(idx("releaseInternalsVmPart") < idx("clearTrickplay"))
        assertTrue(idx("clearTrickplay") < idx("reconcileSyncPlayQueue"))
        assertEquals(1, h.stages.count { it == "releaseInternalsVmPart" })
        assertFalse(h.stages.contains("reattachSyncPlay"))
        // The spine's terminal outcome, followed by the finally-side loading
        // lift (a same-value no-op on the happy path — pinned like this by
        // SessionLoadPipelineTest).
        assertEquals("onOutcome(Completed)", h.stages[h.stages.size - 2])
        assertEquals("onInitializing(false)", h.stages.last())
    }

    @Test
    fun initialize_detailFetchFailure_leavesSessionNotReadyAndUnresolved() = runTest {
        val h = buildHarness()
        h.failDetail("item-1")

        h.session.initialize(request("item-1")).join()

        val state = h.psm.sessionState.value
        assertEquals("item-1", state.currentItemId)
        assertFalse(state.isReady)
        assertEquals("Error loading media", state.title)
        assertNull(state.playSessionId)
        assertNull(h.psm.engine)
        coVerify(exactly = 0) {
            h.playbackRepository.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun initialize_sameItemWhileReady_shortCircuitsWithoutReRunningPipeline() = runTest {
        val h = buildHarness()
        h.stubDetail("item-1")
        val firstJob = h.session.initialize(request("item-1"))
        // Live, started playback at position 0 — the re-select is a no-op.
        h.engine.simulateState(EnginePlaybackState.READY)
        assertEquals(0L, h.engine.currentPositionMs)
        val loadsBefore = h.stages.count { it == "reconcileSyncPlayQueue" }

        val secondJob = h.session.initialize(request("item-1"))

        // An already-finished handle is returned and the first load stays the
        // tracked loadJob; the pipeline, teardown and engine never re-ran.
        assertTrue(secondJob.isCompleted)
        assertFalse(secondJob.isActive)
        assertSame(firstJob, h.session.loadJob)
        assertEquals(loadsBefore, h.stages.count { it == "reconcileSyncPlayQueue" })
        assertEquals(1, h.stages.count { it == "releaseInternalsVmPart" })
        assertEquals(1, h.createdEngines.size)
        coVerify(exactly = 1) {
            h.playbackRepository.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun initialize_newItem_cancelsInFlightLoadJobBeforeStartingReplacement() = runTest {
        val h = buildHarness()
        h.stubDetail("item-2")
        val blockedDetail = CompletableDeferred<Unit>()
        coEvery { h.mediaRepository.getMediaDetail("item-1") } coAnswers {
            blockedDetail.await()
            Result.success(detail("item-1"))
        }

        val firstJob = h.session.initialize(request("item-1"))
        assertTrue(firstJob.isActive)

        h.session.initialize(request("item-2")).join()
        blockedDetail.complete(Unit)

        // Latest load wins: the in-flight load was cancelled, the replacement
        // completed and is the tracked loadJob.
        assertTrue(firstJob.isCancelled)
        assertNotSame(firstJob, h.session.loadJob)
        assertTrue(h.session.loadJob!!.isCompleted)
        assertEquals("item-2", h.psm.sessionState.value.currentItemId)
        assertTrue(h.psm.sessionState.value.isReady)
        // Neither load had a position yet — no stop-report may fire.
        coVerify(exactly = 0) { h.playbackRepository.reportPlaybackStopped(any(), any(), any()) }
    }

    // ── 2. Reload preserves session/position ────────────────────────────────

    @Test
    fun retryWithEngine_reloadsAtCurrentPosition_preservesSessionIdAndPersistedPosition() = runTest {
        val h = buildHarness()
        h.stubDetail("item-1")
        h.session.initialize(request("item-1")).join()
        val localSessionId = h.session.playSessionId
        h.engine.advanceTo(42_000)

        h.session.retryWithEngine(
            playerType = PlayerType.LIBVLC,
            playbackSpeed = 1.5f,
            streamingQuality = StreamingQuality.AUTO,
        )

        // The engine swap happens at the captured current position.
        assertEquals(2, h.createdEngines.size)
        assertTrue(h.createdEngines[0].released)
        assertEquals(42_000L, h.createdEngines[1].lastRequest!!.startPositionMs)
        // Session identity survives the reload: the server id in the PSM state
        // and the local fallback id are untouched, and the process-death
        // position store was never written (no clobber).
        assertEquals("srv-item-1", h.psm.sessionState.value.playSessionId)
        assertEquals(localSessionId, h.session.playSessionId)
        assertTrue(h.positionStore.persists.isEmpty())
        coVerify(exactly = 1) { h.playbackStore.setPreferredPlayer(PlayerType.LIBVLC) }
        // cancelJobs fired once for the initialize teardown and once for the
        // engine swap itself.
        verify(exactly = 2) { h.progressReporter.cancelJobs() }
        verify(exactly = 1) { h.progressReporter.startPositionTracking() }
        verify(exactly = 1) { h.progressReporter.startProgressReporting() }
    }

    @Test
    fun reloadForMode_transcode_stopsOutgoingSessionOnce_informsUser_preservesPosition() = runTest {
        val h = buildHarness()
        h.stubDetail("item-1")
        h.transcodeResolution("item-1", playSessionId = "srv-item-1-t")
        h.session.initialize(request("item-1")).join()
        val localSessionId = h.session.playSessionId
        h.engine.advanceTo(30_000)

        h.session.reloadForMode(PlaybackMode.FORCE_TRANSCODE, StreamingQuality.AUTO)

        // The outgoing server session is stopped exactly once, at the position
        // captured before the swap, and the new engine resumes there.
        coVerify(exactly = 1) {
            h.playbackRepository.reportPlaybackStopped("item-1", "srv-item-1", 30_000L * 10_000L)
        }
        assertEquals(30_000L, h.createdEngines.last().lastRequest!!.startPositionMs)
        assertEquals("srv-item-1-t", h.psm.sessionState.value.playSessionId)
        // The transcode switch surfaces its re-buffer notice; the local
        // fallback session id and the persisted resume position are untouched.
        assertEquals(
            listOf(SessionEvent.InformUser("Switched to transcoded stream — re-buffering")),
            h.events,
        )
        assertEquals(localSessionId, h.session.playSessionId)
        assertTrue(h.positionStore.persists.isEmpty())
        verify(exactly = 1) { h.progressReporter.startPositionTracking() }
    }

    // ── 3. Stop-report dedup + release idempotence ──────────────────────────

    @Test
    fun stopReports_fireExactlyOncePerSessionId_acrossTeardownAndRelease() = runTest {
        val h = buildHarness()
        h.stubDetail("item-1")
        h.stubDetail("item-2")
        h.session.initialize(request("item-1")).join()
        h.engine.advanceTo(60_000)

        // Switching items: the initialize teardown reports the OUTGOING
        // session (item-1 / srv-item-1) exactly once.
        h.session.initialize(request("item-2")).join()
        coVerify(exactly = 1) {
            h.playbackRepository.reportPlaybackStopped("item-1", "srv-item-1", 60_000L * 10_000L)
        }

        // Full release reports the CURRENT session (item-2 / srv-item-2),
        // which has its own playSessionId — once, on the release scope.
        h.engine.advanceTo(90_000)
        h.session.release { }
        h.drainReleaseScope()

        coVerify(exactly = 1) {
            h.playbackRepository.reportPlaybackStopped("item-2", "srv-item-2", 90_000L * 10_000L)
        }
        coVerify(exactly = 2) { h.playbackRepository.reportPlaybackStopped(any(), any(), any()) }
        assertEquals("srv-item-2", h.session.stopReportedForSession)
    }

    @Test
    fun release_isIdempotent_whenStopAlreadyReportedBySession() = runTest {
        val h = buildHarness()
        h.stubDetail("item-1")
        h.session.initialize(request("item-1")).join()
        h.engine.advanceTo(45_000)

        // The end-of-item / transcode-fallback path reports the stop itself…
        h.session.reportCurrentPlaybackStopped()
        coVerify(exactly = 1) {
            h.playbackRepository.reportPlaybackStopped("item-1", "srv-item-1", 45_000L * 10_000L)
        }

        // …so the teardown in release() must not duplicate it, and a second
        // release() stays a no-op (the dedup latch survives).
        h.session.release { }
        h.drainReleaseScope()
        h.session.release { }
        h.drainReleaseScope()

        coVerify(exactly = 1) { h.playbackRepository.reportPlaybackStopped(any(), any(), any()) }
        assertEquals("srv-item-1", h.session.stopReportedForSession)
    }

    // ── 4. Decision → event mapping ─────────────────────────────────────────

    @Test
    fun engineErrors_mapToShowErrorEvents_retryableFlagForwarded() = runTest {
        val h = buildHarness()
        h.stubDetail("item-1")
        h.session.initialize(request("item-1")).join()

        h.engine.errorEmissions.tryEmit(EngineError.Network(null))
        h.engine.errorEmissions.tryEmit(EngineError.Decoder("h264", null))

        // The structured taxonomy's message AND retry verdict are forwarded
        // 1:1 — the dialog offers same-engine retry vs. switch-engine on it.
        assertEquals(
            listOf(
                SessionEvent.ShowError("Network error", retryable = true, clearBuffering = false),
                SessionEvent.ShowError("Decoder error (h264)", retryable = false, clearBuffering = false),
            ),
            h.events,
        )
    }

    @Test
    fun playbackEnded_mapsToPlaybackEndedEvent() = runTest {
        val h = buildHarness()
        h.stubDetail("item-1")
        h.session.initialize(request("item-1")).join()

        h.engine.simulateEnd()

        assertEquals(listOf<SessionEvent>(SessionEvent.PlaybackEnded), h.events)
    }

    @Test
    fun malformedSubtitleTrack_mapsToInformUserEvent() = runTest {
        val h = buildHarness()
        h.stubDetail("item-1")
        h.session.initialize(request("item-1")).join()

        h.engine.subtitleEventEmissions.tryEmit(SubtitleEvent.MalformedTrackDisabled)

        assertEquals(
            listOf(SessionEvent.InformUser("Subtitles disabled — malformed subtitle track detected")),
            h.events,
        )
    }

    @Test
    fun bufferingWatchdogTimeout_mapsToShowErrorWithClearBuffering() = runTest {
        val h = buildHarness()
        h.stubDetail("item-1")
        h.session.initialize(request("item-1")).join()

        // The engine never leaves the initial buffer — the watchdog fires
        // after its (virtual-time) window and the mapped event must also lift
        // the stuck buffering spinner.
        h.engine.simulateState(EnginePlaybackState.BUFFERING)
        advanceTimeBy(BUFFERING_TIMEOUT_MS + 1)

        assertEquals(
            listOf(
                SessionEvent.ShowError(
                    error = "Playback failed to start. Try a different player engine.",
                    retryable = true,
                    clearBuffering = true,
                ),
            ),
            h.events,
        )
    }

    @Test
    fun forceDirectPlayFirstError_fallsBackToTranscode_emitsInformUser() = runTest {
        val h = buildHarness(playbackMode = { PlaybackMode.FORCE_DIRECT_PLAY })
        h.stubDetail("item-1")
        h.transcodeResolution("item-1", playSessionId = "srv-item-1-t")
        h.session.initialize(request("item-1")).join()
        h.engine.advanceTo(25_000)

        // First runtime error under FORCE_DIRECT_PLAY: the one-shot fallback
        // consumes it — a notice, a mode flip (ui mirror + persisted store)
        // and a stop-report + transcode reload at the error position.
        h.engine.errorEmissions.tryEmit(EngineError.Decoder("h264", null))

        assertEquals(
            listOf(SessionEvent.InformUser("Falling back to transcode: Decoder error (h264)")),
            h.events,
        )
        assertEquals(listOf(PlaybackMode.FORCE_TRANSCODE), h.uiPlaybackModes)
        coVerify(exactly = 1) { h.playbackStore.setPlaybackMode(PlaybackMode.FORCE_TRANSCODE) }
        coVerify(exactly = 1) {
            h.playbackRepository.reportPlaybackStopped("item-1", "srv-item-1", 25_000L * 10_000L)
        }
        assertEquals(25_000L, h.createdEngines.last().lastRequest!!.startPositionMs)
        assertEquals("srv-item-1-t", h.psm.sessionState.value.playSessionId)
        verify(exactly = 1) { h.progressReporter.startPositionTracking() }

        // One-shot: a second error under the same item surfaces the dialog
        // instead of re-falling-back.
        h.engine.errorEmissions.tryEmit(EngineError.Decoder("vpx", null))
        assertEquals(
            SessionEvent.ShowError("Decoder error (vpx)", retryable = false, clearBuffering = false),
            h.events.last(),
        )
        coVerify(exactly = 1) { h.playbackStore.setPlaybackMode(PlaybackMode.FORCE_TRANSCODE) }
    }

    // ── 5. Seek persist + coalesced mirror writes ───────────────────────────

    @Test
    fun seekPersisted_writesPositionStoreImmediately_coalescesMirrorWrites() = runTest {
        val h = buildHarness()
        h.stubDetail("item-1")
        h.session.initialize(request("item-1")).join()
        h.engine.durationValue = 600_000
        h.engine.advanceTo(5_000)

        h.session.seekPersisted(120_000)
        // The process-death snapshot is synchronous — the FIRST seek persists
        // before any virtual time passes, carrying the current play-session id.
        assertEquals(listOf(PersistCall("item-1", 120_000, "srv-item-1")), h.positionStore.persists)

        // Rapid scrubbing: every seek still snapshots the store immediately…
        h.session.seekPersisted(121_000)
        h.session.seekPersisted(122_000)
        assertEquals(
            listOf(
                PersistCall("item-1", 120_000, "srv-item-1"),
                PersistCall("item-1", 121_000, "srv-item-1"),
                PersistCall("item-1", 122_000, "srv-item-1"),
            ),
            h.positionStore.persists,
        )
        // …a fresh seek wins over the engine's not-yet-caught-up position…
        assertEquals(122_000L, h.session.getReportPositionMs())
        // …while the offline-mirror DB write stays coalesced and pending.
        coVerify(exactly = 0) { h.offlinePlaybackFacade.recordProgress(any(), any(), any(), any()) }
        assertTrue(h.session.pendingSeekProgressJob!!.isActive)

        advanceTimeBy(501)

        // Exactly one mirror write per quiet window, at the LAST position.
        coVerify(exactly = 1) {
            h.offlinePlaybackFacade.recordProgress("item-1", 122_000L * 10_000L, any(), false)
        }
        coVerify(exactly = 1) { h.offlinePlaybackFacade.recordProgress(any(), any(), any(), any()) }
    }

    @Test
    fun persistPlaybackPosition_throttlesNonForcedWrites() = runTest {
        val h = buildHarness()
        h.stubDetail("item-1")
        h.session.initialize(request("item-1")).join()

        h.session.persistPlaybackPosition(100_000, force = true)
        // Below the 5s throttle window — dropped…
        h.session.persistPlaybackPosition(102_000, force = false)
        // …beyond it — persisted.
        h.session.persistPlaybackPosition(110_000, force = false)

        assertEquals(
            listOf(
                PersistCall("item-1", 100_000, "srv-item-1"),
                PersistCall("item-1", 110_000, "srv-item-1"),
            ),
            h.positionStore.persists,
        )
        coVerify(exactly = 2) { h.offlinePlaybackFacade.recordProgress(any(), any(), any(), any()) }
    }
}

/** Item detail with a 20-minute runtime so the duration-seed stage fires. */
private fun detail(itemId: String) = MediaDetail(
    item = MediaItem(
        id = itemId,
        name = "Test Movie $itemId",
        mediaType = MediaType.MOVIE,
        runTimeTicks = 1_200_000L * 10_000L,
    ),
)

private fun request(itemId: String, startPositionTicks: Long = 0L) = LoadRequest(
    itemId = itemId,
    mediaSourceId = null,
    startPositionTicks = startPositionTicks,
    allowCinemaMode = false,
    subtitleStreamIndex = null,
    audioStreamIndex = null,
)
