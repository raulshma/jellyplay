package com.raulshma.jellyplay.screensaver

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.DreamImage
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import kotlinx.coroutines.delay
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
fun DreamSlideshow(
    images: List<DreamImage>,
    intervalMs: Long,
    kenBurnsEnabled: Boolean,
    transitionStyle: DreamTransitionStyle,
    showTitle: Boolean,
    modifier: Modifier = Modifier,
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var isAnimating by remember { mutableStateOf(true) }

    LaunchedEffect(images.size) {
        currentIndex = 0
    }

    LaunchedEffect(isAnimating) {
        while (isAnimating && images.isNotEmpty()) {
            delay(intervalMs)
            currentIndex = (currentIndex + 1) % images.size
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (images.isEmpty()) {
            DreamEmptyState()
        } else {
            AnimatedContent(
                targetState = currentIndex,
                transitionSpec = { transitionSpecFor(transitionStyle) },
                label = "dreamSlideshow",
            ) { index ->
                val image = images[index % images.size]
                KenBurnsImage(
                    imageUrl = image.backdropUrl,
                    durationMs = intervalMs,
                    enabled = kenBurnsEnabled,
                )
            }

            if (showTitle) {
                val currentImage = images[currentIndex % images.size]
                DreamTitleOverlay(
                    title = currentImage.title,
                    categoryLabel = when (currentImage.type) {
                        com.raulshma.jellyplay.core.model.DreamImageCategory.MOVIES -> "Movie"
                        com.raulshma.jellyplay.core.model.DreamImageCategory.SERIES -> "TV Show"
                        com.raulshma.jellyplay.core.model.DreamImageCategory.MUSIC -> "Music"
                    },
                )
            }
        }
    }
}

@Composable
private fun DreamTitleOverlay(
    title: String,
    categoryLabel: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.BottomStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = categoryLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun DreamEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Tabler.Outline.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = "JellyPlay",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun AnimatedContentTransitionScope<Int>.transitionSpecFor(
    style: DreamTransitionStyle,
): ContentTransform = when (style) {
    DreamTransitionStyle.CROSSFADE -> fadeIn(animationSpec = tween(1500)) togetherWith
        fadeOut(animationSpec = tween(1500))
    DreamTransitionStyle.SLIDE -> (
        slideInHorizontally(
            animationSpec = tween(1200),
            initialOffsetX = { it },
        ) + fadeIn(animationSpec = tween(800))
        ) togetherWith (
        slideOutHorizontally(
            animationSpec = tween(1200),
            targetOffsetX = { -it },
        ) + fadeOut(animationSpec = tween(800))
        )
    DreamTransitionStyle.NONE -> fadeIn(animationSpec = tween(200)) togetherWith
        fadeOut(animationSpec = tween(200))
}
