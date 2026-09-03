package com.raulshma.jellyplay.core.data.remote

import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackProgress
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins [RemotePlaybackReporter]'s remotely-initiated session contract:
 *
 * - `startSession` reports `PlaybackStart` for the first item with a fresh
 *   session UUID, marks the audio manager's remote-session flag, and seeds
 *   the progress loop's position from the start ticks.
 * - An empty item list and an unauthenticated account are silent no-ops that
 *   leave the remote-session flag off.
 * - `stopSession` reports `PlaybackStopped` with the last known position and
 *   clears the flag; without an active session it sends nothing. Restarting a
 *   session (start → start) never emits an intermediate stop.
 * - While a remote-playable engine is bound, the progress loop reports its
 *   position (ms → ticks ×10 000) and paused state.
 */
class RemotePlaybackReporterTest {

    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val audioPlaybackManager: AudioPlaybackManager = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk()
    private val activePlayerController: ActivePlayerController = mockk(relaxed = true)

    /** Mirrors the mocked manager's `remoteSessionActive` var for assertions. */
    private var remoteSessionFlag = false

    private val progressReports = mutableListOf<PlaybackProgress>()

    @Before
    fun setUp() {
        every { authRepository.isAuthenticated } returns flowOf(true)
        every { audioPlaybackManager.remoteSessionActive } answers { remoteSessionFlag }
        every { audioPlaybackManager.remoteSessionActive = any<Boolean>() } answers { remoteSessionFlag = firstArg<Boolean>() }
        every { audioPlaybackManager.hasActiveSession } returns false
        every { activePlayerController.engine } returns null
        coEvery { playbackRepository.reportPlaybackStart(any()) } returns Result.success(Unit)
        coEvery { playbackRepository.reportPlaybackProgress(any()) } answers {
            progressReports += firstArg<PlaybackProgress>()
            Result.success(Unit)
        }
        coEvery { playbackRepository.reportPlaybackStopped(any(), any(), any()) } returns Result.success(Unit)
    }

    private fun reporter() = RemotePlaybackReporter(
        playbackRepository = playbackRepository,
        audioPlaybackManager = audioPlaybackManager,
        authRepository = authRepository,
        activePlayerController = activePlayerController,
    )

    private fun captureStart(block: (PlaybackStartInfo) -> Unit) {
        coEvery { playbackRepository.reportPlaybackStart(any()) } answers {
            block(firstArg<PlaybackStartInfo>())
            Result.success(Unit)
        }
    }

    @Test
    fun `startSession reports playback start with a fresh session id`() = runBlocking {
        val reporter = reporter()
        var info: PlaybackStartInfo? = null
        captureStart { info = it }

        reporter.startSession(
            itemIds = listOf("item1", "item2"),
            startPositionTicks = 123_000_000L,
            mediaSourceId = "ms1",
        )

        assertEquals("item1", info?.itemId)
        assertEquals("ms1", info?.mediaSourceId)
        assertEquals(PlayMethod.DIRECT_PLAY, info?.playMethod)
        assertTrue("session id must be a fresh UUID", !info?.sessionId.isNullOrBlank())
        assertTrue(remoteSessionFlag)
        reporter.stopSession()
    }

    @Test
    fun `startSession with an empty item list is a no-op`() = runBlocking {
        val reporter = reporter()

        reporter.startSession(itemIds = emptyList(), startPositionTicks = 0L)

        coVerify(exactly = 0) { playbackRepository.reportPlaybackStart(any()) }
        assertFalse(remoteSessionFlag)
    }

    @Test
    fun `startSession while unauthenticated is a no-op`() = runBlocking {
        val reporter = reporter()
        every { authRepository.isAuthenticated } returns flowOf(false)

        reporter.startSession(itemIds = listOf("item1"), startPositionTicks = 0L)

        coVerify(exactly = 0) { playbackRepository.reportPlaybackStart(any()) }
        assertFalse(remoteSessionFlag)
    }

    @Test
    fun `stopSession reports stopped with the last known position and clears the flag`() = runBlocking {
        val reporter = reporter()
        reporter.startSession(
            itemIds = listOf("item1"),
            startPositionTicks = 55_000_000L,
        )
        assertTrue(remoteSessionFlag)

        reporter.stopSession()

        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped(
                itemId = "item1",
                sessionId = any(),
                positionTicks = 55_000_000L,
            )
        }
        assertFalse(remoteSessionFlag)
    }

    @Test
    fun `stopSession without an active session sends nothing`() = runBlocking {
        val reporter = reporter()

        reporter.stopSession()

        coVerify(exactly = 0) { playbackRepository.reportPlaybackStopped(any(), any(), any()) }
        assertFalse(remoteSessionFlag)
    }

    @Test
    fun `restarting a session never emits an intermediate stop`() = runBlocking {
        val reporter = reporter()
        reporter.startSession(itemIds = listOf("a"), startPositionTicks = 0L)

        reporter.startSession(itemIds = listOf("b"), startPositionTicks = 0L)

        coVerify(exactly = 0) { playbackRepository.reportPlaybackStopped(any(), any(), any()) }
        coVerify(exactly = 2) { playbackRepository.reportPlaybackStart(any()) }
        assertTrue(remoteSessionFlag)
        reporter.stopSession()
    }

    @Test
    fun `the progress loop reports the bound engine position and paused state`() = runBlocking {
        val reporter = reporter()
        val engine = mockk<RemotePlayableEngine>(relaxed = true)
        every { engine.currentPositionMs } returns 5_500L
        every { engine.isPlaying } returns kotlinx.coroutines.flow.MutableStateFlow(true)
        every { activePlayerController.engine } returns engine
        reporter.startSession(itemIds = listOf("item1"), startPositionTicks = 0L)

        awaitNotEmpty(5_000)
        val progress = progressReports.first()

        assertEquals("item1", progress.itemId)
        assertEquals(55_000_000L, progress.positionTicks)
        assertFalse(progress.isPaused)
        assertEquals(PlayMethod.DIRECT_PLAY, progress.playMethod)

        reporter.stopSession()
    }

    private fun awaitNotEmpty(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (progressReports.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
    }
}
