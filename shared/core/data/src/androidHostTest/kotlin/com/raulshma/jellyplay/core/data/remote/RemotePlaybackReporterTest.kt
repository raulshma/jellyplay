package com.raulshma.jellyplay.core.data.remote

import com.raulshma.jellyplay.core.data.playback.AudioNowPlayingFace
import com.raulshma.jellyplay.core.data.playback.NowPlayingVerdict
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
 *   session UUID, marks the verdict's remote-session cell, and seeds
 *   the progress loop's position from the start ticks.
 * - An empty item list and an unauthenticated account are silent no-ops that
 *   leave the remote-session cell off.
 * - `stopSession` reports `PlaybackStopped` with the last known position and
 *   clears the cell; without an active session it sends nothing. Restarting a
 *   session (start → start) never emits an intermediate stop.
 * - While a remote-playable engine is bound, the progress loop reports its
 *   position (ms → ticks ×10 000) and paused state.
 *
 * The reporter's poll face is the [NowPlayingVerdict] now (the engine-vs-
 * manager fork moved there verbatim), so the harness drives a REAL verdict
 * over a fake [AudioNowPlayingFace] + the mocked engine registry — the
 * folds themselves are pinned once in NowPlayingVerdictTest, and these ten
 * tests keep pinning the REPORTING behavior on top of them.
 */
class RemotePlaybackReporterTest {

    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk()
    private val activePlayerController: ActivePlayerController = mockk(relaxed = true)

    /** The verdict's audio face — the former mocked manager's poll inputs. */
    private val audioFace = FakeAudioFace()

    private val verdict = NowPlayingVerdict(
        videoEngines = activePlayerController,
        audio = audioFace,
    )

    private val progressReports = mutableListOf<PlaybackProgress>()

    @Before
    fun setUp() {
        every { authRepository.isAuthenticated } returns MutableStateFlow(true)
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
        nowPlayingVerdict = verdict,
        authRepository = authRepository,
        applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
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
        assertTrue(verdict.remoteSessionActive.value)
        reporter.stopSession()
    }

    @Test
    fun `startSession with an empty item list is a no-op`() = runBlocking {
        val reporter = reporter()

        reporter.startSession(itemIds = emptyList(), startPositionTicks = 0L)

        coVerify(exactly = 0) { playbackRepository.reportPlaybackStart(any()) }
        assertFalse(verdict.remoteSessionActive.value)
    }

    @Test
    fun `startSession while unauthenticated is a no-op`() = runBlocking {
        val reporter = reporter()
        every { authRepository.isAuthenticated } returns MutableStateFlow(false)

        reporter.startSession(itemIds = listOf("item1"), startPositionTicks = 0L)

        coVerify(exactly = 0) { playbackRepository.reportPlaybackStart(any()) }
        assertFalse(verdict.remoteSessionActive.value)
    }

    @Test
    fun `stopSession reports stopped with the last known position and clears the flag`() = runBlocking {
        val reporter = reporter()
        reporter.startSession(
            itemIds = listOf("item1"),
            startPositionTicks = 55_000_000L,
        )
        assertTrue(verdict.remoteSessionActive.value)

        reporter.stopSession()

        coVerify(exactly = 1) {
            playbackRepository.reportPlaybackStopped(
                itemId = "item1",
                sessionId = any(),
                positionTicks = 55_000_000L,
            )
        }
        assertFalse(verdict.remoteSessionActive.value)
    }

    @Test
    fun `stopSession without an active session sends nothing`() = runBlocking {
        val reporter = reporter()

        reporter.stopSession()

        coVerify(exactly = 0) { playbackRepository.reportPlaybackStopped(any(), any(), any()) }
        assertFalse(verdict.remoteSessionActive.value)
    }

    @Test
    fun `restarting a session never emits an intermediate stop`() = runBlocking {
        val reporter = reporter()
        reporter.startSession(itemIds = listOf("a"), startPositionTicks = 0L)

        reporter.startSession(itemIds = listOf("b"), startPositionTicks = 0L)

        coVerify(exactly = 0) { playbackRepository.reportPlaybackStopped(any(), any(), any()) }
        coVerify(exactly = 2) { playbackRepository.reportPlaybackStart(any()) }
        assertTrue(verdict.remoteSessionActive.value)
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

    @Test
    fun `the progress loop falls back to the audio manager when no engine is bound`() = runBlocking {
        // Pure remote-audio session (no cast/engine surface): the loop reads
        // the audio face's position ×10 000 into ticks.
        val reporter = reporter()
        audioFace.hasActiveSession = true
        audioFace._currentPosition.value = 2_500L
        audioFace._isPlaying.value = true

        reporter.startSession(itemIds = listOf("item1"), startPositionTicks = 0L)

        awaitNotEmpty(5_000)
        val progress = progressReports.first()

        assertEquals("item1", progress.itemId)
        assertEquals(25_000_000L, progress.positionTicks)
        assertFalse(progress.isPaused)

        reporter.stopSession()
    }

    @Test
    fun `the progress loop reports paused while the bound engine is not playing`() = runBlocking {
        val reporter = reporter()
        val engine = mockk<RemotePlayableEngine>(relaxed = true)
        every { engine.currentPositionMs } returns 1_000L
        every { engine.isPlaying } returns kotlinx.coroutines.flow.MutableStateFlow(false)
        every { activePlayerController.engine } returns engine

        reporter.startSession(itemIds = listOf("item1"), startPositionTicks = 0L)

        awaitNotEmpty(5_000)
        assertTrue(progressReports.first().isPaused)

        reporter.stopSession()
    }

    @Test
    fun `the progress loop stays silent while no session and no engine report a position`() = runBlocking {
        // Neither an engine nor an active audio session: the verdict's
        // progressTicksAndPaused is null and no progress report goes out.
        val reporter = reporter()
        audioFace.hasActiveSession = false

        reporter.startSession(itemIds = listOf("item1"), startPositionTicks = 0L)

        Thread.sleep(500)
        assertTrue(progressReports.isEmpty())
        // The START still went out — only the progress loop is gated.
        coVerify(exactly = 1) { playbackRepository.reportPlaybackStart(any()) }

        reporter.stopSession()
    }

    private fun awaitNotEmpty(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (progressReports.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
    }

    /** Mutable stand-in for the audio family's read face. */
    private class FakeAudioFace : AudioNowPlayingFace {
        val _isPlaying = MutableStateFlow(false)
        override val isPlaying: StateFlow<Boolean> = _isPlaying
        val _currentPosition = MutableStateFlow(0L)
        override val currentPosition: StateFlow<Long> = _currentPosition
        override val currentPlayingItemId = MutableStateFlow<String?>(null)
        override val title = MutableStateFlow("")
        override var hasActiveSession: Boolean = false
    }
}
