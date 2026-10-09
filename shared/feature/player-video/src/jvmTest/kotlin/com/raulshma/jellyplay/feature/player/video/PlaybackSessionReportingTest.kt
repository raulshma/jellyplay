package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.testfixtures.FakePositionStore
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.ResolvedPlayback
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.testfixtures.FakeMediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.slot
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Conventions: the session's injected scope uses an Unconfined dispatcher so
 * the session's `scope.launch` blocks run synchronously on the test thread;
 * repositories are relaxed mocks and the VM-facing seams
 * ([SessionLifecycleHooks], [SessionPositionStore]) are recording fakes. The
 * session builds its release scope internally ([PlaybackSession.releaseScope],
 * a real IO scope, the C6 composition-root behavior) — the per-test teardown
 * cancels it AFTER its work was verified: that teardown must outlive the
 * caller's scope, so it cannot run on the test dispatcher. The wall clock is
 * injected as a controllable fake ([nowMs]), so the seek-freshness window and
 * the persist throttle are pinned deterministically instead of against real
 * time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionReportingTest {

    /** The construction graph (scheduler-less: the session scope runs on a plain Unconfined dispatcher). */
    private lateinit var graph: PlaybackSessionTestGraph

    /** The injected wall clock's current reading (the graph's controllable fake). */
    private val nowMs get() = graph.nowMs

    private lateinit var playerSessionManager: PlayerSessionManager
    private lateinit var sessionStateFlow: MutableStateFlow<PlayerSessionState>
    private lateinit var engine: FakeMediaEngine
    private lateinit var progressReporter: PlaybackProgressReporter
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var offlinePlaybackFacade: OfflinePlaybackFacade
    private lateinit var hooks: RecordingHooks
    private lateinit var positionStore: FakePositionStore
    private lateinit var session: PlaybackSession

    @kotlin.test.AfterTest
    fun tearDown() {
        // Cancel the session's internally-built release scope AFTER its IO
        // work was verified, then the session scope — the owner's teardown
        // path, as in the VM's onCleared (cancel-after-release ordering).
        if (this::graph.isInitialized) {
            session.releaseScope.cancel()
            graph.sessionScope.cancel()
        }
    }

    @kotlin.test.BeforeTest
    fun setUp() {
        buildSession()
    }

    /**
     * Builds the session under test; `incognito` flips the incognito gate.
     * `mirrorQuality`/`mirrorMode` back the session's ui-mirror getter seams
     * (read only by the decision-time transcode fallback, which has no caller
     * arguments) so a test can make them DISAGREE with the explicit
     * [PlaybackSession.reloadForMode] arguments.
     */
    private fun buildSession(
        incognito: Boolean = false,
        mirrorQuality: StreamingQuality = StreamingQuality.AUTO,
        mirrorMode: PlaybackMode = PlaybackMode.AUTO,
    ) {
        hooks = RecordingHooks()
        graph = PlaybackSessionTestGraph(
            hooks = hooks,
            initialItem = "item-1",
            initialPlaySessionId = "server-1",
        )
        graph.nowMs = 1_000_000L
        engine = graph.engine
        sessionStateFlow = graph.sessionStateFlow
        playerSessionManager = graph.playerSessionManager
        playbackRepository = graph.playbackRepository
        offlinePlaybackFacade = graph.offlinePlaybackFacade
        positionStore = graph.positionStore
        progressReporter = graph.progressReporter
        session = graph.session
        if (incognito) {
            // The incognito gate reads the session's cached preference
            // aggregate (arm-time collector-fed; the suites don't arm, so the
            // test seeds it directly — the graph KDoc's flip-the-gate seam).
            session.cachedAggregate = com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate(
                videoPlayer = com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice(
                    incognitoModeEnabled = true,
                ),
            )
        }
        if (mirrorQuality != StreamingQuality.AUTO || mirrorMode != PlaybackMode.AUTO) {
            graph.uiState.update {
                it.copy(uiPrefs = it.uiPrefs.copy(streamingQuality = mirrorQuality, playbackMode = mirrorMode))
            }
        }
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
        session.lastSeekTimestamp = nowMs // the latch was stamped "just now"

        assertEquals(42_000L, session.getReportPositionMs())
    }

    @Test
    fun getReportPositionMs_staleSeek_fallsBackToEnginePosition() {
        engine.advanceTo(50_000L)
        session.lastSeekPositionMs = 42_000L
        session.lastSeekTimestamp = nowMs - 60_000L // far past the 3 s window

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
        assertEquals(nowMs, persist.nowMs, "the snapshot carries the injected clock's reading")
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

    // ── Failed flag + stalled-finish stop dedup ────────────────────

    @Test
    fun reportCurrentPlaybackStopped_errorLatched_reportsFailedTrue() = runTest {
        every { progressReporter.isErrorLatched() } returns true
        engine.advanceTo(60_000L)

        session.reportCurrentPlaybackStopped()

        // An error-aborted session must carry failed=true so the server
        // skips its own "≥X % = played" rule on the stop.
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped("item-1", "server-1", 600_000_000L, true)
        }
    }

    @Test
    fun reportCurrentPlaybackStopped_errorLatchHeld_reportsFailedFalse() = runTest {
        engine.advanceTo(60_000L)

        session.reportCurrentPlaybackStopped()

        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped("item-1", "server-1", 600_000_000L, false)
        }
    }

    @Test
    fun reportCurrentPlaybackStopped_stalledFinishAlreadyReported_isSkippedAndLatched() = runTest {
        every { progressReporter.hasReportedStopFor("server-1") } returns true
        engine.advanceTo(60_000L)

        session.reportCurrentPlaybackStopped()

        // The reporter already stop-reported this session at the FULL
        // duration (stalled-finish): a second stop at the stalled position
        // would only downgrade it.
        coVerify(exactly = 0) { playbackRepository.reportPlaybackStopped(any(), any(), any(), any()) }
        assertEquals("server-1", session.stopReportedForSession)
    }

    @Test
    fun release_errorLatched_reportsFailedTrue() = runTest {
        every { progressReporter.isErrorLatched() } returns true
        engine.advanceTo(60_000L)

        session.release(vmTeardownAfterInternals = {})

        coVerify(timeout = 5_000L, exactly = 1) {
            playbackRepository.reportPlaybackStopped("item-1", "server-1", 600_000_000L, true)
        }
    }

    @Test
    fun release_stalledFinishAlreadyReported_doesNotDuplicateTheStop() = runTest {
        every { progressReporter.hasReportedStopFor("server-1") } returns true
        engine.advanceTo(60_000L)

        session.release(vmTeardownAfterInternals = {})

        coVerify(timeout = 5_000L, exactly = 0) {
            playbackRepository.reportPlaybackStopped(any(), any(), any(), any())
        }
        assertEquals("server-1", session.stopReportedForSession)
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
            listOf(SessionEvent.InformUserKey(PlayerVideoMessage.TranscodeSwitched))
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
            SessionEvent.InformUserKey(PlayerVideoMessage.TranscodeSwitched),
            SessionEvent.InformUserKey(PlayerVideoMessage.DirectPlayUnavailable),
        )
        assertEquals(expected, events)
        // The fallback path re-runs reportCurrentPlaybackStopped, but the
        // pre-swap report already latched this session — exactly one Stop.
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped("item-1", "server-1", 300_000_000L)
        }
    }

    @Test
    fun reloadForMode_usesTheExplicitModeAndQuality_notTheMirrorGetters() = runTest {
        // The VM's playback-pref setters (setPlaybackMode / setStreamingQuality
        // / setAdaptiveBitrateEnabled, via the applyPlaybackPrefChange command)
        // pass their post-change values EXPLICITLY — the reload used to read
        // mode/quality back from the ui-prefs mirror, which only worked because
        // each setter had written the mirror first. Pin the contract at the
        // session seam (no full-VM harness exists in this module): the
        // mirror-backed getter seams deliberately disagree, and the re-POST
        // must still resolve against the caller's explicit arguments.
        buildSession(
            mirrorQuality = StreamingQuality.SD_480P,
            mirrorMode = PlaybackMode.FORCE_TRANSCODE,
        )
        coEvery { playerSessionManager.reloadPlayback(any(), any(), any(), any()) } returns
            resolved(PlayMethod.DIRECT_PLAY)

        session.reloadForMode(PlaybackMode.FORCE_DIRECT_PLAY, StreamingQuality.UHD_4K)

        // Exactly one re-POST, with the EXPLICIT mode + quality (direct play
        // resolved, so no transcode notice and no fallback chain).
        coVerify(exactly = 1) {
            playerSessionManager.reloadPlayback(
                PlaybackMode.FORCE_DIRECT_PLAY,
                StreamingQuality.UHD_4K,
                30_000L,
                null,
            )
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

    // ── reportPlaybackStart: the server start report (moved from the deleted
    //    VideoSessionHostTest when the fun landed on the session, beside its
    //    stop-report twin) ────────────────────────────────────────────────────

    @kotlin.test.Test
    fun reportPlaybackStart_normalPath_reportsWithTheResolvedPlaySessionId() = runTest {
        coEvery { playbackRepository.reportPlaybackStart(any()) } returns Result.success(Unit)

        session.reportPlaybackStart("item-1", null, PlayMethod.DIRECT_PLAY)

        coVerify(exactly = 1) { playbackRepository.reportPlaybackStart(any()) }
        val slot = io.mockk.slot<PlaybackStartInfo>()
        coVerify(exactly = 1) { playbackRepository.reportPlaybackStart(capture(slot)) }
        assertEquals("item-1", slot.captured.itemId)
        // The server-issued id wins over the locally-allocated UUID fallback.
        assertEquals("server-1", slot.captured.sessionId)
        assertEquals(PlayMethod.DIRECT_PLAY, slot.captured.playMethod)
    }

    @kotlin.test.Test
    fun reportPlaybackStart_withoutAServerIssuedId_fallsBackToTheLocalUuid() = runTest {
        coEvery { playbackRepository.reportPlaybackStart(any()) } returns Result.success(Unit)
        sessionStateFlow.value = PlayerSessionState(currentItemId = "item-1")

        session.reportPlaybackStart("item-1", null, PlayMethod.TRANSCODE)

        val slot = io.mockk.slot<PlaybackStartInfo>()
        coVerify(exactly = 1) { playbackRepository.reportPlaybackStart(capture(slot)) }
        assertEquals(session.playSessionId, slot.captured.sessionId)
    }

    @kotlin.test.Test
    fun reportPlaybackStart_incognitoSkipsTheServerStartReport() = runTest {
        // Rebuild the session with the incognito gate armed.
        buildSession(incognito = true)
        coEvery { playbackRepository.reportPlaybackStart(any()) } returns Result.success(Unit)

        session.reportPlaybackStart("item-1", null, PlayMethod.DIRECT_PLAY)

        coVerify(exactly = 0) { playbackRepository.reportPlaybackStart(any()) }
    }

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

    // [FakePositionStore] is the shared recording double
    // (com.raulshma.jellyplay.core.testfixtures.FakePositionStore) since the
    // fixtures hoist — this file's former private variant (records persists,
    // serves nulls) is its DEFAULT shape (the saved*Value fields stay null).
}
