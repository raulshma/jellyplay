package com.raulshma.jellyplay.core.ui.tv.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.ui.tv.input.DpadSeekAcceleration

@Stable
class DpadSeekState(
    private val acceleration: DpadSeekAcceleration,
    private val getBaseStepMs: () -> Long,
    private val getCurrentPositionMs: () -> Long,
    private val getDurationMs: () -> Long,
    private val onCommit: (Long) -> Unit,
) {
    var direction: Int by mutableIntStateOf(0)
        private set
    var offsetMs: Long by mutableLongStateOf(0L)
        private set
    var timestamp: Long by mutableLongStateOf(0L)
        private set

    private var startPositionMs: Long = 0L

    fun seekForward(repeatCount: Int = 0) {
        accumulate(1, repeatCount)
    }

    fun seekBackward(repeatCount: Int = 0) {
        accumulate(-1, repeatCount)
    }

    fun commitForward() {
        if (direction == 1 && offsetMs > 0L) {
            val dur = getDurationMs().coerceAtLeast(0L)
            val target = (startPositionMs + offsetMs).coerceAtMost(dur)
            onCommit(target)
        }
    }

    fun commitBackward() {
        if (direction == -1 && offsetMs > 0L) {
            val target = (startPositionMs - offsetMs).coerceAtLeast(0L)
            onCommit(target)
        }
    }

    fun addOffset(targetDirection: Int, amountMs: Long) {
        if (direction == targetDirection && offsetMs > 0L) {
            offsetMs += amountMs
        } else {
            direction = targetDirection
            offsetMs = amountMs
            startPositionMs = getCurrentPositionMs()
        }
        offsetMs = clampOffsetToRange(targetDirection)
        timestamp++
    }

    fun reset() {
        direction = 0
        offsetMs = 0L
    }

    private fun accumulate(targetDirection: Int, repeatCount: Int) {
        val step = acceleration.calculateStep(
            baseStepMs = getBaseStepMs(),
            repeatCount = repeatCount,
            durationMs = getDurationMs().coerceAtLeast(0L),
        )
        if (direction == targetDirection && offsetMs > 0L) {
            offsetMs += step
        } else {
            direction = targetDirection
            offsetMs = step
            startPositionMs = getCurrentPositionMs()
        }
        // Clamp the *previewed* offset to what's actually reachable so the on-screen
        // "+Ns/-Ns" indicator never advertises a seek beyond the media bounds — the
        // commit handlers already clamp the final target, but the live preview should
        // not mislead the user into expecting a larger jump than will occur.
        offsetMs = clampOffsetToRange(targetDirection)
        timestamp++
    }

    /**
     * Caps [offsetMs] to the remaining seekable range from [startPositionMs]:
     * forward seeks cannot exceed the gap to the end; backward seeks cannot
     * undershoot position 0. Returns 0 when the duration is still unknown (so a
     * mid-load press doesn't freeze seeking entirely).
     */
    private fun clampOffsetToRange(targetDirection: Int): Long {
        val dur = getDurationMs()
        if (dur <= 0L) return offsetMs.coerceAtLeast(0L)
        return if (targetDirection == 1) {
            offsetMs.coerceAtMost((dur - startPositionMs).coerceAtLeast(0L))
        } else {
            offsetMs.coerceAtMost(startPositionMs.coerceAtLeast(0L))
        }
    }
}

@Composable
fun rememberDpadSeekState(
    getBaseStepMs: () -> Long,
    getCurrentPositionMs: () -> Long,
    getDurationMs: () -> Long,
    onCommit: (Long) -> Unit,
    accelerator: DpadSeekAcceleration = DpadSeekAcceleration.Default,
): DpadSeekState = remember(accelerator) {
    DpadSeekState(
        acceleration = accelerator,
        getBaseStepMs = getBaseStepMs,
        getCurrentPositionMs = getCurrentPositionMs,
        getDurationMs = getDurationMs,
        onCommit = onCommit,
    )
}
