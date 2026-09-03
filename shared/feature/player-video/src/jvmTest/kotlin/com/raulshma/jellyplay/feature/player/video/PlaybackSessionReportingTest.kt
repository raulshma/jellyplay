package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.ResolvedPlayback
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.feature.player.video.engine.FakeMediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Session-scoped reporting/teardown behaviors extracted from the ViewModel at
 * refactor step B3 ([PlaybackSession]): the report-position seek-freshness
 * window, the throttled process-death position persist, the Stop-report dedup
 * latch and its incognito gate, the [PlaybackSession.release] split (final
 * stop-report + pending-seek join on the release scope), and the transcode
 * fallback's [SessionEvent.InformUser] notices.
 *
 * Conventions: the session's injected scope uses [Dispatchers.Unconfined] so
 * the session's `scope.launch` blocks run synchronously on the test thread;
 * repositories are relaxed mocks and the VM-facing seams
 * ([SessionLifecycleHooks], [SessionPositionStore]) are recording fakes. The
 * release-scope work ([PlaybackSession.releaseScope], a real IO scope built
 * inside the session) is awaited with mockk's timeout verification — that
 * teardown must outlive the caller's scope, so it cannot run on the test
 * dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionReportingTest {

    /** The session's injected scope — Unconfined so launches run synchronously. */
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private lateinit var playerSessionManager: PlayerSessionManager
    private lateinit var sessionStateFlow: MutableStateFlow<PlayerSessionState>
    private lateinit var engine: FakeMediaEngine
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var offlinePlaybackFacade: OfflinePlaybackFacade
    private lateinit var hooks: RecordingHooks
    private lateinit var positionStore: FakePositionStore
    private lateinit var session: PlaybackSession

    @kotlin.test.AfterTest
    fun tearDown() {
        // Cancel the session-owned release scope AFTER its IO work was verified.
        session.onOwnerCleared()
        sessionScope.cancel()
    }

    @kotlin.test.BeforeTest
    fun setUp() {
        buildSession()
    }

    /** Builds the session under test; `incognito` flips the incognito gate. */
    private fun buildSession(incognito: Boolean = false) {
        engine = FakeMediaEngine().apply {
            durationValue = 100_000L
            advanceTo(30_000L)
        }
        sessionStateFlow = MutableStateFlow(
            PlayerSessionState(currentItemId = "item-1", playSessionId = "server-1"),
        )
        playerSessionManager = mockk(relaxed = true)
        every { playerSessionManager.sessionState } returns sessionStateFlow
        every { playerSessionManager.engineFlow } returns MutableStateFlow<MediaEngine?>(engine)
        every { playerSessionManager.engine } returns engine
        playbackRepository = mockk(relaxed = true)
        offlinePlaybackFacade = mockk(relaxed = true)
        hooks = RecordingHooks()
        positionStore = FakePositionStore()

        session = PlaybackSession(
            scope = sessionScope,
            playerSessionManager = playerSessionManager,
            progressReporter = mockk(relaxed = true),
            sessionLoadPipeline = mockk(relaxed = true),
            hooks = hooks,
            mediaSessionController = mockk(relaxed = true),
            playbackStore = mockk(relaxed = true),
            adaptiveBitrateManager = mockk(relaxed = true),
            playbackRepository = playbackRepository,
            offlinePlaybackFacade = offlinePlaybackFacade,
            mediaRepository = mockk(relaxed = true),
            setCinemaIntroState = {},
            seedDisplayedPositionMs = {},
            positionStore = positionStore,
            getStreamingQuality = { StreamingQuality.AUTO },
            setUiPlaybackMode = {},
            getIncognitoModeEnabled = { incognito },
            setPendingStreams = {},
            getPlaybackMode = { PlaybackMode.AUTO },
            directPlayFallbackNotice = { it },
            passOutHours = flowOf(0),
            onEngineEventCoordinatorRearmed = {},
        )
    }

    // ── getReportPositionMs: the 3-second seek-freshness window ────────────

    @Test
    fun getReportPositionMs_withoutSeekLatch_returnsEnginePosition() {
        engine.advanceTo(33_000L)

        assertEquals(33_000L, session.getReportPositionMs())
    }

    @Test
    fun getReportPositionMs_freshSeek_winsOverEnginePosition() {
        engine.advanceTo(50_000L)
        // A seek followed by an immediate teardown must report the seek
        // position, not the engine's not-yet-caught-up position.
        session.lastSeekPositionMs = 42_000L
        session.lastSeekTimestamp = System.currentTimeMillis()

        assertEquals(42_000L, session.getReportPositionMs())
    }

    @Test
    fun getReportPositionMs_staleSeek_fallsBackToEnginePosition() {
        engine.advanceTo(50_000L)
        session.lastSeekPositionMs = 42_000L
        session.lastSeekTimestamp = System.currentTimeMillis() - 60_000L // far past the 3 s window

        assertEquals(50_000L, session.getReportPositionMs())
    }

    // ── persistPlaybackPosition: 5-second throttle + force bypass ──────────

    @Test
    fun persistPlaybackPosition_firstCall_writesStoreSnapshotAndMirrorsOffline() = runTest {
        val percentage = slot<Double>()

        session.persistPlaybackPosition(positionMs = 10_000L, force = false)

        // The process-death store snapshot is written synchronously, stashing
        // the resolved play-session id so the eventual stop-report pairs with it.
        val persist = positionStore.persists.single()
        assertEquals("item-1", persist.itemId)
        assertEquals(10_000L, persist.positionMs)
        assertEquals("server-1", persist.playSessionId)
        assertTrue(persist.nowMs > 0L)
        assertEquals(10_000L, session.lastPersistedPositionMs)

        // The offline mirror runs on the session scope (Unconfined → synchronous).
        coVerify(exactly = 1) {
            offlinePlaybackFacade.recordProgress("item-1", 100_000_000L, capture(percentage), false)
        }
        assertEquals(10.0, percentage.captured, 0.01) // 10 s of a 100 s runtime
    }

    @Test
    fun persistPlaybackPosition_withinThrottleWindow_isSkipped() = runTest {
        session.persistPlaybackPosition(positionMs = 10_000L, force = false)
        session.persistPlaybackPosition(positionMs = 12_000L, force = false) // 2 s delta < 5 s

        // Only the first write landed; the throttled one left no trace.
        assertEquals(1, positionStore.persists.size)
        assertEquals(10_000L, session.lastPersistedPositionMs)
        coVerify(exactly = 1) { offlinePlaybackFacade.recordProgress(any(), any(), any(), any()) }
    }

    @Test
    fun persistPlaybackPosition_force_bypassesThrottle() = runTest {
        session.persistPlaybackPosition(positionMs = 10_000L, force = false)
        session.persistPlaybackPosition(positionMs = 12_000L, force = false)
        session.persistPlaybackPosition(positionMs = 40_000L, force = true) // explicit seek

        assertEquals(2, positionStore.persists.size)
        assertEquals(40_000L, positionStore.persists.last().positionMs)
        coVerify(exactly = 1) { offlinePlaybackFacade.recordProgress("item-1", 400_000_000L, any(), false) }
    }

    @Test
    fun persistPlaybackPosition_withoutCurrentItem_isNoOp() = runTest {
        sessionStateFlow.value = PlayerSessionState(currentItemId = null)

        session.persistPlaybackPosition(positionMs = 10_000L, force = true)

        assertTrue(positionStore.persists.isEmpty())
        coVerify(exactly = 0) { offlinePlaybackFacade.recordProgress(any(), any(), any(), any()) }
    }

    // ── reportCurrentPlaybackStopped: dedup latch + incognito gate ─────────

    @Test
    fun reportPlaybackStopped_reportsOnce_thenDedups() = runTest {
        engine.advanceTo(60_000L)

        session.reportCurrentPlaybackStopped()
        session.reportCurrentPlaybackStopped() // same session: dedup latch holds

        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped("item-1", "server-1", 600_000_000L)
        }
        assertEquals("server-1", session.stopReportedForSession)
    }

    @Test
    fun reportPlaybackStopped_dedupLatchIsKeyedPerSession() = runTest {
        engine.advanceTo(60_000L)
        session.reportCurrentPlaybackStopped()

        // A new load issues a new play-session id: the latch must NOT swallow
        // the new session's stop report.
        sessionStateFlow.value = sessionStateFlow.value.copy(playSessionId = "server-2")
        session.reportCurrentPlaybackStopped()

        coVerify(exactly = 1) { playbackRepository.reportPlaybackStopped("item-1", "server-1", 600_000_000L) }
        coVerify(exactly = 1) { playbackRepository.reportPlaybackStopped("item-1", "server-2", 600_000_000L) }
    }

    @Test
    fun reportPlaybackStopped_incognito_isSkippedAndLatchStaysUntouched() = runTest {
        buildSession(incognito = true)
        engine.advanceTo(60_000L)

        session.reportCurrentPlaybackStopped()

        coVerify(exactly = 0) { playbackRepository.reportPlaybackStopped(any(), any(), any()) }
        assertNull(session.stopReportedForSession)
    }

    @Test
    fun reportPlaybackStopped_zeroPosition_isSkipped() = runTest {
        engine.advanceTo(0L)

        session.reportCurrentPlaybackStopped()

        // A zero-tick stop is worthless to the server — never reported, and
        // the latch stays open so a real position can still be reported later.
        coVerify(exactly = 0) { playbackRepository.reportPlaybackStopped(any(), any(), any()) }
        assertNull(session.stopReportedForSession)
    }

    // ── reloadForMode: SessionEvent.InformUser notices ──────────────────────

    @Test
    fun reloadForMode_transcodeResolved_emitsNotice_andStopReportsOutgoingSession() = runTest {
        val events = mutableListOf<SessionEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            session.events.collect { events += it }
        }
        coEvery { playerSessionManager.reloadPlayback(any(), any(), any(), any()) } returns
            resolved(PlayMethod.TRANSCODE)

        session.reloadForMode(PlaybackMode.AUTO, StreamingQuality.AUTO)

        val expected: List<SessionEvent> =
            listOf(SessionEvent.InformUser("Switched to transcoded stream — re-buffering"))
        assertEquals(expected, events)
        // The outgoing server session was stop-reported BEFORE the swap, at the
        // engine's current position (no seek latch).
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped("item-1", "server-1", 300_000_000L)
        }
    }

    @Test
    fun reloadForMode_forcedDirectPlay_fallsBackWithNotices_withoutDoubleStopReport() = runTest {
        val events = mutableListOf<SessionEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            session.events.collect { events += it }
        }
        coEvery { playerSessionManager.reloadPlayback(any(), any(), any(), any()) } returns
            resolved(PlayMethod.TRANSCODE)

        session.reloadForMode(PlaybackMode.FORCE_DIRECT_PLAY, StreamingQuality.AUTO)

        // Both notices fire, in order: the transcode re-buffer notice and the
        // forced-direct-play fallback notice.
        val expected: List<SessionEvent> = listOf(
            SessionEvent.InformUser("Switched to transcoded stream — re-buffering"),
            SessionEvent.InformUser("Direct Play unavailable for this item — falling back to transcode"),
        )
        assertEquals(expected, events)
        // The fallback path re-runs reportCurrentPlaybackStopped, but the
        // pre-swap report already latched this session — exactly one Stop.
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped("item-1", "server-1", 300_000_000L)
        }
    }

    // ── release: final stop-report dedup + pending-seek join ───────────────

    @Test
    fun release_reportsStopOnce_joinsPendingSeekMirror_andRunsBothTeardownHalves() = runTest {
        var vmTeardownRan = false
        // An explicit seek right before teardown: schedules the coalesced
        // offline-mirror write (500 ms quiet period) and seeds the report
        // position via the fresh seek latch.
        session.seekPersisted(45_000L)
        assertEquals(45_000L, positionStore.persists.single().positionMs)

        session.release(vmTeardownAfterInternals = { vmTeardownRan = true })

        assertTrue(vmTeardownRan, "the caller-supplied VM teardown must run")
        assertTrue(
            hooks.calls.contains("releaseInternalsVmPart"),
            "the VM-owned teardown half must run after the session-owned half",
        )
        // Final Stop goes out on the release scope (real IO dispatcher) — await
        // it with a timeout verification. The fresh seek wins the report position.
        coVerify(timeout = 5_000L, exactly = 1) {
            playbackRepository.reportPlaybackStopped("item-1", "server-1", 450_000_000L)
        }
        // The pending coalesced seek-mirror write was joined and flushed.
        coVerify(timeout = 5_000L, exactly = 1) {
            offlinePlaybackFacade.recordProgress("item-1", 450_000_000L, any(), false)
        }
        assertEquals("server-1", session.stopReportedForSession)
    }

    @Test
    fun release_afterExplicitStopReport_doesNotDoubleReport() = runTest {
        engine.advanceTo(60_000L)
        session.reportCurrentPlaybackStopped()

        session.release(vmTeardownAfterInternals = {})

        // reportCurrentPlaybackStopped already fired for this session; the
        // release-time Stop must be deduped away.
        coVerify(timeout = 5_000L, exactly = 1) {
            playbackRepository.reportPlaybackStopped("item-1", "server-1", 600_000_000L)
        }
    }

    // ── Fakes ───────────────────────────────────────────────────────────────

    private fun resolved(playMethod: PlayMethod) = ResolvedPlayback(
        mediaSourceId = "ms-1",
        streamUrl = "https://jellyfin/stream",
        playMethod = playMethod,
        playSessionId = "server-2",
        maxStreamingBitrate = null,
    )

    /** Records the VM-bound hook invocations the session makes. */
    private class RecordingHooks : SessionLifecycleHooks {
        val calls = mutableListOf<String>()

        override fun rearmTransports() { calls += "rearmTransports" }

        override fun resetForNewItem(selection: MediaStreamSelection) { calls += "resetForNewItem" }

        override fun routeToRemotePlaySession(request: LoadRequest): Boolean = false

        override fun tryReclaimMiniPlayer(itemId: String): MediaEngine? = null

        override fun onMiniPlayerReclaimed() { calls += "onMiniPlayerReclaimed" }

        override fun hydrateReclaimedItem(itemId: String, detail: MediaDetail) {
            calls += "hydrateReclaimedItem"
        }

        override fun releaseMiniPlayerState() { calls += "releaseMiniPlayerState" }

        override fun releaseInternalsVmPart() { calls += "releaseInternalsVmPart" }

        override fun clearTrickplay() { calls += "clearTrickplay" }

        override fun reattachSyncPlay() { calls += "reattachSyncPlay" }

        override fun wasInSyncPlay(): Boolean = false
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

        override fun persist(itemId: String, positionMs: Long, playSessionId: String, nowMs: Long) {
            persists += PersistCall(itemId, positionMs, playSessionId, nowMs)
        }

        override fun savedItemId(): String? = null

        override fun savedPositionMs(): Long? = null

        override fun savedPersistedAtMs(): Long? = null

        override fun savedPlaySessionId(): String? = null
    }
}
