package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun BoxAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: androidx.compose.animation.EnterTransition = fadeIn(
        MaterialTheme.motionScheme.defaultEffectsSpec()
    ) + expandIn(),
    exit: androidx.compose.animation.ExitTransition = shrinkOut() + fadeOut(
        MaterialTheme.motionScheme.fastEffectsSpec()
    ),
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) = AnimatedVisibility(visible, modifier, enter, exit, label, content)
