package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing

@Composable
fun <T> defaultSpatialSpec() = MaterialTheme.motionScheme.defaultSpatialSpec<T>()

@Composable
fun <T> fastSpatialSpec() = MaterialTheme.motionScheme.fastSpatialSpec<T>()

@Composable
fun <T> slowSpatialSpec() = MaterialTheme.motionScheme.slowSpatialSpec<T>()

@Composable
fun <T> defaultEffectsSpec() = MaterialTheme.motionScheme.defaultEffectsSpec<T>()

@Composable
fun <T> fastEffectsSpec() = MaterialTheme.motionScheme.fastEffectsSpec<T>()

@Composable
fun <T> slowEffectsSpec() = MaterialTheme.motionScheme.slowEffectsSpec<T>()

@Composable
inline fun <T> lessSpringySpec() = MaterialTheme.motionScheme.defaultSpatialSpec<T>()

@Composable
inline fun <T> springySpec() = MaterialTheme.motionScheme.slowSpatialSpec<T>()

fun fancySlideTransition(
    isForward: Boolean,
    screenWidthPx: Int,
): ContentTransform = if (isForward) {
    slideInHorizontally(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        initialOffsetX = { screenWidthPx }
    ) + fadeIn(
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )
    ) togetherWith slideOutHorizontally(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        targetOffsetX = { -screenWidthPx }
    ) + fadeOut(
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )
    )
} else {
    slideInHorizontally(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        initialOffsetX = { -screenWidthPx }
    ) + fadeIn(
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )
    ) togetherWith slideOutHorizontally(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        targetOffsetX = { screenWidthPx }
    ) + fadeOut(
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )
    )
}
