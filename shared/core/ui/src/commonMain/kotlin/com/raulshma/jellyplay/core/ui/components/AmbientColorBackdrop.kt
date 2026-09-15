package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.raulshma.jellyplay.core.designsystem.theme.AmbientColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Minimum time between blob phase updates (~30 Hz). The blobs drift over
// 10-22 s, so display-rate (60-120 Hz) phase writes are wasted full-screen
// redraws; a time-based gate yields ~30 Hz on any display refresh rate.
private const val MIN_PHASE_UPDATE_INTERVAL_NS = 33_000_000L

// Triangle-wave blob progress (0→1→0) with the same per-blob periods the
// Animatable spec produced (10 s + index * 3 s, linear, reversed). Derived
// from the frame timestamp, so the motion is unchanged — just stepped.
private fun blobDriftProgress(nanos: Long, index: Int): Float {
    val periodNanos = (10_000L + index * 3_000L) * 2 * 1_000_000L
    val fraction = (nanos % periodNanos).toFloat() / periodNanos
    return if (fraction < 0.5f) fraction * 2f else 2f - fraction * 2f
}

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
 * @param blobCount how many drifting blobs to render. All step from one
 *  shared ~30 Hz-gated frame clock, so keep this small for performance.
 */
@Composable
fun AmbientColorBackdrop(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    blobCount: Int = 3,
) {
    val reducedMotion = LocalReducedMotion.current
    val blobProgress = remember(blobCount) {
        List(blobCount) { mutableFloatStateOf(0f) }
    }

    // Resolve the blob palette + the per-blob 3-stop gradient stops ONCE (keyed
    // on the palette). The Canvas below redraws on every gated phase step
    // (~30 Hz over a 10-22s drift), and previously allocated a fresh
    // List<Color> per blob per frame just to build the radialGradient stops.
    // The center/radius still vary per frame, but the stop colors are identical
    // for a given palette, so hoisting them out of the draw phase removes that
    // churn.
    val blobStops = rememberBlobStops(colors, blobCount)

    // Each brush is built ONCE at a nominal radius of 1 and drawn through a
    // translate+scale transform; a uniformly scaled radial gradient is
    // mathematically identical to one built at the frame's radius, so the draw
    // pass no longer allocates a fresh shader per blob per frame.
    val blobBrushes = remember(blobStops) {
        blobStops.map { stops ->
            Brush.radialGradient(colors = stops, center = Offset.Zero, radius = 1f)
        }
    }

    // The blobs drift on a shared ~30 Hz-gated frame clock (see
    // [LaunchBlobDrift]) instead of per-blob Animatables. Frozen
    // under reduced motion (the effect body is skipped, values stay 0f) which
    // also serves as the performance-mode freeze — callers gate this
    // composable on performance mode themselves when they want zero animation
    // cost.
    if (!reducedMotion) {
        LaunchBlobDrift(blobProgress)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        blobBrushes.forEachIndexed { index, brush ->
            val progress = blobProgress[index].floatValue
            val x = width * (0.2f + 0.6f * sin(progress * 2f * PI.toFloat() + index))
            val y = height * (0.2f + 0.6f * cos(progress * 2f * PI.toFloat() + index * 1.5f))
            val radius = (width.coerceAtMost(height) * 0.4f) *
                (0.8f + 0.2f * sin(progress * PI.toFloat()))

            translate(x, y) {
                scale(radius, radius, pivot = Offset.Zero) {
                    drawCircle(brush = brush, radius = 1f, center = Offset.Zero)
                }
            }
        }
    }
}

/**
 * Steps [blobProgress] on the shared ~30 Hz-gated frame clock: each entry
 * receives its triangle-wave drift phase ([blobDriftProgress]) derived from
 * the frame timestamp, skipping frame callbacks closer together than
 * [MIN_PHASE_UPDATE_INTERVAL_NS]. Shared by [AmbientColorBackdrop] and the
 * audio player's `AmbientBackground` so the gated clock loop lives in one
 * place. Not composing this (the reduced-motion path) freezes the blobs at
 * their current values.
 */
@Composable
fun LaunchBlobDrift(blobProgress: List<MutableFloatState>) {
    LaunchedEffect(blobProgress) {
        var lastPhaseUpdateNanos = 0L
        while (true) {
            withFrameNanos { nanoTime ->
                if (nanoTime - lastPhaseUpdateNanos >= MIN_PHASE_UPDATE_INTERVAL_NS) {
                    lastPhaseUpdateNanos = nanoTime
                    blobProgress.forEachIndexed { index, progress ->
                        progress.floatValue = blobDriftProgress(nanoTime, index)
                    }
                }
            }
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
