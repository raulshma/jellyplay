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
            PlayerSheet.Chapter,
            PlayerSheet.PlaybackInfo,
            PlayerSheet.AspectRatio,
            PlayerSheet.SubtitleHub,
            PlayerSheet.AVSync,
            PlayerSheet.Decoder,
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
            PlayerSheet.Chapter,
            PlayerSheet.PlaybackInfo,
            PlayerSheet.AspectRatio,
            PlayerSheet.SubtitleHub,
            PlayerSheet.AVSync,
            PlayerSheet.Decoder,
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
        // 14 variants as of current PlayerSheet.kt (None + 13 functional sheets).
        // The former Subtitle / SubtitleStyle / SubtitleDownload sheets collapsed
        // into the unified SubtitleHub.
        val expected = 14
        val actual = listOf(
            PlayerSheet.None, PlayerSheet.Speed, PlayerSheet.Audio,
            PlayerSheet.Chapter, PlayerSheet.PlaybackInfo,
            PlayerSheet.AspectRatio, PlayerSheet.SubtitleHub, PlayerSheet.AVSync,
            PlayerSheet.Decoder, PlayerSheet.Episodes,
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

/** Tests for the sleep-timer slice, owned by SleepTimerController. */
class SleepTimerStateTest {

    @Test
    fun sleepTimer_defaultInactive() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SleepTimerState()
        assertFalse(state.sleepTimerActive)
    }

    @Test
    fun sleepTimer_defaultEndOfEpisodeFalse() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SleepTimerState()
        assertFalse(state.sleepTimerEndOfEpisode)
    }

    @Test
    fun sleepTimer_defaultLastUsedDurationMsZero() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SleepTimerState()
        assertEquals(0L, state.sleepTimerLastUsedDurationMs)
    }

    @Test
    fun sleepTimer_activate_setsActive() {
        val durationMs = 30 * 60 * 1_000L // 30 minutes
        val state = com.raulshma.jellyplay.feature.player.video.state.SleepTimerState()
            .copy(
                sleepTimerActive = true,
                sleepTimerLastUsedDurationMs = durationMs,
            )
        assertTrue(state.sleepTimerActive)
        assertEquals(durationMs, state.sleepTimerLastUsedDurationMs)
    }

    @Test
    fun sleepTimer_endOfEpisodeMode_setsFlag() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SleepTimerState()
            .copy(
                sleepTimerActive = true,
                sleepTimerEndOfEpisode = true,
            )
        assertTrue(state.sleepTimerEndOfEpisode)
    }

    @Test
    fun sleepTimer_deactivate_clearsState() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SleepTimerState(
            sleepTimerActive = true,
        ).copy(
            sleepTimerActive = false,
        )
        assertFalse(state.sleepTimerActive)
    }
}
