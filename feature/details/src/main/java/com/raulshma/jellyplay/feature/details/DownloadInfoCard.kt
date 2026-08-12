package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.DeviceFloppy
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.DownloadAttachment
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.ui.components.formatDurationFromTicks
import com.raulshma.jellyplay.core.ui.components.formatRelativeTime
import java.text.DateFormat
import java.util.Date

/**
 * Download-info card for the unified media-detail screen.
 *
 * Ports `OfflineDetailScreen.DownloadInfoCard` so the same card renders for a
 * REMOTE item with an attached completed download AND a LOCAL/offline origin.
 * Sources file size / downloaded-at / status / partial-progress from the
 * attached [DownloadAttachment], and appends a [WatchProgressSection] when the
 * item has watch activity (sourced from the unified [MediaItem], which is
 * populated for BOTH origins).
 *
 * @param download the attached download (status / bytes / created-at). Null
 *   hides the card — callers gate it on `detailContext.download != null`.
 * @param item the unified detail item (provides watch state for both origins).
 */
@Composable
internal fun DownloadInfoCard(
    download: DownloadAttachment?,
    item: MediaItem,
    modifier: Modifier = Modifier,
    /** When non-null, the card header becomes a tappable affordance that opens
     *  the full download-details sheet. Null keeps the card informational only
     *  (previews, legacy callers). */
    onClick: (() -> Unit)? = null,
) {
    if (download == null) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Header row: title + optional chevron. When [onClick] is supplied the
        // whole header is clickable (rounded ripple) and announces itself as a
        // button so the "open full details" intent is discoverable.
        Row(
            modifier = if (onClick != null) {
                Modifier
                    .fillMaxWidth()
                    .clip(ShapeCache.smooth12)
                    .clickable(onClick = onClick)
                    .padding(vertical = 2.dp)
            } else {
                Modifier.fillMaxWidth()
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Tabler.Outline.DeviceFloppy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = stringResource(R.string.detail_download_info),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (onClick != null) {
                Icon(
                    Tabler.Outline.ChevronRight,
                    contentDescription = stringResource(R.string.detail_view_download_details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        InfoLine(
            label = stringResource(R.string.detail_file_size),
            value = download.totalSizeBytes.formatBytes().takeIf { it.isNotBlank() } ?: "—",
        )
        InfoLine(
            label = stringResource(R.string.detail_downloaded),
            value = if (download.createdAtEpochMillis > 0) {
                formatDate(download.createdAtEpochMillis)
            } else "—",
        )
        InfoLine(
            label = stringResource(R.string.detail_status),
            value = downloadStatusLabel(download.status),
        )
        if (download.downloadedBytes > 0 && download.totalSizeBytes > 0 &&
            download.downloadedBytes < download.totalSizeBytes
        ) {
            InfoLine(
                label = stringResource(R.string.detail_progress),
                value = "${(download.downloadedBytes.toFloat() / download.totalSizeBytes * 100).toInt()}%",
            )
        }

        // ── Watch progress ──
        // Shown alongside the download info so the user can see, at a glance,
        // how far they got and when they last watched. Skipped entirely for
        // items with no recorded progress (position == null/0 and not played)
        // so fresh downloads don't show a redundant "0%" row.
        val positionTicks = item.playbackPositionTicks ?: 0L
        val hasWatchProgress = item.isPlayed || positionTicks > 0L
        if (hasWatchProgress) {
            Spacer(Modifier.height(4.dp))
            WatchProgressSection(item)
        }
    }
}

/**
 * Watch-progress breakdown shown inside [DownloadInfoCard]. Ports
 * `OfflineDetailScreen.WatchProgressSection`, but sources every field from the
 * unified [MediaItem] (populated for both remote-with-download and local origins).
 *
 * Renders: watch-status label (Watched / In Progress / Started), watched %,
 * position-of-runtime ("23m of 1h 2m"), and a last-watched relative time with an
 * absolute-date fallback. The percentage is computed via [computeWatchPercentage]
 * when not stored (MediaItem has no playedPercentage field).
 */
@Composable
internal fun WatchProgressSection(item: MediaItem) {
    val positionTicks = item.playbackPositionTicks ?: 0L
    val runtimeTicks = item.runTimeTicks

    val watchStatus = when {
        item.isPlayed -> stringResource(R.string.detail_watched_status_watched)
        positionTicks > 0L -> stringResource(R.string.detail_watched_status_in_progress)
        else -> stringResource(R.string.detail_watched_status_started)
    }
    InfoLine(label = stringResource(R.string.detail_watch_status), value = watchStatus)

    // Watched percentage: computed from position/runtime (MediaItem carries no
    // stored percentage; an item seeded only with ticks still shows a %).
    val percentage = computeWatchPercentage(positionTicks, runtimeTicks)
    if (percentage > 0.0) {
        InfoLine(
            label = stringResource(R.string.detail_watched_status_label),
            value = "${percentage.toInt()}%",
        )
    }

    // Position / runtime — "23m of 1h 2m".
    if (positionTicks > 0L) {
        val posStr = formatDurationFromTicks(positionTicks)
        val value = if (runtimeTicks != null && runtimeTicks > positionTicks) {
            stringResource(R.string.detail_position_of, posStr, formatDurationFromTicks(runtimeTicks))
        } else {
            posStr
        }
        InfoLine(label = stringResource(R.string.detail_position), value = value)
    }

    // Last-watched relative time (e.g. "2d ago"). Falls back to absolute date
    // when the relative formatter can't parse the stored timestamp.
    val lastPlayedRelative = formatRelativeTime(item.lastPlayedDate)
    val lastPlayedValue = lastPlayedRelative ?: item.lastPlayedDate?.let { formatAbsoluteDate(it) }
    if (lastPlayedValue != null) {
        InfoLine(label = stringResource(R.string.detail_last_watched), value = lastPlayedValue)
    }
}

/** Localized display label for a [DownloadStatus]. */
@Composable
internal fun downloadStatusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.PENDING -> stringResource(R.string.detail_download_status_pending)
    DownloadStatus.QUEUED -> stringResource(R.string.detail_download_status_queued)
    DownloadStatus.DOWNLOADING -> stringResource(R.string.detail_download_status_downloading)
    DownloadStatus.PAUSED -> stringResource(R.string.detail_download_status_paused)
    DownloadStatus.COMPLETED -> stringResource(R.string.detail_download_status_completed)
    DownloadStatus.FAILED -> stringResource(R.string.detail_download_status_failed)
    DownloadStatus.CANCELLED -> stringResource(R.string.detail_download_status_cancelled)
}

/** Derives a 0–100 watched percentage from position/runtime, guarding /0. */
internal fun computeWatchPercentage(positionTicks: Long?, runTimeTicks: Long?): Double {
    if (positionTicks == null || positionTicks <= 0L) return 0.0
    if (runTimeTicks == null || runTimeTicks <= 0L) return 0.0
    return ((positionTicks.toDouble() / runTimeTicks.toDouble()) * 100.0).coerceIn(0.0, 100.0)
}

/** Best-effort absolute-date fallback for an ISO timestamp string. */
internal fun formatAbsoluteDate(isoTimestamp: String): String? =
    runCatching {
        val millis = runCatching {
            java.time.OffsetDateTime.parse(isoTimestamp).toInstant().toEpochMilli()
        }.getOrElse {
            java.time.LocalDateTime.parse(isoTimestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
    }.getOrNull()

/** Label/value row used by [DownloadInfoCard] and [WatchProgressSection]. */
@Composable
internal fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
