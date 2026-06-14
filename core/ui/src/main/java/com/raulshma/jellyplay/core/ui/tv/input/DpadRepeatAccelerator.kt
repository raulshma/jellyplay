package com.raulshma.jellyplay.core.ui.tv.input

import androidx.compose.runtime.Stable

@Stable
class DpadRepeatAccelerator(
    private val accelerationFactor: Float = 0.1f,
    private val maxMultiplier: Float = 2.5f,
) {
    fun calculateMultiplier(repeatCount: Int): Float =
        (1f + repeatCount * accelerationFactor).coerceAtMost(maxMultiplier)

    fun calculateStep(baseStepMs: Long, repeatCount: Int): Long =
        (baseStepMs * calculateMultiplier(repeatCount)).toLong()

    companion object {
        val Default = DpadRepeatAccelerator()
    }
}
