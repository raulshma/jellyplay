package com.raulshma.jellyplay.feature.requests

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.ExternalLink
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Trash
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestStatus
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.image.MediaImage
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestDetailBottomSheet(
    request: SeerrRequestItem,
    mediaInfo: RequestMediaInfo?,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onApprove: () -> Unit = {},
    onDecline: () -> Unit = {},
    onRetry: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRemoveFromService: () -> Unit = {},
    onNavigateToDetail: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
) {
    val colorScheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val requestStatus = remember(request.status) { SeerrRequestStatus.fromValue(request.status) }
    var isConfirmingDelete by remember(request.id) { mutableStateOf(false) }
    var isConfirmingRemoveFromService by remember(request.id) { mutableStateOf(false) }

    LaunchedEffect(isConfirmingDelete) {
        if (isConfirmingDelete) {
            delay(3000)
            isConfirmingDelete = false
        }
    }

    LaunchedEffect(isConfirmingRemoveFromService) {
        if (isConfirmingRemoveFromService) {
            delay(3000)
            isConfirmingRemoveFromService = false
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

    val formattedDate = remember(request.createdAt) {
        try {
            val parsed = LocalDateTime.parse(request.createdAt, DateTimeFormatter.ISO_DATE_TIME)
            parsed.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        } catch (_: Exception) {
            request.createdAt.take(10)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = ShapeCache.smooth20,
        containerColor = colorScheme.surfaceContainer.copy(alpha = 0.95f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 72.dp, height = 108.dp)
                        .clip(ShapeCache.smooth10)
                        .background(colorScheme.onSurface.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (mediaInfo?.posterUrl != null) {
                        MediaImage(
                            url = mediaInfo.posterUrl,
                            contentDescription = displayTitle,
                            modifier = Modifier.size(width = 72.dp, height = 108.dp),
                            contentScale = ContentScale.Crop,
                            crossfade = true,
                        )
                    } else {
                        Text(
                            text = if (request.type.equals("movie", ignoreCase = true)) "🎬" else "📺",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    if (mediaInfo?.year != null) {
                        Text(
                            text = "${mediaInfo.year} · ${request.type.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!mediaInfo?.overview.isNullOrBlank()) {
                Text(
                    text = mediaInfo!!.overview!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.3f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("Request ID", "#${request.id}")
                DetailRow("Type", request.type.replaceFirstChar { it.uppercase() })
                DetailRow("TMDB ID", request.media.tmdbId.toString())
                request.requestedBy.username?.let { DetailRow("Requested by", it) }
                DetailRow("Requested", formattedDate)
                request.profileName?.let { DetailRow("Quality Profile", it) }
                request.rootFolder?.let { DetailRow("Root Folder", it) }
                if (request.seasons.isNotEmpty()) {
                    DetailRow(
                        "Seasons",
                        request.seasons.joinToString(", ") { "S${it.seasonNumber}" },
                    )
                }
                if (request.media.downloadStatus.isNotEmpty()) {
                    val downloadStatusText = request.media.downloadStatus
                        .mapNotNull { it.status }
                        .joinToString(", ")
                        .ifBlank { "In Queue" }
                    DetailRow("Download", downloadStatusText)
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.3f))

            OutlinedButton(
                onClick = { onNavigateToDetail(request.media.tmdbId, request.type) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusIndicator(),
                shape = ShapeCache.smooth12,
            ) {
                Icon(
                    Tabler.Outline.ExternalLink,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("View Media Details")
            }

            if (isAdmin) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        requestStatus == SeerrRequestStatus.FAILED -> {
                            Button(
                                onClick = onRetry,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusIndicator(),
                                shape = ShapeCache.smooth12,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StatusColors.info,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            ) {
                                Icon(Tabler.Outline.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry Request", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        requestStatus == SeerrRequestStatus.PENDING -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Button(
                                    onClick = onApprove,
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusIndicator(),
                                    shape = ShapeCache.smooth12,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = StatusColors.available,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                ) {
                                    Icon(Tabler.Outline.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Approve", style = MaterialTheme.typography.labelLarge)
                                }
                                Button(
                                    onClick = onDecline,
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusIndicator(),
                                    shape = ShapeCache.smooth12,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = StatusColors.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                ) {
                                    Icon(Tabler.Outline.X, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Decline", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    if (requestStatus != SeerrRequestStatus.PENDING) {
                        OutlinedButton(
                            onClick = {
                                if (isConfirmingDelete) {
                                    onDelete()
                                    isConfirmingDelete = false
                                } else {
                                    isConfirmingDelete = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusIndicator(),
                            shape = ShapeCache.smooth12,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colorScheme.error,
                            ),
                        ) {
                            Icon(Tabler.Outline.Trash, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isConfirmingDelete) "Sure?" else "Delete Request")
                        }

                        if (request.canRemove) {
                            val serviceLabel = if (request.type.equals("movie", ignoreCase = true)) "Radarr" else "Sonarr"
                            OutlinedButton(
                                onClick = {
                                    if (isConfirmingRemoveFromService) {
                                        onRemoveFromService()
                                        isConfirmingRemoveFromService = false
                                    } else {
                                        isConfirmingRemoveFromService = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusIndicator(),
                                shape = ShapeCache.smooth12,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = colorScheme.error,
                                ),
                            ) {
                                Icon(Tabler.Outline.Trash, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isConfirmingRemoveFromService) "Sure?" else "Remove from $serviceLabel")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
