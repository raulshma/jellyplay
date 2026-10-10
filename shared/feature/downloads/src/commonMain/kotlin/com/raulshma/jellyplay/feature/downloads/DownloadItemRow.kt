package com.raulshma.jellyplay.feature.downloads

import androidx.compose.animation.core.animateFloatAsState
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.components.episodeContextLine
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.data.repository.DownloadProgress
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import kotlinx.coroutines.flow.StateFlow
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.downloads.generated.resources.Res
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_cancel
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_delete
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_lower_priority
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_move_to_front
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_pause
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_play
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_resume
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_retry
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_selection_hint
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_status_cancelled
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_status_failed
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_status_paused
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_status_queued
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_status_waiting
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_storage_used

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DownloadItemRow(
    item: DownloadItem,
    modifier: Modifier = Modifier,
    /**
     * Live byte/speed override from the progress split (see the screen's
     * progressById) — present while this row is in flight, null otherwise,
     * in which case the item's own (already current) fields render.
     */
    liveProgress: DownloadProgress?,
    formatBytes: (Long) -> String,
    formatSpeed: (Long) -> String,
    formatEta: (Long, Long, Long) -> String,
    selected: Boolean,
    selectionMode: Boolean,
    hasUpdate: Boolean = false,
    onOpenDetail: () -> Unit,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onMoveToFront: () -> Unit,
    onLowerPriority: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    // Merge once at the top: the live values while the row ticks, the item's
    // structural values otherwise (statuses outside the in-flight set never
    // tick, so their structural bytes are always current).
    val downloadedBytes = liveProgress?.downloadedBytes ?: item.downloadedBytes
    val speedBytesPerSec = liveProgress?.speedBytesPerSec ?: item.speedBytesPerSec
    val progress = if (item.totalSizeBytes > 0) {
        downloadedBytes.toFloat() / item.totalSizeBytes
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "downloadProgress",
    )
    val cardRowFocusState = rememberTvFocusState(focusedScale = 1.01f)

    ElevatedCard(
        // Caller-supplied modifier comes first so the shared entrance
        // graphicsLayer wraps the whole card.
        modifier = modifier
            .fillMaxWidth()
            .then(cardRowFocusState.focusModifier)
            .tvFocusIndicator(cardRowFocusState, ShapeCache.smooth12)
            .combinedClickable(
                onClick = {
                    // In selection mode a tap toggles selection; otherwise it
                    // opens the detail page (completed items only).
                    if (selectionMode) onToggleSelection() else onOpenDetail()
                },
                onLongClick = onToggleSelection,
            ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelection() },
                )
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(ShapeCache.smooth8),
                contentAlignment = Alignment.Center,
            ) {
            val imageUrl = item.imageUrl
            if (imageUrl != null) {
                MediaImage(
                    url = imageUrl,
                    contentDescription = item.name,
                    blurHash = item.imageBlurHash,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                } else {
                    Icon(
                        when (item.mediaType) {
                            com.raulshma.jellyplay.core.model.MediaType.AUDIO,
                            com.raulshma.jellyplay.core.model.MediaType.MUSIC,
                            com.raulshma.jellyplay.core.model.MediaType.ALBUM -> Tabler.Outline.Music
                            com.raulshma.jellyplay.core.model.MediaType.BOOK -> Tabler.Outline.Book
                            else -> Tabler.Outline.Movie
                        },
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // "Update available" dot overlaid on the thumbnail so a flagged
                // item is visible at a glance without opening the resync sheet.
                if (hasUpdate) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // For series episodes, surface the parent series and an SXXEXX
                // tag below the episode title so rows are identifiable in a flat
                // download list (mirrors the context line on the unified
                // MediaDetailScreen).
                // The SxxExx + " · " + series shape is shared with the resync
                // sheets via [episodeContextLine] so a format change is one place.
                episodeContextLine(
                    mediaType = item.mediaType,
                    seriesName = item.seriesName,
                    seasonNumber = item.seasonNumber,
                    episodeNumber = item.episodeNumber,
                )?.let { annotatedContext ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        annotatedContext,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                when (item.status) {
                    DownloadStatus.DOWNLOADING -> {
                        JellyPlayLinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(2.dp))
                        val sizeText = "${formatBytes(downloadedBytes)} / ${formatBytes(item.totalSizeBytes.coerceAtLeast(1))}"
                        val speedText = formatSpeed(speedBytesPerSec)
                        val etaText = formatEta(downloadedBytes, item.totalSizeBytes, speedBytesPerSec)
                        Text(
                            buildString {
                                append(sizeText)
                                if (speedText.isNotEmpty()) append(" · $speedText")
                                if (etaText.isNotEmpty()) append(" · $etaText")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DownloadStatus.QUEUED -> {
                        Text(
                            stringResource(Res.string.downloads_status_queued),
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusColors.info,
                        )
                    }
                    DownloadStatus.COMPLETED -> {
                        Text(
                            formatBytes(downloadedBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DownloadStatus.PENDING -> {
                        Text(
                            stringResource(Res.string.downloads_status_waiting),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DownloadStatus.FAILED -> {
                        Column {
                            Text(
                                text = stringResource(Res.string.downloads_status_failed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            item.errorMessage?.takeIf { it.isNotBlank() }?.let { msg ->
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        Text(
                            stringResource(Res.string.downloads_status_paused),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DownloadStatus.CANCELLED -> {
                        Text(
                            stringResource(Res.string.downloads_status_cancelled),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Per-row actions are hidden while selecting — bulk controls live
            // in the bottom action bar instead (matches ArrQueueScreen).
            if (!selectionMode) {
                when (item.status) {
                    DownloadStatus.DOWNLOADING -> {
                        DownloadActionButton(
                            icon = Tabler.Outline.ArrowDown,
                            contentDescription = stringResource(Res.string.downloads_action_lower_priority),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onLowerPriority,
                        )
                        DownloadActionButton(
                            icon = Tabler.Outline.PlayerPause,
                            contentDescription = stringResource(Res.string.downloads_action_pause),
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onPause,
                        )
                        DownloadActionButton(
                            icon = Tabler.Outline.Trash,
                            contentDescription = stringResource(Res.string.downloads_action_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onCancel,
                        )
                    }
                    DownloadStatus.PENDING -> {
                        DownloadActionButton(
                            icon = Tabler.Outline.ArrowUp,
                            contentDescription = stringResource(Res.string.downloads_action_move_to_front),
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onMoveToFront,
                        )
                        DownloadActionButton(
                            icon = Tabler.Outline.Trash,
                            contentDescription = stringResource(Res.string.downloads_action_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onCancel,
                        )
                    }
                    DownloadStatus.QUEUED -> {
                        DownloadActionButton(
                            icon = Tabler.Outline.ArrowUp,
                            contentDescription = stringResource(Res.string.downloads_action_move_to_front),
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onMoveToFront,
                        )
                        DownloadActionButton(
                            icon = Tabler.Outline.Trash,
                            contentDescription = stringResource(Res.string.downloads_action_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onCancel,
                        )
                    }
                    DownloadStatus.PAUSED -> {
                        DownloadActionButton(
                            icon = Tabler.Outline.PlayerPlay,
                            contentDescription = stringResource(Res.string.downloads_action_resume),
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onResume,
                        )
                        DownloadActionButton(
                            icon = Tabler.Outline.Trash,
                            contentDescription = stringResource(Res.string.downloads_action_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onCancel,
                        )
                    }
                    DownloadStatus.FAILED -> {
                        DownloadActionButton(
                            icon = Tabler.Outline.Refresh,
                            contentDescription = stringResource(Res.string.downloads_action_retry),
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onRetry,
                        )
                    }
                    DownloadStatus.COMPLETED -> {
                        DownloadActionButton(
                            icon = Tabler.Outline.PlayerPlay,
                            contentDescription = stringResource(Res.string.downloads_action_play),
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onPlay,
                        )
                        DownloadActionButton(
                            icon = Tabler.Outline.Trash,
                            contentDescription = stringResource(Res.string.downloads_action_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onDelete,
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun DownloadActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.1f)
    Box(
        modifier = Modifier
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth10),
    ) {
        IconButton(onClick = onClick) {
            Icon(
                icon,
                contentDescription,
                tint = tint,
            )
        }
    }
}

/**
 * "Storage used" header line, as a narrow binder (VideoPlayerScreen's
 * ChapterPickerBinder pattern): the total rides the live download-progress
 * tick — bytes accumulate mid-transfer — so the StateFlow is collected
 * INSIDE this leaf. Only this Text recomposes per tick; the downloads screen
 * body that hosts it does not.
 */
@Composable
internal fun DownloadsStorageUsedText(
    totalStorageBytes: StateFlow<Long>,
    formatBytes: (Long) -> String,
    horizontalPadding: Dp,
) {
    val totalStorage by totalStorageBytes.collectAsStateWithLifecycle()
    if (totalStorage > 0) {
        Text(
            stringResource(Res.string.downloads_storage_used, formatBytes(totalStorage)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = horizontalPadding, end = horizontalPadding, bottom = 8.dp),
        )
    }
}

@Composable
internal fun CompactIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
    ) {
        content()
    }
}

@Composable
internal fun SelectionHintRow() {
    Surface(
        shape = ShapeCache.smooth8,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Tabler.Outline.HandMove,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(Res.string.downloads_selection_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
