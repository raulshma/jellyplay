package com.raulshma.jellyplay.feature.downloads

import androidx.compose.animation.core.animateFloatAsState
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_more_cd
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_move_to_front
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_pause
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_play
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_resume
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_retry
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_stream
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_selection_hint
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_status_cancelled
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_status_failed
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_status_paused
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_status_queued
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_status_waiting
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_storage_quota
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_storage_used

/**
 * One download row: thumbnail, title/context line, status body, and the
 * per-status action cluster. The cluster's AVAILABILITY is the module's
 * [DownloadActions.availableActions] fold over the admission table (a status'
 * buttons exist exactly when the table admits them); only PLACEMENT — which
 * slot a button renders in, its icon, tint, and order — stays this file's
 * per-status code, and dispatch flows through the single [onAction] sink
 * instead of a callback per verb.
 */
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
    /**
     * Stream-instead action for completed downloads while online — plays the
     * server copy instead of the local file. Null hides the action (offline,
     * or a non-completed row).
     */
    onStream: (() -> Unit)? = null,
    /**
     * The single sink for every [DownloadBulkAction] the row's inline cluster
     * offers (pause / resume / cancel / retry / delete). One callback covers
     * the whole verb fan (the screen's sink decides the per-verb
     * policy — e.g. delete confirms before it dispatches).
     */
    onAction: (DownloadBulkAction) -> Unit,
    /** Queue-ordering verbs — deliberately outside the bulk-action vocabulary. */
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
        // Match the SwipeActionBox clip (ShapeCache.smooth12): with the larger
        // inherited shapes.medium the card recedes from the wrapper's rounded
        // corners and the always-painted errorContainer swipe background peeks
        // through as thin red crescents on the edges.
        shape = ShapeCache.smooth12,
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
                        // Two deterministic lines instead of one free-wrapping
                        // string — size, then speed · ETA — so wraps land
                        // between segments, never mid-segment with a dangling
                        // "·". A server that never reports a total renders the
                        // downloaded bytes alone (no bogus "/ 1 B") and simply
                        // has no ETA; each line ellipsizes its own tail rather
                        // than wrapping the row taller.
                        Text(
                            if (item.totalSizeBytes > 0) {
                                "${formatBytes(downloadedBytes)} / ${formatBytes(item.totalSizeBytes)}"
                            } else {
                                formatBytes(downloadedBytes)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            buildString {
                                val speedText = formatSpeed(speedBytesPerSec)
                                val etaText = formatEta(downloadedBytes, item.totalSizeBytes, speedBytesPerSec)
                                if (speedText.isNotEmpty()) append(speedText)
                                if (etaText.isNotEmpty()) {
                                    if (isNotEmpty()) append(" · ")
                                    append(etaText)
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
            // in the bottom action bar instead (matches ArrQueueScreen). Every
            // status' buttons render as one tight cluster: no extra Row
            // spacing — adjacent compact IconButtons already separate via
            // their intrinsic padding.
            if (!selectionMode) {
                // The module's admission fold, keyed on status only — the fold
                // reads nothing else off the item, so progress ticks re-render
                // the row without recomputing it.
                val available = remember(item.status) { DownloadActions.availableActions(item) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (item.status) {
                        DownloadStatus.DOWNLOADING -> {
                            DownloadActionButton(
                                icon = Tabler.Outline.ArrowDown,
                                contentDescription = stringResource(Res.string.downloads_action_lower_priority),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = onLowerPriority,
                            )
                            RowActionSlot(
                                action = DownloadBulkAction.PAUSE,
                                available = available,
                                icon = Tabler.Outline.PlayerPause,
                                contentDescription = stringResource(Res.string.downloads_action_pause),
                                tint = MaterialTheme.colorScheme.primary,
                                onAction = onAction,
                            )
                            RowActionSlot(
                                action = DownloadBulkAction.CANCEL,
                                available = available,
                                icon = Tabler.Outline.Trash,
                                contentDescription = stringResource(Res.string.downloads_action_cancel),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                onAction = onAction,
                            )
                        }
                        DownloadStatus.PENDING -> {
                            DownloadActionButton(
                                icon = Tabler.Outline.ArrowUp,
                                contentDescription = stringResource(Res.string.downloads_action_move_to_front),
                                tint = MaterialTheme.colorScheme.primary,
                                onClick = onMoveToFront,
                            )
                            RowActionSlot(
                                action = DownloadBulkAction.CANCEL,
                                available = available,
                                icon = Tabler.Outline.Trash,
                                contentDescription = stringResource(Res.string.downloads_action_cancel),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                onAction = onAction,
                            )
                        }
                        DownloadStatus.QUEUED -> {
                            DownloadActionButton(
                                icon = Tabler.Outline.ArrowUp,
                                contentDescription = stringResource(Res.string.downloads_action_move_to_front),
                                tint = MaterialTheme.colorScheme.primary,
                                onClick = onMoveToFront,
                            )
                            RowActionSlot(
                                action = DownloadBulkAction.CANCEL,
                                available = available,
                                icon = Tabler.Outline.Trash,
                                contentDescription = stringResource(Res.string.downloads_action_cancel),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                onAction = onAction,
                            )
                        }
                        DownloadStatus.PAUSED -> {
                            RowActionSlot(
                                action = DownloadBulkAction.RESUME,
                                available = available,
                                icon = Tabler.Outline.PlayerPlay,
                                contentDescription = stringResource(Res.string.downloads_action_resume),
                                tint = MaterialTheme.colorScheme.primary,
                                onAction = onAction,
                            )
                            RowActionSlot(
                                action = DownloadBulkAction.CANCEL,
                                available = available,
                                icon = Tabler.Outline.Trash,
                                contentDescription = stringResource(Res.string.downloads_action_cancel),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                onAction = onAction,
                            )
                        }
                        DownloadStatus.FAILED -> {
                            RowActionSlot(
                                action = DownloadBulkAction.RETRY_FAILED,
                                available = available,
                                icon = Tabler.Outline.Refresh,
                                contentDescription = stringResource(Res.string.downloads_action_retry),
                                tint = MaterialTheme.colorScheme.primary,
                                onAction = onAction,
                            )
                        }
                        DownloadStatus.COMPLETED -> {
                            // Play + stream live in an overflow menu so the row
                            // keeps only two inline actions; stream hides while
                            // offline.
                            CompletedRowOverflowMenu(
                                onPlay = onPlay,
                                onStream = onStream,
                            )
                            RowActionSlot(
                                action = DownloadBulkAction.DELETE,
                                available = available,
                                icon = Tabler.Outline.Trash,
                                contentDescription = stringResource(Res.string.downloads_action_delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                onAction = onAction,
                            )
                        }
                        DownloadStatus.CANCELLED -> {
                            // A cancelled row's artifacts are already gone; the
                            // only meaningful action is delete, which the swipe
                            // gesture and selection mode still reach.
                        }
                    }
                }
            }
        }
    }
}

/**
 * One inline slot for a bulk action: renders [DownloadActionButton] only while
 * the module's admission fold ([available]) admits [action] for this row's
 * status, and dispatches it through the row's single sink. The per-status
 * branches above own placement (slot, icon, tint, order); this slot owns the
 * admission — a table change re-dresses rows without touching them.
 */
@Composable
private fun RowActionSlot(
    action: DownloadBulkAction,
    available: List<DownloadBulkAction>,
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onAction: (DownloadBulkAction) -> Unit,
) {
    if (action in available) {
        DownloadActionButton(
            icon = icon,
            contentDescription = contentDescription,
            tint = tint,
            onClick = { onAction(action) },
        )
    }
}

/**
 * A row's inline action button — compact (32dp box, 18dp icon, TrackRow's
 * row-menu pattern) so up to three of them don't starve the text column on a
 * phone. The reduced touch target matches the music row's; TV focus
 * still rings via [tvFocusIndicator].
 */
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
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                icon,
                contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * A completed download row's overflow menu — play, and stream-instead while
 * online. The anchor reuses [DownloadActionButton] (DotsVertical) so it
 * matches the delete button beside it in size and TV focus; the stream entry
 * is dropped when [onStream] is null (offline or non-completed row).
 */
@Composable
private fun CompletedRowOverflowMenu(
    onPlay: () -> Unit,
    onStream: (() -> Unit)?,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    DownloadActionButton(
        icon = Tabler.Outline.DotsVertical,
        contentDescription = stringResource(Res.string.downloads_action_more_cd),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = { menuExpanded = true },
    )
    DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.downloads_action_play)) },
            leadingIcon = {
                Icon(Tabler.Outline.PlayerPlay, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            onClick = {
                menuExpanded = false
                onPlay()
            },
        )
        val streamAction = onStream
        if (streamAction != null) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.downloads_action_stream)) },
                leadingIcon = {
                    Icon(Tabler.Outline.World, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = {
                    menuExpanded = false
                    streamAction()
                },
            )
        }
    }
}

