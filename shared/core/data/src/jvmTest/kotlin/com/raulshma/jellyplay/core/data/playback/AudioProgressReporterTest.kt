package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.PlaybackProgress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pins the promoted (commonMain) [AudioProgressReporter]: the 10 s cadence,
 * the paused-position dedup, the remote-session gate and — the load-bearing
 * one — the STOP ORDERING: the stop call is launched NEVER awaited while the
 * play session id rotates SYNCHRONOUSLY, so the start-report following a
 * transition always carries the fresh id.
 *
 * Moved from the androidHostTest suite (which existed only because the class
 * lived in androidMain and its one Android member was an ExoPlayer provider).
 * The promotion replaced that provider with plain lambdas, so the player
 * double here is two knobs: [playerPositionMs] (null = no live player this
 * cycle) and [playerPlaying].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioProgressReporterTest {

    private lateinit var playbackRepository: PlaybackRepository
    private var remoteSessionActive = false
    private var currentItemId: String? = "item-1"
    private var playSessionId = "session-1"

    /** Player double: what positionMsProvider/isPlayingProvider report. */
    private var playerPositionMs: Long? = 5_000L
    private var playerPlaying: Boolean = true

    @BeforeTest
    fun setUp() {
        playbackRepository = mockk(relaxed = true)
        coEvery { playbackRepository.reportPlaybackProgress(any()) } returns Result.success(Unit)
        coEvery { playbackRepository.reportPlaybackStopped(any(), any(), any()) } returns Result.success(Unit)
        remoteSessionActive = false
        currentItemId = "item-1"
        playSessionId = "session-1"
        playerPositionMs = 5_000L
        playerPlaying = true
    }

    private fun createReporter(
        scope: CoroutineScope,
        reportIntervalMs: Long = AudioProgressReporter.PROGRESS_REPORT_INTERVAL_MS,
    ) = AudioProgressReporter(
        scope = scope,
        playbackRepository = playbackRepository,
        remoteSessionActive = { remoteSessionActive },
        positionMsProvider = { playerPositionMs },
        isPlayingProvider = { playerPlaying },
        itemIdProvider = { currentItemId },
        playSessionIdProvider = { playSessionId },
        playSessionIdSetter = { playSessionId = it },
        reportIntervalMs = reportIntervalMs,
    )

    @Test
    fun `start progress reporting does not run when remote session is active`() = runTest {
        val reporter = createReporter(this)
        remoteSessionActive = true
        reporter.start()

        advanceTimeBy(11_000)
        runCurrent()

        coVerify(exactly = 0) {
            playbackRepository.reportPlaybackProgress(any())
        }
        reporter.cancel()
    }

    @Test
    fun `start progress reporting runs and reports progress every 10 seconds`() = runTest {
        val reporter = createReporter(this)
        playerPositionMs = 5_000L
        playerPlaying = true

        reporter.start()
        runCurrent()

        // Before 10s: no reports
        advanceTimeBy(5_000)
        runCurrent()
        coVerify(exactly = 0) {
            playbackRepository.reportPlaybackProgress(any())
        }

        // At 10s: first report
        advanceTimeBy(5_000)
        runCurrent()

        val progressList1 = mutableListOf<PlaybackProgress>()
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackProgress(capture(progressList1))
        }
        assertEquals(1, progressList1.size)
        assertEquals("item-1", progressList1[0].itemId)
        assertEquals("session-1", progressList1[0].sessionId)
        assertEquals(50_000_000L, progressList1[0].positionTicks)
        assertEquals(false, progressList1[0].isPaused)

        // At 20s: second report
        playerPositionMs = 15_000L
        advanceTimeBy(10_000)
        runCurrent()

        val progressList2 = mutableListOf<PlaybackProgress>()
        coVerify(exactly = 2) {
            playbackRepository.reportPlaybackProgress(capture(progressList2))
        }
        assertEquals(2, progressList2.size)
        assertEquals("item-1", progressList2[1].itemId)
        assertEquals("session-1", progressList2[1].sessionId)
        assertEquals(150_000_000L, progressList2[1].positionTicks)
        assertEquals(false, progressList2[1].isPaused)

        reporter.cancel()
    }

    @Test
    fun `progress reporting loop skips duplicate paused reports`() = runTest {
        val reporter = createReporter(this)
        playerPositionMs = 5_000L
        playerPlaying = false

        reporter.start()
        runCurrent()

        // 10s: first paused report
        advanceTimeBy(10_000)
        runCurrent()

        val progressList1 = mutableListOf<PlaybackProgress>()
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackProgress(capture(progressList1))
        }
        assertEquals(1, progressList1.size)
        assertEquals("item-1", progressList1[0].itemId)
        assertEquals("session-1", progressList1[0].sessionId)
        assertEquals(50_000_000L, progressList1[0].positionTicks)
        assertEquals(true, progressList1[0].isPaused)

        // 20s: second paused report at same position is skipped
        advanceTimeBy(10_000)
        runCurrent()
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackProgress(any())
        }

        // 30s: position changes while paused, it should report again
        playerPositionMs = 6_000L
        advanceTimeBy(10_000)
        runCurrent()

        val progressList2 = mutableListOf<PlaybackProgress>()
        coVerify(exactly = 2) {
            playbackRepository.reportPlaybackProgress(capture(progressList2))
        }
        assertEquals(2, progressList2.size)
        assertEquals("item-1", progressList2[1].itemId)
        assertEquals("session-1", progressList2[1].sessionId)
        assertEquals(60_000_000L, progressList2[1].positionTicks)
        assertEquals(true, progressList2[1].isPaused)

        reporter.cancel()
    }

    @Test
    fun `cycles without a live player are skipped until one appears`() = runTest {
        val reporter = createReporter(this)
        playerPositionMs = null // no engine/player yet
        playerPlaying = true

        reporter.start()
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        coVerify(exactly = 0) {
            playbackRepository.reportPlaybackProgress(any())
        }

        // Player materializes mid-session: the very next cycle reports.
        playerPositionMs = 7_000L
        advanceTimeBy(10_000)
        runCurrent()

        val progressList = mutableListOf<PlaybackProgress>()
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackProgress(capture(progressList))
        }
        assertEquals(70_000_000L, progressList[0].positionTicks)

        reporter.cancel()
    }

    @Test
    fun `injected reportIntervalMs overrides the cadence constant`() = runTest {
        val reporter = createReporter(this, reportIntervalMs = 40L)
        playerPositionMs = 5_000L
        playerPlaying = true

        reporter.start()
        runCurrent()
        advanceTimeBy(80) // two cycles at the injected 40 ms cadence
        runCurrent()

        coVerify(exactly = 2) {
            playbackRepository.reportPlaybackProgress(any())
        }
        reporter.cancel()
    }

    @Test
    fun `reportStopped rotates playSessionId and reports stopped if position positive`() = runTest {
        val reporter = createReporter(this)
        playerPositionMs = 12_000L
        val originalSessionId = playSessionId

        reporter.reportStopped()
        runCurrent()

        val itemIdSlot = slot<String>()
        val sessionIdSlot = slot<String>()
        val positionSlot = slot<Long>()
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped(capture(itemIdSlot), capture(sessionIdSlot), capture(positionSlot))
        }
        assertEquals("item-1", itemIdSlot.captured)
        assertEquals(originalSessionId, sessionIdSlot.captured)
        assertEquals(120_000_000L, positionSlot.captured)

        assertNotEquals(originalSessionId, playSessionId)
        reporter.cancel()
    }

    @Test
    fun `reportStopped rotates the session id synchronously before the launched stop call runs`() = runTest {
        val reporter = createReporter(this)
        playerPositionMs = 12_000L
        val originalSessionId = playSessionId

        // NO runCurrent(): the launched stop call has not run yet, but the
        // rotation must already be visible — the ordering invariant that
        // guarantees the FOLLOWING start-report can never ride the session
        // the stop just used.
        reporter.reportStopped()
        assertNotEquals(originalSessionId, playSessionId)

        runCurrent()

        val sessionIdSlot = slot<String>()
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped(any(), capture(sessionIdSlot), any())
        }
        assertEquals(originalSessionId, sessionIdSlot.captured, "the stop itself still rides the pre-rotation id")
        reporter.cancel()
    }

    @Test
    fun `reportStopped with explicit overrides uses them and rotates session ID`() = runTest {
        val reporter = createReporter(this)
        val originalSessionId = playSessionId

        reporter.reportStopped(
            itemId = "override-item",
            sessionId = "override-session",
            positionTicks = 990_000_000L
        )
        runCurrent()

        val itemIdSlot = slot<String>()
        val sessionIdSlot = slot<String>()
        val positionSlot = slot<Long>()
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped(capture(itemIdSlot), capture(sessionIdSlot), capture(positionSlot))
        }
        assertEquals("override-item", itemIdSlot.captured)
        assertEquals("override-session", sessionIdSlot.captured)
        assertEquals(990_000_000L, positionSlot.captured)

        assertNotEquals(originalSessionId, playSessionId)
        reporter.cancel()
    }

    @Test
    fun `reportStopped does not report stopped if position is zero or negative but still rotates session ID`() = runTest {
        val reporter = createReporter(this)
        playerPositionMs = 0L
        val originalSessionId = playSessionId

        reporter.reportStopped()
        runCurrent()

        coVerify(exactly = 0) {
            playbackRepository.reportPlaybackStopped(any(), any(), any())
        }
        assertNotEquals(originalSessionId, playSessionId)
        reporter.cancel()
    }

    @Test
    fun `reportStopped without a resolvable item id is a full no-op`() = runTest {
        val reporter = createReporter(this)
        currentItemId = null
        val originalSessionId = playSessionId

        reporter.reportStopped()
        runCurrent()

        coVerify(exactly = 0) {
            playbackRepository.reportPlaybackStopped(any(), any(), any())
        }
        assertEquals(originalSessionId, playSessionId, "no item → no stop report AND no rotation")
        reporter.cancel()
    }

    @Test
    fun `stopAndCancel reports the final stop rides the pre-rotation session and rotates synchronously`() = runTest {
        val reporter = createReporter(this)
        playerPositionMs = 12_000L
        val originalSessionId = playSessionId

        // NO runCurrent(): the launched stop call has not run yet, but the
        // teardown rotation must already be visible (the hand-rolled
        // stopAndRelease tails this replaced rotated synchronously too —
        // the next start-report can never ride the stopped session).
        reporter.stopAndCancel()
        assertNotEquals(originalSessionId, playSessionId)

        runCurrent()

        val itemIdSlot = slot<String>()
        val sessionIdSlot = slot<String>()
        val positionSlot = slot<Long>()
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped(capture(itemIdSlot), capture(sessionIdSlot), capture(positionSlot))
        }
        assertEquals("item-1", itemIdSlot.captured)
        assertEquals(originalSessionId, sessionIdSlot.captured, "the final stop still rides the pre-rotation id")
        assertEquals(120_000_000L, positionSlot.captured)
    }

    @Test
    fun `stopAndCancel rotates the session id even when no stop report fires`() = runTest {
        val reporter = createReporter(this)
        currentItemId = null // never played anything
        val originalSessionId = playSessionId

        reporter.stopAndCancel()
        runCurrent()

        coVerify(exactly = 0) {
            playbackRepository.reportPlaybackStopped(any(), any(), any())
        }
        assertNotEquals(
            originalSessionId,
            playSessionId,
            "declared delta vs reportStopped's no-item early return: the teardown tail rotated unconditionally",
        )
    }

    @Test
    fun `stopAndCancel cancels the progress loop`() = runTest {
        val reporter = createReporter(this)
        playerPositionMs = 5_000L
        playerPlaying = true

        reporter.start()
        runCurrent()
        advanceTimeBy(10_000) // first report sent
        runCurrent()

        reporter.stopAndCancel()
        advanceTimeBy(30_000) // loop is dead — no further rows
        runCurrent()

        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackProgress(any())
        }
        coVerify(exactly = 1) {
            // exactly the ONE teardown stop, off the final position.
            playbackRepository.reportPlaybackStopped("item-1", "session-1", 50_000_000L)
        }
    }

    @Test
    fun `cancel stops progress reporting loop`() = runTest {
        val reporter = createReporter(this)
        playerPositionMs = 5_000L
        playerPlaying = true

        reporter.start()
        runCurrent()
        advanceTimeBy(10_000) // first report sent
        runCurrent()

        reporter.cancel()
        advanceTimeBy(10_000) // should be skipped because cancelled
        runCurrent()

        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackProgress(any())
        }
    }

    @Test
    fun `start resets the paused dedup state across restarts`() = runTest {
        val reporter = createReporter(this)
        playerPositionMs = 5_000L
        playerPlaying = false

        reporter.start()
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackProgress(any()) // one deduped paused row
        }
        reporter.cancel()

        // Restart at the SAME paused position: the dedup seed must have been
        // reset, so the first cycle reports again instead of being swallowed.
        reporter.start()
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        coVerify(exactly = 2) {
            playbackRepository.reportPlaybackProgress(any())
        }
        reporter.cancel()
    }

    @Test
    fun `progress reporting loop skips cycles without a resolvable item id`() = runTest {
        val reporter = createReporter(this)
        currentItemId = null
        playerPositionMs = 5_000L
        playerPlaying = true

        reporter.start()
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()

        coVerify(exactly = 0) {
            playbackRepository.reportPlaybackProgress(any())
        }
        assertTrue(playSessionId == "session-1", "no reporting, no rotation")
        reporter.cancel()
    }
}
