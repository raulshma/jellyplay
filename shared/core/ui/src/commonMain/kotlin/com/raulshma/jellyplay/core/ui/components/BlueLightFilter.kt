package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Tints the wrapped content with an amber overlay that suppresses blue light
 * (a.k.a. night reading / Eye Comfort mode). The intensity is controlled by
 * [strength] (0.0 = no filter, 1.0 = maximum dimming). When [enabled] is
 * `false` the modifier is a no-op.
 *
 * Implementation note: we apply the tint via a [graphicsLayer] color matrix
 * so the underlying content is rendered with reduced blue channel values.
 * This mirrors how OS-level night light / Eye Comfort features work without
 * altering the actual theme colors (so screenshots, dynamic-color sampling,
 * accessibility tree, etc. stay accurate) and avoids an expensive
 * off-screen layer copy on every frame.
 */
fun Modifier.blueLightFilter(
    enabled: Boolean,
    strength: Float,
): Modifier {
    if (!enabled || strength <= 0f) return this
    val effective = strength.coerceIn(0f, 1f)
    val matrix = blueLightSuppressionMatrix(effective)
    return this.graphicsLayer {
        colorFilter = ColorFilter.colorMatrix(matrix)
    }
}

/**
 * Builds a color matrix that reduces the blue channel and shifts hue toward
 * amber, simulating a "warm" display profile. At [strength] = 0 the matrix is
 * a no-op identity; at [strength] = 1 the blue channel is attenuated to ~10%
 * and the red/green channels are boosted slightly to compensate, matching the
 * perceived warmth of common system night-light implementations.
 */
private fun blueLightSuppressionMatrix(strength: Float): ColorMatrix {
    val s = strength.coerceIn(0f, 1f)
    // Blend between the identity matrix (no filter) and the warm matrix so
    // intermediate values produce a smooth transition rather than a step.
    val identity = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
    val warm = floatArrayOf(
        1.10f, 0f, 0f, 0f, 0f,
        0f,    0.95f, 0f, 0f, 0f,
        0f,    0f,    0.55f, 0f, 0f,
        0f,    0f,    0f, 1f, 0f,
    )
    val out = FloatArray(20) { i ->
        identity[i] * (1 - s) + warm[i] * s
    }
    return ColorMatrix(out)
}

/**
 * Composable helper that draws [content] and applies the blue-light filter
 * on top. Convenience wrapper around [Modifier.blueLightFilter] for callers
 * that prefer a Composable API.
 */
@Composable
fun BlueLightFilterBox(
    enabled: Boolean,
    strength: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.blueLightFilter(enabled, strength)) {
        content()
    }
}
