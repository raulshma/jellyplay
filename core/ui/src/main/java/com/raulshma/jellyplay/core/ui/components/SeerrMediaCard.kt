package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.animation.lessSpringySpec
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.isTvDevice
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import java.time.LocalDate

@Composable
fun SeerrMediaCard(
    item: SeerrSearchItem,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRequestClick: (() -> Unit)? = null,
    isLoading: Boolean = false,
) {
    val isTv = isTvDevice()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            pulseScale.animateTo(
                targetValue = 0.97f,
                animationSpec = tween(300, easing = FancyTransitionEasing),
            )
        } else {
            pulseScale.snapTo(1f)
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isLoading) pulseScale.value else if (isPressed) 0.95f else 1f,
        animationSpec = lessSpringySpec(),
        label = "seerrCardScale",
    )

    val brightnessOverlay by animateFloatAsState(
        targetValue = if (isPressed && !isLoading) 0.08f else 0f,
        animationSpec = tween(150, easing = AlphaEasing),
        label = "seerrCardBrightness",
    )

    val shimmerAndGlow = if (isLoading) {
        val infiniteTransition = rememberInfiniteTransition(label = "seerrShimmer")
        val shimmerOffset by infiniteTransition.animateFloat(
            initialValue = -500f,
            targetValue = 1500f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "seerrShimmerOffset",
        )

        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FancyTransitionEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "seerrGlowAlpha",
        )
        shimmerOffset to glowAlpha
    } else {
        remember { 0f to 0f }
    }
    val (shimmerOffset, glowAlpha) = shimmerAndGlow

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

    val mediaStatus = item.mediaInfo?.status?.let { SeerrMediaStatus.fromValue(it) }
        ?: SeerrMediaStatus.UNKNOWN
    val isAvailable = mediaStatus == SeerrMediaStatus.AVAILABLE ||
            mediaStatus == SeerrMediaStatus.PARTIALLY_AVAILABLE
    val isPending = mediaStatus == SeerrMediaStatus.PENDING ||
            mediaStatus == SeerrMediaStatus.PROCESSING
    val hasRequest = item.mediaInfo?.requests?.isNotEmpty() == true

    val cardShape = RoundedCornerShape(12.dp)
    val glowColor = MaterialTheme.colorScheme.primary

    val imageModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(2f / 3f)

    Column(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
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
                                    glowColor.copy(alpha = glowAlpha * 0.6f),
                                    glowColor.copy(alpha = glowAlpha),
                                    glowColor.copy(alpha = glowAlpha * 0.6f),
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
                .tvFocusable()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    enabled = !isLoading,
                ),
            shape = cardShape,
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isLoading) 12.dp else if (isTv) 12.dp else 4.dp
            ),
        ) {
            Box {
                if (imageUrl != null) {
                    MediaImage(
                        url = imageUrl,
                        contentDescription = item.displayName,
                        modifier = imageModifier,
                        contentScale = ContentScale.Crop,
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
                            .background(Color.White.copy(alpha = brightnessOverlay))
                    )
                }

                if (isLoading) {
                    val shimmerBrush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.0f),
                            Color.White.copy(alpha = 0.18f),
                            Color.White.copy(alpha = 0.0f),
                        ),
                        start = Offset(shimmerOffset, 0f),
                        end = Offset(shimmerOffset + 400f, 400f),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(cardShape)
                            .background(shimmerBrush)
                    )
                }

                if (!isLoading) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.5f),
                                    ),
                                )
                            )
                    )
                }

                if (!isLoading && item.voteAverage != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(
                                Color.Black.copy(alpha = 0.7f),
                                RoundedCornerShape(6.dp),
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
                                color = Color(0xFFFFC107),
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
                    val mediaLabel = when {
                        isUpcoming -> "UPCOMING"
                        item.mediaType.equals("tv", ignoreCase = true) -> "SERIES"
                        item.mediaType.equals("movie", ignoreCase = true) -> "MOVIE"
                        else -> item.mediaType.uppercase()
                    }

                    val labelColor = if (isUpcoming) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
                    } else {
                        Color.Black.copy(alpha = 0.6f)
                    }

                    val textColor = if (isUpcoming) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        Color.White.copy(alpha = 0.9f)
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .background(
                                labelColor,
                                RoundedCornerShape(4.dp),
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
                    val badgeColor = when {
                        isAvailable -> Color(0xFF4CAF50)
                        isPending -> Color(0xFFFFA726)
                        hasRequest -> Color(0xFF42A5F5)
                        else -> Color.Transparent
                    }

                    if (badgeColor != Color.Transparent) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .background(
                                    badgeColor.copy(alpha = 0.9f),
                                    RoundedCornerShape(6.dp),
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
                        IconButton(
                            onClick = onRequestClick,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 4.dp, bottom = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .tvFocusable(),
                        ) {
                            Icon(
                                Icons.Default.Add,
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = if (isLoading) 0.5f else 0.9f),
            )
            if (item.year != null) {
                Text(
                    text = item.year.toString(),
                    style = if (isTv) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = if (isLoading) 0.3f else 0.55f),
                )
            }
        }
    }
}
