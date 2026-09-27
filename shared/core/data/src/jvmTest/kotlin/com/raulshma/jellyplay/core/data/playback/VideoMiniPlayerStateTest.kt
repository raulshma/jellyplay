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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Direct pins for [VideoMiniPlayerState]'s engine-retention choreography on
 * virtual time (the former SleepTimerManagerTest setMain idiom): the 5-minute
 * auto-release timeout that frees the native video engine after the user
 * dismisses the mini player, the reclaim identity guard (a different item
 * must never receive someone else's engine), the typed-reclaim capability
 * (the deposit-time [VideoMiniPlayerState.enterMiniMode] registration that
 * backs [VideoMiniPlayerState.tryReclaimMediaEngine] — no reclaim-site
 * downcast), the release reset ladder, and the timeout's cancellation on
 * release/reclaim (no double-release of native resources). The holder
 * previously had no direct test — its semantics were only incidentally
 * exercised through the livetv feature.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoMiniPlayerStateTest {

    /**
     * Hand fake engine: records play/pause/release calls (the assertions are
     * about WHO holds and WHO releases the engine, not about playback) and
     * exposes a writable isPlaying flow the holder's collector mirrors.
     */
    private open class FakeEngine(
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
        override fun setVolume(value: Float, isUserChange: Boolean) = Unit
        override fun increaseVolume(delta: Float) = Unit
        override fun decreaseVolume(delta: Float) = Unit
        override fun setMuted(muted: Boolean) = Unit
        override fun release() { releaseCount++ }
    }

    /**
     * The typed view the video feature's deposit registers: the depositor
     * holds its engine AS a [MediaEngine] and vouches for that fact by
     * returning the same instance — the capability, not a cast. The members
     * beyond [FakeEngine]'s remote-control surface are inert stubs; the
     * assertions are about WHO holds and WHO releases the engine.
     */
    private class TypedEngine : FakeEngine(), com.raulshma.jellyplay.feature.player.video.engine.MediaEngine {
        override val displayName: String get() = "typed"
        override fun load(request: com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest) = Unit
        override val playbackSpeed: Float get() = 1f
        override fun setPlaybackSpeed(speed: Float) = Unit
        override val playbackState: StateFlow<com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState> =
            MutableStateFlow(com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.IDLE)
        override val durationMs: Long get() = 0L
        override val positionFlow: kotlinx.coroutines.flow.Flow<Long> = kotlinx.coroutines.flow.emptyFlow()
        override val errorFlow: kotlinx.coroutines.flow.Flow<com.raulshma.jellyplay.feature.player.video.engine.EngineError> =
            kotlinx.coroutines.flow.emptyFlow()
        override val subtitleEvents: kotlinx.coroutines.flow.Flow<com.raulshma.jellyplay.feature.player.video.engine.SubtitleEvent> =
            kotlinx.coroutines.flow.emptyFlow()
        override val bufferedPositionMs: StateFlow<Long> = MutableStateFlow(0L)
        override val videoStats: StateFlow<com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats> =
            MutableStateFlow(com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats())
        override val currentCues: StateFlow<List<com.raulshma.jellyplay.feature.player.video.engine.TimedCue>> =
            MutableStateFlow(emptyList())
        override val liveSubtitleCue: StateFlow<CharSequence?> = MutableStateFlow(null)
        override val pollingIntervalMs: StateFlow<Long> = MutableStateFlow(1_000L)
        override val videoStatsEnabled: StateFlow<Boolean> = MutableStateFlow(false)
        override fun setPollingIntervalMs(ms: Long) = Unit
        override fun setVideoStatsEnabled(enabled: Boolean) = Unit
        override val audioSessionId: Int get() = 0
        override val capabilities: com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
            get() = com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities()
        override fun updateConfig(config: com.raulshma.jellyplay.feature.player.video.engine.EngineConfig) = Unit
        override val availableTracks: StateFlow<List<com.raulshma.jellyplay.feature.player.video.engine.MediaTrack>> =
            MutableStateFlow(emptyList())
        override fun applySubtitleStyle(style: com.raulshma.jellyplay.core.model.SubtitleStyle) = Unit
        override fun setAspectRatio(ratio: com.raulshma.jellyplay.feature.player.video.engine.AspectRatio) = Unit
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

    /**
     * Deposit WITH the typed-reclaim capability — the video feature's shape:
     * the depositor passes the typed view of the same instance it deposits.
     */
    private fun enterTyped(engine: TypedEngine, itemId: String = "item-1") {
        state.enterMiniMode(
            engine = engine,
            itemId = itemId,
            mediaSourceId = "src-1",
            title = "Title",
            subtitle = "Subtitle",
            asMediaEngine = { engine },
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
    fun `typed reclaim hands back the registered MediaEngine and pops the session`() = runTest(testDispatcher.scheduler) {
        val engine = TypedEngine()
        enterTyped(engine)

        // A different item never reclaims — typed or broad, same guard.
        assertNull(state.tryReclaimMediaEngine("item-other"), "a different item id must not reclaim (typed)")
        assertTrue(state.isMiniMode.value, "the mismatched typed reclaim must not consume the deposit")

        val reclaimed = state.tryReclaimMediaEngine("item-1")
        assertSame(engine, reclaimed, "the typed reclaim returns the registered instance — no downcast")
        assertEquals(0, engine.releaseCount, "reclaim hands the engine over — it must NOT be released")
        assertFalse(state.isMiniMode.value)
        assertNull(state.engine)

        // Not in mini mode anymore: nothing to reclaim at all.
        assertNull(state.tryReclaimMediaEngine("item-1"))
    }

    @Test
    fun `typed reclaim without the capability returns null and leaves the deposit intact`() = runTest(testDispatcher.scheduler) {
        // A broad deposit (no registered capability — a foreign depositor's
        // shape): the typed reclaim must NOT pop the session, because the
        // popped engine would be dropped without ever being released.
        val engine = FakeEngine()
        enter(engine)

        assertNull(state.tryReclaimMediaEngine("item-1"), "no registered capability means no typed reclaim")
        assertTrue(state.isMiniMode.value, "the deposit must survive the capability miss")
        assertSame(engine, state.engine)

        // The broad seam still reclaims it afterwards.
        assertSame(engine, state.tryReclaimEngine("item-1"))
    }

    @Test
    fun `re-entering broad overwrites the capability - typed reclaim no longer fires`() = runTest(testDispatcher.scheduler) {
        val typed = TypedEngine()
        enterTyped(typed)

        val broad = FakeEngine()
        enter(broad) // a new broad deposit replaces the typed one mid-window

        assertNull(state.tryReclaimMediaEngine("item-1"), "the overwrite cleared the typed capability")
        assertTrue(state.isMiniMode.value, "and again must not consume the deposit")
        assertSame(broad, state.tryReclaimEngine("item-1"))
        assertEquals(0, typed.releaseCount, "the replaced engine is dropped unreleased — the existing re-enter semantics")
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
