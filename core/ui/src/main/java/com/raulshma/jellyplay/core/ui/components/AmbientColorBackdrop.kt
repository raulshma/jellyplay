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

    // Resolve the blob palette + the per-blob 3-stop gradient stops ONCE (keyed
    // on the palette). The Canvas below redraws every animation frame (~60fps
    // over a 10-22s drift), and previously allocated a fresh List<Color> per
    // blob per frame just to build the radialGradient stops. The center/radius
    // still vary per frame, but the stop colors are identical for a given
    // palette, so hoisting them out of the draw phase removes that churn.
    val blobStops = rememberBlobStops(colors, blobCount)

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

        blobStops.forEachIndexed { index, stops ->
            val progress = animatables[index].value
            val x = width * (0.2f + 0.6f * sin(progress * 2f * Math.PI.toFloat() + index))
            val y = height * (0.2f + 0.6f * cos(progress * 2f * Math.PI.toFloat() + index * 1.5f))
            val radius = (width.coerceAtMost(height) * 0.4f) *
                (0.8f + 0.2f * sin(progress * Math.PI.toFloat()))

            drawCircle(
                brush = Brush.radialGradient(
                    colors = stops,
                    center = Offset(x, y),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(x, y),
            )
        }
    }
}

/**
 * Resolves the per-blob 3-stop radial-gradient colour stops for a palette,
 * memoised on ([colors], [blobCount]). Shared by [AmbientColorBackdrop] and
 * the audio player's `AmbientBackground` so the palette → stops projection
 * (the `ifEmpty` fallback + the alpha-stop mapping) lives in one place
 * instead of being duplicated across both ambient surfaces.
 *
 * Center/radius still vary per draw frame; only the stop colours are hoisted.
 */
@Composable
fun rememberBlobStops(colors: List<Color>, blobCount: Int): List<List<Color>> =
    remember(colors, blobCount) {
        val blobColors = colors.ifEmpty {
            listOf(
                AmbientColors.deepIndigo,
                AmbientColors.deepPurple,
                AmbientColors.deepTeal,
                AmbientColors.deepRed,
            )
        }
        blobColors.take(blobCount).map { color ->
            listOf(color.copy(alpha = 0.6f), color.copy(alpha = 0.2f), Color.Transparent)
        }
    }
