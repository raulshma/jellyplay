package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

@Composable
fun rememberAnimatedBorderPhase(
    phase: Float = 80f,
    repeatDuration: Int = 1000,
): Float {
    if (com.raulshma.jellyplay.core.ui.components.LocalReducedMotion.current) return 0f

    val transition = rememberInfiniteTransition(label = "animatedBorderPhase")

    val animatedPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = phase,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = repeatDuration,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "borderPhase",
    )

    return animatedPhase
}
