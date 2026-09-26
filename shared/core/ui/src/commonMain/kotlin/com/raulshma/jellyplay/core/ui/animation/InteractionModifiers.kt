package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.animation.core.AnimationSpec
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
 * Reduced-motion posture (settled): press scales FLATTEN. Under reduced motion
 * this returns 1f so the [Modifier.pressScale] [graphicsLayer] becomes a visual
 * no-op while still attached — micro-movement is exactly what reduced motion
 * opts out of. [pressScaleValueForLogic] and `rememberCardChrome`'s press arm
 * share this contract; chrome that must keep scaling under reduced motion opts in
 * explicitly (`CardChrome.pressScaleOverridesReducedMotion`), never by default.
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
 * The ONE press-feedback modifier: scale (optionally alpha dim) via
 * [graphicsLayer] (draw phase — no recomposition). Reads press state from
 * [interactionSource] — pass the SAME source the site's click/long-press uses,
 * never a new one. Animates through [MaterialTheme.motionScheme] so reduced
 * motion / performance mode flatten the transition via the theme's
 * [ReducedMotionScheme].
 *
 * Reduced-motion posture: the SCALE flattens to 1f (see
 * [pressScaleValueForLogic]) — the modifier stays attached, the [graphicsLayer]
 * simply resolving to scale 1f. A supplied [pressedAlpha] still dims while
 * pressed: an opacity change is not motion (matches the historical alpha arm).
 *
 * @param defaultScale pressed scale value — the settings/list-row idiom is
 *   0.97f; the card family uses [AnimationTokens.CardPressScale].
 * @param spec scale-transition spec. Defaults to the theme's default spatial
 *   spec; pass the site's historical `fastSpatialSpec()`/`fastEffectsSpec()` to
 *   keep its exact motion.
 * @param pressedAlpha pressed alpha dim (e.g. 0.7f) animated with the theme's
 *   fast effects spec, or null for no alpha arm.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    defaultScale: Float = AnimationTokens.CardPressScale,
    spec: AnimationSpec<Float> = MaterialTheme.motionScheme.defaultSpatialSpec(),
    pressedAlpha: Float? = null,
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
        animationSpec = spec,
        label = "pressScale",
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isPressed && pressedAlpha != null) pressedAlpha else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "pressScaleAlpha",
    )
    return this.graphicsLayer {
        scaleX = animatedScale
        scaleY = animatedScale
        if (pressedAlpha != null) this.alpha = animatedAlpha
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
