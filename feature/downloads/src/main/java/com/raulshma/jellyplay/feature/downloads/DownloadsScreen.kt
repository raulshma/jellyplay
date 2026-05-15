package com.raulshma.jellyplay.feature.downloads

import androidx.compose.animation.AnimatedVisibility
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import androidx.compose.animation.core.animateFloatAsState
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import com.raulshma.jellyplay.core.ui.tv.isTvDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onItemClick: (String) -> Unit,
    onPlayOffline: (filePath: String, title: String) -> Unit,
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val downloads = viewModel.downloads
    val networkStatus by com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val headerStatus = com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus(
        isLoading = viewModel.isLoading,
        hasError = false,
        networkStatus = networkStatus,
    )

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = isTvDevice()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Downloads")
                    com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
                        status = headerStatus,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
        )

        if (viewModel.totalStorageBytes > 0) {
            Text(
                "Storage used: ${viewModel.formatBytes(viewModel.totalStorageBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = adaptiveInfo.contentPadding(isTv), end = adaptiveInfo.contentPadding(isTv), bottom = 8.dp),
            )
        }

        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No downloads yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
                            animationSpec = tween(AnimationTokens.MediumDuration, delayMillis = index * 50, easing = AlphaEasing)
                        ) + slideInVertically(
                            initialOffsetY = { it / 10 },
                            animationSpec = tween(AnimationTokens.MediumDuration, delayMillis = index * 50, easing = FancyTransitionEasing),
                        ),
                    ) {
                        DownloadItemRow(
                            item = download,
                            formatBytes = { viewModel.formatBytes(it) },
                            formatSpeed = { viewModel.formatSpeed(it) },
                            formatEta = { d, t, s -> viewModel.formatEta(d, t, s) },
                            onClick = {
                                if (download.status == DownloadStatus.COMPLETED) {
                                    onPlayOffline(download.downloadPath, download.name)
                                }
                            },
                            onCancel = { viewModel.cancelDownload(download) },
                            onPause = { viewModel.pauseDownload(download) },
                            onResume = { viewModel.resumeDownload(download) },
                            onDelete = { viewModel.deleteDownload(download) },
                            onRetry = { viewModel.retryDownload(download) },
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
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    val progress = if (item.totalSizeBytes > 0) {
        item.downloadedBytes.toFloat() / item.totalSizeBytes
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(AnimationTokens.MediumDuration, easing = AlphaEasing),
        label = "downloadProgress",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable().clickable(enabled = item.status == DownloadStatus.COMPLETED, onClick = onClick),
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
                            com.raulshma.jellyplay.core.model.MediaType.ALBUM -> Icons.Default.MusicNote
                            else -> Icons.Default.Movie
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
                        LinearProgressIndicator(
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
                        Text(
                            "Failed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
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
                    IconButton(onClick = onPause) {
                        Icon(
                            Icons.Default.Pause,
                            "Pause",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.Default.Delete,
                            "Cancel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DownloadStatus.PENDING -> {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.Default.Delete,
                            "Cancel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DownloadStatus.PAUSED -> {
                    IconButton(onClick = onResume) {
                        Icon(
                            Icons.Default.PlayArrow,
                            "Resume",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.Default.Delete,
                            "Cancel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DownloadStatus.FAILED -> {
                    IconButton(onClick = onRetry) {
                        Icon(
                            Icons.Default.Refresh,
                            "Retry",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                DownloadStatus.COMPLETED -> {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {}
            }
        }
    }
}
