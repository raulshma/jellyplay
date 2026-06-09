package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.RatingColors
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.animation.defaultSpatialSpec
import com.raulshma.jellyplay.core.ui.animation.fastEffectsSpec
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import java.time.LocalDate
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
fun SeerrMediaCard(
    item: SeerrSearchItem,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRequestClick: (() -> Unit)? = null,
    isLoading: Boolean = false,
) {
    val isTv = LocalTvMode.current
    val tvFocusState = rememberTvFocusState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pulseScale = remember { Animatable(1f) }
    val pulseSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(isLoading) {
        if (isLoading) {
            pulseScale.animateTo(
                targetValue = 0.97f,
                animationSpec = pulseSpec,
            )
        } else {
            pulseScale.snapTo(1f)
        }
    }

    val baseScale by animateFloatAsState(
        targetValue = if (isLoading) pulseScale.value else if (isPressed) 0.95f else 1f,
        animationSpec = defaultSpatialSpec<Float>(),
        label = "seerrCardScale",
    )
    val scale by animateFloatAsState(
        targetValue = baseScale * tvFocusState.scale,
        animationSpec = defaultSpatialSpec<Float>(),
        label = "seerrCardCombinedScale",
    )

    val brightnessOverlay by animateFloatAsState(
        targetValue = if (isPressed && !isLoading) 0.08f else 0f,
        animationSpec = fastEffectsSpec(),
        label = "seerrCardBrightness",
    )

    val shimmerOffset = remember { Animatable(-500f) }
    val glowAlpha = remember { Animatable(0.3f) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            launch {
                while (isActive) {
                    shimmerOffset.animateTo(
                        1500f,
                        animationSpec = tween(durationMillis = 1200, easing = LinearEasing),
                    )
                    shimmerOffset.snapTo(-500f)
                }
            }
            launch {
                while (isActive) {
                    glowAlpha.animateTo(
                        0.8f,
                        animationSpec = tween(800, easing = FancyTransitionEasing),
                    )
                    glowAlpha.animateTo(
                        0.3f,
                        animationSpec = tween(800, easing = FancyTransitionEasing),
                    )
                }
            }
        } else {
            shimmerOffset.snapTo(-500f)
            glowAlpha.snapTo(0.3f)
        }
    }

    val effectiveShimmerOffset = if (isLoading) shimmerOffset.value else 0f
    val effectiveGlowAlpha = if (isLoading) glowAlpha.value else 0f

    val isUpcoming = remember(item.releaseDate, item.firstAirDate) {
        val dateStr = item.releaseDate ?: item.firstAirDate
        if (dateStr.isNullOrBlank()) false
        else {
            try {
                val now = LocalDate.now()
                val releaseDate = LocalDate.parse(dateStr)
                releaseDate.isAfter(now)
            } catch (e: Exception) {
                false
            }
        }
    }

    val mediaStatus = remember(item.mediaInfo?.status) {
        item.mediaInfo?.status?.let { SeerrMediaStatus.fromValue(it) }
            ?: SeerrMediaStatus.UNKNOWN
    }
    val isAvailable = remember(mediaStatus) {
        mediaStatus == SeerrMediaStatus.AVAILABLE ||
            mediaStatus == SeerrMediaStatus.PARTIALLY_AVAILABLE
    }
    val isPending = remember(mediaStatus) {
        mediaStatus == SeerrMediaStatus.PENDING ||
            mediaStatus == SeerrMediaStatus.PROCESSING
    }
    val hasRequest = item.mediaInfo?.requests?.isNotEmpty() == true

    val cardShape = ShapeCache.smooth12
    val glowColor = MaterialTheme.colorScheme.primary

    val imageModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(2f / 3f)

    Column(modifier = modifier) {
        val isSynthwave = LocalIsSynthwave.current
        val isSoothing = LocalIsSoothingTheme.current
        val primary = MaterialTheme.colorScheme.primary
        val secondary = MaterialTheme.colorScheme.secondary
        val synthwaveBorder = remember(primary, secondary) {
            androidx.compose.foundation.BorderStroke(
                width = 1.5.dp,
                brush = Brush.linearGradient(colors = listOf(primary, secondary))
            )
        }
        val outlineColor = MaterialTheme.colorScheme.outline
        val soothingBorder = remember(outlineColor) {
            androidx.compose.foundation.BorderStroke(
                width = 0.8.dp,
                color = outlineColor.copy(alpha = 0.35f)
            )
        }
        val border = when {
            isSynthwave -> synthwaveBorder
            isSoothing -> soothingBorder
            else -> null
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(tvFocusState.focusModifier)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .then(
                    if (isLoading) {
                        Modifier.border(
                            width = (1.5).dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    glowColor.copy(alpha = effectiveGlowAlpha * 0.6f),
                                    glowColor.copy(alpha = effectiveGlowAlpha),
                                    glowColor.copy(alpha = effectiveGlowAlpha * 0.6f),
                                ),
                                start = Offset.Zero,
                                end = Offset(1000f, 1500f),
                            ),
                            shape = cardShape,
                        )
                    } else {
                        Modifier
                    }
                )
                .tvFocusIndicator(tvFocusState, cardShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    enabled = !isLoading,
                ),
            shape = cardShape,
            border = border,
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isLoading) 12.dp else 0.dp
            ),
        ) {
            Box {
                if (imageUrl != null) {
                    MediaImage(
                        url = imageUrl,
                        contentDescription = item.displayName,
                        modifier = imageModifier,
                        contentScale = ContentScale.Crop,
                        crossfade = false,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = item.displayName.take(2),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (brightnessOverlay > 0.01f) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = brightnessOverlay))
                    )
                }

                if (isLoading) {
                    val shimmerBrush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.0f),
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.0f),
                        ),
                        start = Offset(effectiveShimmerOffset, 0f),
                        end = Offset(effectiveShimmerOffset + 400f, 400f),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(cardShape)
                            .background(shimmerBrush)
                    )
                }

                if (!isLoading) {
                    val surface = MaterialTheme.colorScheme.surface
                    val bottomGradient = remember(surface) {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                surface.copy(alpha = 0.5f),
                            ),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(bottomGradient)
                    )
                }

                if (!isLoading && item.voteAverage != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                ShapeCache.smooth4,
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "★",
                                style = MaterialTheme.typography.labelSmall,
                                color = RatingColors.star,
                            )
                            Text(
                                text = "%.1f".format(item.voteAverage),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                    }
                }

                if (!isLoading) {
                    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
                    val surface = MaterialTheme.colorScheme.surface
                    val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryContainer
                    val mediaLabel = remember(isUpcoming, item.mediaType) {
                        when {
                            isUpcoming -> "UPCOMING"
                            item.mediaType.equals("tv", ignoreCase = true) -> "SERIES"
                            item.mediaType.equals("movie", ignoreCase = true) -> "MOVIE"
                            else -> item.mediaType.uppercase()
                        }
                    }

                    val labelColor = remember(isUpcoming, tertiaryContainer, surface) {
                        if (isUpcoming) {
                            tertiaryContainer.copy(alpha = 0.9f)
                        } else {
                            surface.copy(alpha = 0.6f)
                        }
                    }

                    val textColor = remember(isUpcoming, onTertiaryContainer) {
                        if (isUpcoming) {
                            onTertiaryContainer
                        } else {
                            Color.White.copy(alpha = 0.9f)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .background(
                                labelColor,
                                ShapeCache.smooth4,
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = mediaLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = textColor,
                        )
                    }
                }

                if (!isLoading) {
                    val badgeColor = remember(isAvailable, isPending, hasRequest) {
                        when {
                            isAvailable -> StatusColors.available
                            isPending -> StatusColors.pending
                            hasRequest -> StatusColors.requested
                            else -> Color.Transparent
                        }
                    }

                    if (badgeColor != Color.Transparent) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .background(
                                    badgeColor.copy(alpha = 0.9f),
                                    ShapeCache.smooth4,
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = when {
                                    isAvailable -> "✓"
                                    isPending -> "⏳"
                                    hasRequest -> "→"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                    }

                    if (onRequestClick != null && !isAvailable && !hasRequest) {
                        val requestBtnFocusState = rememberTvFocusState(focusedScale = 1.12f)
                        IconButton(
                            onClick = onRequestClick,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 4.dp, bottom = 4.dp)
                                .then(requestBtnFocusState.focusModifier)
                                .clip(ShapeCache.smooth8)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                .tvFocusIndicator(requestBtnFocusState, ShapeCache.smooth8),
                        ) {
                            Icon(
                                Tabler.Outline.Plus,
                                contentDescription = "Request",
                                tint = Color.White,
                                modifier = Modifier.padding(2.dp),
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = if (isTv) 8.dp else 6.dp),
        ) {
            Text(
                text = item.displayName,
                style = if (isTv) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isLoading) 0.5f else 0.9f),
            )
            if (item.year != null) {
                Text(
                    text = item.year.toString(),
                    style = if (isTv) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isLoading) 0.3f else 0.55f),
                )
            }
        }
    }
}
