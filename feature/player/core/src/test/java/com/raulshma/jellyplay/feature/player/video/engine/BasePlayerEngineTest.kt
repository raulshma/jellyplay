package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.view.View
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BasePlayerEngineTest {

    private class ConcreteBaseEngine : BasePlayerEngine() {
        override val capabilities: EngineCapabilities = EngineCapabilities()
        override val displayName: String = "test-engine"

        var configChangeCount = 0
        var lastOldConfig: EngineConfig? = null
        var lastNewConfig: EngineConfig? = null

        override fun onConfigChanged(oldConfig: EngineConfig, newConfig: EngineConfig) {
            configChangeCount++
            lastOldConfig = oldConfig
            lastNewConfig = newConfig
        }

        override fun load(request: PlaybackRequest) {}
        override fun play() {}
        override fun pause() {}
        override fun stop() {}
        override fun release() {}
        override fun seekTo(positionMs: Long) {}
        override val currentPositionMs: Long get() = 0L
        override val durationMs: Long get() = 0L
        override val positionFlow: Flow<Long> get() = emptyFlow()
        override fun createSurfaceView(context: Context): View = View(context)
        override fun applySubtitleStyle(style: SubtitleStyle) {}
        override fun selectTrack(type: TrackType, index: Int) {}
        override fun setMaxVideoBitrate(bps: Int?) {}
        override fun setPlaybackSpeed(speed: Float) {}
        override val playbackSpeed: Float get() = 1f
        override fun setVolume(volume: Float) {}
        override val volume: Float get() = 1f
        override fun increaseVolume(delta: Float) {}
        override fun decreaseVolume(delta: Float) {}
        override fun setMuted(muted: Boolean) {}
        override fun setAspectRatio(ratio: AspectRatio) {}
        override val audioSessionId: Int get() = -1
    }

    private lateinit var engine: ConcreteBaseEngine

    @Before
    fun setUp() {
        engine = ConcreteBaseEngine()
    }

    @Test
    fun defaultStateValues_matchExpectedDefaults() {
        assertEquals(EnginePlaybackState.IDLE, engine.playbackState.value)
        assertFalse(engine.isPlaying.value)
        assertEquals(0, engine.availableTracks.value.size)
        assertEquals(0L, engine.bufferedPositionMs.value)
        assertEquals(1000L, engine.pollingIntervalMs.value)
        assertFalse(engine.videoStatsEnabled.value)
    }

    @Test
    fun setters_updateStateFlowValues() {
        engine.setPollingIntervalMs(500L)
        assertEquals(500L, engine.pollingIntervalMs.value)

        engine.setVideoStatsEnabled(true)
        assertTrue(engine.videoStatsEnabled.value)
    }

    @Test
    fun updateConfig_deduplicatesEqualConfigAndInvokesOnConfigChangedOnlyOnDiff() {
        val config1 = EngineConfig(decoderMode = DecoderMode.SW_ONLY)
        val config2 = EngineConfig(decoderMode = DecoderMode.HW_ONLY)

        engine.updateConfig(config1)
        assertEquals(1, engine.configChangeCount)
        assertEquals(config1, engine.lastNewConfig)

        // Equal config update must be ignored by early return
        engine.updateConfig(config1)
        assertEquals(1, engine.configChangeCount)

        // Different config update triggers onConfigChanged
        engine.updateConfig(config2)
        assertEquals(2, engine.configChangeCount)
        assertEquals(config1, engine.lastOldConfig)
        assertEquals(config2, engine.lastNewConfig)
    }
}
