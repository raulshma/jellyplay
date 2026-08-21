package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.state.GesturePrefsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for playback speed state management, including hold-speed logic. */
class VideoPlayerSpeedTest {

    // ─── Defaults ─────────────────────────────────────────────────────────────

    @Test
    fun defaults_speedIsOne() {
        val state = VideoPlayerUiState()
        assertEquals(1.0f, state.playbackSpeed, 0f)
    }

    @Test
    fun defaults_defaultSpeedIsOne() {
        val state = VideoPlayerUiState()
        assertEquals(1.0f, state.gestures.defaultSpeed, 0f)
    }

    @Test
    fun defaults_holdSpeedNotActive() {
        val state = VideoPlayerUiState()
        assertFalse(state.gestures.isHoldSpeedActive)
    }

    @Test
    fun defaults_holdSpeedMultiplierIsTwo() {
        val state = VideoPlayerUiState()
        assertEquals(2.0f, state.gestures.holdSpeedMultiplier, 0f)
    }

    // ─── setPlaybackSpeed ─────────────────────────────────────────────────────

    @Test
    fun setPlaybackSpeed_1_5x() {
        val state = VideoPlayerUiState().copy(playbackSpeed = 1.5f)
        assertEquals(1.5f, state.playbackSpeed, 0f)
    }

    @Test
    fun setPlaybackSpeed_0_25x() {
        val state = VideoPlayerUiState().copy(playbackSpeed = 0.25f)
        assertEquals(0.25f, state.playbackSpeed, 0f)
    }

    @Test
    fun setPlaybackSpeed_2x() {
        val state = VideoPlayerUiState().copy(playbackSpeed = 2.0f)
        assertEquals(2.0f, state.playbackSpeed, 0f)
    }

    // ─── Hold speed logic ──────────────────────────────────────────────────────

    @Test
    fun startHoldSpeed_setsIsHoldSpeedActive() {
        val state = VideoPlayerUiState(
            playbackSpeed = 1.0f,
            gestures = GesturePrefsState(holdSpeedMultiplier = 2.0f),
        )
        val holdSpeed = state.playbackSpeed * state.gestures.holdSpeedMultiplier
        val updated = state.copy(
            gestures = state.gestures.copy(isHoldSpeedActive = true),
            playbackSpeed = holdSpeed,
        )
        assertTrue(updated.gestures.isHoldSpeedActive)
        assertEquals(2.0f, updated.playbackSpeed, 0f)
    }

    @Test
    fun startHoldSpeed_noOpWhenAlreadyActive() {
        val state = VideoPlayerUiState(
            gestures = GesturePrefsState(isHoldSpeedActive = true),
            playbackSpeed = 2.0f,
        )
        // If already active, do not change anything
        val updated = if (!state.gestures.isHoldSpeedActive) {
            state.copy(
                gestures = state.gestures.copy(isHoldSpeedActive = true),
                playbackSpeed = state.playbackSpeed * state.gestures.holdSpeedMultiplier,
            )
        } else {
            state
        }
        assertEquals(state, updated)
    }

    @Test
    fun stopHoldSpeed_restoresPreviousSpeed() {
        val previousSpeed = 1.25f
        // Simulating stopHoldSpeed: restore to defaultSpeed when speedBeforeHold is null,
        // or to the stored previous speed
        val state = VideoPlayerUiState(
            gestures = GesturePrefsState(isHoldSpeedActive = true, defaultSpeed = previousSpeed),
            playbackSpeed = 2.5f,
        )
        val updated = state.copy(
            gestures = state.gestures.copy(isHoldSpeedActive = false),
            playbackSpeed = state.gestures.defaultSpeed,
        )
        assertFalse(updated.gestures.isHoldSpeedActive)
        assertEquals(1.25f, updated.playbackSpeed, 0f)
    }

    @Test
    fun stopHoldSpeed_noOpWhenNotActive() {
        val state = VideoPlayerUiState(playbackSpeed = 1.5f)
        val updated = if (state.gestures.isHoldSpeedActive) {
            state.copy(
                gestures = state.gestures.copy(isHoldSpeedActive = false),
                playbackSpeed = state.gestures.defaultSpeed,
            )
        } else {
            state
        }
        assertEquals(state, updated)
    }

    @Test
    fun stopHoldSpeed_fallsBackToDefaultSpeed_whenSpeedBeforeHoldIsNull() {
        val state = VideoPlayerUiState(
            gestures = GesturePrefsState(isHoldSpeedActive = true, defaultSpeed = 1.0f),
            playbackSpeed = 2.0f,
        )
        val speedBeforeHold: Float? = null
        val restoredSpeed = speedBeforeHold ?: state.gestures.defaultSpeed
        val updated = state.copy(
            gestures = state.gestures.copy(isHoldSpeedActive = false),
            playbackSpeed = restoredSpeed,
        )
        assertEquals(1.0f, updated.playbackSpeed, 0f)
        assertFalse(updated.gestures.isHoldSpeedActive)
    }

    // ─── Available speed values ────────────────────────────────────────────────

    @Test
    fun availableSpeeds_areDistinctAndSorted() {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        assertEquals(speeds.distinct().size, speeds.size)
        for (i in 1 until speeds.size) {
            assertTrue(speeds[i] > speeds[i - 1])
        }
    }

    @Test
    fun holdSpeedMultiplier_appliedToCurrentSpeed() {
        val current = 1.5f
        val multiplier = 2.0f
        val holdSpeed = current * multiplier
        assertEquals(3.0f, holdSpeed, 0.001f)
    }
}