/**
 * "Storage used" header, as a narrow binder (VideoPlayerScreen's
 * ChapterPickerBinder pattern): the total rides the live download-progress
 * tick — bytes accumulate mid-transfer — so the StateFlow is collected
 * INSIDE this leaf. Only this header recomposes per tick; the downloads screen
 * body that hosts it does not.
 *
 * One line either way: with a download storage cap set ([maxStorageBytes]
 * non-null — the same value [com.raulshma.jellyplay.core.data.repository.StoragePolicy]
 * enforces) the line is the quota bar plus its "X of Y used" label — the
 * label carries both numbers, so the plain-text line is dropped rather than
 * saying the same thing twice. Without a cap the "Storage used" text renders
 * alone. The bar's fill turns warning-colored at 90% and error-colored at the
 * cap, and the whole row is clickable into the storage settings screen where
 * the cap is adjustable.
 */
@Composable
internal fun DownloadsStorageUsedText(
    totalStorageBytes: StateFlow<Long>,
    maxStorageBytes: StateFlow<Long?>,
    formatBytes: (Long) -> String,
    horizontalPadding: Dp,
    onOpenStorageSettings: (() -> Unit)? = null,
) {
    val totalStorage by totalStorageBytes.collectAsStateWithLifecycle()
    val maxStorage by maxStorageBytes.collectAsStateWithLifecycle()
    if (totalStorage > 0) {
        val maxBytes = maxStorage
        if (maxBytes != null && maxBytes > 0) {
            val fraction = (totalStorage.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f)
            val quotaColor = when {
                fraction >= 1f -> MaterialTheme.colorScheme.error
                fraction >= 0.9f -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = horizontalPadding, end = horizontalPadding, bottom = 8.dp)
                    .then(
                        if (onOpenStorageSettings != null) {
                            Modifier.clickable(onClick = onOpenStorageSettings)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                JellyPlayLinearProgressIndicator(
                    progress = { fraction },
                    color = quotaColor,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(Res.string.downloads_storage_quota, formatBytes(totalStorage), formatBytes(maxBytes)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                stringResource(Res.string.downloads_storage_used, formatBytes(totalStorage)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = horizontalPadding, end = horizontalPadding, bottom = 8.dp),
            )
        }
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
