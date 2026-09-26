package com.raulshma.jellyplay.feature.player.video.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins BasePlayerEngine's scope invariant — a live coroutine scope per load;
 * only the terminal release kills one — at the base level, so no per-engine
 * revive choreography can regress (d6b764964 patched the frozen-seek-bar
 * failure mode one call site at a time; the accessor now makes it
 * structurally impossible).
 */
@RunWith(AndroidJUnit4::class)
class BasePlayerEngineScopeLawTest {

    /**
     * Minimal BasePlayerEngine harness: every engine-specific member is a
     * no-op. [release] mirrors the real adapters' terminal teardown — the
     * scope cancel is its one load-bearing line.
     */
    private class ScopeLawEngine : BasePlayerEngine() {
        fun exposedEngineScope(): CoroutineScope = engineScope

        override fun onConfigChanged(oldConfig: EngineConfig, newConfig: EngineConfig) {}
        override fun release() { engineScope.cancel() }
        override fun load(request: PlaybackRequest) {}
        override fun play() {}
        override fun pause() {}
        override fun stop() {}
        override fun seekTo(positionMs: Long) {}
        override fun setPlaybackSpeed(speed: Float) {}
        override fun selectTrack(type: TrackType, index: Int) {}
        override fun setMaxVideoBitrate(bps: Int?) {}
        override fun applySubtitleStyle(style: SubtitleStyle) {}
        override fun setAspectRatio(ratio: AspectRatio) {}
        override fun setVolume(value: Float, isUserChange: Boolean) {}
        override fun increaseVolume(delta: Float) {}
        override fun decreaseVolume(delta: Float) {}
        override fun setMuted(muted: Boolean) {}

        override val displayName: String = "scope-law-fake"
        override val currentPositionMs: Long = 0L
        override val durationMs: Long = 0L
        override val playbackSpeed: Float = 1f
        override val positionFlow: Flow<Long> = emptyFlow()
        override val audioSessionId: Int = -1
        override val capabilities: EngineCapabilities = EngineCapabilities()
        override val volume: Float = 1f
    }

    @Test
    fun load_afterRelease_readsALiveScope() {
        val engine = ScopeLawEngine()
        val first = engine.exposedEngineScope()
        assertTrue(first.isActive)

        // Terminal teardown — the only sanctioned cancel.
        engine.release()
        assertFalse(first.isActive)

        // The law: the very next scope read (what load() and its ticker /
        // coalescer consumers do) gets a fresh LIVE generation, so no load
        // path can ever run on a cancelled scope.
        val revived = engine.exposedEngineScope()
        assertTrue(revived.isActive)
        assertNotSame(first, revived)
    }

    @Test
    fun repeatedRelease_keepsTheReadContract() {
        val engine = ScopeLawEngine()
        engine.release()
        engine.release()
        assertTrue(engine.exposedEngineScope().isActive)
    }
}
