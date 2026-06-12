package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    val borderWidth: Dp = 0.dp,
    val glowElevation: Dp = 0.dp,
    val focusModifier: Modifier = Modifier,
)

@Composable
fun rememberTvFocusState(
    focusedScale: Float = 1.08f,
    focusedBorderWidth: Dp = TvFocusDefaults.BorderWidth,
): TvFocusState {
    val isTv = LocalTvMode.current
    var isFocused by remember { mutableStateOf(false) }

    val motionScheme = MaterialTheme.motionScheme
    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "tvFocusScale",
    )

    val animatedBorder by animateDpAsState(
        targetValue = if (isFocused && isTv) focusedBorderWidth else 0.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "tvFocusBorder",
    )

    val animatedGlowElevation by animateDpAsState(
        targetValue = if (isFocused && isTv) TvFocusDefaults.GlowElevation else 0.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "tvFocusGlow",
    )

    val focusModifier = if (isTv) {
        Modifier.onFocusChanged { focusState ->
            isFocused = focusState.isFocused
        }
    } else {
        Modifier
    }

    return TvFocusState(
        isFocused = isFocused && isTv,
        scale = if (isTv) animatedScale else 1f,
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
    var isFocused by remember { mutableStateOf(false) }

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1f,
        label = "tvFocusScaleShared",
    )

    val animatedBorder by animateDpAsState(
        targetValue = if (isFocused && isTv) focusedBorderWidth else 0.dp,
        label = "tvFocusBorderShared",
    )

    val animatedGlowElevation by animateDpAsState(
        targetValue = if (isFocused && isTv) TvFocusDefaults.GlowElevation else 0.dp,
        label = "tvFocusGlowShared",
    )

    val focusModifier = if (isTv) {
        Modifier.onFocusChanged { focusState ->
            isFocused = focusState.isFocused
        }
    } else {
        Modifier
    }

    return TvFocusState(
        isFocused = isFocused && isTv,
        scale = if (isTv) animatedScale else 1f,
        borderWidth = animatedBorder,
        glowElevation = animatedGlowElevation,
        focusModifier = focusModifier,
    )
}

fun Modifier.tvFocusIndicator(
    focusState: TvFocusState,
    shape: Shape = RectangleShape,
): Modifier = composed {
    val isTv = LocalTvMode.current
    if (!isTv) return@composed this

    val glowColor = MaterialTheme.colorScheme.primary

    this
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
                    color = MaterialTheme.colorScheme.primary,
                    shape = shape,
                )
            } else {
                Modifier
            },
        )
}

fun Modifier.tvFocusChanged(onFocusChanged: (Boolean) -> Unit): Modifier = composed {
    val isTv = LocalTvMode.current
    if (!isTv) return@composed this

    this.onFocusChanged { focusState ->
        onFocusChanged(focusState.isFocused)
    }
}
