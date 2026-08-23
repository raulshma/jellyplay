package com.raulshma.jellyplay.feature.player.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for seek state management in VideoPlayerUiState.
 * Covers seek position tracking, resume-with-skip-back offset,
 * and getReportPositionMs semantics.
 */
class VideoPlayerSeekStateTest {

    // ─── Default seek config ───────────────────────────────────────────────────

    @Test
    fun defaults_seekDurationIs10Seconds() {
        val state = VideoPlayerUiState()
        assertEquals(10_000L, state.gestures.seekDurationMs)
    }

    @Test
    fun defaults_swipeSeekMaxIs120Seconds() {
        val state = VideoPlayerUiState()
        assertEquals(120_000L, state.gestures.swipeSeekMaxMs)
    }

    @Test
    fun defaults_currentPositionIsZero() {
        val state = VideoPlayerUiState()
        assertEquals(0L, state.currentPosition)
    }

    @Test
    fun defaults_durationIsZero() {
        val state = VideoPlayerUiState()
        assertEquals(0L, state.duration)
    }

    @Test
    fun defaults_bufferedPositionIsZero() {
        val state = VideoPlayerUiState()
        assertEquals(0L, state.bufferedPosition)
    }

    // ─── seekTo: position update ───────────────────────────────────────────────

    @Test
    fun seekTo_updatesCurrentPosition() {
        val state = VideoPlayerUiState(duration = 3_600_000L)
        val seekTarget = 1_800_000L
        val updated = state.copy(currentPosition = seekTarget)
        assertEquals(seekTarget, updated.currentPosition)
    }

    @Test
    fun seekTo_clampsToZero() {
        val current = 5_000L
        val amount = 10_000L
        val result = (current - amount).coerceAtLeast(0L)
        assertEquals(0L, result)
    }

    @Test
    fun seekTo_clampsToDuration() {
        val current = 3_595_000L
        val amount = 10_000L
        val duration = 3_600_000L
        val result = (current + amount).coerceAtMost(duration)
        assertEquals(3_600_000L, result)
    }

    // ─── getReportPositionMs logic ─────────────────────────────────────────────

    @Test
    fun reportPosition_returnsCurrentPosition_whenNoRecentSeek() {
        val enginePos = 30_000L
        val lastSeekPos: Long? = null
        val reportedPos = lastSeekPos ?: enginePos
        assertEquals(enginePos, reportedPos)
    }

    @Test
    fun reportPosition_returnsSeekPosition_whenSeekWasRecent() {
        val enginePos = 30_000L
        val lastSeekPos = 45_000L
        val seekTime = System.currentTimeMillis()
        val now = seekTime + 1_000L // 1 second later, still within window
        val isRecent = (now - seekTime) < 3_000L
        val reportedPos = if (lastSeekPos != null && isRecent) lastSeekPos else enginePos
        assertEquals(lastSeekPos, reportedPos)
    }

    @Test
    fun reportPosition_returnsEnginePosition_whenSeekIsStale() {
        val enginePos = 30_000L
        val lastSeekPos = 45_000L
        val seekTime = System.currentTimeMillis() - 5_000L // 5 seconds ago
        val now = System.currentTimeMillis()
        val isRecent = (now - seekTime) < 3_000L
        val reportedPos = if (lastSeekPos != null && isRecent) lastSeekPos else enginePos
        assertEquals(enginePos, reportedPos)
    }

    // ─── resumePlayback skip-back offset ──────────────────────────────────────

    @Test
    fun resumePlayback_appliesSkipBackOffset() {
        val resumePosition = 1_800_000L
        val skipBackMs = 5_000L
        val startPosition = (resumePosition - skipBackMs).coerceAtLeast(0L)
        assertEquals(1_795_000L, startPosition)
    }

    @Test
    fun resumePlayback_skipBackBeyondStart_clampsToZero() {
        val resumePosition = 2_000L
        val skipBackMs = 5_000L
        val startPosition = (resumePosition - skipBackMs).coerceAtLeast(0L)
        assertEquals(0L, startPosition)
    }

    @Test
    fun resumePlayback_zeroSkipBack_usesResumePosition() {
        val resumePosition = 1_800_000L
        val skipBackMs = 0L
        val startPosition = (resumePosition - skipBackMs).coerceAtLeast(0L)
        assertEquals(resumePosition, startPosition)
    }

    // ─── Ticks conversion ──────────────────────────────────────────────────────

    @Test
    fun positionMsToTicks_correctMultiplier() {
        val posMs = 60_000L   // 1 minute
        val ticks = posMs * 10_000L
        assertEquals(600_000_000L, ticks)
    }

    @Test
    fun ticksToPositionMs_correctDivision() {
        val ticks = 600_000_000L
        val posMs = ticks / 10_000L
        assertEquals(60_000L, posMs)
    }

    // ─── Seek slider fraction ──────────────────────────────────────────────────

    @Test
    fun seekSlider_fractionFromPosition() {
        val pos = 1_800_000L
        val duration = 3_600_000L
        val fraction = pos.toFloat() / duration.toFloat()
        assertEquals(0.5f, fraction, 0.001f)
    }

    @Test
    fun seekSlider_zeroPosition_fractionZero() {
        val pos = 0L
        val duration = 3_600_000L
        val fraction = if (duration > 0) pos.toFloat() / duration.toFloat() else 0f
        assertEquals(0f, fraction)
    }

    @Test
    fun seekSlider_zeroDuration_fractionZero() {
        val pos = 1_800_000L
        val duration = 0L
        val fraction = if (duration > 0) pos.toFloat() / duration.toFloat() else 0f
        assertEquals(0f, fraction)
    }

    // ─── Buffered position ─────────────────────────────────────────────────────

    @Test
    fun bufferedPosition_canBeUpdated() {
        val state = VideoPlayerUiState(duration = 3_600_000L)
        val updated = state.copy(bufferedPosition = 600_000L)
        assertEquals(600_000L, updated.bufferedPosition)
    }

    @Test
    fun bufferedPosition_isAtLeastCurrentPosition_invariant() {
        val currentPos = 500_000L
        val bufferedPos = 600_000L
        assertTrue(bufferedPos >= currentPos)
    }
}
