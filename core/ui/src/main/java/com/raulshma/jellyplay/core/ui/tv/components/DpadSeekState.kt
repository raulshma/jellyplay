package com.raulshma.jellyplay.core.ui.tv.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.ui.tv.input.DpadRepeatAccelerator

@Stable
class DpadSeekState(
    private val accelerator: DpadRepeatAccelerator,
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
        timestamp++
    }

    fun reset() {
        direction = 0
        offsetMs = 0L
    }

    private fun accumulate(targetDirection: Int, repeatCount: Int) {
        val step = accelerator.calculateStep(getBaseStepMs(), repeatCount)
        if (direction == targetDirection && offsetMs > 0L) {
            offsetMs += step
        } else {
            direction = targetDirection
            offsetMs = step
            startPositionMs = getCurrentPositionMs()
        }
        timestamp++
    }
}

@Composable
fun rememberDpadSeekState(
    getBaseStepMs: () -> Long,
    getCurrentPositionMs: () -> Long,
    getDurationMs: () -> Long,
    onCommit: (Long) -> Unit,
    accelerator: DpadRepeatAccelerator = DpadRepeatAccelerator.Default,
): DpadSeekState = remember(accelerator) {
    DpadSeekState(
        accelerator = accelerator,
        getBaseStepMs = getBaseStepMs,
        getCurrentPositionMs = getCurrentPositionMs,
        getDurationMs = getDurationMs,
        onCommit = onCommit,
    )
}
