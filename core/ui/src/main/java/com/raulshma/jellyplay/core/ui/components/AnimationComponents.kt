package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.PointToPointEasing

@Composable
fun StaggeredSection(
    visible: Boolean,
    index: Int,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(340, delayMillis = index * 70, easing = AlphaEasing),
        ) + slideInVertically(
            initialOffsetY = { it / 14 },
            animationSpec = tween(400, delayMillis = index * 70, easing = FancyTransitionEasing),
        ),
        exit = fadeOut(tween(160, easing = AlphaEasing)) + slideOutVertically(
            targetOffsetY = { -it / 24 },
            animationSpec = tween(180, easing = FancyTransitionEasing),
        ),
    ) {
        content()
    }
}

@Composable
fun AnimatedEntrance(
    visible: Boolean,
    delayMillis: Int = 0,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(350, delayMillis = delayMillis, easing = AlphaEasing)) +
                slideInVertically(
                    initialOffsetY = { it / 10 },
                    animationSpec = tween(400, delayMillis = delayMillis, easing = FancyTransitionEasing),
                ),
        exit = fadeOut(tween(200, easing = AlphaEasing)) + slideOutVertically(
            targetOffsetY = { it / 10 },
            animationSpec = tween(200, easing = FancyTransitionEasing)
        ),
        content = content,
    )
}

@Composable
fun AnimatedScaleEntrance(
    visible: Boolean,
    delayMillis: Int = 0,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300, delayMillis = delayMillis, easing = AlphaEasing)) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(400, delayMillis = delayMillis, easing = PointToPointEasing),
                ),
        exit = fadeOut(tween(150, easing = AlphaEasing)) +
                scaleOut(
                    targetScale = 0.92f,
                    animationSpec = tween(150, easing = PointToPointEasing),
                ),
        content = content,
    )
}

@Composable
fun rememberAnimatedItemVisibility(index: Int): Boolean {
    var visible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        visible = true
    }
    return visible
}

@Composable
fun AnimatedMediaItem(
    index: Int,
    delayPerItem: Int = 40,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(320, delayMillis = index * delayPerItem, easing = AlphaEasing),
        ) + slideInVertically(
            initialOffsetY = { it / 10 },
            animationSpec = tween(400, delayMillis = index * delayPerItem, easing = FancyTransitionEasing),
        ),
        exit = fadeOut(tween(140, easing = AlphaEasing)),
    ) {
        content()
    }
}
