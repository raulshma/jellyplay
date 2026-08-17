package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Clock
import com.composables.icons.tabler.outline.CloudOff
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.Refresh
import com.raulshma.jellyplay.core.data.repository.ResolvedMediaRef
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEventType
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.components.formatDurationMs
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import kotlin.math.abs

/**
 * Details sheet for pending playback-progress sync. Opened from the home dock
 * `SyncStatusIcon`. Lists the queued outbox entries (oldest-first) with their
 * event type and captured position, summarises overall drain status, and offers
 * a "Sync now" action that enqueues the drain worker.
 *
 * Each row is enriched with the item's media details (poster thumbnail + title)
 * via [itemDetails], resolved offline-first by [HomeViewModel] (see
 * [HomeViewModel.ensurePendingItemDetails]). When a row's id hasn't resolved
 * yet — or neither the offline store nor the server had a row for it — the row
 * falls back to a truncated-id pill so the user still sees "something queued".
 *
 * Built on [TvSafeSheet] (TV full-screen Dialog / mobile ModalBottomSheet) to
 * match the project's sheet design language (SeerrRequestDialog,
 * HomeSectionConfigSheet).
 *
 * @param itemDetails per-item resolved media (title + poster URL), keyed by
 *   the outbox entry's `itemId`. May be partial during initial resolution.
 * @param onSyncNow invoked when the user taps "Sync now"; the caller is
 *   expected to dismiss the sheet afterwards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SyncDetailsSheet(
    entries: List<PlaybackOutboxEntry>,
    itemDetails: Map<String, ResolvedMediaRef>,
    offlineMode: OfflineMode,
    onSyncNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isOnline = offlineMode == OfflineMode.ONLINE
    val count = entries.size
    val isDraining = isOnline && count > 0

    TvSafeSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.sync_details_title),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            // ── Summary header: status icon + count + status line ──────────
            SheetHeader(
                title = stringResource(R.string.sync_items_queued, count),
                subtitle = when {
                    count == 0 -> stringResource(R.string.sync_up_to_date)
                    isDraining -> stringResource(R.string.syncing)
                    else -> stringResource(R.string.sync_will_sync_when_online)
                },
                icon = if (isDraining) Tabler.Outline.Refresh else Tabler.Outline.CloudOff,
                modifier = Modifier.padding(bottom = 14.dp),
            )

            HorizontalDivider(
                color = colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // ── Pending entries list ────────────────────────────────────────
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.sync_up_to_date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        PendingEntryRow(entry = entry, resolved = itemDetails[entry.itemId])
                    }
                }
            }

            Spacer(Modifier.size(16.dp))

            // ── Action row ──────────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.sync_close))
                }
                Spacer(Modifier.size(8.dp))
                FilledTonalButton(
                    onClick = onSyncNow,
                    enabled = isOnline && count > 0,
                ) {
                    Text(if (isOnline) stringResource(R.string.sync_now) else stringResource(R.string.sync_now_disabled_offline))
                }
            }
        }
    }
}

@Composable
private fun PendingEntryRow(
    entry: PlaybackOutboxEntry,
    resolved: ResolvedMediaRef?,
) {
    val colorScheme = MaterialTheme.colorScheme
    val focusState = rememberTvFocusState()
    val item = resolved?.item
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth12)
            .clip(ShapeCache.smooth12)
            .background(colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // Poster thumbnail (2:3). Falls back to a Movie icon placeholder while
        // the URL is unresolved or still loading.
        MediaImage(
            url = resolved?.posterUrl.orEmpty(),
            contentDescription = item?.name,
            modifier = Modifier
                .size(width = 40.dp, height = 60.dp)
                .aspectRatio(2f / 3f)
                .clip(ShapeCache.smooth8),
            placeholderIcon = Tabler.Outline.Movie,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = item?.let(::formatMediaTitle) ?: stringResource(R.string.sync_unknown_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = buildString {
                    append(entry.eventTypeLabel())
                    append("  ·  ")
                    append(formatEntryDetail(entry))
                },
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        // Truncated-id pill is only meaningful when the row hasn't resolved to
        // a title — otherwise the title already identifies the item.
        if (item == null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colorScheme.secondaryContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = entry.itemId.takeLast(6),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSecondaryContainer,
                )
            }
        } else {
            Icon(
                imageVector = Tabler.Outline.Clock,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Title line for a resolved row. For episodes, prefixes the series name and
 * `S##E##` context (the episode title alone rarely identifies the show); for
 * everything else just the item name.
 */
private fun formatMediaTitle(item: com.raulshma.jellyplay.core.model.MediaItem): String {
    if (item.mediaType != MediaType.EPISODE) return item.name
    val prefix = buildString {
        item.seriesName?.let { append(it).append(" ") }
        val s = item.seasonNumber
        val e = item.episodeNumber
        if (s != null && e != null) {
            append("S").append(s).append("E").append(e.toString().padStart(2, '0'))
        } else if (e != null) {
            append("E").append(e.toString().padStart(2, '0'))
        } else if (s != null) {
            append("S").append(s)
        }
    }
    return if (prefix.isBlank()) item.name else "$prefix · ${item.name}"
}

@Composable
private fun PlaybackOutboxEntry.eventTypeLabel(): String = when (eventType) {
    PlaybackOutboxEventType.START -> stringResource(R.string.sync_event_start)
    PlaybackOutboxEventType.PROGRESS -> stringResource(R.string.sync_event_progress)
    PlaybackOutboxEventType.STOP -> stringResource(R.string.sync_event_stop)
    PlaybackOutboxEventType.PLAYED -> stringResource(R.string.sync_event_played)
    PlaybackOutboxEventType.UNPLAYED -> stringResource(R.string.sync_event_unplayed)
    PlaybackOutboxEventType.FAVORITE -> stringResource(R.string.sync_event_favorite)
    PlaybackOutboxEventType.UNFAVORITE -> stringResource(R.string.sync_event_unfavorite)
}

/**
 * Renders the entry detail line: position (mm:ss or hh:mm:ss) + relative age.
 * Position uses Jellyfin's 10,000,000 ticks/second scale.
 */
private fun formatEntryDetail(entry: PlaybackOutboxEntry): String {
    val positionLabel = formatDurationMs(entry.positionTicks / 10_000)
    val ageLabel = formatAge(System.currentTimeMillis() - entry.recordedAt)
    return "$positionLabel  ·  $ageLabel"
}

private fun formatAge(deltaMillis: Long): String {
    val minutes = abs(deltaMillis) / 60_000
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "${minutes}m ago"
        minutes < 24L * 60L -> "${minutes / 60L}h ${minutes % 60L}m ago"
        else -> {
            val days = minutes / (24L * 60L)
            val remainingHours = (minutes % (24L * 60L)) / 60L
            if (remainingHours == 0L) "${days}d ago" else "${days}d ${remainingHours}h ago"
        }
    }
}
