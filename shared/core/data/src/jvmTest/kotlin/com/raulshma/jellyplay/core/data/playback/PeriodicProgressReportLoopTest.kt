package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackProgress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Virtual-time pins for [PeriodicProgressReportLoop] — the loop core both
 * players' reporters now delegate to. CADENCE, PAUSED DEDUP, both gates and
 * the skip behaviour are pinned HERE once (AudioProgressReporterTest keeps
 * its pins as delegation guards; the idiom — runTest + advanceTimeBy +
 * runCurrent, relaxed [PlaybackRepository] mock — is that suite's).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PeriodicProgressReportLoopTest {

    private lateinit var playbackRepository: PlaybackRepository
    private var currentItemId: String? = "item-1"
    private var playSessionId = "session-1"

    /** Player double: what positionMsProvider/isPlayingProvider report. */
    private var playerPositionMs: Long? = 5_000L
    private var playerPlaying: Boolean = true

    private var gateStart: Boolean = false
    private var cycleGated: Boolean = false

    @BeforeTest
    fun setUp() {
        playbackRepository = mockk(relaxed = true)
        coEvery { playbackRepository.reportPlaybackProgress(any()) } returns Result.success(Unit)
        currentItemId = "item-1"
        playSessionId = "session-1"
        playerPositionMs = 5_000L
        playerPlaying = true
        gateStart = false
        cycleGated = false
    }

    private fun createLoop(
        scope: CoroutineScope,
        reportIntervalMs: Long = PeriodicProgressReportLoop.PROGRESS_REPORT_INTERVAL_MS,
        playMethodProvider: () -> PlayMethod = { PlayMethod.DIRECT_PLAY },
    ) = PeriodicProgressReportLoop(
        scope = scope,
        playbackRepository = playbackRepository,
        positionMsProvider = { playerPositionMs },
        isPlayingProvider = { playerPlaying },
        itemIdProvider = { currentItemId },
        sessionIdProvider = { playSessionId },
        reportIntervalMs = reportIntervalMs,
        gateStart = { gateStart },
        cycleGate = { cycleGated },
        playMethodProvider = playMethodProvider,
    )

    @Test
    fun `reports on the injected cadence carrying item session position and play state`() = runTest {
        val loop = createLoop(this, reportIntervalMs = 40L)
        playerPositionMs = 5_000L
        playerPlaying = true

        loop.start()
        runCurrent()
        advanceTimeBy(80) // two cycles at the injected 40 ms cadence
        runCurrent()

        val progressList = mutableListOf<PlaybackProgress>()
        coVerify(exactly = 2) {
            playbackRepository.reportPlaybackProgress(capture(progressList))
        }
        assertEquals(50_000_000L, progressList[0].positionTicks, "the ms * 10_000 tick math")
        assertEquals(false, progressList[0].isPaused)
        assertEquals("item-1", progressList[0].itemId)
        assertEquals("session-1", progressList[0].sessionId)
        loop.cancel()
    }

    @Test
    fun `playMethod defaults to DIRECT_PLAY and a provider override flows into the row`() = runTest {
        val loop = createLoop(this, reportIntervalMs = 40L)

        loop.start()
        runCurrent()
        advanceTimeBy(40)
        runCurrent()

        val progressList = mutableListOf<PlaybackProgress>()
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackProgress(capture(progressList))
        }
        assertEquals(PlayMethod.DIRECT_PLAY, progressList[0].playMethod)
        loop.cancel()

        // The video reporter overrides the provider with its resolved method.
        val transcodingLoop = createLoop(this, reportIntervalMs = 40L) { PlayMethod.TRANSCODE }
        transcodingLoop.start()
        runCurrent()
        advanceTimeBy(40)
        runCurrent()

        val transcodedRows = mutableListOf<PlaybackProgress>()
        coVerify(exactly = 2) {
            playbackRepository.reportPlaybackProgress(capture(transcodedRows))
        }
        assertEquals(PlayMethod.TRANSCODE, transcodedRows[1].playMethod)
        transcodingLoop.cancel()
    }

    @Test
    fun `skips duplicate paused reports and reports again after a seek while paused`() = runTest {
        val loop = createLoop(this, reportIntervalMs = 40L)
        playerPositionMs = 5_000L
        playerPlaying = false

        loop.start()
        runCurrent()
        advanceTimeBy(40)
        runCurrent()

        val progressList = mutableListOf<PlaybackProgress>()
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackProgress(capture(progressList))
        }
        assertEquals(true, progressList[0].isPaused)

        // Same paused position: deduped.
        advanceTimeBy(40)
        runCurrent()
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackProgress(any())
        }

        // Seek while paused (position changes): reports again.
        playerPositionMs = 6_000L
        advanceTimeBy(40)
        runCurrent()

        val allRows = mutableListOf<PlaybackProgress>()
        coVerify(exactly = 2) {
            playbackRepository.reportPlaybackProgress(capture(allRows))
        }
        assertEquals(60_000_000L, allRows[1].positionTicks)
        assertEquals(true, allRows[1].isPaused)
        loop.cancel()
    }

    @Test
    fun `a playing row resets the paused dedup`() = runTest {
        val loop = createLoop(this, reportIntervalMs = 40L)
        playerPositionMs = 5_000L
        playerPlaying = false

        loop.start()
        runCurrent()
        advanceTimeBy(40)
        runCurrent()
        coVerify(exactly = 1) { playbackRepository.reportPlaybackProgress(any()) }

        // Resumes playing at the SAME position: the playing row flushes AND
        // resets the dedup, so pausing again at the same position reports too.
        playerPlaying = true
        advanceTimeBy(40)
        runCurrent()
        coVerify(exactly = 2) { playbackRepository.reportPlaybackProgress(any()) }

        playerPlaying = false
        advanceTimeBy(40)
        runCurrent()
        coVerify(exactly = 3) { playbackRepository.reportPlaybackProgress(any()) }
        loop.cancel()
    }

    @Test
    fun `cycles without a live player are skipped until one appears`() = runTest {
        val loop = createLoop(this, reportIntervalMs = 40L)
        playerPositionMs = null // no engine/player yet

        loop.start()
        runCurrent()
        advanceTimeBy(120)
        runCurrent()
        coVerify(exactly = 0) {
            playbackRepository.reportPlaybackProgress(any())
        }

        // Player materializes mid-session: the very next cycle reports.
        playerPositionMs = 7_000L
        advanceTimeBy(40)
        runCurrent()

        val progressList = mutableListOf<PlaybackProgress>()
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackProgress(capture(progressList))
        }
        assertEquals(70_000_000L, progressList[0].positionTicks)
        loop.cancel()
    }

    @Test
    fun `skips cycles without a resolvable item id`() = runTest {
        val loop = createLoop(this, reportIntervalMs = 40L)
        currentItemId = null

        loop.start()
        runCurrent()
        advanceTimeBy(120)
        runCurrent()

        coVerify(exactly = 0) {
            playbackRepository.reportPlaybackProgress(any())
        }
        loop.cancel()
    }

    @Test
    fun `start gate makes start a no-op until it clears`() = runTest {
        val loop = createLoop(this, reportIntervalMs = 40L)
        gateStart = true

        loop.start()
        runCurrent()
        advanceTimeBy(120)
        runCurrent()
        coVerify(exactly = 0) {
            playbackRepository.reportPlaybackProgress(any())
        }
        loop.cancel()

        // Gate clears: the next start runs and reports.
        gateStart = false
        loop.start()
        runCurrent()
        advanceTimeBy(40)
        runCurrent()
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackProgress(any())
        }
        loop.cancel()
    }

    @Test
    fun `cycle gate skips cycles without seeding the paused dedup`() = runTest {
        val loop = createLoop(this, reportIntervalMs = 40L)
        playerPositionMs = 5_000L
        playerPlaying = false
        cycleGated = true

        loop.start()
        runCurrent()
        advanceTimeBy(80)
        runCurrent()
        coVerify(exactly = 0) {
            playbackRepository.reportPlaybackProgress(any())
        }

        // Gate lifts at the SAME paused position: the first ungated cycle
        // flushes — the gated cycles must never have seeded the dedup.
        cycleGated = false
        advanceTimeBy(40)
        runCurrent()

        val progressList = mutableListOf<PlaybackProgress>()
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackProgress(capture(progressList))
        }
        assertEquals(true, progressList[0].isPaused)
        loop.cancel()
    }

    @Test
    fun `cancel stops the loop and restart re-seeds the paused dedup`() = runTest {
        val loop = createLoop(this, reportIntervalMs = 40L)
        playerPositionMs = 5_000L
        playerPlaying = false

        loop.start()
        runCurrent()
        advanceTimeBy(80)
        runCurrent()
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackProgress(any()) // one deduped paused row
        }
        loop.cancel()
        advanceTimeBy(80)
        runCurrent()
        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackProgress(any()) // cancelled: nothing further
        }

        // Restart at the SAME paused position: the re-seeded dedup reports again.
        loop.start()
        runCurrent()
        advanceTimeBy(40)
        runCurrent()
        coVerify(exactly = 2) {
            playbackRepository.reportPlaybackProgress(any())
        }
        loop.cancel()
    }
}
