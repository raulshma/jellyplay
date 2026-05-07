package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.isTvDevice
import com.raulshma.jellyplay.core.ui.tv.tvFocusable

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PosterCard(
    item: MediaItem,
    imageUrl: String,
    fallbackUrls: List<String> = emptyList(),
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
    progressPercent: Float = 0f,
    blurHash: String? = null,
    sharedElementKey: String? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
) {
    val isTv = isTvDevice()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(150),
        label = "cardScale",
    )
    val sharedTransitionScope = LocalSharedTransitionScope.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .then(if (isTv) Modifier.tvFocusable() else Modifier)
            .then(if (isTv) Modifier.shadow(12.dp, RoundedCornerShape(12.dp)) else Modifier),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isTv) 12.dp else 4.dp),
    ) {
        Box {
            val imageModifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)

            val resolvedModifier = if (
                sharedElementKey != null &&
                sharedTransitionScope != null &&
                animatedVisibilityScope != null
            ) {
                with(sharedTransitionScope) {
                    imageModifier.sharedElement(
                        rememberSharedContentState(key = sharedElementKey),
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            } else {
                imageModifier
            }

            MediaImage(
                url = imageUrl,
                fallbackUrls = fallbackUrls,
                contentDescription = item.name,
                blurHash = blurHash,
                modifier = resolvedModifier,
                contentScale = ContentScale.Crop,
            )

            if (showProgress && progressPercent > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progressPercent)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            if (item.isPlayed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "Watched",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(8.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.year != null) {
                Text(
                    text = item.year.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun MediaRow(
    title: String,
    items: List<MediaItem>,
    imageUrlBuilder: (MediaItem) -> String,
    fallbackImageUrlBuilder: (MediaItem) -> List<String> = { emptyList() },
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    blurHashBuilder: (MediaItem) -> String? = { it.blurHashes.primary },
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val adaptiveInfo = LocalAdaptiveInfo.current
        val cardWidth = when (adaptiveInfo.windowSizeClass) {
            WindowSizeClass.Expanded -> 200.dp
            WindowSizeClass.Medium -> 180.dp
            WindowSizeClass.Compact -> 160.dp
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { "${title}_${it.id}" }, contentType = { "mediaItem" }) { item ->
                PosterCard(
                    item = item,
                    imageUrl = imageUrlBuilder(item),
                    fallbackUrls = fallbackImageUrlBuilder(item),
                    onClick = { onItemClick(item) },
                    modifier = Modifier.width(cardWidth),
                    showProgress = item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0,
                    progressPercent = if (item.runTimeTicks != null && item.runTimeTicks!! > 0) {
                        (item.playbackPositionTicks?.toFloat() ?: 0f) / item.runTimeTicks!!.toFloat()
                    } else 0f,
                    blurHash = blurHashBuilder(item),
                )
            }
        }
    }
}

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorScreen(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.TextButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
fun StaggeredSection(
    visible: Boolean,
    index: Int,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(250, delayMillis = index * 80)
        ) + slideInVertically(
            initialOffsetY = { it / 8 },
            animationSpec = tween(250, delayMillis = index * 80),
        ),
    ) {
        content()
    }
}
