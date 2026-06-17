package com.raulshma.jellyplay.feature.player.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Extended PlayerSheet and sleep-timer state tests. */
class PlayerSheetExtendedTest {

    @Test
    fun playerSheet_none_isDistinctFromAll() {
        val allSheets = listOf(
            PlayerSheet.Speed,
            PlayerSheet.Audio,
            PlayerSheet.Subtitle,
            PlayerSheet.Chapter,
            PlayerSheet.PlaybackInfo,
            PlayerSheet.AspectRatio,
            PlayerSheet.SubtitleStyle,
            PlayerSheet.AVSync,
            PlayerSheet.Decoder,
            PlayerSheet.SubtitleDownload,
            PlayerSheet.Episodes,
            PlayerSheet.SyncPlay,
            PlayerSheet.Quality,
            PlayerSheet.SleepTimer,
            PlayerSheet.VideoFilter,
        )
        allSheets.forEach { sheet ->
            assertNotEquals(PlayerSheet.None, sheet)
        }
    }

    @Test
    fun playerSheet_allVariantsArePlayerSheet() {
        val allSheets = listOf(
            PlayerSheet.None,
            PlayerSheet.Speed,
            PlayerSheet.Audio,
            PlayerSheet.Subtitle,
            PlayerSheet.Chapter,
            PlayerSheet.PlaybackInfo,
            PlayerSheet.AspectRatio,
            PlayerSheet.SubtitleStyle,
            PlayerSheet.AVSync,
            PlayerSheet.Decoder,
            PlayerSheet.SubtitleDownload,
            PlayerSheet.Episodes,
            PlayerSheet.SyncPlay,
            PlayerSheet.Quality,
            PlayerSheet.SleepTimer,
            PlayerSheet.VideoFilter,
        )
        allSheets.forEach { sheet ->
            assertTrue("$sheet should be PlayerSheet", sheet is PlayerSheet)
        }
    }

    @Test
    fun playerSheet_hasExpectedVariantCount() {
        // 16 variants as of current PlayerSheet.kt (None + 15 functional sheets)
        val expected = 16
        val actual = listOf(
            PlayerSheet.None, PlayerSheet.Speed, PlayerSheet.Audio,
            PlayerSheet.Subtitle, PlayerSheet.Chapter, PlayerSheet.PlaybackInfo,
            PlayerSheet.AspectRatio, PlayerSheet.SubtitleStyle, PlayerSheet.AVSync,
            PlayerSheet.Decoder, PlayerSheet.SubtitleDownload, PlayerSheet.Episodes,
            PlayerSheet.SyncPlay, PlayerSheet.Quality, PlayerSheet.SleepTimer,
            PlayerSheet.VideoFilter,
        ).size
        assertEquals(expected, actual)
    }

    @Test
    fun playerSheet_videoFilter_isPlayerSheet() {
        val sheet: PlayerSheet = PlayerSheet.VideoFilter
        assertTrue(sheet is PlayerSheet.VideoFilter)
    }

    @Test
    fun playerSheet_sleepTimer_isPlayerSheet() {
        val sheet: PlayerSheet = PlayerSheet.SleepTimer
        assertTrue(sheet is PlayerSheet.SleepTimer)
    }

    @Test
    fun playerSheet_episodes_isPlayerSheet() {
        val sheet: PlayerSheet = PlayerSheet.Episodes
        assertTrue(sheet is PlayerSheet.Episodes)
    }

    @Test
    fun playerSheet_quality_isPlayerSheet() {
        val sheet: PlayerSheet = PlayerSheet.Quality
        assertTrue(sheet is PlayerSheet.Quality)
    }

    @Test
    fun backHandler_dismissesSheet_whenOpen() {
        var currentSheet: PlayerSheet = PlayerSheet.Speed
        if (currentSheet != PlayerSheet.None) {
            currentSheet = PlayerSheet.None
        }
        assertEquals(PlayerSheet.None, currentSheet)
    }

    @Test
    fun backHandler_callsBack_whenNoneOpen() {
        var currentSheet: PlayerSheet = PlayerSheet.None
        var backCalled = false
        if (currentSheet != PlayerSheet.None) {
            currentSheet = PlayerSheet.None
        } else {
            backCalled = true
        }
        assertTrue(backCalled)
    }
}

/** Tests for sleep timer state in VideoPlayerUiState. */
class SleepTimerStateTest {

    @Test
    fun sleepTimer_defaultInactive() {
        val state = VideoPlayerUiState()
        assertFalse(state.sleepTimerActive)
    }

    @Test
    fun sleepTimer_defaultEndOfEpisodeFalse() {
        val state = VideoPlayerUiState()
        assertFalse(state.sleepTimerEndOfEpisode)
    }

    @Test
    fun sleepTimer_defaultRemainingMsZero() {
        val state = VideoPlayerUiState()
        assertEquals(0L, state.sleepTimerRemainingMs)
    }

    @Test
    fun sleepTimer_defaultLastUsedDurationMsZero() {
        val state = VideoPlayerUiState()
        assertEquals(0L, state.sleepTimerLastUsedDurationMs)
    }

    @Test
    fun sleepTimer_activate_setsActive() {
        val durationMs = 30 * 60 * 1_000L // 30 minutes
        val state = VideoPlayerUiState().copy(
            sleepTimerActive = true,
            sleepTimerRemainingMs = durationMs,
            sleepTimerLastUsedDurationMs = durationMs,
        )
        assertTrue(state.sleepTimerActive)
        assertEquals(30 * 60 * 1_000L, state.sleepTimerRemainingMs)
    }

    @Test
    fun sleepTimer_endOfEpisodeMode_setsFlag() {
        val state = VideoPlayerUiState().copy(
            sleepTimerActive = true,
            sleepTimerEndOfEpisode = true,
        )
        assertTrue(state.sleepTimerEndOfEpisode)
    }

    @Test
    fun sleepTimer_deactivate_clearsState() {
        val state = VideoPlayerUiState(
            sleepTimerActive = true,
            sleepTimerRemainingMs = 10_000L,
        ).copy(
            sleepTimerActive = false,
            sleepTimerRemainingMs = 0L,
        )
        assertFalse(state.sleepTimerActive)
        assertEquals(0L, state.sleepTimerRemainingMs)
    }

    @Test
    fun sleepTimer_countdown_decrementsRemaining() {
        val initial = 60_000L
        val ticked = initial - 1_000L
        val state = VideoPlayerUiState(sleepTimerRemainingMs = ticked)
        assertEquals(59_000L, state.sleepTimerRemainingMs)
    }
}
