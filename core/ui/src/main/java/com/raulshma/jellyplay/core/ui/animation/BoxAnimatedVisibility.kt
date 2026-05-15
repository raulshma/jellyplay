package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import androidx.compose.animation.core.tween

@Composable
fun BoxAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: androidx.compose.animation.EnterTransition = fadeIn(
        tween(AnimationTokens.MediumDuration, easing = AlphaEasing)
    ) + expandIn(),
    exit: androidx.compose.animation.ExitTransition = shrinkOut() + fadeOut(
        tween(AnimationTokens.DefaultDuration, easing = AlphaEasing)
    ),
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) = AnimatedVisibility(visible, modifier, enter, exit, label, content)
