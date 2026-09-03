package com.raulshma.jellyplay.core.data.remote

import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.core.model.remote.GeneralCommand
import com.raulshma.jellyplay.core.model.remote.PlayRequest
import com.raulshma.jellyplay.core.model.remote.PlaystateCommand
import com.raulshma.jellyplay.core.model.remote.PlaybackDomain
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Pins the [VideoRemoteControlDispatcher] routing invariant: every engine
 * touch is marshalled to the main thread (`withContext(Main.immediate)`) and
 * routed to the engine currently registered in [ActivePlayerController]; with
 * **no engine bound** playstate/general commands are silently dropped (no
 * crash, no engine calls). A remote Stop is terminal: `engine.stop()` **and**
 * a [NavigationTarget.ClosePlayer] navigation request. A fresh "Play" never
 * touches the engine — it only emits [NavigationTarget.OpenVideoPlayer] with
 * the full request payload passed through.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoRemoteControlDispatcherTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeEngine(initialPlaying: Boolean = false) : RemotePlayableEngine {
        override val currentPositionMs: Long = 0L
        override val isPlaying: MutableStateFlow<Boolean> = MutableStateFlow(initialPlaying)
        override val underlyingPlayer: Any? = null

        // Backing field + val override: a `var` would generate a JVM
        // setVolume(Float) clashing with the interface method.
        private var volumeField: Float = 1f
        override val volume: Float get() = volumeField

        /** Test hook that mutates volume without recording a call. */
        fun forceVolume(value: Float) { volumeField = value }

        val calls = mutableListOf<String>()

        override fun play() { calls += "play"; isPlaying.value = true }
        override fun pause() { calls += "pause"; isPlaying.value = false }
        override fun stop() { calls += "stop" }
        override fun seekTo(positionMs: Long) { calls += "seekTo:$positionMs" }
        override fun selectTrack(type: TrackType, index: Int) { calls += "selectTrack:${type.name}:$index" }
        override fun setMaxVideoBitrate(bps: Int?) { calls += "setMaxVideoBitrate:$bps" }
        override fun setVolume(value: Float) { volumeField = value; calls += "setVolume:$value" }
        override fun increaseVolume(delta: Float) { volumeField += delta; calls += "increaseVolume:$delta" }
        override fun decreaseVolume(delta: Float) { volumeField -= delta; calls += "decreaseVolume:$delta" }
        override fun setMuted(muted: Boolean) { calls += "setMuted:$muted" }
        override fun release() { calls += "release" }
    }

    private val activePlayerController = ActivePlayerController()
    private val bridge = RemoteNavigationBridge()

    private fun dispatcher() = VideoRemoteControlDispatcher(
        activePlayerController = activePlayerController,
        remoteNavigationBridge = bridge,
    )

    private fun bindEngine(initialPlaying: Boolean = false): FakeEngine {
        val engine = FakeEngine(initialPlaying)
        activePlayerController.bindEngine(engine)
        return engine
    }

    // ---- domain ------------------------------------------------------------

    @Test
    fun `domain is VIDEO`() {
        assertEquals(PlaybackDomain.VIDEO, dispatcher().domain)
    }

    // ---- play ----------------------------------------------------------------

    @Test
    fun `play emits OpenVideoPlayer with full request payload and touches no engine`() = runTest(mainDispatcherRule.testDispatcher) {
        val engine = bindEngine()
        val targets = mutableListOf<NavigationTarget>()
        val collector = launch(UnconfinedTestDispatcher(mainDispatcherRule.testDispatcher.scheduler)) {
            bridge.targets.collect { targets += it }
        }

        dispatcher().play(
            PlayRequest(
                itemIds = listOf("movie-1"),
                startPositionTicks = 1_234_567L,
                mediaSourceId = "ms-9",
                audioStreamIndex = 2,
                subtitleStreamIndex = 5,
            ),
        )

        collector.cancel()
        assertEquals(
            listOf(
                NavigationTarget.OpenVideoPlayer(
                    itemId = "movie-1",
                    mediaSourceId = "ms-9",
                    startPositionTicks = 1_234_567L,
                    audioStreamIndex = 2,
                    subtitleStreamIndex = 5,
                ),
            ),
            targets,
        )
        // "Play" only navigates; the engine is driven by the player UI, not here.
        assertTrue(engine.calls.isEmpty())
    }

    @Test
    fun `play with empty itemIds emits nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        bindEngine()
        val targets = mutableListOf<NavigationTarget>()
        val collector = launch(UnconfinedTestDispatcher(mainDispatcherRule.testDispatcher.scheduler)) {
            bridge.targets.collect { targets += it }
        }

        dispatcher().play(PlayRequest(itemIds = emptyList()))

        collector.cancel()
        assertTrue(targets.isEmpty())
    }

    // ---- playstate with no engine bound ---------------------------------------

    @Test
    fun `playstate with no engine bound is a silent no-op`() = runTest(mainDispatcherRule.testDispatcher) {
        // Nothing bound — must not throw.
        dispatcher().handlePlaystate(PlaystateCommand.Pause)
        dispatcher().handlePlaystate(PlaystateCommand.Stop)
    }

    // ---- playstate routing -----------------------------------------------------

    @Test
    fun `Stop stops the engine and requests ClosePlayer`() = runTest(mainDispatcherRule.testDispatcher) {
        val engine = bindEngine()
        val targets = mutableListOf<NavigationTarget>()
        val collector = launch(UnconfinedTestDispatcher(mainDispatcherRule.testDispatcher.scheduler)) {
            bridge.targets.collect { targets += it }
        }

        dispatcher().handlePlaystate(PlaystateCommand.Stop)

        collector.cancel()
        assertEquals(listOf("stop"), engine.calls)
        assertEquals(listOf<NavigationTarget>(NavigationTarget.ClosePlayer), targets)
    }

    @Test
    fun `Pause pauses and Unpause plays`() = runTest(mainDispatcherRule.testDispatcher) {
        val engine = bindEngine()

        dispatcher().handlePlaystate(PlaystateCommand.Pause)
        dispatcher().handlePlaystate(PlaystateCommand.Unpause)

        assertEquals(listOf("pause", "play"), engine.calls)
    }

    @Test
    fun `PlayPause toggles based on engine isPlaying`() = runTest(mainDispatcherRule.testDispatcher) {
        val playingEngine = bindEngine(initialPlaying = true)
        dispatcher().handlePlaystate(PlaystateCommand.PlayPause)
        assertEquals(listOf("pause"), playingEngine.calls)

        activePlayerController.clearEngine()
        val pausedEngine = bindEngine(initialPlaying = false)
        dispatcher().handlePlaystate(PlaystateCommand.PlayPause)
        assertEquals(listOf("play"), pausedEngine.calls)
    }

    @Test
    fun `Seek converts ticks to milliseconds`() = runTest(mainDispatcherRule.testDispatcher) {
        val engine = bindEngine()

        dispatcher().handlePlaystate(PlaystateCommand.Seek(positionTicks = 123_456L))

        assertEquals(listOf("seekTo:12"), engine.calls)
    }

    @Test
    fun `queue and rewind commands are no-ops for single-item video`() = runTest(mainDispatcherRule.testDispatcher) {
        val engine = bindEngine()

        dispatcher().handlePlaystate(PlaystateCommand.NextTrack)
        dispatcher().handlePlaystate(PlaystateCommand.PreviousTrack)
        dispatcher().handlePlaystate(PlaystateCommand.Rewind)
        dispatcher().handlePlaystate(PlaystateCommand.FastForward)

        assertTrue(engine.calls.isEmpty())
    }

    // ---- general commands ------------------------------------------------------

    @Test
    fun `SetVolume coerces to 0to1 and only mutes on explicit mute`() = runTest(mainDispatcherRule.testDispatcher) {
        val engine = bindEngine()

        dispatcher().handleGeneral(GeneralCommand.SetVolume(volume0to100 = 150, mute = true))
        assertEquals(listOf("setVolume:1.0", "setMuted:true"), engine.calls)

        engine.calls.clear()
        dispatcher().handleGeneral(GeneralCommand.SetVolume(volume0to100 = 40, mute = null))
        assertEquals(listOf("setVolume:0.4"), engine.calls)
    }

    @Test
    fun `volume up down and mute toggles delegate to the engine`() = runTest(mainDispatcherRule.testDispatcher) {
        val engine = bindEngine()

        dispatcher().handleGeneral(GeneralCommand.VolumeUp)
        dispatcher().handleGeneral(GeneralCommand.VolumeDown)
        dispatcher().handleGeneral(GeneralCommand.Mute)
        dispatcher().handleGeneral(GeneralCommand.Unmute)

        assertEquals(
            listOf("increaseVolume:0.05", "decreaseVolume:0.05", "setMuted:true", "setMuted:false"),
            engine.calls,
        )
    }

    @Test
    fun `ToggleMute un-mutes only when volume is zero`() = runTest(mainDispatcherRule.testDispatcher) {
        val engine = bindEngine(initialPlaying = false).apply { forceVolume(0f) }
        dispatcher().handleGeneral(GeneralCommand.ToggleMute)
        assertEquals(listOf("setMuted:false"), engine.calls)

        engine.calls.clear()
        engine.forceVolume(0.7f)
        dispatcher().handleGeneral(GeneralCommand.ToggleMute)
        assertEquals(listOf("setMuted:true"), engine.calls)
    }

    @Test
    fun `stream index commands select the matching track type`() = runTest(mainDispatcherRule.testDispatcher) {
        val engine = bindEngine()

        dispatcher().handleGeneral(GeneralCommand.SetAudioStreamIndex(index = 3))
        dispatcher().handleGeneral(GeneralCommand.SetSubtitleStreamIndex(index = 7))

        assertEquals(
            listOf("selectTrack:AUDIO:3", "selectTrack:SUBTITLE:7"),
            engine.calls,
        )
    }

    @Test
    fun `SetMaxStreamingBitrate delegates to the engine`() = runTest(mainDispatcherRule.testDispatcher) {
        val engine = bindEngine()

        dispatcher().handleGeneral(GeneralCommand.SetMaxStreamingBitrate(bitrate = 4_000_000))

        assertEquals(listOf("setMaxVideoBitrate:4000000"), engine.calls)
    }

    @Test
    fun `queue and unsupported general commands touch no engine`() = runTest(mainDispatcherRule.testDispatcher) {
        val engine = bindEngine()

        dispatcher().handleGeneral(GeneralCommand.SetRepeatMode(mode = "RepeatAll"))
        dispatcher().handleGeneral(GeneralCommand.SetShuffleQueue(shuffle = true))
        dispatcher().handleGeneral(GeneralCommand.SetPlaybackOrder(order = "Shuffle"))
        dispatcher().handleGeneral(GeneralCommand.ToggleFullscreen)
        dispatcher().handleGeneral(GeneralCommand.DisplayMessage(header = "h", text = "t", timeoutMs = null))
        dispatcher().handleGeneral(GeneralCommand.Unknown(name = "Whatever"))

        assertTrue(engine.calls.isEmpty())
    }

    @Test
    fun `general commands with no engine bound are a silent no-op`() = runTest(mainDispatcherRule.testDispatcher) {
        // Must not throw for any command family.
        dispatcher().handleGeneral(GeneralCommand.SetVolume(volume0to100 = 50, mute = null))
        dispatcher().handleGeneral(GeneralCommand.ToggleMute)
        dispatcher().handleGeneral(GeneralCommand.SetAudioStreamIndex(index = 1))
        dispatcher().handleGeneral(GeneralCommand.SetMaxStreamingBitrate(bitrate = 100))
    }
}
