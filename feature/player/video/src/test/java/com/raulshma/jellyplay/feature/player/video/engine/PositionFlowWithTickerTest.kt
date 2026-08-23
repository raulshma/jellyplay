package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * Red-loop probe: the REAL shared `callbackFlow + EnginePositionTicker` shell
 * ([ReloadablePlayerEngine.positionFlowWithTicker]) must keep emitting
 * advancing positions while playback is active — the seek bar's data source.
 */
@RunWith(RobolectricTestRunner::class)
class PositionFlowWithTickerTest {

    private class Harness(context: Context) : ReloadablePlayerEngine(context) {
        override val capabilities: EngineCapabilities = EngineCapabilities()
        override val displayName: String = "TickerHarness"
        override val durationMs: Long get() = 0L

        var pos: Long = 0L
        override val currentPositionMs: Long get() = pos

        // Real shell under test — mirrors what MpvPlayerEngine/LibVlcPlayerEngine run.
        override val positionFlow: Flow<Long> = positionFlowWithTicker(
            onActive = { pos += 100L },
        )

        fun setPlaying(value: Boolean) { _isPlaying.value = value }
        fun setPolling(ms: Long) { setPollingIntervalMs(ms) }

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
        override val playbackSpeed: Float get() = 1f
        override fun setPlaybackSpeed(speed: Float) {}
        override val volume: Float get() = 1f
        override fun setVolume(value: Float) {}
        override fun increaseVolume(delta: Float) {}
        override fun decreaseVolume(delta: Float) {}
        override fun setMuted(muted: Boolean) {}
        override fun onConfigChanged(oldConfig: EngineConfig, newConfig: EngineConfig) {}
    }

    @Test
    fun positionFlow_emitsAdvancingPositionsWhilePlaying() {
        val h = Harness(ApplicationProvider.getApplicationContext())
        h.setPolling(100L)
        h.setPlaying(true)

        val collected = mutableListOf<Long>()
        val job: Job = CoroutineScope(Dispatchers.Main).launch {
            h.positionFlow.collect { collected.add(it) }
        }

        // Drive the Robolectric main looper ~1.05s of virtual time → ~10 ticks.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_050L))

        job.cancel()

        // Initial seed (0) + at least a few advancing ticks.
        assertTrue(
            "expected multiple advancing emissions, got $collected",
            collected.size >= 3,
        )
        assertTrue(
            "position must advance while playing, got $collected",
            collected.last() > collected.first(),
        )
        // Strictly monotonic after warm-up: the seed and tick-1 both carry pos=0
        // (onActive increments after trySend), so skip the leading duplicate.
        val window = if (collected.size >= 2 && collected[0] == collected[1]) collected.drop(1) else collected
        assertTrue(
            "emissions must strictly advance after warm-up, got $collected",
            window.zipWithNext().all { (a, b) -> b > a },
        )
    }

    @Test
    fun positionFlow_startsEmittingAfterResumeFromPausedCollect() {
        // Real sequence: reporter collects while still buffering/paused, playback
        // starts afterwards. The bounded paused-wait must wake on the isPlaying
        // edge and then emit continuously.
        val h = Harness(ApplicationProvider.getApplicationContext())
        h.setPolling(100L)
        h.setPlaying(false)

        val collected = mutableListOf<Long>()
        val job: Job = CoroutineScope(Dispatchers.Main).launch {
            h.positionFlow.collect { collected.add(it) }
        }

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500L))
        val beforeResume = collected.size

        h.setPlaying(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_050L))
        job.cancel()

        assertTrue(
            "paused phase must not churn emissions, got $collected",
            beforeResume <= 1,
        )
        assertTrue(
            "resume must start continuous emissions, got $collected",
            collected.size - beforeResume >= 5,
        )
        assertTrue(collected.last() > 0L)
    }
}
