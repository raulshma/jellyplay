package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi

object TvFocusDefaults {
    val BorderWidth = 2.dp
    val GlowElevation = 16.dp
    const val GlowAmbientAlpha = 0.5f
    const val GlowSpotAlpha = 0.3f
}

@Stable
data class TvFocusState(
    val isFocused: Boolean = false,
    val scale: Float = 1f,
    val alphaProvider: () -> Float = { 1f },
    val borderWidth: Dp = 0.dp,
    val glowElevation: Dp = 0.dp,
    val focusModifier: Modifier = Modifier,
) {
    val alpha: Float get() = alphaProvider()
}

@Composable
fun rememberTvFocusState(
    focusedScale: Float = 1.08f,
    focusedBorderWidth: Dp = TvFocusDefaults.BorderWidth,
): TvFocusState {
    val isTv = LocalTvMode.current
    val focusTokens = LocalJellyPlayUi.current.focus
    var isFocused by remember { mutableStateOf(false) }

    val motionScheme = MaterialTheme.motionScheme
    val reducedMotion = com.raulshma.jellyplay.core.ui.components.LocalReducedMotion.current

    // Breathing fade alpha animation (TV only). On non-TV devices the breathing
    // fraction is always 0 so the effect is invisible; skip constructing the
    // infinite transition entirely to avoid a continuous animation coroutine
    // driving recomposition on every focusable item. Also skip under reduce
    // motion / performance mode.
    val alphaProvider = if (isTv && !reducedMotion) {
        val infiniteTransition = rememberInfiniteTransition(label = "tvFocusBreathing")
        val breathingAlpha = infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.65f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathingAlpha"
        )

        val breathingFraction = animateFloatAsState(
            targetValue = if (isFocused) 1f else 0f,
            animationSpec = motionScheme.fastSpatialSpec(),
            label = "breathingFraction"
        )

        // Wrap in lambda to avoid recomposition on every animation frame
        remember(breathingAlpha, breathingFraction) {
            {
                1f - (1f - breathingAlpha.value) * breathingFraction.value
            }
        }
    } else {
        remember { { 1f } }
    }

    val animatedBorder by animateDpAsState(
        targetValue = if (isFocused) {
            if (isTv) focusedBorderWidth else focusTokens.borderWidth
        } else 0.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "tvFocusBorder",
    )

    val animatedGlowElevation by animateDpAsState(
        targetValue = if (isFocused && isTv) TvFocusDefaults.GlowElevation else 0.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "tvFocusGlow",
    )

    val focusModifier = Modifier.onFocusChanged { focusState ->
        isFocused = focusState.isFocused
    }

    return TvFocusState(
        isFocused = isFocused,
        scale = 1f, // Always 1f to disable scaling on TV focused items
        alphaProvider = alphaProvider,
        borderWidth = animatedBorder,
        glowElevation = animatedGlowElevation,
        focusModifier = focusModifier,
    )
}

@Composable
fun rememberRowSharedFocusState(
    focusedScale: Float = 1.08f,
    focusedBorderWidth: Dp = TvFocusDefaults.BorderWidth,
): TvFocusState {
    val isTv = LocalTvMode.current
    val focusTokens = LocalJellyPlayUi.current.focus
    var isFocused by remember { mutableStateOf(false) }
    val reducedMotion = com.raulshma.jellyplay.core.ui.components.LocalReducedMotion.current

    // Breathing fade alpha animation (TV only). See rememberTvFocusState for
    // rationale on gating behind isTv. Also skipped under reduce motion /
    // performance mode.
    val alphaProvider = if (isTv && !reducedMotion) {
        val infiniteTransition = rememberInfiniteTransition(label = "tvFocusBreathingShared")
        val breathingAlpha = infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.65f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathingAlphaShared"
        )

        val breathingFraction = animateFloatAsState(
            targetValue = if (isFocused) 1f else 0f,
            label = "breathingFractionShared"
        )

        // Wrap in lambda to avoid recomposition on every animation frame
        remember(breathingAlpha, breathingFraction) {
            {
                1f - (1f - breathingAlpha.value) * breathingFraction.value
            }
        }
    } else {
        remember { { 1f } }
    }

    val animatedBorder by animateDpAsState(
        targetValue = if (isFocused) {
            if (isTv) focusedBorderWidth else focusTokens.borderWidth
        } else 0.dp,
        label = "tvFocusBorderShared",
    )

    val animatedGlowElevation by animateDpAsState(
        targetValue = if (isFocused && isTv) TvFocusDefaults.GlowElevation else 0.dp,
        label = "tvFocusGlowShared",
    )

    val focusModifier = Modifier.onFocusChanged { focusState ->
        isFocused = focusState.isFocused
    }

    return TvFocusState(
        isFocused = isFocused,
        scale = 1f, // Always 1f to disable scaling on TV focused items
        alphaProvider = alphaProvider,
        borderWidth = animatedBorder,
        glowElevation = animatedGlowElevation,
        focusModifier = focusModifier,
    )
}

@Composable
fun Modifier.tvFocusIndicator(
    focusState: TvFocusState,
    shape: Shape = RectangleShape,
    color: Color? = null,
): Modifier {
    val glowColor = color ?: MaterialTheme.colorScheme.primary
    val borderColor = color ?: MaterialTheme.colorScheme.primary

    return this
        .graphicsLayer {
            alpha = focusState.alphaProvider()
        }
        .then(
            if (focusState.glowElevation > 0.dp) {
                Modifier.shadow(
                    elevation = focusState.glowElevation,
                    shape = shape,
                    clip = false,
                    ambientColor = glowColor.copy(alpha = TvFocusDefaults.GlowAmbientAlpha),
                    spotColor = glowColor.copy(alpha = TvFocusDefaults.GlowSpotAlpha),
                )
            } else {
                Modifier
            },
        )
        .then(
            if (focusState.borderWidth > 0.dp) {
                Modifier.border(
                    width = focusState.borderWidth,
                    color = borderColor,
                    shape = shape,
                )
            } else {
                Modifier
            },
        )
}

fun Modifier.tvFocusChanged(onFocusChanged: (Boolean) -> Unit): Modifier =
    this.onFocusChanged { focusState ->
        onFocusChanged(focusState.isFocused)
    }
