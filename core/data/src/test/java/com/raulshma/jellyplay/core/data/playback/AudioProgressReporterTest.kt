package com.raulshma.jellyplay.core.data.playback

import androidx.media3.exoplayer.ExoPlayer
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.PlaybackProgress
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioProgressReporterTest {

    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var exoPlayer: ExoPlayer
    private var remoteSessionActive = false
    private var currentItemId: String? = "item-1"
    private var playSessionId = "session-1"

    @Before
    fun setUp() {
        playbackRepository = mockk(relaxed = true)
        io.mockk.coEvery { playbackRepository.reportPlaybackProgress(any()) } returns Result.success(Unit)
        io.mockk.coEvery { playbackRepository.reportPlaybackStopped(any(), any(), any()) } returns Result.success(Unit)
        exoPlayer = mockk(relaxed = true)
        remoteSessionActive = false
        currentItemId = "item-1"
        playSessionId = "session-1"
    }

    private fun createReporter(scope: CoroutineScope) = AudioProgressReporter(
        scope = scope,
        playbackRepository = playbackRepository,
        remoteSessionActive = { remoteSessionActive },
        exoPlayerProvider = { exoPlayer },
        itemIdProvider = { currentItemId },
        playSessionIdProvider = { playSessionId },
        playSessionIdSetter = { playSessionId = it }
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
        every { exoPlayer.currentPosition } returns 5_000L
        every { exoPlayer.isPlaying } returns true

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
        every { exoPlayer.currentPosition } returns 15_000L
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
        every { exoPlayer.currentPosition } returns 5_000L
        every { exoPlayer.isPlaying } returns false

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
        every { exoPlayer.currentPosition } returns 6_000L
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
    fun `reportStopped rotates playSessionId and reports stopped if position positive`() = runTest {
        val reporter = createReporter(this)
        every { exoPlayer.currentPosition } returns 12_000L
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
        every { exoPlayer.currentPosition } returns 0L
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
    fun `cancel stops progress reporting loop`() = runTest {
        val reporter = createReporter(this)
        every { exoPlayer.currentPosition } returns 5_000L
        every { exoPlayer.isPlaying } returns true

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
}
