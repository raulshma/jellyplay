package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine
import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the invariants of the [RemotePlayableEngine] contract — the engine-agnostic
 * "Play To" surface the remote-control dispatchers call from
 * [kotlinx.coroutines.Dispatchers.Default]:
 *
 *  - A pure-data engine can implement the interface without any platform
 *    dependency (the interface is commonMain-pure: `underlyingPlayer` is
 *    type-erased to `Any?` and defaults-independent).
 *  - `increaseVolume`/`decreaseVolume` carry default deltas of `0.05f` in the
 *    signature — a caller may invoke them with no argument (the remote
 *    "VolumeUp"/"VolumeDown" commands do exactly that).
 *  - Every control call is a plain function the implementation may record and
 *    replay from any thread (thread-marshalling is an implementation duty the
 *    interface cannot enforce — but the signatures must stay non-suspending).
 *  - [RemotePlayableEngine.underlyingPlayer] is nullable: an engine with no
 *    native handle reports `null` rather than throwing.
 */
class RemotePlayableEngineContractTest {

    /** Minimal recording engine — the reference implementation shape. */
    private class RecordingEngine : RemotePlayableEngine {
        val calls = mutableListOf<String>()
        val volumeValues = mutableListOf<Float>()

        private val playingFlow = MutableStateFlow(false)
        var positionMs: Long = 0L
        private var currentVolume: Float = 0.5f
        override val volume: Float get() = currentVolume
        private var mutedState: Boolean = false
        val muted: Boolean get() = mutedState
        var released: Boolean = false

        override val currentPositionMs: Long get() = positionMs
        override val isPlaying: StateFlow<Boolean> = playingFlow
        override val underlyingPlayer: Any? get() = null

        override fun play() {
            calls += "play"
            playingFlow.value = true
        }

        override fun pause() {
            calls += "pause"
            playingFlow.value = false
        }

        override fun stop() {
            calls += "stop"
            playingFlow.value = false
        }

        override fun seekTo(positionMs: Long) {
            calls += "seekTo($positionMs)"
            this.positionMs = positionMs
        }

        override fun selectTrack(type: TrackType, index: Int) {
            calls += "selectTrack($type,$index)"
        }

        override fun setMaxVideoBitrate(bps: Int?) {
            calls += "setMaxVideoBitrate($bps)"
        }

        override fun setVolume(value: Float) {
            calls += "setVolume($value)"
            currentVolume = value
            volumeValues += value
        }

        override fun increaseVolume(delta: Float) {
            setVolume(volume + delta)
        }

        override fun decreaseVolume(delta: Float) {
            setVolume(volume - delta)
        }

        override fun setMuted(muted: Boolean) {
            calls += "setMuted($muted)"
            mutedState = muted
        }

        override fun release() {
            calls += "release"
            released = true
        }
    }

    @Test
    fun `a pure-data engine implements the full contract`() {
        val engine = RecordingEngine()
        assertNull(engine.underlyingPlayer)
        assertEquals(0L, engine.currentPositionMs)
        assertTrue(!engine.isPlaying.value)
    }

    @Test
    fun `control calls are recorded in order`() {
        val engine = RecordingEngine()
        engine.play()
        engine.seekTo(1_500L)
        engine.selectTrack(TrackType.AUDIO, index = 1)
        engine.setMaxVideoBitrate(null)
        engine.pause()
        assertEquals(
            listOf("play", "seekTo(1500)", "selectTrack(AUDIO,1)", "setMaxVideoBitrate(null)", "pause"),
            engine.calls,
        )
        assertEquals(false, engine.isPlaying.value)
        assertEquals(1_500L, engine.currentPositionMs)
    }

    @Test
    fun `volume helpers carry the documented default delta of 0_05`() {
        // Defaults live on the interface, so the call must go through an
        // interface-typed reference to pick them up.
        val engine: RemotePlayableEngine = RecordingEngine()
        engine.increaseVolume()
        assertEquals(0.55f, engine.volume)
        engine.decreaseVolume()
        assertEquals(0.5f, engine.volume)
    }

    @Test
    fun `explicit deltas override the default`() {
        val engine = RecordingEngine()
        engine.increaseVolume(0.25f)
        assertEquals(0.75f, engine.volume)
        engine.decreaseVolume(1.0f)
        assertEquals(-0.25f, engine.volume)
    }

    @Test
    fun `release terminates the engine`() {
        val engine = RecordingEngine()
        engine.release()
        assertTrue(engine.released)
        assertEquals(listOf("release"), engine.calls)
    }

    @Test
    fun `setMuted is independent of volume`() {
        val engine = RecordingEngine()
        engine.setMuted(true)
        assertTrue(engine.muted)
        assertEquals(0.5f, engine.volume)
    }
}
