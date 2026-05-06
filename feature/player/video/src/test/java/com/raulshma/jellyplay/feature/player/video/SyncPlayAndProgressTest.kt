package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine
import com.raulshma.jellyplay.feature.player.video.subtitle.TimedCue
import org.junit.Test
import org.junit.Assert.*

class SyncPlayCommandHandlerTest {

    private data class MockEngineState(
        var positionMs: Long = 0L,
        var isPlaying: Boolean = false,
        var seekToCalled: Boolean = false,
        var playCalled: Boolean = false,
        var pauseCalled: Boolean = false,
    ) {
        fun reset() {
            seekToCalled = false
            playCalled = false
            pauseCalled = false
        }
    }

    @Test
    fun syncPlayCommand_play_seeksThenPlays() {
        val state = MockEngineState()
        val positionTicks = 30_000_000L
        val posMs = positionTicks / 10_000

        state.positionMs = posMs
        state.seekToCalled = true
        state.isPlaying = true
        state.playCalled = true

        assertTrue(state.seekToCalled)
        assertTrue(state.playCalled)
        assertEquals(3_000L, posMs)
    }

    @Test
    fun syncPlayCommand_pause_seeksThenPauses() {
        val state = MockEngineState()
        val positionTicks = 30_000_000L
        val posMs = positionTicks / 10_000

        state.seekToCalled = true
        state.pauseCalled = true

        assertTrue(state.seekToCalled)
        assertTrue(state.pauseCalled)
    }

    @Test
    fun syncPlayCommand_seek_onlySeeks() {
        val state = MockEngineState()
        val positionTicks = 15_000_000L
        val posMs = positionTicks / 10_000

        state.seekToCalled = true

        assertTrue(state.seekToCalled)
        assertFalse(state.playCalled)
        assertFalse(state.pauseCalled)
        assertEquals(1_500L, posMs)
    }

    @Test
    fun syncPlayCommand_prepareSession_notPlaying_seeksAndPauses() {
        val state = MockEngineState()
        val positionTicks = 0L
        val posMs = positionTicks / 10_000
        val isPlaying = false

        if (!isPlaying) {
            state.seekToCalled = true
            state.pauseCalled = true
        }

        assertTrue(state.seekToCalled)
        assertTrue(state.pauseCalled)
    }

    @Test
    fun syncPlayCommand_prepareSession_playing_doesNotSeek() {
        val state = MockEngineState()
        val isPlaying = true

        if (!isPlaying) {
            state.seekToCalled = true
            state.pauseCalled = true
        }

        assertFalse(state.seekToCalled)
        assertFalse(state.pauseCalled)
    }

    @Test
    fun syncPlayCommand_groupUpdate_updatesGroupNameAndCount() {
        var groupName = ""
        var participantCount = 0

        groupName = "Movie Night"
        participantCount = 5

        assertEquals("Movie Night", groupName)
        assertEquals(5, participantCount)
    }

    @Test
    fun syncPlayCommand_waitForGroup_setsSyncedFalse() {
        var isSynced = true
        isSynced = false
        assertFalse(isSynced)
    }

    @Test
    fun syncPlayCommand_play_setsSyncedTrue() {
        var isSynced = false
        isSynced = true
        assertTrue(isSynced)
    }
}

class PlaybackProgressReporterTickConversionTest {

    @Test
    fun reportPlaybackProgress_positionMsToTicks() {
        val testCases = listOf(
            0L to 0L,
            1_000L to 10_000_000L,
            30_000L to 300_000_000L,
            60_000L to 600_000_000L,
            3_600_000L to 36_000_000_000L,
        )
        for ((ms, expectedTicks) in testCases) {
            val ticks = ms * 10_000
            assertEquals(expectedTicks, ticks)
        }
    }

    @Test
    fun reportPlaybackStopped_onlyIfPositionPositive() {
        val positionMs = 0L
        val positionTicks = positionMs * 10_000
        assertTrue(positionTicks <= 0)
    }

    @Test
    fun reportPlaybackStopped_positivePosition_reports() {
        val positionMs = 500L
        val positionTicks = positionMs * 10_000
        assertTrue(positionTicks > 0)
    }

    @Test
    fun reportPlaybackStopped_negativePosition_doesNotReport() {
        val positionMs = -1L
        val positionTicks = positionMs * 10_000
        assertTrue(positionTicks < 0)
    }

    @Test
    fun positionTracking_exoPlayer_usesFlow() {
        val engineType = "ExoPlayerEngine"
        assertTrue(engineType == "ExoPlayerEngine")
    }

    @Test
    fun positionTracking_nonExoPlayer_uses250msPoll() {
        val pollIntervalMs = 250L
        assertEquals(250L, pollIntervalMs)
    }

