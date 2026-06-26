package com.raulshma.jellyplay.feature.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestStatus
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.image.MediaImage
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.OffsetDateTime

@Composable
fun RequestListItem(
    request: SeerrRequestItem,
    mediaInfo: RequestMediaInfo?,
    isAdmin: Boolean,
    actionInProgress: Boolean,
    onApprove: () -> Unit = {},
    onDecline: () -> Unit = {},
    onRetry: () -> Unit = {},
    onDelete: () -> Unit = {},
    onClick: () -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    val requestStatus = remember(request.status) { SeerrRequestStatus.fromValue(request.status) }
    var isConfirmingDelete by remember(request.id) { mutableStateOf(false) }

    LaunchedEffect(isConfirmingDelete) {
        if (isConfirmingDelete) {
            delay(3000)
            isConfirmingDelete = false
        }
    }

    val effectiveMediaStatus = if (request.is4k) request.media.status4k else request.media.status
    val mediaStatus = remember(effectiveMediaStatus) { SeerrMediaStatus.fromValue(effectiveMediaStatus) }
    val displayTitle = mediaInfo?.title ?: "TMDB ${request.media.tmdbId}"

    val (statusLabel, statusColor) = when {
        requestStatus == SeerrRequestStatus.DECLINED -> "Declined" to StatusColors.error
        requestStatus == SeerrRequestStatus.FAILED -> "Failed" to StatusColors.error
        requestStatus == SeerrRequestStatus.PENDING && mediaStatus == SeerrMediaStatus.DELETED -> "Pending" to StatusColors.pending
        else -> when (mediaStatus) {
            SeerrMediaStatus.AVAILABLE -> "Available" to StatusColors.available
            SeerrMediaStatus.PROCESSING -> "Processing" to StatusColors.info
            SeerrMediaStatus.PARTIALLY_AVAILABLE -> "Partially Available" to StatusColors.pendingLight
            SeerrMediaStatus.PENDING -> "Pending" to StatusColors.pending
            SeerrMediaStatus.DELETED -> "Deleted" to StatusColors.error
            SeerrMediaStatus.UNKNOWN -> "Unknown" to colorScheme.onSurfaceVariant
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .focusIndicator()
            .clickable { onClick() },
        shape = ShapeCache.smooth16,
        color = colorScheme.surfaceContainer.copy(alpha = 0.6f),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorScheme.onSurface.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                if (mediaInfo?.posterUrl != null) {
                    MediaImage(
                        url = mediaInfo.posterUrl,
                        contentDescription = displayTitle,
                        modifier = Modifier.size(width = 48.dp, height = 72.dp),
                        contentScale = ContentScale.Crop,
                        crossfade = true,
                    )
                } else {
                    Text(
                        text = if (request.type.equals("movie", ignoreCase = true)) "🎬" else "📺",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.onSurface,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = request.type.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    if (mediaInfo?.year != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = mediaInfo.year.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (request.seasons.isNotEmpty()) {
                    Text(
                        text = request.seasons.joinToString(", ") { "S${it.seasonNumber}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val metadataText = remember(request) {
                    buildString {
                        append(request.requestedBy.username ?: request.requestedBy.email)
                        val requestedAgo = formatRelativeTime(request.createdAt)
                        if (requestedAgo != null) {
                            append(" · ")
                            append(requestedAgo)
                        }
                        if (request.modifiedBy != null && request.updatedAt != request.createdAt) {
                            val modifiedAgo = formatRelativeTime(request.updatedAt)
                            if (modifiedAgo != null) {
                                append(" · modified ")
                                append(modifiedAgo)
                            }
                        }
                        val profileName = request.profileName
                        if (profileName != null) {
                            append(" · ")
                            append(profileName)
                        }
                    }
                }
                Text(
                    text = metadataText,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isAdmin) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    when {
                        requestStatus == SeerrRequestStatus.FAILED -> {
                            FilledTonalButton(
                                onClick = onRetry,
                                enabled = !actionInProgress,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .focusIndicator(),
                            ) {
                                Text("Retry", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        requestStatus == SeerrRequestStatus.PENDING -> {
                            FilledTonalButton(
                                onClick = onApprove,
                                enabled = !actionInProgress,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .focusIndicator(),
                            ) {
                                Text("Approve", style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(
                                onClick = onDecline,
                                enabled = !actionInProgress,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .focusIndicator(),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = colorScheme.errorContainer,
                                    contentColor = colorScheme.onErrorContainer,
                                ),
                            ) {
                                Text("Decline", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        else -> {
                            FilledTonalButton(
                                onClick = {
                                    if (isConfirmingDelete) {
                                        onDelete()
                                        isConfirmingDelete = false
                                    } else {
                                        isConfirmingDelete = true
                                    }
                                },
                                enabled = !actionInProgress,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .focusIndicator(),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = colorScheme.errorContainer,
                                    contentColor = colorScheme.onErrorContainer,
                                ),
                            ) {
                                Text(if (isConfirmingDelete) "Sure?" else "Delete", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatRelativeTime(dateStr: String): String? {
    return try {
        val date = OffsetDateTime.parse(dateStr)
        val now = OffsetDateTime.now()
        val duration = Duration.between(date, now)
        when {
            duration.toMinutes() < 1 -> "just now"
            duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
            duration.toHours() < 24 -> "${duration.toHours()}h ago"
            duration.toDays() < 30 -> "${duration.toDays()}d ago"
            duration.toDays() < 365 -> "${duration.toDays() / 30}mo ago"
            else -> "${duration.toDays() / 365}y ago"
        }
    } catch (_: Exception) {
        null
    }
}
