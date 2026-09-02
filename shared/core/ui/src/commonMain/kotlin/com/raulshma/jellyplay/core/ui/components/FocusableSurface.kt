package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import com.raulshma.jellyplay.core.ui.tv.TvFocusState
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

@Stable
data class JellyFocusableInteraction(
    val isFocused: Boolean,
    val scale: Float,
    val modifier: Modifier,
    val focusState: TvFocusState,
)

@Composable
fun rememberJellyFocusableInteraction(
    focusedScale: Float = LocalJellyPlayUi.current.focus.focusedScale,
): JellyFocusableInteraction {
    val focusState = rememberTvFocusState(focusedScale = focusedScale)
    return JellyFocusableInteraction(
        isFocused = focusState.isFocused,
        scale = focusState.scale,
        modifier = focusState.focusModifier,
        focusState = focusState,
    )
}

@Composable
fun Modifier.jellyFocusIndicator(
    interaction: JellyFocusableInteraction,
    shape: Shape = ShapeCache.smooth12,
): Modifier = tvFocusIndicator(interaction.focusState, shape)

/**
 * Simple focus indicator modifier for any clickable/focusable element.
 * Usage: `.focusIndicator()` or `.focusIndicator(shape = CircleShape)`
 *
 * Tracks focus state and shows a subtle border when focused.
 * Works on both phone and TV.
 */
@Composable
fun Modifier.focusIndicator(
    shape: Shape = ShapeCache.smooth12,
): Modifier {
    val focusState = rememberTvFocusState()
    return this
        .then(focusState.focusModifier)
        .tvFocusIndicator(focusState, shape)
}
