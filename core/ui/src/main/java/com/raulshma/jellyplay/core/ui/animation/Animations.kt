package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
        animationSpec = androidx.compose.animation.core.tween(600, easing = FancyTransitionEasing),
        initialOffsetX = { screenWidthPx }
    ) + fadeIn(
        androidx.compose.animation.core.tween(300, 100, AlphaEasing)
    ) togetherWith slideOutHorizontally(
        animationSpec = androidx.compose.animation.core.tween(600, easing = FancyTransitionEasing),
        targetOffsetX = { -screenWidthPx }
    ) + fadeOut(
        androidx.compose.animation.core.tween(300, 100, AlphaEasing)
    )
} else {
    slideInHorizontally(
        animationSpec = androidx.compose.animation.core.tween(600, easing = FancyTransitionEasing),
        initialOffsetX = { -screenWidthPx }
    ) + fadeIn(
        androidx.compose.animation.core.tween(300, 100, AlphaEasing)
    ) togetherWith slideOutHorizontally(
        animationSpec = androidx.compose.animation.core.tween(600, easing = FancyTransitionEasing),
        targetOffsetX = { screenWidthPx }
    ) + fadeOut(
        androidx.compose.animation.core.tween(300, 100, AlphaEasing)
    )
}
