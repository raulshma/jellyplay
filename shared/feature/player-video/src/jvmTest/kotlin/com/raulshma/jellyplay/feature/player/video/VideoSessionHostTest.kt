package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayController
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayPreparation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pins [VideoSessionHost] — the extracted [SessionHost] implementation (the
 * ViewModel's former object literal, now beside the other controllers) and
 * the home of the session load's [SessionLoadHooks] bundle. The suite pins
 * the behavior-preserving halves the extraction was NOT allowed to change:
 *
 *  - `resetForNewItem`'s synchronous-prefix ORDER (autoplay reset →
 *    still-watching reset → the autoplay-cancelled uiState command → the
 *    coordinator's new-item latch → the pending stream seed);
 *  - the start report's incognito gate (incognito NEVER reaches the server);
 *  - the remembered-muted restore's two-step (uiState mirror first, engine
 *    second) and its `rememberMuted && muted` gate;
 *  - the mini-player reclaim gate's typed pass-through both ways;
 *  - the VM-teardown forward (exactly once) and the offline-resume
 *    delegation into the core resolver;
 *  - the load-hook bodies that moved wholesale (segments fetch's
 *    offline-first precedence, the cinema gate's SyncPlay veto, the
 *    hydration trio's order behind the reclaim).
 *
 * Construction shape: recording command lambdas (the VM-side halves) +
 * mockk (relaxed + recording answers) for the concrete collaborators —
 * the same callLog pattern as [EngineAttachControllerTest]. No ViewModel,
 * no uiState.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoSessionHostTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    /**
     * The host's launch scope (the VM passes its real scope in production) —
     * a dedicated scope cancelled in teardown so the fire-and-forget segment
     * fetch never trips runTest's UncompletedCoroutinesError (the
     * EngineAttachControllerTest pattern). Under the UnconfinedTestDispatcher
     * its launches run inline, so the fetch's command order is observable.
     */
    private val hostScope = CoroutineScope(SupervisorJob() + testDispatcher)

    @AfterTest
    fun tearDown() {
        hostScope.cancel()
    }

    /** The one shared order log every recording seam appends to. */
    private val callLog = mutableListOf<String>()

    // ── mockk collaborators (the concrete session-stack types) ──────────────
    private val mediaContentProjector = mockk<MediaContentProjector>(relaxed = true)
    private val pipTransport = mockk<PipTransportController>(relaxed = true)
    private val videoMiniPlayerState = mockk<VideoMiniPlayerState>(relaxed = true)
    private val trickplayManager = mockk<TrickplayController>(relaxed = true)
    private val trickplayPreparation = mockk<TrickplayPreparation>(relaxed = true)
    private val syncPlay = mockk<SyncPlayBridge>(relaxed = true)
    private val syncPlayManager = mockk<SyncPlayManager>(relaxed = true)
    private val playbackSourceResolver = mockk<PlaybackSourceResolver>(relaxed = true)
    private val autoplayController = mockk<AutoPlayController>(relaxed = true)
    private val stillWatching = mockk<StillWatchingController>(relaxed = true)
    private val progressReporter = mockk<PlaybackProgressReporter>(relaxed = true)
    private val mediaSessionController = mockk<MediaSessionController>(relaxed = true)
    private val mediaDetailProjection = mockk<MediaDetailProjection>(relaxed = true)
    private val playbackRepository = mockk<PlaybackRepository>(relaxed = true)
    private val offlinePlaybackFacade = mockk<OfflinePlaybackFacade>(relaxed = true)
    private val engine = mockk<MediaEngine>(relaxed = true)

    // ── recording command state (the VM-side halves) ─────────────────────────
    private var prefsProjectionApplications = 0
    private val initializingWrites = mutableListOf<Boolean>()
    private val durationSeeds = mutableListOf<Long>()
    private val playheadSeeds = mutableListOf<Long>()
    private val playheadChips = mutableListOf<Long>()
    private val autoplayCancelledWrites = mutableListOf<Boolean>()
    private var coordinatorResets = 0
    private val pendingStreamSeeds = mutableListOf<MediaStreamSelection?>()
    private var routeAttempts = 0
    private var routeResult = false
    private var releasedVmInternals = 0
    private val mutedMirrorWrites = mutableListOf<Boolean>()
    private val hydratedItems = mutableListOf<Pair<String, VideoPlayerAggregate>>()
    private val trickplaySeeds = mutableListOf<Any?>()
    private val segmentWrites = mutableListOf<List<MediaSegment>>()
    private val adjacentFetches = mutableListOf<MediaDetail>()
    private val seriesLoads = mutableListOf<MediaDetail>()
    private val startReports = mutableListOf<PlaybackStartInfo>()

    // Live reads behind the getter commands (arranged per test).
    private var incognito = false
    private val playSessionId = "psid-1"
    private var detailHolder: MediaDetail? = null
    private var liveEngine: MediaEngine? = engine

    private lateinit var host: VideoSessionHost

    @BeforeTest
    fun setUp() {
        // The start report records through the (relaxed) repository so the
        // incognito gate is observable in callLog order as well.
        coEvery { playbackRepository.reportPlaybackStart(any()) } answers {
            startReports += firstArg<PlaybackStartInfo>()
            callLog += "startReport"
            Result.success(Unit)
        }
        every { engine.setMuted(any()) } answers {
            callLog += "engine.setMuted(${firstArg<Boolean>()})"
            Unit
        }
        every { autoplayController.resetForNewItem() } answers { callLog += "autoplay.resetForNewItem" }
        every { stillWatching.resetForItem() } answers { callLog += "stillWatching.resetForItem" }

        host = VideoSessionHost(
            scope = hostScope,
            mediaContentProjector = mediaContentProjector,
            pipTransport = pipTransport,
            videoMiniPlayerState = videoMiniPlayerState,
            trickplayManager = trickplayManager,
            trickplayPreparation = trickplayPreparation,
            syncPlay = syncPlay,
            syncPlayManager = syncPlayManager,
            playbackSourceResolver = playbackSourceResolver,
            autoplayController = autoplayController,
            stillWatching = stillWatching,
            progressReporter = progressReporter,
            mediaSessionController = mediaSessionController,
            mediaDetailProjection = mediaDetailProjection,
            playbackRepository = playbackRepository,
            offlinePlaybackFacade = offlinePlaybackFacade,
            applyPrefsProjection = { _ ->
                prefsProjectionApplications++
                callLog += "prefsProjection"
            },
            setInitializing = { visible ->
                initializingWrites += visible
                callLog += "initializing($visible)"
            },
            seedDuration = { durationSeeds += it },
            preSeedPlayhead = { ticks ->
                playheadSeeds += ticks
                callLog += "preSeedPlayhead"
            },
            seedPlayheadChip = { ticks ->
                playheadChips += ticks
                callLog += "playheadChip"
            },
            setAutoplayCancelled = { cancelled ->
                autoplayCancelledWrites += cancelled
                callLog += "autoplayCancelledWrite"
            },
            resetEngineEventCoordinator = {
                coordinatorResets++
                callLog += "coordinator.onNewItem"
            },
            setPendingStreams = { selection ->
                pendingStreamSeeds += selection
                callLog += "setPendingStreams"
            },
            routeRemotePlay = { _ ->
                routeAttempts++
                routeResult
            },
            releaseVmInternals = {
                releasedVmInternals++
                callLog += "releaseVmInternals"
            },
            beginCinemaMode = { intros, request ->
                callLog += "beginCinemaMode(${intros.size},${request.itemId})"
            },
            setMutedMirror = { muted ->
                mutedMirrorWrites += muted
                callLog += "mutedMirror($muted)"
            },
            applyItemHydration = { itemId, hydrated ->
                hydratedItems += itemId to hydrated
                callLog += "applyItemHydration"
            },
            seedTrickplayInfo = { info ->
                trickplaySeeds += info
                callLog += "trickplaySeed"
            },
            isIncognito = { incognito },
            getPlaySessionId = { playSessionId },
            getMediaDetail = { detailHolder },
            getEngine = { liveEngine },
            setSegments = { segments ->
                segmentWrites += segments
                callLog += "segmentsWrite"
            },
            fetchAdjacentEpisodes = { detail ->
                adjacentFetches += detail
                callLog += "refreshAdjacent"
            },
            loadSeriesEpisodes = { detail ->
                seriesLoads += detail
                callLog += "loadSeries"
            },
        )
    }

    // ── resetForNewItem: the synchronous-prefix ORDER ────────────────────────

    @Test
    fun resetForNewItem_runsTheSynchronousPrefix_inTheLoadBearingOrder() {
        val selection = MediaStreamSelection(audioStreamIndex = 3, subtitleStreamIndex = null)

        host.resetForNewItem(selection)

        // EXACT order: the autoplay reset, the still-watching defensive reset,
        // the autoplay-cancelled mirror clear, the coordinator's new-item
        // fallback-latch flip, and the pending stream seed LAST.
        assertEquals(
            listOf(
                "autoplay.resetForNewItem",
                "stillWatching.resetForItem",
                "autoplayCancelledWrite",
                "coordinator.onNewItem",
                "setPendingStreams",
            ),
            callLog.toList(),
        )
        assertEquals(listOf(false), autoplayCancelledWrites, "the clear is a false write")
        assertEquals(listOf<MediaStreamSelection?>(selection), pendingStreamSeeds)
    }

    // ── the initializing veil: onInitializing + the reclaim arm share it ─────

    @Test
    fun onInitializing_andOnMiniPlayerReclaimed_writeTheSameVeilCommand() {
        host.onInitializing(true)
        host.onMiniPlayerReclaimed() // the reclaim veil lift = initializing(false)

        assertEquals(listOf(true, false), initializingWrites)
    }

    @Test
    fun onDurationSeeded_forwardsToTheSeedCommand_andOnPrefsProjectedToItsCommand() {
        host.onDurationSeeded(42_000L)
        host.onPrefsProjected { this }

        assertEquals(listOf(42_000L), durationSeeds)
        assertEquals(1, prefsProjectionApplications)
    }

    // ── onPlayheadSeeded: session pre-seed + the >0 chip gate ────────────────

    @Test
    fun onPlayheadSeeded_preSeedsTheSession_andChipsOnlyForPositiveTicks() {
        host.onPlayheadSeeded(1_500_000L)
        host.onPlayheadSeeded(0L)

        // The session pre-seed runs for BOTH (the session gates its own
        // display write); the resume chip only for a non-zero resolved start
        // (ticks → ms).
        assertEquals(listOf(1_500_000L, 0L), playheadSeeds)
        assertEquals(listOf(150L), playheadChips)
        assertEquals(
            listOf("preSeedPlayhead", "playheadChip", "preSeedPlayhead"),
            callLog.toList(),
        )
    }

    @Test
    fun onStreamUrlResolved_forwardsToTheMediaProjector() {
        host.onStreamUrlResolved("https://stream")

        verify(exactly = 1) { mediaContentProjector.onStreamUrl("https://stream") }
    }

    // ── rearmTransports / clearTrickplay / reattachSyncPlay / wasInSyncPlay ──

    @Test
    fun lifecycleDelegates_reachTheirCollaborators() {
        every { syncPlayManager.isInSyncPlaySession } returns true

        host.rearmTransports()
        host.clearTrickplay()
        host.reattachSyncPlay()

        verify(exactly = 1) { pipTransport.registerPipTransport() }
        verify(exactly = 1) { trickplayManager.clear() }
        verify(exactly = 1) { syncPlay.reattachSession() }
        assertTrue(host.wasInSyncPlay(), "the flag read is the SyncPlay manager's")
    }

    // ── the mini-player reclaim gate: typed pass-through both ways ───────────

    @Test
    fun tryReclaimMiniPlayer_passesThroughTheHolder_nullAndNonNull() {
        every { videoMiniPlayerState.tryReclaimMediaEngine("item-1") } returns null
        assertNull(host.tryReclaimMiniPlayer("item-1"), "nothing deposited: nothing reclaimed")

        val reclaimed = mockk<MediaEngine>()
        every { videoMiniPlayerState.tryReclaimMediaEngine("item-2") } returns reclaimed

        assertSame(reclaimed, host.tryReclaimMiniPlayer("item-2"))

        host.releaseMiniPlayerState()
        verify(exactly = 1) { videoMiniPlayerState.release() }
    }

    // ── routeToRemotePlaySession: a pure command forward, both outcomes ──────

    @Test
    fun routeToRemotePlaySession_forwardsToTheCommand_andHonorsItsVerdict() {
        routeResult = false
        assertFalse(host.routeToRemotePlaySession(request("item-1")))
        routeResult = true
        assertTrue(host.routeToRemotePlaySession(request("item-1")))

        assertEquals(2, routeAttempts)
    }

    // ── releaseInternalsVmPart: the VM-teardown forward ──────────────────────

    @Test
    fun releaseInternalsVmPart_forwardsToTheVmCommand_exactlyOncePerCall() {
        host.releaseInternalsVmPart()
        host.releaseInternalsVmPart()

        assertEquals(2, releasedVmInternals, "one forward per hook call, nothing else")
    }

    // ── the load hooks ────────────────────────────────────────────────────────

    @Test
    fun reconcileSyncPlayQueue_delegatesToTheBridge() = testScope.runTest {
        host.loadHooks.reconcileSyncPlayQueue("item-1", "source-1", 5_000L)

        coVerify(exactly = 1) { syncPlay.reconcileQueueForItem("item-1", "source-1", 5_000L) }
    }

    @Test
    fun resolveOfflineResumeTicks_delegatesToTheCoreSourceResolver() = testScope.runTest {
        coEvery { playbackSourceResolver.resolveStartPositionTicks("item-1", 5L) } returns 900_000L

        assertEquals(900_000L, host.loadHooks.resolveOfflineResumeTicks("item-1", 5L))
        coVerify(exactly = 1) { playbackSourceResolver.resolveStartPositionTicks("item-1", 5L) }
    }

    @Test
    fun onSessionPrefsApplied_seedsTheAutoplayController() {
        val agg = VideoPlayerAggregate(
            videoPlayer = VideoPlayerSlice(videoAutoplayNext = true, stillWatchingEpisodeThreshold = 3),
        )

        host.loadHooks.onSessionPrefsApplied(agg)

        verify(exactly = 1) { autoplayController.setEnabled(true) }
        verify(exactly = 1) { autoplayController.setStillWatchingThreshold(3) }
    }

    @Test
    fun restoreRememberedMuted_whenArmed_mirrorsThenMutesTheEngine() {
        val agg = VideoPlayerAggregate(
            videoPlayer = VideoPlayerSlice(videoRememberMuted = true, videoMuted = true),
        )

        host.loadHooks.restoreRememberedMuted(agg)

        // EXACT order: the uiState mirror write first, the engine command second.
        assertEquals(
            listOf("mutedMirror(true)", "engine.setMuted(true)"),
            callLog.toList(),
        )
    }

    @Test
    fun restoreRememberedMuted_isGatedOnRememberMutedAndMuted() {
        host.loadHooks.restoreRememberedMuted(
            VideoPlayerAggregate(videoPlayer = VideoPlayerSlice(videoRememberMuted = false, videoMuted = true)),
        )
        host.loadHooks.restoreRememberedMuted(
            VideoPlayerAggregate(videoPlayer = VideoPlayerSlice(videoRememberMuted = true, videoMuted = false)),
        )

        assertTrue(mutedMirrorWrites.isEmpty(), "the gate holds: neither arm ran")
        verify(exactly = 0) { engine.setMuted(any()) }
    }

    @Test
    fun onItemHydrated_forwardsToTheVmHydrationCommand() {
        val agg = VideoPlayerAggregate()

        host.loadHooks.onItemHydrated("item-1", agg)

        assertEquals(listOf("item-1" to agg), hydratedItems)
        assertEquals(listOf("applyItemHydration"), callLog.toList())
    }

    @Test
    fun createMediaSession_buildsTheSessionThroughTheController() {
        host.loadHooks.createMediaSession("item-1", "Title", "Subtitle")

        verify(exactly = 1) { mediaSessionController.createForItem("item-1", "Title", "Subtitle") }
    }

    @Test
    fun applyMediaDetail_forwardsToTheProjection() {
        val detail = mockk<MediaDetail>(relaxed = true)

        host.loadHooks.applyMediaDetail(detail)

        verify(exactly = 1) { mediaDetailProjection.applyDetail(detail) }
    }

    @Test
    fun initializeTrickplay_seedsOnlyWhenPreparationProducesAManifest() = testScope.runTest {
        val info = mockk<com.raulshma.jellyplay.core.model.TrickplayInfo>()
        coEvery { trickplayPreparation.prepare("item-1", null) } returns info
        host.loadHooks.initializeTrickplay("item-1", null)
        assertEquals(1, trickplaySeeds.size, "the prepared manifest seeds the single uiState write")

        coEvery { trickplayPreparation.prepare("item-2", null) } returns null
        host.loadHooks.initializeTrickplay("item-2", null)
        assertEquals(1, trickplaySeeds.size, "no manifest → no uiState write")
    }

    @Test
    fun reportPlaybackStart_incognitoSkipsTheServerStartReport() = testScope.runTest {
        incognito = true

        host.loadHooks.reportPlaybackStart("item-1", null, PlayMethod.DIRECT_PLAY)

        assertTrue(startReports.isEmpty(), "incognito never reaches the server")
        coVerify(exactly = 0) { playbackRepository.reportPlaybackStart(any()) }
    }

    @Test
    fun reportPlaybackStart_normalPath_reportsWithTheResolvedPlaySessionId() = testScope.runTest {
        host.loadHooks.reportPlaybackStart("item-1", null, PlayMethod.DIRECT_PLAY)

        val report = startReports.single()
        assertEquals("item-1", report.itemId)
        assertEquals(playSessionId, report.sessionId)
        assertEquals(PlayMethod.DIRECT_PLAY, report.playMethod)
    }

    @Test
    fun shouldAttemptCinemaMode_vetoesNonFreshStartsAndSyncPlay() {
        val armed = VideoPlayerAggregate(
            videoPlayer = VideoPlayerSlice(cinemaModeEnabled = true),
        )
        detailHolder = null
        every { syncPlayManager.isInSyncPlaySession } returns false

        // Function-type invocation (the loadHooks property) takes positional
        // args: (agg, itemId, startPositionTicks).
        assertTrue(
            host.loadHooks.shouldAttemptCinemaMode(armed, "item-1", 0L),
            "fresh start on a video item: the gate opens",
        )
        assertFalse(
            host.loadHooks.shouldAttemptCinemaMode(armed, "item-1", 1L),
            "a resume position vetoes the pre-roll",
        )
        every { syncPlayManager.isInSyncPlaySession } returns true
        assertFalse(
            host.loadHooks.shouldAttemptCinemaMode(armed, "item-1", 0L),
            "SyncPlay group pacing vetoes the pre-roll",
        )
        every { syncPlayManager.isInSyncPlaySession } returns false
        assertFalse(
            host.loadHooks.shouldAttemptCinemaMode(
                armed.copy(videoPlayer = VideoPlayerSlice(cinemaModeEnabled = false)),
                "item-1",
                0L,
            ),
            "the pref off vetoes the pre-roll",
        )
        assertFalse(
            host.loadHooks.shouldAttemptCinemaMode(
                armed.copy(playback = armed.playback.copy(preferredPlayer = PlayerType.EXTERNAL)),
                "item-1",
                0L,
            ),
            "an external player vetoes the pre-roll",
        )
    }

    // ── fetchMediaSegments: offline-first precedence + the reclaim order ─────

    @Test
    fun fetchMediaSegments_prefersTheOfflineBundle_beforeAnyServerRoundTrip() {
        val local = listOf(mockk<MediaSegment>(), mockk<MediaSegment>())
        coEvery { offlinePlaybackFacade.loadSegments("item-1") } returns local

        host.loadHooks.fetchMediaSegments("item-1")

        // Under the UnconfinedTestDispatcher the fetch's launch ran inline:
        // the local bundle won, the server was never asked.
        assertEquals(listOf(local), segmentWrites)
        coVerify(exactly = 0) { playbackRepository.getMediaSegments(any()) }
    }

    @Test
    fun fetchMediaSegments_fallsBackToTheServer_whenNoBundleShips() {
        coEvery { offlinePlaybackFacade.loadSegments("item-1") } returns null
        val server = listOf(mockk<MediaSegment>())
        coEvery { playbackRepository.getMediaSegments("item-1") } returns Result.success(server)

        host.loadHooks.fetchMediaSegments("item-1")

        assertEquals(listOf(server), segmentWrites)
    }

    @Test
    fun hydrateReclaimedItem_runsTheHydrationTrio_inTheirOldOrder() {
        coEvery { offlinePlaybackFacade.loadSegments("item-1") } returns null
        coEvery { playbackRepository.getMediaSegments("item-1") } returns Result.success(emptyList())
        val detail = mockk<MediaDetail>(relaxed = true)

        host.hydrateReclaimedItem("item-1", detail)

        // The old loadReclaimedEngine-hook tail: segments fetch first, then
        // the adjacent-episodes refresh, then the series load.
        assertEquals(
            listOf("segmentsWrite", "refreshAdjacent", "loadSeries"),
            callLog.toList(),
        )
        assertEquals(listOf(detail), adjacentFetches)
        assertEquals(listOf(detail), seriesLoads)
    }

    // ── beginCinemaMode + onOutcome: the sequencing handoff ──────────────────

    @Test
    fun beginCinemaMode_handsTheIntrosToTheSessionSequencing() {
        val intros = listOf(mockk<com.raulshma.jellyplay.core.model.MediaItem>())

        host.loadHooks.beginCinemaMode(intros, request("item-1"))

        assertEquals(
            listOf("beginCinemaMode(1,item-1)"),
            callLog.toList(),
        )
    }

    private fun request(itemId: String) = LoadRequest(
        itemId = itemId,
        mediaSourceId = null,
        startPositionTicks = 0L,
        allowCinemaMode = true,
        subtitleStreamIndex = null,
        audioStreamIndex = null,
    )
}
