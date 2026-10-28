package com.raulshma.jellyplay.navigation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Full-scrim dismiss layer behind the overflow menu items. A tap anywhere
 * outside the items collapses the menu. Transparent — the menu is meant to feel
 * lightweight — but intercepts pointer input so taps don't fall through to the
 * screen beneath while open. Fades in smoothly on open and fades out on dismiss.
 */
@Composable
fun OverflowMenuScrim(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    exitDurationMillis: Int = OVERFLOW_MENU_EXIT_DURATION_MS,
) {
    val state = remember { MutableTransitionState(initialState = false) }
    state.targetState = visible

    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(animationSpec = tween(durationMillis = OVERFLOW_MENU_FADE_DURATION_MS)),
        exit = fadeOut(animationSpec = tween(durationMillis = exitDurationMillis)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }
}
