package com.raulshma.jellyplay.core.ui.player

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor

/**
 * Top-of-screen gradient scrim for player chrome (strongest at the top edge).
 * Extracted from :feature:player:video's PlayerControls so the live player
 * can reuse the same look. Must be called from a composable scope.
 */
@Composable
fun Modifier.playerTopScrim(): Modifier {
    val scrim = playerScrimColor()
    return remember(scrim) {
        Modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    scrim.copy(alpha = 0.6f),
                    scrim.copy(alpha = 0.3f),
                    scrim.copy(alpha = 0.08f),
                    Color.Transparent,
                ),
            ),
        )
    }
}

/**
 * Bottom-of-screen gradient scrim for player chrome (strongest at the bottom edge).
 * Must be called from a composable scope.
 */
@Composable
fun Modifier.playerBottomScrim(): Modifier {
    val scrim = playerScrimColor()
    return remember(scrim) {
        Modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    scrim.copy(alpha = 0.1f),
                    scrim.copy(alpha = 0.5f),
                    scrim.copy(alpha = 0.7f),
                ),
            ),
        )
    }
}
