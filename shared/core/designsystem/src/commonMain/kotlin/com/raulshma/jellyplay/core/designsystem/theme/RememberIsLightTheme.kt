package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Returns whether the current [MaterialTheme] background is perceived as light,
 * using the standard luminance weights. Memoized on the background colour so it
 * does not recompute on every recomposition.
 *
 * Extracted from duplicate private copies previously inlined in
 * `MediaDetailScreen` and `SeerrDetailScreen`.
 */
@Composable
fun rememberIsLightTheme(): Boolean {
    val bg = MaterialTheme.colorScheme.background
    return remember(bg) {
        (bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f) > 0.5f
    }
}
