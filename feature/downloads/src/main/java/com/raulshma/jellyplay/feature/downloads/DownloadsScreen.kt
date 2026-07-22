package com.raulshma.jellyplay.feature.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import com.raulshma.jellyplay.core.designsystem.theme.Dimensions
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.components.LocalFloatingNavOffset
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadsScreen(
    onItemClick: (String) -> Unit,
    onPlayOffline: (itemId: String, mediaType: com.raulshma.jellyplay.core.model.MediaType) -> Unit,
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloads = uiState.downloads
    val networkStatus by com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val headerStatus = com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus(
        isLoading = uiState.isLoading,
        hasError = uiState.error != null,
        networkStatus = networkStatus,
    )

    // Pending delete confirmation. Deleting a completed download removes the
    // file from disk, so we confirm first — matching OfflineDetailScreen and
    // OfflineSeriesScreen within the same module.
    var pendingDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var pendingBulkDelete by remember { mutableStateOf(false) }

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    // Bottom-pinned action bar must clear the app's floating navigation bar
    // (it paints above screen content at BottomCenter). Use the canonical nav
    // height + system nav-bar inset, and slide up in lockstep with the nav's
    // hide animation via LocalFloatingNavOffset (returns 0f where the nav is
    // absent — TV/expanded/full-screen).
    val navOffsetPx = LocalFloatingNavOffset.current
    val navBarBottomInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val selectionBarClearance = Dimensions.floatingNavHeight + navBarBottomInset

    val selectionMode = uiState.selectionMode
    val selectedIds = uiState.selectedIds
    // Action-bar predicates: a bulk control is enabled only when the current
    // selection actually contains an item of the matching status, so the bar
    // never offers a no-op (e.g. Pause with only paused items selected).
    val selectedItems = if (selectionMode) downloads.filter { it.id in selectedIds } else emptyList()
    val hasPauseable = selectedItems.any { it.status == DownloadStatus.DOWNLOADING }
    val hasResumable = selectedItems.any { it.status == DownloadStatus.PAUSED }
    val hasCancellable = selectedItems.any {
        it.status == DownloadStatus.PENDING ||
            it.status == DownloadStatus.QUEUED ||
            it.status == DownloadStatus.DOWNLOADING ||
            it.status == DownloadStatus.PAUSED
    }

    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    // TV focus-on-launch: focus the first download row once data arrives so D-pad input lands on
    // content, not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = downloads.size,
        tag = "downloads_init",
    )

    com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold(
        title = "Downloads",
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
                status = headerStatus,
                modifier = Modifier.padding(start = 12.dp),
            )
        },
    ) {
        if (uiState.totalStorageBytes > 0) {
            Text(
                "Storage used: ${viewModel.formatBytes(uiState.totalStorageBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = adaptiveInfo.contentPadding(isTv), end = adaptiveInfo.contentPadding(isTv), bottom = 8.dp),
            )
        }

        if (downloads.isEmpty()) {
            com.raulshma.jellyplay.core.ui.components.ScreenEmptyState(
                icon = Tabler.Outline.Download,
                title = "No downloads yet",
                description = "Downloaded content will appear here",
            )
        } else {
            // Hoist the three pure formatter lambdas once above the list. They
            // only delegate to viewModel and are identical across rows, so
            // allocating them per-row per-recomposition (the list recomposes on
            // every progress tick / speed sample) was pure churn (11 fresh
            // lambdas/row).
            val formatBytes = remember(viewModel) { { v: Long -> viewModel.formatBytes(v) } }
            val formatSpeed = remember(viewModel) { { v: Long -> viewModel.formatSpeed(v) } }
            val formatEta = remember(viewModel) { { d: Long, t: Long, s: Long -> viewModel.formatEta(d, t, s) } }
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .tvFocusRestorer()
                        .focusRequester(listFocusRequester),
                    contentPadding = PaddingValues(
                        start = adaptiveInfo.contentPadding(isTv),
                        end = adaptiveInfo.contentPadding(isTv),
                        top = 8.dp,
                        // Grow bottom padding while selecting so the action bar
                        // clears the floating nav and doesn't cover the last row.
                        bottom = if (selectionMode) selectionBarClearance + 72.dp else adaptiveInfo.bottomPadding(isTv),
                    ),
                    verticalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
                ) {
                    // Long-press hint: shown only until the user enters selection
                    // mode for the first time, so the affordance is discoverable
                    // without lingering on every session.
                    item(key = "selection_hint", contentType = "selectionHint") {
                        if (!selectionMode) {
                            SelectionHintRow()
                        }
                    }
                    itemsIndexed(items = downloads, key = { _, it -> it.id }, contentType = { _, _ -> "downloadItem" }) { index, download ->
                        val visible = remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { visible.value = true }
                        AnimatedVisibility(
                            visible = visible.value,
                            enter = fadeIn(
                                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
                            ) + slideInVertically(
                                initialOffsetY = { it / 10 },
                                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            ),
                        ) {
                            DownloadItemRow(
                                item = download,
                                formatBytes = formatBytes,
                                formatSpeed = formatSpeed,
                                formatEta = formatEta,
                                selected = download.id in selectedIds,
                                selectionMode = selectionMode,
                                // Completed downloads open their detail page on tap
                                // (matching the online experience) rather than auto-playing.
                                // A distinct Play action is still available in the row.
                                onOpenDetail = {
                                    if (download.status == DownloadStatus.COMPLETED) {
                                        onItemClick(download.mediaItemId)
                                    }
                                },
                                onPlay = {
                                    if (download.status == DownloadStatus.COMPLETED) {
                                        onPlayOffline(download.mediaItemId, download.mediaType)
                                    }
                                },
                                onCancel = { viewModel.cancelDownload(download) },
                                onPause = { viewModel.pauseDownload(download) },
                                onResume = { viewModel.resumeDownload(download) },
                                onDelete = { pendingDelete = download },
                                onRetry = { viewModel.retryDownload(download) },
                                onMoveToFront = { viewModel.moveToFront(download) },
                                onLowerPriority = { viewModel.lowerPriority(download) },
                                onToggleSelection = { viewModel.toggleSelection(download) },
                            )
                        }
                    }
                }

                // Selection-mode bottom action bar.
                if (selectionMode) {
                    SelectionActionBar(
                        selectedCount = selectedIds.size,
                        hasPauseable = hasPauseable,
                        hasResumable = hasResumable,
                        hasCancellable = hasCancellable,
                        onSelectAll = { viewModel.selectAll() },
                        onClear = { viewModel.clearSelection() },
                        onPause = { viewModel.pauseSelected() },
                        onResume = { viewModel.resumeSelected() },
                        onCancel = { viewModel.cancelSelected() },
                        onBulkDelete = { pendingBulkDelete = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            // Sit above the floating nav (clearance) and slide up
                            // in lockstep when the nav hides itself.
                            .padding(bottom = selectionBarClearance)
                            .offset {
                                val maxOffset = Dimensions.floatingNavHeight.toPx()
                                val yOffset = (-navOffsetPx()).coerceAtMost(maxOffset)
                                IntOffset(x = 0, y = yOffset.roundToInt())
                            },
                    )
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Tabler.Outline.Trash, contentDescription = null) },
            title = { Text("Delete download") },
            text = {
                Text("Remove \"${item.name}\" from your device? This frees up ${viewModel.formatBytes(item.totalSizeBytes)}.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        viewModel.deleteDownload(item)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (pendingBulkDelete) {
        val count = selectedIds.size
        val freedBytes = selectedItems.sumOf { it.totalSizeBytes }
        AlertDialog(
            onDismissRequest = { pendingBulkDelete = false },
            icon = { Icon(Tabler.Outline.Trash, contentDescription = null) },
            title = { Text("Delete downloads") },
            text = {
                Text(
                    "Remove $count selected download${if (count == 1) "" else "s"} from your device?" +
                        if (freedBytes > 0) " This frees up ${viewModel.formatBytes(freedBytes)}." else "",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingBulkDelete = false
                        viewModel.deleteSelected()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulkDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadItemRow(
    item: DownloadItem,
    formatBytes: (Long) -> String,
    formatSpeed: (Long) -> String,
    formatEta: (Long, Long, Long) -> String,
    selected: Boolean,
    selectionMode: Boolean,
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
    val progress = if (item.totalSizeBytes > 0) {
        item.downloadedBytes.toFloat() / item.totalSizeBytes
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "downloadProgress",
    )
    val cardRowFocusState = rememberTvFocusState(focusedScale = 1.01f)

    ElevatedCard(
        modifier = Modifier
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
                            else -> Tabler.Outline.Movie
                        },
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Spacer(Modifier.height(2.dp))
                when (item.status) {
                    DownloadStatus.DOWNLOADING -> {
                        JellyPlayLinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(2.dp))
                        val sizeText = "${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalSizeBytes.coerceAtLeast(1))}"
                        val speedText = formatSpeed(item.speedBytesPerSec)
                        val etaText = formatEta(item.downloadedBytes, item.totalSizeBytes, item.speedBytesPerSec)
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
                            "Queued",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusColors.info,
                        )
                    }
                    DownloadStatus.COMPLETED -> {
                        Text(
                            formatBytes(item.downloadedBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DownloadStatus.PENDING -> {
                        Text(
                            "Waiting...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DownloadStatus.FAILED -> {
                        Column {
                            Text(
                                text = "Failed",
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
                            "Paused",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DownloadStatus.CANCELLED -> {
                        Text(
                            "Cancelled",
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
                            contentDescription = "Lower Priority",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onLowerPriority,
                        )
                        DownloadActionButton(
                            icon = Tabler.Outline.PlayerPause,
                            contentDescription = "Pause",
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onPause,
                        )
                        DownloadActionButton(
                            icon = Tabler.Outline.Trash,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onCancel,
                        )
                    }
                    DownloadStatus.PENDING -> {
                        DownloadActionButton(
                            icon = Tabler.Outline.ArrowUp,
                            contentDescription = "Move to Front",
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onMoveToFront,
                        )
                        DownloadActionButton(
                            icon = Tabler.Outline.Trash,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onCancel,
                        )
                    }
                    DownloadStatus.QUEUED -> {
                        DownloadActionButton(
                            icon = Tabler.Outline.ArrowUp,
                            contentDescription = "Move to Front",
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onMoveToFront,
                        )
                        DownloadActionButton(
                            icon = Tabler.Outline.Trash,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onCancel,
                        )
                    }
                    DownloadStatus.PAUSED -> {
                        DownloadActionButton(
                            icon = Tabler.Outline.PlayerPlay,
                            contentDescription = "Resume",
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onResume,
                        )
                        DownloadActionButton(
                            icon = Tabler.Outline.Trash,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onCancel,
                        )
                    }
                    DownloadStatus.FAILED -> {
                        DownloadActionButton(
                            icon = Tabler.Outline.Refresh,
                            contentDescription = "Retry",
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onRetry,
                        )
                    }
                    DownloadStatus.COMPLETED -> {
                        DownloadActionButton(
                            icon = Tabler.Outline.PlayerPlay,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onPlay,
                        )
                        DownloadActionButton(
                            icon = Tabler.Outline.Trash,
                            contentDescription = "Delete",
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

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    hasPauseable: Boolean,
    hasResumable: Boolean,
    hasCancellable: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onBulkDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = ShapeCache.smooth12,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            // Controls get their intrinsic width first; the count text takes
            // whatever remains and ellipsizes. Without this the row would
            // squeeze the text column to ~0 width and render letters stacked
            // vertically on narrow screens.
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "$selectedCount selected",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            // Compact icon-only actions keep the bar to one line on phones.
            CompactIconButton(onClick = onPause, enabled = hasPauseable) {
                Icon(Tabler.Outline.PlayerPause, contentDescription = "Pause", modifier = Modifier.size(20.dp))
            }
            CompactIconButton(onClick = onResume, enabled = hasResumable) {
                Icon(Tabler.Outline.PlayerPlay, contentDescription = "Resume", modifier = Modifier.size(20.dp))
            }
            CompactIconButton(onClick = onCancel, enabled = hasCancellable) {
                Icon(Tabler.Outline.PlayerStop, contentDescription = "Cancel", modifier = Modifier.size(20.dp))
            }
            CompactIconButton(onClick = onSelectAll, enabled = true) {
                Icon(Tabler.Outline.Check, contentDescription = "Select all", modifier = Modifier.size(20.dp))
            }
            CompactIconButton(onClick = onClear, enabled = true) {
                Icon(Tabler.Outline.X, contentDescription = "Clear selection", modifier = Modifier.size(20.dp))
            }
            FilledTonalButton(
                onClick = onBulkDelete,
                enabled = selectedCount > 0,
                shape = ShapeCache.smooth12,
                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                colors = ButtonDefaults.filledTonalButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Tabler.Outline.Trash, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete")
            }
        }
    }
}

@Composable
private fun CompactIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp),
    ) {
        content()
    }
}

@Composable
private fun SelectionHintRow() {
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
                "Long-press a download to select and apply bulk actions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
