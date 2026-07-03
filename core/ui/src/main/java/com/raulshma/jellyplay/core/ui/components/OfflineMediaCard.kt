package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.Download
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.OfflineMediaItem
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
    val hasProgress = item.playedPercentage in 1.0..94.99
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

        // Offline-status badge. Suppressed on the offline home (where every
        // item is already downloaded and the badge is redundant); shown on the
        // standalone library where completed vs in-flight states matter.
        if (showStatusBadge) {
            when (item.downloadStatus) {
                DownloadStatus.COMPLETED -> OfflineStatusChip(
                    label = "Downloaded",
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
                        animationSpec = tween(durationMillis = 400),
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(ShapeCache.smooth4)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(3.dp))
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(ShapeCache.smooth4)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Icon(
            imageVector = Tabler.Outline.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = "${(fraction * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}
