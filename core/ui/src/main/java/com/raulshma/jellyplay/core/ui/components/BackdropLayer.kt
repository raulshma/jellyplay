package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.image.MediaImage

/**
 * The fixed backdrop layer behind the scrolling content. Matches the online
 * detail screen: image with parallax-ready scale + a 4-stop vertical gradient
 * scrim that fades to the surface color where content begins.
 *
 * @param backdropUrl optional backdrop image URL (e.g. server URL online, local
 *   file path offline). When null/blank, a [MaterialTheme.colorScheme.surfaceVariant]
 *   block is shown instead.
 * @param scrollTranslationY vertical translation applied to the image+scrim (use
 *   `{ -scrollOffset * 0.5f }` for half-speed parallax). Supplied as a lambda and
 *   read inside the layer/draw phase so scroll-driven values don't recompose the
 *   backdrop. `{ 0f }` when not parallaxing.
 * @param scrollAlpha opacity of the image+scrim (use
 *   `{ 1f - (scrollFraction * 0.8f) }` so it fades out as the user scrolls).
 *   `{ 1f }` when not fading.
 * @param height the full backdrop height; the gradient starts at `(height / 1.2f) - 200dp`.
 */
@Composable
fun BackdropLayer(
    backdropUrl: String?,
    blurHash: String?,
    height: Dp,
    scrollTranslationY: () -> Float = { 0f },
    scrollAlpha: () -> Float = { 1f },
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val baseBackdropHeight = height / 1.2f
    val surface = MaterialTheme.colorScheme.surface
    // Build the gradient in composable scope (MaterialTheme.colorScheme is a
    // @Composable read and can't be accessed inside drawWithCache's DrawScope).
    val startYPx = with(density) { (baseBackdropHeight - 200.dp).toPx() }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer {
                    translationY = scrollTranslationY()
                    alpha = scrollAlpha()
                },
        ) {
            if (!backdropUrl.isNullOrBlank()) {
                MediaImage(
                    url = backdropUrl,
                    contentDescription = null,
                    blurHash = blurHash,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            // 4-stop gradient scrim, identical to the online detail screen. The
            // Brush is built in the cache phase (rebuilt only when the size or
            // scrim colors change), not on every draw pass.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val scrimBrush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                surface.copy(alpha = 0.4f),
                                surface.copy(alpha = 0.9f),
                                surface,
                            ),
                            startY = startYPx,
                            endY = size.height,
                        )
                        onDrawBehind {
                            drawRect(scrimBrush)
                        }
                    },
            )
        }
    }
}
