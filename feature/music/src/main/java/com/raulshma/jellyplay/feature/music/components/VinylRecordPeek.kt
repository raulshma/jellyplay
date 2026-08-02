package com.raulshma.jellyplay.feature.music.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.components.LocalReducedMotion

@Composable
fun VinylRecordPeek(
    isHoveredOrFocused: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    labelColor: Color = MaterialTheme.colorScheme.primary,
) {
    val reducedMotion = LocalReducedMotion.current
    val slideFraction by animateFloatAsState(
        targetValue = if (isHoveredOrFocused) 0.35f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "vinylSlide"
    )

    // The vinyl rotation is only meaningful while hovered/focused. This
    // composable is a grid/list item, so allocating an infinite transition per
    // card spins a redraw coroutine for every visible album — even when the
    // card is at rest (slideFraction == 0). Gate the transition behind the
    // hover state so cards at rest pay zero animation cost. Reduced-motion and
    // idle cards fall back to a static 0f.
    val rotation = if (!reducedMotion && isHoveredOrFocused) {
        rememberInfiniteTransition(label = "vinylRotationTransition").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "vinylRotation"
        ).value
    } else {
        0f
    }

    Box(
        modifier = modifier
            .size(size)
            .offset(x = size * slideFraction)
            .rotate(if (slideFraction > 0.05f) rotation else 0f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.Center
    ) {
        val grooveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val maxRadius = size.toPx() / 2f

            val grooveSpacing = 12f
            var currentRadius = maxRadius - 10f
            while (currentRadius > maxRadius * 0.42f) {
                drawCircle(
                    color = grooveColor,
                    radius = currentRadius,
                    center = center,
                    style = Stroke(width = 1f)
                )
                currentRadius -= grooveSpacing
            }
        }

        Box(
            modifier = Modifier
                .size(size * 0.35f)
                .clip(CircleShape)
                .background(labelColor),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(size * 0.08f)
                    .clip(CircleShape)
                    .background(Color.Black)
            )
        }
    }
}
