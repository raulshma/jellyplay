package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Horizontally fades the edges of a horizontally-scrollable row so users can
 * tell there is more content to scroll to. Each fade is only drawn while there
 * is content beyond that edge, and disappears once the row reaches its end.
 *
 * Requires an offscreen compositing layer so the [BlendMode.DstIn] mask keeps
 * the faded pixels fully transparent instead of blending against the parent.
 */
fun Modifier.horizontalFadingEdges(
    scrollState: ScrollState,
    length: Dp = 16.dp,
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithCache {
        val width = size.width
        val lengthPx = length.toPx()
        val leadingFade = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.Black),
            startX = 0f,
            endX = lengthPx,
        )
        val trailingFade = Brush.horizontalGradient(
            colors = listOf(Color.Black, Color.Transparent),
            startX = width - lengthPx,
            endX = width,
        )

        onDrawWithContent {
            drawContent()

            if (scrollState.value > 0) {
                drawRect(
                    brush = leadingFade,
                    blendMode = BlendMode.DstIn,
                )
            }

            if (scrollState.value < scrollState.maxValue) {
                drawRect(
                    brush = trailingFade,
                    blendMode = BlendMode.DstIn,
                )
            }
        }
    }
