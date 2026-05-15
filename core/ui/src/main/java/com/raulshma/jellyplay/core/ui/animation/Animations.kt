package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.PointToPointEasing

inline fun <T> springySpec() = spring<T>(
    dampingRatio = 0.35f,
    stiffness = Spring.StiffnessLow
)

inline fun <T> lessSpringySpec() = spring<T>(
    dampingRatio = 0.4f,
    stiffness = Spring.StiffnessLow
)

fun fancySlideTransition(
    isForward: Boolean,
    screenWidthPx: Int,
    duration: Int = 600
): ContentTransform = if (isForward) {
    slideInHorizontally(
        animationSpec = tween(duration, easing = FancyTransitionEasing),
        initialOffsetX = { screenWidthPx }
    ) + fadeIn(
        tween(300, 100, AlphaEasing)
    ) togetherWith slideOutHorizontally(
        animationSpec = tween(duration, easing = FancyTransitionEasing),
        targetOffsetX = { -screenWidthPx }
    ) + fadeOut(
        tween(300, 100, AlphaEasing)
    )
} else {
    slideInHorizontally(
        animationSpec = tween(duration, easing = FancyTransitionEasing),
        initialOffsetX = { -screenWidthPx }
    ) + fadeIn(
        tween(300, 100, AlphaEasing)
    ) togetherWith slideOutHorizontally(
        animationSpec = tween(duration, easing = FancyTransitionEasing),
        targetOffsetX = { screenWidthPx }
    ) + fadeOut(
        tween(300, 100, AlphaEasing)
    )
}
