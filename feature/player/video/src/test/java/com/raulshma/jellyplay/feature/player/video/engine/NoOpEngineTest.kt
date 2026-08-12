package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The [NoOpEngine] is the deliberate placeholder for
 * [com.raulshma.jellyplay.core.model.PlayerType.EXTERNAL]; these tests pin its
 * "does nothing, never crashes" contract so the EXTERNAL path stays inert.
 */
class NoOpEngineTest {

    private fun newEngine() = NoOpEngine()

    @Test
    fun capabilities_allFalse() {
        val caps = newEngine().capabilities
        assertFalse(caps.supportsPip)
        assertFalse(caps.supportsMiniMode)
        assertFalse(caps.supportsCues)
        assertFalse(caps.supportsAudioDelay)
        assertFalse(caps.supportsVideoFilters)
        assertFalse(caps.supportsLiveQualitySwitch)
    }

    @Test
    fun initialState_isIdleAndEmpty() {
        val engine = newEngine()
        assertEquals(EnginePlaybackState.IDLE, engine.playbackState.value)
        assertFalse(engine.isPlaying.value)
        assertEquals(0L, engine.currentPositionMs)
        assertEquals(0L, engine.durationMs)
        assertTrue(engine.availableTracks.value.isEmpty())
        assertEquals(0L, engine.bufferedPositionMs.value)
    }

    @Test
    fun volume_defaultsToOneAndClamps() {
        val engine = newEngine()
        assertEquals(1f, engine.volume, 0f)
        engine.setVolume(2f)
        assertEquals(1f, engine.volume, 0f)
        engine.setVolume(-1f)
        assertEquals(0f, engine.volume, 0f)
        engine.setVolume(0.5f)
        assertEquals(0.5f, engine.volume, 0f)
    }

    @Test
    fun mute_zerosVolume() {
        val engine = newEngine()
        engine.setMuted(true)
        assertEquals(0f, engine.volume, 0f)
    }

    @Test
    fun increaseAndDecreaseVolume_adjustWithinRange() {
        val engine = newEngine()
        engine.decreaseVolume(0.3f)
        assertEquals(0.7f, engine.volume, 0.0001f)
        engine.increaseVolume(0.1f)
        assertEquals(0.8f, engine.volume, 0.0001f)
    }

    @Test
    fun controlMethods_areNoOps_andDoNotThrow() {
        val engine = newEngine()
        engine.load(PlaybackRequest(uri = "", title = ""))
        engine.play()
        engine.pause()
        engine.stop()
        engine.seekTo(1_000L)
        engine.setPlaybackSpeed(2f)
        engine.updateConfig(EngineConfig())
        engine.selectTrack(TrackType.AUDIO, 0)
        engine.setMaxVideoBitrate(1_000)
        engine.setPollingIntervalMs(500L)
        engine.setVideoStatsEnabled(true)
        engine.setAspectRatio(AspectRatio.FIT)
        engine.release()
        // State is unchanged by any control call.
        assertEquals(EnginePlaybackState.IDLE, engine.playbackState.value)
        assertFalse(engine.isPlaying.value)
    }

    @Test
    fun flows_areEmptyByDefault() = runBlocking {
        val engine = newEngine()
        assertEquals(emptyList<Long>(), engine.positionFlow.toList())
        assertEquals(emptyList<EngineError>(), engine.errorFlow.toList())
        assertNull(engine.underlyingPlayer)
    }

    @Test
    fun defaults_forDerivedProperties() {
        val engine = newEngine()
        assertEquals(1f, engine.playbackSpeed, 0f)
        assertEquals(-1, engine.audioSessionId)
        assertFalse(engine.videoStatsEnabled.value)
    }
}
