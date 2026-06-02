package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.core.ui.components.LocalPerformanceMode

object AnimationTokens {
    const val StaggerDelayPerItem = 40
    const val SectionStaggerDelay = 70

    const val CardPressScale = 0.95f
    const val ButtonPressScale = 0.85f
    const val MiniPlayerButtonPressScale = 0.85f
    const val ScaleEntranceInitial = 0.92f

    const val BottomSheetPredictiveBackMinScale = 0.85f
}

@Composable
fun performanceAwareScale(default: Float): Float {
    return if (LocalPerformanceMode.current) 1f else default
}

@Composable
fun performanceAwareStaggerDelay(defaultMs: Int): Int {
    return if (LocalPerformanceMode.current) 0 else defaultMs
}
