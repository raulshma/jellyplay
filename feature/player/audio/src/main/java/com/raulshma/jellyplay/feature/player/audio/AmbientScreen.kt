package com.raulshma.jellyplay.feature.player.audio

import androidx.activity.compose.BackHandler
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkColors
import com.raulshma.jellyplay.core.designsystem.theme.rememberArtworkColors

@Composable
fun AmbientScreen(
    imageUrl: String?,
    title: String,
    artist: String,
    onTap: () -> Unit,
) {
    val artworkColors = rememberArtworkColors(imageUrl)
    val colors = extractAmbientColors(artworkColors)

    BackHandler { onTap() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .tvFocusable().clickable(onClick = onTap),
    ) {
        AmbientBackground(colors = colors)

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp)
                .padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "Tap to exit",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun AmbientBackground(colors: List<Color>) {
    val blobCount = 4
    val animatables = remember(blobCount) {
        List(blobCount) { index ->
            Animatable(initialValue = 0f).apply {
                val delay = index * 1000
                val duration = 8000 + index * 2000
            }
        }
    }

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

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Color.Black)

        val width = size.width
        val height = size.height

        val blobColors = colors.ifEmpty {
            listOf(
                Color(0xFF1a237e),
                Color(0xFF4a148c),
                Color(0xFF004d40),
                Color(0xFFb71c1c),
            )
        }

        blobColors.take(blobCount).forEachIndexed { index, color ->
            val progress = animatables[index].value
            val x = width * (0.2f + 0.6f * kotlin.math.sin(progress * 2 * Math.PI + index).toFloat())
            val y = height * (0.2f + 0.6f * kotlin.math.cos(progress * 2 * Math.PI + index * 1.5f).toFloat())
            val radius = (width.coerceAtMost(height) * 0.4f) * (0.8f + 0.2f * kotlin.math.sin(progress * Math.PI).toFloat())

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

private fun extractAmbientColors(artworkColors: ArtworkColors?): List<Color> {
    if (artworkColors == null) return emptyList()

    return listOfNotNull(
        artworkColors.vibrant,
        artworkColors.darkVibrant,
        artworkColors.lightVibrant,
        artworkColors.muted,
        artworkColors.darkMuted,
        artworkColors.lightMuted,
        artworkColors.dominant,
    ).map { it.copy(alpha = 1f) }
}
