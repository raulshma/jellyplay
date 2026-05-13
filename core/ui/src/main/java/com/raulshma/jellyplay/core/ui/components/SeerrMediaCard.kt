package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.isTvDevice
import com.raulshma.jellyplay.core.ui.tv.tvFocusable

@Composable
fun SeerrMediaCard(
    item: SeerrSearchItem,
    imageUrl: String?,
    onClick: () -> Unit,
    onRequestClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isTv = isTvDevice()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(150),
        label = "seerrCardScale",
    )

    val mediaStatus = item.mediaInfo?.status?.let { SeerrMediaStatus.fromValue(it) }
        ?: SeerrMediaStatus.UNKNOWN
    val isAvailable = mediaStatus == SeerrMediaStatus.AVAILABLE ||
            mediaStatus == SeerrMediaStatus.PARTIALLY_AVAILABLE
    val isPending = mediaStatus == SeerrMediaStatus.PENDING ||
            mediaStatus == SeerrMediaStatus.PROCESSING
    val hasRequest = item.mediaInfo?.requests?.isNotEmpty() == true

    Column(modifier = modifier) {
        Card(
            modifier = Modifier
                .width(if (isTv) 180.dp else 140.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .tvFocusable()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isTv) 12.dp else 4.dp),
        ) {
            Box {
                if (imageUrl != null) {
                    MediaImage(
                        url = imageUrl,
                        contentDescription = item.displayName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f),
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

                // Gradient overlay at bottom
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

                // Availability status badge
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

                // Request action button (only when not available and not yet requested)
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

        // Info below card
        Column(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = if (isTv) 8.dp else 6.dp),
        ) {
            Text(
                text = item.displayName,
                style = if (isTv) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = 0.9f),
            )
            if (item.year != null) {
                Text(
                    text = item.year.toString(),
                    style = if (isTv) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }
    }
}
