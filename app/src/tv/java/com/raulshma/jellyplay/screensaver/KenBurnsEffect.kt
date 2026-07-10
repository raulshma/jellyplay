package com.raulshma.jellyplay.screensaver

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlin.random.Random

data class KenBurnsTransform(
    val scaleFrom: Float,
    val scaleTo: Float,
    val panFromX: Float,
    val panToX: Float,
    val panFromY: Float,
    val panToY: Float,
) {
    companion object {
        fun random(): KenBurnsTransform {
            val scaleTo = Random.nextFloat() * 0.15f + 1.05f
            return KenBurnsTransform(
                scaleFrom = 1f,
                scaleTo = scaleTo,
                panFromX = Random.nextFloat() * 0.1f - 0.05f,
                panToX = Random.nextFloat() * 0.1f - 0.05f,
                panFromY = Random.nextFloat() * 0.1f - 0.05f,
                panToY = Random.nextFloat() * 0.1f - 0.05f,
            )
        }
    }
}

@Composable
fun KenBurnsImage(
    imageUrl: String,
    durationMs: Long,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val transform = remember(imageUrl) { KenBurnsTransform.random() }

    // Build the ImageRequest once per imageUrl. Previously this was constructed
    // inline in the composable body, so the per-frame `progress` recomposition
    // (driven by rememberInfiniteTransition at ~60 Hz) rebuilt thousands of
    // short-lived ImageRequest/Size/data-wrapper objects per minute while the
    // screensaver was active. Coil dedupes by URL, so the rebuild was wasted.
    val context = LocalContext.current
    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(1920, 1080)
            .build()
    }

    if (enabled && !com.raulshma.jellyplay.core.ui.components.LocalReducedMotion.current) {
        val infiniteTransition = rememberInfiniteTransition(label = "kenBurns")
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = durationMs.toInt(),
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Restart,
            ),
            label = "kenBurnsProgress",
        )

        val scale = transform.scaleFrom + (transform.scaleTo - transform.scaleFrom) * progress
        val panX = transform.panFromX + (transform.panToX - transform.panFromX) * progress
        val panY = transform.panFromY + (transform.panToY - transform.panFromY) * progress

        Box(
            modifier = modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = size.width * panX
                    translationY = size.height * panY
                },
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
    }
}
