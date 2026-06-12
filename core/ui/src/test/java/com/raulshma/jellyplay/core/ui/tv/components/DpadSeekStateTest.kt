package com.raulshma.jellyplay.core.ui.tv.components

import com.raulshma.jellyplay.core.ui.tv.input.DpadRepeatAccelerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DpadSeekStateTest {

    private var committedPosition: Long? = null
    private var currentPosition = 50_000L
    private var duration = 120_000L
    private val baseStepMs = 10_000L

    private fun createState(): DpadSeekState = DpadSeekState(
        accelerator = DpadRepeatAccelerator.Default,
        getBaseStepMs = { baseStepMs },
        getCurrentPositionMs = { currentPosition },
        getDurationMs = { duration },
        onCommit = { committedPosition = it },
    )

    @Test
    fun initialState_isZeroed() {
        val state = createState()
        assertEquals(0, state.direction)
        assertEquals(0L, state.offsetMs)
        assertEquals(0L, state.timestamp)
    }

    @Test
    fun seekForward_setsDirectionAndOffset() {
        val state = createState()
        state.seekForward()
        assertEquals(1, state.direction)
        assertEquals(baseStepMs, state.offsetMs)
    }

    @Test
    fun seekForward_twice_accumulatesOffset() {
        val state = createState()
        state.seekForward()
        state.seekForward()
        assertEquals(1, state.direction)
        assertEquals(baseStepMs * 2, state.offsetMs)
    }

    @Test
    fun seekBackward_setsDirectionAndOffset() {
        val state = createState()
        state.seekBackward()
        assertEquals(-1, state.direction)
        assertEquals(baseStepMs, state.offsetMs)
    }

    @Test
    fun directionChange_resetsAccumulation() {
        val state = createState()
        state.seekForward()
        state.seekForward()
        state.seekBackward()
        assertEquals(-1, state.direction)
        assertEquals(baseStepMs, state.offsetMs)
    }

    @Test
    fun seekForward_withRepeat_appliesAcceleration() {
        val state = createState()
        state.seekForward(repeatCount = 10)
        assertEquals(20_000L, state.offsetMs)
    }

    @Test
    fun commitForward_commitsCorrectPosition() {
        val state = createState()
        state.seekForward()
        state.commitForward()
        assertEquals(60_000L, committedPosition)
    }

    @Test
    fun commitForward_clampsToDuration() {
        val state = createState()
        repeat(8) { state.seekForward() }
        state.commitForward()
        assertEquals(120_000L, committedPosition)
    }

    @Test
    fun commitBackward_commitsCorrectPosition() {
        val state = createState()
        state.seekBackward()
        state.commitBackward()
        assertEquals(40_000L, committedPosition)
    }

    @Test
    fun commitBackward_clampsToZero() {
        val state = createState()
        state.seekBackward()
        state.seekBackward()
        state.seekBackward()
        state.seekBackward()
        state.seekBackward()
        state.seekBackward()
        state.commitBackward()
        assertEquals(0L, committedPosition)
    }

    @Test
    fun commitForward_withoutSeeking_doesNothing() {
        val state = createState()
        state.commitForward()
        assertEquals(null, committedPosition)
    }

    @Test
    fun commitBackward_afterForwardSeek_doesNothing() {
        val state = createState()
        state.seekForward()
        state.commitBackward()
        assertEquals(null, committedPosition)
    }

    @Test
    fun addOffset_sameDirection_accumulates() {
        val state = createState()
        state.seekForward()
        state.addOffset(1, 5_000L)
        assertEquals(1, state.direction)
        assertEquals(15_000L, state.offsetMs)
    }

    @Test
    fun addOffset_newDirection_resets() {
        val state = createState()
        state.seekForward()
        state.addOffset(-1, 3_000L)
        assertEquals(-1, state.direction)
        assertEquals(3_000L, state.offsetMs)
    }

    @Test
    fun reset_clearsState() {
        val state = createState()
        state.seekForward()
        state.seekForward()
        state.reset()
        assertEquals(0, state.direction)
        assertEquals(0L, state.offsetMs)
    }

    @Test
    fun timestamp_incrementsOnEachAccumulation() {
        val state = createState()
        assertEquals(0L, state.timestamp)
        state.seekForward()
        assertEquals(1L, state.timestamp)
        state.seekForward()
        assertEquals(2L, state.timestamp)
        state.addOffset(1, 1_000L)
        assertEquals(3L, state.timestamp)
    }

    @Test
    fun startPosition_capturedOnFirstSeek() {
        currentPosition = 50_000L
        val state = createState()
        state.seekForward()
        currentPosition = 70_000L
        state.commitForward()
        assertEquals(60_000L, committedPosition)
    }
}
