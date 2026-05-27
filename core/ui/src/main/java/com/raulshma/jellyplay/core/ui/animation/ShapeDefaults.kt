package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun shapeByInteraction(
    shape: Shape,
    pressedShape: Shape,
    interactionSource: InteractionSource?,
    animationSpec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.fastSpatialSpec(),
    enabled: Boolean = true,
): Shape {
    if (!enabled || interactionSource == null) return shape

    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val usePressedShape = pressed || focused
    val targetShape = if (usePressedShape) pressedShape else shape

    if (targetShape is CornerBasedShape && shape is CornerBasedShape) {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(usePressedShape) {
            progress.animateTo(
                targetValue = if (usePressedShape) 1f else 0f,
                animationSpec = animationSpec,
            )
        }
        val p = progress.value
        val density = LocalDensity.current
        val size = Size.Unspecified
        return RoundedCornerShape(
            topStart = lerpCornerSizeDp(shape.topStart, targetShape.topStart, p, density, size),
            topEnd = lerpCornerSizeDp(shape.topEnd, targetShape.topEnd, p, density, size),
            bottomEnd = lerpCornerSizeDp(shape.bottomEnd, targetShape.bottomEnd, p, density, size),
            bottomStart = lerpCornerSizeDp(shape.bottomStart, targetShape.bottomStart, p, density, size),
        )
    }

    return targetShape
}

private fun lerpCornerSizeDp(
    from: CornerSize,
    to: CornerSize,
    progress: Float,
    density: androidx.compose.ui.unit.Density,
    size: Size,
): CornerSize {
    val fromPx = from.toPx(size, density)
    val toPx = to.toPx(size, density)
    val lerpedPx = fromPx + (toPx - fromPx) * progress
    return CornerSize(lerpedPx)
}
