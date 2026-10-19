package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache

/**
 * The three bar heights of the shared "now playing" equalizer indicator,
 * in 0..1 fractions of the 12.dp bar height.
 */
data class NowPlayingBarHeights(
    val bar1: Float,
    val bar2: Float,
    val bar3: Float,
)

/**
 * HOISTED animation source for the shared [NowPlayingBars] indicator:
 * settings' active-devices rows and the admin dashboard's
 * active-sessions rows each used to run their own byte-identical
 * [rememberInfiniteTransition] copy — an N-row screen drove N per-row
 * animation clocks redrawing in lockstep, buying nothing per row. Call this
 * ONCE per screen (at the row-list level) and hand the result to every
 * [NowPlayingBars]; one clock, identical visuals. Reduced-motion freezes the
 * bars at their static mid heights, exactly like the private copies did.
 */
@Composable
fun rememberNowPlayingBarHeights(): NowPlayingBarHeights {
    if (LocalReducedMotion.current) return NowPlayingBarHeights(bar1 = 0.6f, bar2 = 0.5f, bar3 = 0.7f)
    val infiniteTransition = rememberInfiniteTransition(label = "nowPlaying")
    val bar1 = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bar1",
    )
    val bar2 = infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bar2",
    )
    val bar3 = infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bar3",
    )
    return NowPlayingBarHeights(bar1.value, bar2.value, bar3.value)
}

/**
 * The three-bar "now playing" equalizer glyph (12.dp tall, 2.dp bars). Reads
 * hoisted [heights] — see [rememberNowPlayingBarHeights] for why the
 * transition lives at the caller. Clips through the shared [ShapeCache.smooth4]
 * like the deleted per-module copies did (this composable redraws every
 * animation frame; a fresh shape per bar per frame is churn the cache exists
 * to avoid).
 */
@Composable
fun NowPlayingBars(
    heights: NowPlayingBarHeights,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(12.dp * heights.bar1)
                .clip(ShapeCache.smooth4)
                .background(color),
        )
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(12.dp * heights.bar2)
                .clip(ShapeCache.smooth4)
                .background(color),
        )
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(12.dp * heights.bar3)
                .clip(ShapeCache.smooth4)
                .background(color),
        )
    }
}