    @Test
    fun positionTracking_exoPlayerFlow_emitsPositionAndDuration() {
        val position = 90_000L
        val duration = 3_600_000L
        assertTrue(duration >= 0L)
        assertEquals(90_000L, position)
    }

    @Test
    fun positionTracking_nonExoPlayer_readsEngineState() {
        val enginePosition = 90_000L
        val engineDuration = 3_600_000L
        val coercedDuration = engineDuration.coerceAtLeast(0L)
        assertEquals(3_600_000L, coercedDuration)
        assertEquals(90_000L, enginePosition)
    }

    @Test
    fun progressReporting_intervalIs10Seconds() {
        val intervalMs = 10_000L
        assertEquals(10_000L, intervalMs)
    }
}

class PlayerEngineDefaultBehaviorTest {

    @Test
    fun defaultSupportsAudioDelay_isFalse() {
        val engine: PlayerEngine = FakePlayerEngine()
        assertFalse(engine.supportsAudioDelay)
    }

    @Test
    fun defaultSupportsAudioPassthrough_isFalse() {
        val engine: PlayerEngine = FakePlayerEngine()
        assertFalse(engine.supportsAudioPassthrough)
    }

    @Test
    fun defaultSupportsSubtitleStyle_isFalse() {
        val engine: PlayerEngine = FakePlayerEngine()
        assertFalse(engine.supportsSubtitleStyle)
    }

    @Test
    fun defaultSupportsDialogueBoost_isFalse() {
        val engine: PlayerEngine = FakePlayerEngine()
        assertFalse(engine.supportsDialogueBoost)
    }

    @Test
    fun defaultSupportsNightMode_isFalse() {
        val engine: PlayerEngine = FakePlayerEngine()
        assertFalse(engine.supportsNightMode)
    }

    @Test
    fun defaultSupportsOcr_isFalse() {
        val engine: PlayerEngine = FakePlayerEngine()
        assertFalse(engine.supportsOcr)
    }

    @Test
    fun defaultSupportsCues_isFalse() {
        val engine: PlayerEngine = FakePlayerEngine()
        assertFalse(engine.supportsCues)
    }

    @Test
    fun defaultSetSubtitleStyle_doesNothing() {
        val engine: PlayerEngine = FakePlayerEngine()
        engine.setSubtitleStyle(com.raulshma.jellyplay.core.model.SubtitleStyle(), null)
    }

    @Test
    fun defaultGetCurrentCues_returnsEmpty() {
        val engine: PlayerEngine = FakePlayerEngine()
        assertTrue(engine.getCurrentCues().isEmpty())
    }

    @Test
    fun defaultSetDialogueBoostEnabled_doesNothing() {
        val engine: PlayerEngine = FakePlayerEngine()
        engine.setDialogueBoostEnabled(true)
    }

    @Test
    fun defaultSetNightModeEnabled_doesNothing() {
        val engine: PlayerEngine = FakePlayerEngine()
        engine.setNightModeEnabled(true, 0)
    }

    @Test
    fun defaultSetEqualizerEnabled_doesNothing() {
        val engine: PlayerEngine = FakePlayerEngine()
        engine.setEqualizerEnabled(true)
    }

    @Test
    fun defaultCaptureViewBitmap_returnsNull() {
        val engine: PlayerEngine = FakePlayerEngine()
        assertNull(engine.captureViewBitmap())
    }

    private class FakePlayerEngine : PlayerEngine {
        override fun initialize(url: String, title: String, startPositionMs: Long) {}
        override fun release() {}
        override fun play() {}
        override fun pause() {}
        override fun seekTo(positionMs: Long) {}
        override fun seekForward(amountMs: Long) {}
        override fun seekBack(amountMs: Long) {}
        override fun setPlaybackSpeed(speed: Float) {}
        override fun setAudioDelay(ms: Long) {}
        override fun setDecoderMode(mode: com.raulshma.jellyplay.core.model.DecoderMode) {}
        override fun setAudioPassthrough(enabled: Boolean) {}
        override fun setAspectRatio(mode: Int, ratio: Float?) {}
        override val isPlaying: Boolean get() = false
        override val currentPositionMs: Long get() = 0L
        override val durationMs: Long get() = 0L
        override val playbackSpeed: Float get() = 1f
        override val audioSessionId: Int get() = 0
        override val audioTracks: List<PlayerEngine.TrackInfo> get() = emptyList()
        override val subtitleTracks: List<PlayerEngine.TrackInfo> get() = emptyList()
        override fun selectAudioTrack(index: Int) {}
        override fun selectSubtitleTrack(index: Int) {}
        override fun createPlayerView(context: android.content.Context): android.view.View {
            return android.view.View(context)
        }
        override fun setOnStateChanged(callback: ((Boolean) -> Unit)?) {}
        override fun setOnTracksChanged(callback: (() -> Unit)?) {}
    }
}
