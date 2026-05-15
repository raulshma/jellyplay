package com.raulshma.jellyplay.feature.library.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerLoadingGrid(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset = transition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )

    // Dark cinematic shimmer matching the library screen background
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.04f),
            Color.White.copy(alpha = 0.10f),
            Color.White.copy(alpha = 0.04f),
        ),
        start = Offset(shimmerOffset.value, 0f),
        end = Offset(shimmerOffset.value + 400f, 400f),
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false,
    ) {
        items(12, contentType = { "shimmer" }) {
            ShimmerCard(shimmerBrush)
        }
    }
}

@Composable
private fun ShimmerCard(brush: Brush) {
    Column(
        modifier = Modifier
            .clip(ShapeCache.smooth12)
            .background(Color.White.copy(alpha = 0.05f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .background(brush),
        )
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(12.dp)
                    .clip(ShapeCache.smooth4)
                    .background(brush),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(10.dp)
                    .clip(ShapeCache.smooth4)
                    .background(brush),
            )
        }
    }
}
