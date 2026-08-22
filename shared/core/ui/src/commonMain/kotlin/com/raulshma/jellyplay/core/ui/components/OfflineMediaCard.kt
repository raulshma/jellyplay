package com.raulshma.jellyplay.core.ui.components
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_downloaded
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_watched

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.Download
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.hasWatchProgress
import com.raulshma.jellyplay.core.model.toMediaItem

/**
 * Offline library / home card. A thin wrapper over the shared [PosterCard]
 * so offline content renders identically to online content (rating badge,
 * watched check, progress bar, TV focus, press scale, peek preview).
 *
 * Adds two offline-specific overlays:
 *  - a "Downloaded" check chip when [OfflineMediaItem.downloadStatus] is
 *    [DownloadStatus.COMPLETED], and
 *  - a download-progress overlay (percent label) while DOWNLOADING.
 */
@Composable
fun OfflineMediaCard(
    item: OfflineMediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayClick: (() -> Unit)? = null,
    sharedElementKey: String? = null,
    /**
     * Whether to render the offline status badge ("Downloaded" / progress %).
     * On the offline home screen every card is by definition downloaded, so
     * the badge is redundant there — callers set this false. Defaults to true
     * so the standalone library (which mixes completed/in-flight states) keeps
     * the affordance.
     */
    showStatusBadge: Boolean = true,
) {
    val mediaItem = item.toMediaItem()
    val posterUrl = item.posterPath.orEmpty()
    // Gate the progress bar on the normalized watch state so it never shows
    // alongside the "Watched" chip/badge (toMediaItem treats >=95% as played).
    val hasProgress = mediaItem.hasWatchProgress
    // PosterCard's progressPercent is a 0–1 fraction (it feeds
    // fillMaxWidth(fraction)), but OfflineMediaItem.playedPercentage is 0–100,
    // so divide by 100 here — otherwise 1% watched would fill the whole bar.
    val progressFraction = (item.playedPercentage.toFloat() / 100f).coerceIn(0f, 1f)

    Box(modifier = modifier) {
        PosterCard(
            item = mediaItem,
            imageUrl = posterUrl,
            fallbackUrls = listOfNotNull(item.backdropPath),
            blurHash = item.blurHashPrimary,
            onClick = onClick,
            onPlayClick = onPlayClick,
            sharedElementKey = sharedElementKey,
            showProgress = hasProgress,
            progressPercent = progressFraction,
        )

        // Read the normalized isPlayed (from toMediaItem) so this chip agrees
        // with PosterCard's watched badge — both treat >=95% resume as played.
        if (mediaItem.isPlayed) {
            OfflineStatusChip(
                label = stringResource(Res.string.core_ui_watched),
                icon = Tabler.Outline.Check,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )
        }

        // Offline-status badge. Suppressed on the offline home (where every
        // item is already downloaded and the badge is redundant); shown on the
        // standalone library where completed vs in-flight states matter.
        if (showStatusBadge) {
            when (item.downloadStatus) {
                DownloadStatus.COMPLETED -> OfflineStatusChip(
                    label = stringResource(Res.string.core_ui_downloaded),
                    icon = Tabler.Outline.Check,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
                DownloadStatus.DOWNLOADING -> {
                    val progress = if (item.totalSizeBytes > 0) {
                        (item.downloadedBytes.toFloat() / item.totalSizeBytes).coerceIn(0f, 1f)
                    } else 0f
                    val animated by animateFloatAsState(
                        targetValue = progress,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "offlineCardProgress",
                    )
                    OfflineProgressChip(
                        fraction = animated,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                    )
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun OfflineStatusChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    GlassBadge(
        modifier = modifier,
        background = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        shape = ShapeCache.smooth4,
        horizontalPadding = 6.dp,
        verticalPadding = 3.dp,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun OfflineProgressChip(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    GlassBadge(
        modifier = modifier,
        background = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        shape = ShapeCache.smooth4,
        horizontalPadding = 6.dp,
        verticalPadding = 3.dp,
    ) {
        Icon(
            imageVector = Tabler.Outline.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = "${(fraction * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}
