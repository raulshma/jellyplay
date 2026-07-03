package com.raulshma.jellyplay.feature.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current

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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .tvFocusRestorer()
                    .focusRequester(listFocusRequester),
                contentPadding = PaddingValues(
                    start = adaptiveInfo.contentPadding(isTv),
                    end = adaptiveInfo.contentPadding(isTv),
                    top = 8.dp,
                    bottom = adaptiveInfo.bottomPadding(isTv),
                ),
                verticalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
            ) {
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
                            formatBytes = { viewModel.formatBytes(it) },
                            formatSpeed = { viewModel.formatSpeed(it) },
                            formatEta = { d, t, s -> viewModel.formatEta(d, t, s) },
                            // Completed downloads open their detail page on tap
                            // (matching the online experience) rather than auto-playing.
                            // A distinct Play action is still available in the row (issue #65-A).
                            onClick = {
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
                            onDelete = { viewModel.deleteDownload(download) },
                            onRetry = { viewModel.retryDownload(download) },
                            onMoveToFront = { viewModel.moveToFront(download) },
                            onLowerPriority = { viewModel.lowerPriority(download) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadItemRow(
    item: DownloadItem,
    formatBytes: (Long) -> String,
    formatSpeed: (Long) -> String,
    formatEta: (Long, Long, Long) -> String,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onMoveToFront: () -> Unit,
    onLowerPriority: () -> Unit,
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
            .clickable(
                enabled = item.status == DownloadStatus.COMPLETED,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
