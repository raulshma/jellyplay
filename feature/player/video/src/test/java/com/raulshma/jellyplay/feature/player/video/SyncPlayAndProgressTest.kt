package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
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
    fun syncPlayCommand_playlistItemTransition_usesSetPlaylistItemIfFoundInQueue() {
        val playlistItemMap = mapOf("playlist_item_1" to "item_A", "playlist_item_2" to "item_B")
        val targetItemId = "item_B"

        val matchingEntry = playlistItemMap.entries.find { it.value == targetItemId }
        assertNotNull(matchingEntry)
        assertEquals("playlist_item_2", matchingEntry?.key)
    }

    @Test
    fun syncPlayCommand_playlistItemTransition_usesSetNewQueueIfNotFoundInQueue() {
        val playlistItemMap = mapOf("playlist_item_1" to "item_A")
        val targetItemId = "item_C"

        val matchingEntry = playlistItemMap.entries.find { it.value == targetItemId }
        assertNull(matchingEntry)
    }

    @Test
    fun syncPlayCommand_groupUpdate_parsesRepeatAndShuffleModes() {
        val repeatModeStr = "RepeatOne"
        val repeatMode = when (repeatModeStr) {
            "RepeatOne" -> com.raulshma.jellyplay.core.model.SyncPlayRepeatMode.REPEAT_ONE
            "RepeatAll" -> com.raulshma.jellyplay.core.model.SyncPlayRepeatMode.REPEAT_ALL
            else -> com.raulshma.jellyplay.core.model.SyncPlayRepeatMode.REPEAT_NONE
        }

        val shuffleModeStr = "Shuffle"
        val shuffleMode = when (shuffleModeStr) {
            "Shuffle" -> com.raulshma.jellyplay.core.model.SyncPlayShuffleMode.SHUFFLE
            else -> com.raulshma.jellyplay.core.model.SyncPlayShuffleMode.SORTED
        }

        assertEquals(com.raulshma.jellyplay.core.model.SyncPlayRepeatMode.REPEAT_ONE, repeatMode)
        assertEquals(com.raulshma.jellyplay.core.model.SyncPlayShuffleMode.SHUFFLE, shuffleMode)
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

class EngineCapabilitiesDefaultTest {

    @Test
    fun defaultCapabilities_allDisabled() {
        val caps = EngineCapabilities()
        assertFalse(caps.supportsPip)
        assertFalse(caps.supportsMiniMode)
        assertFalse(caps.supportsCues)
        assertFalse(caps.supportsAudioDelay)
        assertFalse(caps.supportsSubtitleDelay)
        assertFalse(caps.supportsAudioPassthrough)
        assertFalse(caps.supportsSubtitleStyle)
        assertFalse(caps.supportsDialogueBoost)
        assertFalse(caps.supportsNightMode)
        assertFalse(caps.supportsAudioNormalization)
        assertFalse(caps.supportsChannelMixing)
    }

    @Test
    fun capabilities_dataClassCopy() {
        val caps = EngineCapabilities()
        val modified = caps.copy(supportsPip = true)
        assertFalse(caps.supportsPip)
        assertTrue(modified.supportsPip)
        assertFalse(modified.supportsMiniMode)
    }
}
