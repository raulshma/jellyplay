package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.ui.viewmodel.StateFlowHandle
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before

/**
 * Integration-shaped tests for the reporter's coroutine wiring (position
 * ticks → persisted/threshold callbacks, job cancellation). Ported to jvmTest
 * with the review round — the legacy Robolectric runner was vestigial
 * (no shadows used; mockk + coroutines-test drive everything). The pure
 * decision algorithms are pinned separately by [PlaybackProgressReporterLogicTest].
 *
 * The suite (error latch, genuine-EOF watched marking, stalled-finish
 * detection) lives here too: those behaviors span the tick loop and the
 * session-driven hooks ([PlaybackProgressReporter.onEngineError] /
 * [onGenuineEof]), so they need the real wiring rather than the pure
 * predicates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackProgressReporterTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + testDispatcher)

    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var uiState: StateFlowHandle<VideoPlayerUiState>
    private lateinit var mediaEngine: MediaEngine
    private lateinit var reporter: PlaybackProgressReporter

    private var positionTicks: MutableList<Long> = mutableListOf()
    private var watchedThresholdItemIds: MutableList<String> = mutableListOf()

    /** Injectable wall clock backing the stalled-finish detector. */
    private var nowMs = 1_000L

    @After
    fun tearDownScope() {
        scope.cancel()
    }

    @Before
    fun setUp() {
        playbackRepository = mockk(relaxed = true)
        uiState = StateFlowHandle(MutableStateFlow(VideoPlayerUiState()))
        mediaEngine = mockk(relaxed = true)

        positionTicks.clear()
        watchedThresholdItemIds.clear()
        nowMs = 1_000L

        every { mediaEngine.positionFlow } returns flowOf(0L, 5_000L, 95_000L)
        every { mediaEngine.durationMs } returns 100_000L
        every { mediaEngine.bufferedPositionMs } returns MutableStateFlow(100_000L)
        every { mediaEngine.videoStats } returns MutableStateFlow(EngineVideoStats())
        every { mediaEngine.isPlaying } returns MutableStateFlow(true)
        every { mediaEngine.currentPositionMs } returns 5_000L
        every { mediaEngine.playbackState } returns MutableStateFlow(EnginePlaybackState.READY)
        coEvery { playbackRepository.reportPlaybackStopped(any(), any(), any(), any()) } returns
            Result.success(Unit)

        reporter = buildReporter()
    }

    private fun buildReporter(
        incognito: Boolean = false,
    ) = PlaybackProgressReporter(
        playbackRepository = playbackRepository,
        scope = scope,
        uiState = uiState,
        getCurrentItemId = { "movie-123" },
        getPlaySessionId = { "session-456" },
        getResolvedPlayMethod = { PlayMethod.DIRECT_PLAY },
        getMediaEngine = { mediaEngine },
        getIncognitoModeEnabled = { incognito },
        onAutoSkip = {},
        onPlaybackEndedNoNext = {},
        onWatchedThresholdReached = { watchedThresholdItemIds.add(it) },
        onPositionPersisted = { positionTicks.add(it) },
        onEnginePositionUpdate = { _, _, _, _ -> },
        nowProvider = { nowMs },
    )

    @Test
    fun startPositionTracking_triggersPositionPersistedAndWatchedThreshold() = runTest {
        reporter.startPositionTracking()

        assertTrue(positionTicks.contains(95_000L))
        assertTrue(watchedThresholdItemIds.contains("movie-123"))
    }

    @Test
    fun cancelJobs_cancelsActiveTrackingJobs() {
        reporter.startPositionTracking()
        reporter.startProgressReporting()
        reporter.cancelJobs()
    }

    // ── Error latch suppresses the watched threshold ───────────────

    @Test
    fun watchedThreshold_engineErrorStateAt99Percent_doesNotMarkWatched() = runTest {
        every { mediaEngine.playbackState } returns MutableStateFlow(EnginePlaybackState.ERROR)
        every { mediaEngine.positionFlow } returns flowOf(0L, 99_000L)

        reporter.startPositionTracking()

        assertTrue(positionTicks.contains(99_000L))
        assertTrue(watchedThresholdItemIds.isEmpty(), "an errored engine at 99% must not mark watched")
    }

    @Test
    fun watchedThreshold_showErrorDecisionLatch_suppressesThreshold() = runTest {
        every { mediaEngine.positionFlow } returns flow {
            emit(0L)
            // The session forwards EngineDecision.ShowError (engine errors
            // plus the buffering watchdog — the paths the tick's own state
            // read can miss while jobs are cancelled between load attempts).
            reporter.onEngineError()
            emit(96_000L) // past 95%, but the latch holds for this item
        }

        reporter.startPositionTracking()

        assertTrue(watchedThresholdItemIds.isEmpty())
    }

    @Test
    fun errorLatch_recoveryViaSuccessfulRestart_rearmsThreshold() = runTest {
        every { mediaEngine.positionFlow } returns flow {
            emit(0L)
            reporter.onEngineError()
            emit(96_000L) // past 95% while latched: suppressed
        }
        reporter.startPositionTracking()
        assertTrue(watchedThresholdItemIds.isEmpty())
        assertTrue(reporter.isErrorLatched())

        // Playback recovers (retry / transcode fallback funnel through
        // startPositionTracking): the latch clears and the threshold re-arms.
        every { mediaEngine.positionFlow } returns flowOf(96_000L)
        reporter.startPositionTracking()

        assertFalse(reporter.isErrorLatched())
        assertEquals(listOf("movie-123"), watchedThresholdItemIds)
    }

    // ── Genuine-EOF counts as watched ──────────────────────────────

    @Test
    fun genuineEof_belowThreshold_marksWatched() = runTest {
        every { mediaEngine.positionFlow } returns flowOf(0L, 50_000L)
        reporter.startPositionTracking()
        assertTrue(watchedThresholdItemIds.isEmpty())

        reporter.onGenuineEof()

        assertEquals(listOf("movie-123"), watchedThresholdItemIds)
    }

    @Test
    fun genuineEof_afterThreshold_doesNotFireTwice() = runTest {
        reporter.startPositionTracking() // flow ends at 95% → threshold fired
        assertEquals(listOf("movie-123"), watchedThresholdItemIds)

        reporter.onGenuineEof()

        assertEquals(listOf("movie-123"), watchedThresholdItemIds)
    }

    @Test
    fun genuineEof_errorLatched_isSuppressed() = runTest {
        reporter.onEngineError()

        reporter.onGenuineEof()

        assertTrue(watchedThresholdItemIds.isEmpty())
    }

    // ── Stalled-finish detection ───────────────────────────────────

    @Test
    fun stalledAtEnd_firesThresholdAndReportsStopAtFullDuration() = runTest {
        every { mediaEngine.positionFlow } returns flow {
            emit(98_500L) // inside the last 2 s, baseline starts
            nowMs += STALLED_FINISH_NO_ADVANCE_MS + 500L
            emit(98_500L) // position frozen for > 10 s while "playing"
        }

        reporter.startPositionTracking()

        assertEquals(listOf("movie-123"), watchedThresholdItemIds)
        assertTrue(reporter.hasReportedStopFor("session-456"))
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped("movie-123", "session-456", 100_000L * 10_000L, false)
        }
    }

    @Test
    fun stalledAtEnd_firesOnlyOnce() = runTest {
        every { mediaEngine.positionFlow } returns flow {
            emit(98_500L)
            nowMs += STALLED_FINISH_NO_ADVANCE_MS + 500L
            emit(98_500L)
            nowMs += STALLED_FINISH_NO_ADVANCE_MS
            emit(98_500L)
        }

        reporter.startPositionTracking()

        assertEquals(1, watchedThresholdItemIds.size)
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped(any(), any(), any(), any())
        }
    }

    @Test
    fun stalledAtEnd_paused_doesNotComplete() = runTest {
        every { mediaEngine.positionFlow } returns flow {
            emit(98_500L)
            nowMs += STALLED_FINISH_NO_ADVANCE_MS + 500L
            emit(98_500L)
        }
        every { mediaEngine.isPlaying } returns MutableStateFlow(false)

        reporter.startPositionTracking()

        // A paused engine is not a stall: no completion stop. (The 95%
        // percentage threshold may still have fired on its own rule — that
        // is pre-existing behavior the stall detector does not change.)
        assertFalse(reporter.hasReportedStopFor("session-456"))
        coVerify(exactly = 0) { playbackRepository.reportPlaybackStopped(any(), any(), any(), any()) }
    }

    @Test
    fun stalledAtEnd_errorState_doesNotComplete() = runTest {
        every { mediaEngine.positionFlow } returns flow {
            emit(98_500L)
            nowMs += STALLED_FINISH_NO_ADVANCE_MS + 500L
            emit(98_500L)
        }
        every { mediaEngine.playbackState } returns MutableStateFlow(EnginePlaybackState.ERROR)

        reporter.startPositionTracking()

        assertTrue(watchedThresholdItemIds.isEmpty())
        coVerify(exactly = 0) { playbackRepository.reportPlaybackStopped(any(), any(), any(), any()) }
    }

    @Test
    fun stalledAtEnd_errorDecisionLatched_doesNotComplete() = runTest {
        every { mediaEngine.positionFlow } returns flow {
            emit(98_500L)
            reporter.onEngineError() // the error surfaces after the baseline
            nowMs += STALLED_FINISH_NO_ADVANCE_MS + 500L
            emit(98_500L)
        }

        reporter.startPositionTracking()

        // The latch arrived after the position already crossed 95% (the
        // percentage rule fired on its own); the stall detector itself must
        // complete nothing while latched.
        assertFalse(reporter.hasReportedStopFor("session-456"))
        coVerify(exactly = 0) { playbackRepository.reportPlaybackStopped(any(), any(), any(), any()) }
    }

    @Test
    fun stalledAtEnd_positionStillAdvancing_doesNotComplete() = runTest {
        every { mediaEngine.positionFlow } returns flow {
            emit(98_500L)
            nowMs += STALLED_FINISH_NO_ADVANCE_MS + 500L
            emit(98_600L) // advanced — not a stall
            nowMs += STALLED_FINISH_NO_ADVANCE_MS + 500L
            emit(98_700L)
        }

        reporter.startPositionTracking()

        assertFalse(reporter.hasReportedStopFor("session-456"))
        coVerify(exactly = 0) { playbackRepository.reportPlaybackStopped(any(), any(), any(), any()) }
    }

    @Test
    fun stalledPosition_outsideTwoSecondWindow_doesNotComplete() = runTest {
        every { mediaEngine.positionFlow } returns flow {
            emit(97_000L) // 3 s before the end — outside the window
            nowMs += STALLED_FINISH_NO_ADVANCE_MS * 3
            emit(97_000L)
        }

        reporter.startPositionTracking()

        // 3 s before the end is outside the detector's window — no
        // completion stop however long the position freezes there.
        assertFalse(reporter.hasReportedStopFor("session-456"))
        coVerify(exactly = 0) { playbackRepository.reportPlaybackStopped(any(), any(), any(), any()) }
    }

    @Test
    fun stalledAtEnd_noAdvanceForLessThan10s_doesNotComplete() = runTest {
        every { mediaEngine.positionFlow } returns flow {
            emit(98_500L)
            nowMs += STALLED_FINISH_NO_ADVANCE_MS - 1L // just under the window
            emit(98_500L)
        }

        reporter.startPositionTracking()

        assertFalse(reporter.hasReportedStopFor("session-456"))
        coVerify(exactly = 0) { playbackRepository.reportPlaybackStopped(any(), any(), any(), any()) }
    }

    @Test
    fun stalledAtEnd_bufferingState_completes() = runTest {
        // A starved cache is what an end-of-file stall looks like: the
        // engine sits in BUFFERING while the position freezes near the end.
        every { mediaEngine.playbackState } returns MutableStateFlow(EnginePlaybackState.BUFFERING)
        every { mediaEngine.positionFlow } returns flow {
            emit(99_000L)
            nowMs += STALLED_FINISH_NO_ADVANCE_MS + 500L
            emit(99_000L)
        }

        reporter.startPositionTracking()

        assertEquals(listOf("movie-123"), watchedThresholdItemIds)
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped("movie-123", "session-456", 100_000L * 10_000L, false)
        }
    }

    @Test
    fun stalledAtEnd_incognito_marksWatchedButNeverReportsStop() = runTest {
        reporter = buildReporter(incognito = true)
        every { mediaEngine.positionFlow } returns flow {
            emit(98_500L)
            nowMs += STALLED_FINISH_NO_ADVANCE_MS + 500L
            emit(98_500L)
        }

        reporter.startPositionTracking()

        assertEquals(listOf("movie-123"), watchedThresholdItemIds)
        assertFalse(reporter.hasReportedStopFor("session-456"))
        coVerify(exactly = 0) { playbackRepository.reportPlaybackStopped(any(), any(), any(), any()) }
    }
}
