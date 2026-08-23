package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Direct unit coverage for [ReloadablePlayerEngine.withPreservedPlayback].
 *
 * The contract suite's `reload_preservesPositionSpeedAndPlayState` verifies the
 * invariant via [FakeMediaEngine] (which mirrors the base logic by fiat). This
 * test pins the *real* shared implementation — the code the three adapters
 * (Exo/mpv/VLC) actually run — without requiring a native player. Real engines
 * keep `behavioralDrivingSupported() = false` for Level-1 because their JNI
 * handles cannot be driven under Robolectric, so this test is the coverage
 * back-stop for the hoisted 5× duplication.
 */
@RunWith(RobolectricTestRunner::class)
class ReloadablePlayerEngineTest {

    private open class Harness(
        context: Context = ApplicationProvider.getApplicationContext(),
    ) : ReloadablePlayerEngine(context), AndroidSurfaceProvider {

        override val capabilities: EngineCapabilities = EngineCapabilities()
        override val displayName: String = "Harness"

        // Mutable backing for the contract's observable state.
        var pos: Long = 0L
        override val currentPositionMs: Long get() = pos
        override val durationMs: Long get() = 0L
        override val positionFlow: Flow<Long> get() = emptyFlow()

        var speed: Float = 1f
        override val playbackSpeed: Float get() = speed
        override fun setPlaybackSpeed(speed: Float) { this.speed = speed }

        var vol: Float = 1f
        override val volume: Float get() = vol
        override fun setVolume(value: Float) { vol = value.coerceIn(0f, 1f) }
        override fun increaseVolume(delta: Float) { setVolume(vol + delta) }
        override fun decreaseVolume(delta: Float) { setVolume(vol - delta) }
        override fun setMuted(muted: Boolean) { vol = if (muted) 0f else 1f }

        // Drive _isPlaying via BasePlayerEngine's protected field.
        fun setPlaying(value: Boolean) { _isPlaying.value = value }

        fun testWithPreservedPlayback(block: (PlaybackSnapshot) -> Unit) {
            withPreservedPlayback(block)
        }

        override fun load(request: PlaybackRequest) {}
        override fun play() { _isPlaying.value = true }
        override fun pause() { _isPlaying.value = false }
        override fun stop() { _isPlaying.value = false }
        override fun release() {}
        override fun seekTo(positionMs: Long) { pos = positionMs.coerceAtLeast(0L) }
        override fun createSurfaceView(context: Context): View = View(context)
        override fun applySubtitleStyle(style: SubtitleStyle) {}
        override fun selectTrack(type: TrackType, index: Int) {}
        override fun setMaxVideoBitrate(bps: Int?) {}
        override fun setAspectRatio(ratio: AspectRatio) {}
        override val audioSessionId: Int get() = -1
        override fun onConfigChanged(oldConfig: EngineConfig, newConfig: EngineConfig) {}
    }

    @Test
    fun withPreservedPlayback_restoresWasPlayingAndSpeed() {
        val h = Harness()
        h.pos = 12_000L
        h.speed = 1.5f
        h.setPlaying(true)

        h.testWithPreservedPlayback { snap ->
            // Simulate a rebuild that clears play state and changes speed.
            assertEquals(12_000L, snap.positionMs)
            assertTrue(snap.wasPlaying)
            assertEquals(1.5f, snap.playbackSpeed, 0f)
            h.setPlaying(false)
            h.speed = 1f
            // Rebuild is responsible for position; emulate `mp.time = snap.positionMs`.
            h.pos = snap.positionMs
        }

        assertEquals(12_000L, h.currentPositionMs)
        assertEquals(1.5f, h.playbackSpeed, 0f)
        assertTrue(h.isPlaying.value)
    }

    @Test
    fun withPreservedPlayback_doesNotForcePlayWhenWasPaused() {
        val h = Harness()
        h.pos = 5_000L
        h.speed = 1f
        h.setPlaying(false)

        h.testWithPreservedPlayback { snap ->
            assertFalse(snap.wasPlaying)
            h.pos = snap.positionMs
        }

        assertFalse(h.isPlaying.value)
        assertEquals(1f, h.playbackSpeed, 0f)
    }

    @Test
    fun withPreservedPlayback_doesNotChurnSpeedWhenAlreadyCorrect() {
        val h = Harness()
        h.speed = 1f
        h.setPlaying(true)
        var speedCalls = 0
        val originalSpeed = h.speed
        // Subclass that counts setPlaybackSpeed invocations.
        val counting = object : Harness() {
            init {
                pos = h.pos
                speed = h.speed
                setPlaying(true)
            }

            override fun setPlaybackSpeed(speed: Float) {
                speedCalls++
                super.setPlaybackSpeed(speed)
            }
        }

        counting.testWithPreservedPlayback { snap ->
            counting.pos = snap.positionMs
            // Leave speed unchanged — withPreservedPlayback should not call setPlaybackSpeed.
        }

        assertEquals(0, speedCalls)
        assertEquals(originalSpeed, counting.playbackSpeed, 0f)
    }

    @Test
    fun withPreservedPlayback_avoidsDoublePlayWhenAlreadyPlaying() {
        val h = Harness()
        h.setPlaying(true)
        var playCalls = 0
        val counting = object : Harness() {
            init { setPlaying(true) }
            override fun play() {
                playCalls++
                super.play()
            }
        }

        counting.testWithPreservedPlayback { snap ->
            // Rebuild already left isPlaying true — withPreservedPlayback must not double-play.
            assertTrue(counting.isPlaying.value)
            counting.pos = snap.positionMs
        }

        assertEquals(0, playCalls)
    }
}
