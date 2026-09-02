package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Pure (non-composable) press-scale value. Unit-testable. Returns [defaultScale]
 * when pressed, 1f otherwise — or 1f always when [reducedMotion] is true.
 *
 * Press/focus micro-scales intentionally stay tactile even under reduced motion
 * (they are cheap draw-phase [graphicsLayer] updates, not recompositions); the
 * reduced-motion branch returns 1f so the [Modifier.pressScale] graphicsLayer
 * becomes a visual no-op while still being attached.
 */
fun pressScaleValueForLogic(
    isPressed: Boolean,
    reducedMotion: Boolean,
    defaultScale: Float = AnimationTokens.CardPressScale,
): Float = when {
    reducedMotion -> 1f
    isPressed -> defaultScale
    else -> 1f
}

/**
 * Applies a press-feedback scale via [graphicsLayer] (draw phase — no
 * recomposition). Reads press state from [interactionSource]. Animates through
 * [MaterialTheme.motionScheme] so reduced motion / performance mode flatten the
 * transition via the theme's [ReducedMotionScheme].
 *
 * Stays attached under reduced motion (the [graphicsLayer] simply resolves to
 * scale 1f) — press feedback is cheap, tactile, and essential.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    defaultScale: Float = AnimationTokens.CardPressScale,
    reducedMotion: Boolean = isReducedMotion(),
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = pressScaleValueForLogic(
        isPressed = isPressed,
        reducedMotion = reducedMotion,
        defaultScale = defaultScale,
    )
    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = animatedScale
        scaleY = animatedScale
    }
}

/**
 * TV focus-feedback scale via [graphicsLayer]. Scales up to [focusScale] when
 * the element has focus — essential DPAD-navigation feedback. No-op when
 * [reducedMotion] is true (still attaches the [graphicsLayer] as a visual
 * no-op). Cheap to leave attached on phone where [isFocused] is never true.
 */
@Composable
fun Modifier.focusScale(
    isFocused: Boolean,
    focusScale: Float = 1.05f,
    reducedMotion: Boolean = isReducedMotion(),
): Modifier {
    val target = if (reducedMotion) 1f else if (isFocused) focusScale else 1f
    val animatedScale by animateFloatAsState(
        targetValue = target,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "focusScale",
    )
    return this.graphicsLayer {
        scaleX = animatedScale
        scaleY = animatedScale
    }
}
