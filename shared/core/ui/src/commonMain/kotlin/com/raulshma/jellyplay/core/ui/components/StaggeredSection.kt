package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Reveals its content with a staggered fade-and-rise, mirroring the online
 * detail screen's `StaggeredDetailSection`. Each section starts ~45ms after
 * the previous one so a detail body reveals top-to-bottom within ~300ms.
 *
 * @param delayIndex ordinal of this section (0-based); higher = later reveal.
 */
@Composable
fun StaggeredSection(
    delayIndex: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress = rememberStaggerProgress(delayIndex)
    Box(
        modifier = modifier.graphicsLayer {
            val p = progress.value
            alpha = p
            translationY = (1f - p) * 24f
        },
    ) {
        content()
    }
}

@Composable
private fun rememberStaggerProgress(delayIndex: Int): State<Float> {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayIndex * 45L)
        revealed = true
    }
    return animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "staggerSection$delayIndex",
    )
}
