package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.raulshma.jellyplay.core.designsystem.theme.AmbientColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * A full-screen decorative background built from a small number of slowly
 * drifting radial-gradient "blobs" painted onto a black canvas. The intended
 * use is as an *ambient* backdrop layer (e.g. behind the home screen) where a
 * full-resolution image is unavailable or undesirable.
 *
 * Adapted from the audio player's private `AmbientBackground` so the same
 * effect is reusable app-wide. Key differences:
 *  - [blobCount] defaults to 3 (the home screen sits behind dense lists, so a
 *    subtler field reads better than the audio screen's 4).
 *  - the base fill is drawn by the caller (no opaque `Color.Black` here), so
 *    this layer can sit transparently over a tinted background colour.
 *
 * @param colors the palette to sample blobs from. When empty, falls back to
 *  [AmbientColors] deep tones.
 * @param blobCount how many drifting blobs to render. Each drives its own
 *  infinite animation, so keep this small for performance.
 */
@Composable
fun AmbientColorBackdrop(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    blobCount: Int = 3,
) {
    val reducedMotion = LocalReducedMotion.current
    val animatables = remember(blobCount) {
        List(blobCount) { Animatable(initialValue = 0f) }
    }

    // Each blob runs its own slow infinite animation. Frozen under reduced
    // motion (the LaunchedEffect bodies are skipped, values stay 0f) which also
    // serves as the performance-mode freeze — callers gate this composable on
    // performance mode themselves when they want zero animation cost.
    if (!reducedMotion) {
        animatables.forEachIndexed { index, animatable ->
            LaunchedEffect(index) {
                animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 10000 + index * 3000,
                            easing = LinearEasing,
                        ),
                        repeatMode = RepeatMode.Reverse,
                    ),
                )
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val blobColors = colors.ifEmpty {
            listOf(
                AmbientColors.deepIndigo,
                AmbientColors.deepPurple,
                AmbientColors.deepTeal,
                AmbientColors.deepRed,
            )
        }

        blobColors.take(blobCount).forEachIndexed { index, color ->
            val progress = animatables[index].value
            val x = width * (0.2f + 0.6f * sin(progress * 2f * Math.PI.toFloat() + index))
            val y = height * (0.2f + 0.6f * cos(progress * 2f * Math.PI.toFloat() + index * 1.5f))
            val radius = (width.coerceAtMost(height) * 0.4f) *
                (0.8f + 0.2f * sin(progress * Math.PI.toFloat()))

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.6f),
                        color.copy(alpha = 0.2f),
                        Color.Transparent,
                    ),
                    center = Offset(x, y),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(x, y),
            )
        }
    }
}
