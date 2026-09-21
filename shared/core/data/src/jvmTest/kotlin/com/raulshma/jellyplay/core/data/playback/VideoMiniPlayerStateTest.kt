package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Direct pins for [VideoMiniPlayerState]'s engine-retention choreography on
 * virtual time (the SleepTimerManagerTest setMain idiom): the 5-minute
 * auto-release timeout that frees the native video engine after the user
 * dismisses the mini player, the reclaim identity guard (a different item
 * must never receive someone else's engine), the release reset ladder, and
 * the timeout's cancellation on release/reclaim (no double-release of native
 * resources). The holder previously had no direct test — its semantics were
 * only incidentally exercised through the livetv feature.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoMiniPlayerStateTest {

    /**
     * Hand fake engine: records play/pause/release calls (the assertions are
     * about WHO holds and WHO releases the engine, not about playback) and
     * exposes a writable isPlaying flow the holder's collector mirrors.
     */
    private class FakeEngine(
        var playing: Boolean = false,
        var releaseCount: Int = 0,
        var pauseCount: Int = 0,
    ) : RemotePlayableEngine {
        val playingFlow = MutableStateFlow(playing)
        override val currentPositionMs: Long get() = 0L
        override val isPlaying get() = playingFlow
        override val volume: Float get() = 1f
        override fun play() { playing = true; playingFlow.value = true }
        override fun pause() { pauseCount++; playing = false; playingFlow.value = false }
        override fun stop() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun selectTrack(type: com.raulshma.jellyplay.core.model.TrackType, index: Int) = Unit
        override fun setMaxVideoBitrate(bps: Int?) = Unit
        override fun setVolume(value: Float) = Unit
        override fun increaseVolume(delta: Float) = Unit
        override fun decreaseVolume(delta: Float) = Unit
        override fun setMuted(muted: Boolean) = Unit
        override fun release() { releaseCount++ }
    }

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var state: VideoMiniPlayerState

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        state = VideoMiniPlayerState()
    }

    @AfterTest
    fun tearDown() {
        state.release()
        Dispatchers.resetMain()
    }

    private fun enter(engine: FakeEngine, itemId: String = "item-1") {
        state.enterMiniMode(
            engine = engine,
            itemId = itemId,
            mediaSourceId = "src-1",
            title = "Title",
            subtitle = "Subtitle",
        )
    }

    @Test
    fun `auto release fires at the timeout and releases the engine`() = runTest(testDispatcher.scheduler) {
        val engine = FakeEngine()
        enter(engine)

        // One tick before the 5-minute window: still mini, engine retained.
        advanceTimeBy(5 * 60 * 1000L - 1)
        assertTrue(state.isMiniMode.value, "engine must be retained inside the 5-minute window")
        assertEquals(0, engine.releaseCount)

        advanceTimeBy(1)
        runCurrent()

        assertFalse(state.isMiniMode.value, "auto-release must fire at exactly the timeout")
        assertEquals(1, engine.releaseCount, "the timeout must release the native engine")
        assertNull(state.engine)
    }

    @Test
    fun `re-entering mini mode restarts the timeout window`() = runTest(testDispatcher.scheduler) {
        val first = FakeEngine()
        val second = FakeEngine()
        enter(first)
        advanceTimeBy(3 * 60 * 1000L)
        enter(second) // a new session replaces the old one mid-window

        // 3 more minutes: 6 total since the FIRST enter, but only 3 since the
        // second — the first engine's pending timeout was cancelled and the
        // window restarted, so neither is released yet.
        advanceTimeBy(3 * 60 * 1000L)
        assertTrue(state.isMiniMode.value)
        assertEquals(0, second.releaseCount)

        advanceTimeBy(2 * 60 * 1000L + 1)
        runCurrent()
        assertEquals(1, second.releaseCount, "the restarted window releases the new engine at ITS 5 minutes")
    }

    @Test
    fun `reclaim identity guard - a different item never receives the engine`() = runTest(testDispatcher.scheduler) {
        val engine = FakeEngine()
        enter(engine, itemId = "item-1")

        assertNull(state.tryReclaimEngine("item-other"), "a different item id must not reclaim")
        // The guard is read-only on mismatch: the mini session survives.
        assertTrue(state.isMiniMode.value)
        assertSame(engine, state.engine)

        val reclaimed = state.tryReclaimEngine("item-1")
        assertSame(engine, reclaimed, "the same item reclaims its engine")
        assertEquals(0, engine.releaseCount, "reclaim hands the engine over — it must NOT be released")
        assertFalse(state.isMiniMode.value)
        assertNull(state.engine)
        assertNull(state.itemId.value)

        // Not in mini mode anymore: nothing to reclaim at all.
        assertNull(state.tryReclaimEngine("item-1"))
    }

    @Test
    fun `release resets the whole ladder`() = runTest(testDispatcher.scheduler) {
        val engine = FakeEngine(playing = true)
        enter(engine)
        runCurrent()
        assertTrue(state.isPlaying.value)

        state.release()

        assertEquals(1, engine.releaseCount)
        assertFalse(state.isMiniMode.value)
        assertNull(state.engine)
        assertNull(state.mediaSourceId)
        assertNull(state.itemId.value)
        assertEquals("", state.title.value)
        assertEquals("", state.subtitle.value)
        assertFalse(state.isPlaying.value)
    }

    @Test
    fun `release cancels the pending timeout - the engine is released exactly once`() = runTest(testDispatcher.scheduler) {
        val engine = FakeEngine()
        enter(engine)

        state.release() // manual dismissal before the 5-minute window
        // Far past the timeout: the cancelled timeout job must not fire a
        // second release (double-release of native players is a crash class).
        advanceTimeBy(10 * 60 * 1000L)
        runCurrent()

        assertEquals(1, engine.releaseCount)
    }

    @Test
    fun `togglePlayPause drives the engine and release makes it a no-op`() = runTest(testDispatcher.scheduler) {
        val engine = FakeEngine(playing = true)
        enter(engine)

        state.togglePlayPause()
        assertEquals(1, engine.pauseCount)

        state.release()
        state.togglePlayPause() // no engine held — must not throw
        assertEquals(1, engine.pauseCount)
    }
}
