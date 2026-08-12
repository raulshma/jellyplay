package com.raulshma.jellyplay.feature.details

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.DeviceFloppy
import com.composables.icons.tabler.outline.FileDescription
import com.composables.icons.tabler.outline.Movie
import com.raulshma.jellyplay.core.model.DownloadAttachment
import com.raulshma.jellyplay.core.model.DownloadFileEntry
import com.raulshma.jellyplay.core.model.DownloadFileInventory
import com.raulshma.jellyplay.core.model.DownloadedFileCategory
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.SheetSection
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.components.formatDurationFromTicks
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Full download-details bottom sheet for the media-detail screen. Opens when the
 * user taps [DownloadInfoCard] and consolidates everything about the on-device
 * item into one scrollable surface:
 *
 *  - **Download** — lifecycle state sourced from the attached [DownloadAttachment]
 *    (status, sizes, progress, date, container, file-present, source id, path).
 *  - **Media** — identity basics from [MediaItem] (title, type, year, episode
 *    context, runtime) plus the shared [WatchProgressSection].
 *  - **Media info** — per-source video/audio/subtitle streams via the shared
 *    [MediaSourceInfoSection] (the same rendering the Technical Info screen uses).
 *  - **Downloaded files** — every on-disk file belonging to the item (media,
 *    subtitles, trickplay sprite sheets, segments, images) grouped by category,
 *    each with its path and actual storage used, sourced from a live filesystem
 *    walk ([DownloadFileInventory]).
 *
 * Uses [TvSafeSheet] so D-pad focus works on TV, matching the rest of the detail
 * screen's sheet handling ([ResyncSheet], [SeriesDownloadSheet]).
 *
 * @param download the attached download, or null for a local origin with no
 *   per-item attachment (the Download section is then omitted).
 * @param item the unified detail item (identity + watch state).
 * @param mediaSources the (possibly probed) sources to render under Media info.
 * @param inventory the on-disk file inventory; null while it is being loaded
 *   (the files section then shows a loading affordance).
 * @param isLoadingInventory true while [inventory] is being computed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadDetailsSheet(
    download: DownloadAttachment?,
    item: MediaItem,
    mediaSources: List<MediaSource>,
    inventory: DownloadFileInventory?,
    isLoadingInventory: Boolean,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    TvSafeSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(
                title = stringResource(R.string.detail_download_details_title),
                icon = Tabler.Outline.DeviceFloppy,
                subtitle = item.name,
                onClose = onDismiss,
            )

            // ── Download lifecycle ──
            if (download != null) {
                DownloadLifecycleSection(download)
            }

            // ── Media identity + watch progress ──
            MediaIdentitySection(item)

            // ── Media info (sources + streams) ──
            SheetHeading(text = stringResource(R.string.detail_download_section_media_info))
            if (mediaSources.isEmpty()) {
                Text(
                    text = stringResource(R.string.detail_media_info_no_info_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                mediaSources.forEachIndexed { index, source ->
                    MediaSourceInfoSection(
                        source = source,
                        sourceIndex = index,
                        totalSources = mediaSources.size,
                    )
                }
            }

            // ── Downloaded files (every on-disk artifact, grouped by category) ──
            DownloadedFilesSection(
                inventory = inventory,
                isLoading = isLoadingInventory,
            )
        }
    }
}

@Composable
private fun DownloadLifecycleSection(download: DownloadAttachment) {
    SheetSection(
        modifier = Modifier.padding(horizontal = 16.dp),
        icon = Tabler.Outline.DeviceFloppy,
        title = stringResource(R.string.detail_download_section_download),
    ) {
        InfoLine(
            label = stringResource(R.string.detail_status),
            value = downloadStatusLabel(download.status),
        )
        InfoLine(
            label = stringResource(R.string.detail_file_size),
            value = download.totalSizeBytes.formatBytes().takeIf { it.isNotBlank() } ?: "—",
        )
        InfoLine(
            label = stringResource(R.string.detail_downloaded_bytes),
            value = download.downloadedBytes.formatBytes().takeIf { it.isNotBlank() } ?: "—",
        )
        if (download.downloadedBytes > 0 && download.totalSizeBytes > 0 &&
            download.downloadedBytes < download.totalSizeBytes
        ) {
            InfoLine(
                label = stringResource(R.string.detail_progress),
                value = "${(download.downloadedBytes.toFloat() / download.totalSizeBytes * 100).toInt()}%",
            )
        }
        InfoLine(
            label = stringResource(R.string.detail_downloaded),
            value = if (download.createdAtEpochMillis > 0) {
                formatDate(download.createdAtEpochMillis)
            } else "—",
        )
        download.container?.takeIf { it.isNotBlank() }?.let { container ->
            InfoLine(label = stringResource(R.string.detail_container), value = container.uppercase())
        }
        InfoLine(
            label = stringResource(R.string.detail_file_present),
            value = if (download.isCompletedFilePresent) "Yes" else "No",
        )
        download.mediaSourceId?.takeIf { it.isNotBlank() }?.let { sourceId ->
            InfoLine(label = stringResource(R.string.detail_media_source), value = sourceId)
        }
        download.downloadPath?.takeIf { it.isNotBlank() }?.let { path ->
            WrappedInfoLine(
                label = stringResource(R.string.detail_download_path),
                value = path,
            )
        }
    }
}

@Composable
private fun MediaIdentitySection(item: MediaItem) {
    SheetSection(
        modifier = Modifier.padding(horizontal = 16.dp),
        icon = Tabler.Outline.Movie,
        title = stringResource(R.string.detail_download_section_media),
    ) {
        InfoLine(label = stringResource(R.string.detail_media_title), value = item.name)
        item.originalTitle?.takeIf { it.isNotBlank() && it != item.name }?.let { original ->
            InfoLine(label = stringResource(R.string.detail_original_title), value = original)
        }
        InfoLine(label = stringResource(R.string.detail_media_type), value = mediaTypeLabel(item.mediaType))
        item.year?.let { year ->
            InfoLine(label = stringResource(R.string.detail_year), value = year.toString())
        }
        episodeContext(item)?.let { context ->
            InfoLine(label = stringResource(R.string.detail_episode_label), value = context)
        }
        item.runTimeTicks?.takeIf { it > 0L }?.let { ticks ->
            InfoLine(
                label = stringResource(R.string.detail_runtime),
                value = formatDurationFromTicks(ticks),
            )
        }

        val positionTicks = item.playbackPositionTicks ?: 0L
        if (item.isPlayed || positionTicks > 0L) {
            Spacer(Modifier.height(4.dp))
            WatchProgressSection(item)
        }
    }
}

@Composable
private fun DownloadedFilesSection(
    inventory: DownloadFileInventory?,
    isLoading: Boolean,
) {
    val context = LocalContext.current
    SheetSection(
        modifier = Modifier.padding(horizontal = 16.dp),
        icon = Tabler.Outline.FileDescription,
        title = stringResource(R.string.detail_download_section_files),
    ) {
        when {
            isLoading && inventory == null -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.detail_download_files_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            inventory == null || inventory.entries.isEmpty() -> {
                Text(
                    text = stringResource(R.string.detail_download_files_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                InfoLine(
                    label = stringResource(R.string.detail_download_files_total),
                    value = inventory.totalSizeBytes.formatBytes(),
                )
                // Group by category preserving the enum's stable order (MEDIA → IMAGE).
                inventory.entries
                    .groupBy { it.category }
                    .toSortedMap(compareBy { it.ordinal })
                    .forEach { (category, entries) ->
                        Spacer(Modifier.height(10.dp))
                        CategoryGroup(category = category, entries = entries, context = context)
                    }
            }
        }
    }
}

@Composable
private fun CategoryGroup(
    category: DownloadedFileCategory,
    entries: List<DownloadFileEntry>,
    context: android.content.Context,
) {
    val subtotal = entries.sumOf { it.sizeBytes }
    val countLabel = context.resources.getQuantityString(
        R.plurals.detail_files_count,
        entries.size,
        entries.size,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = categoryLabel(category),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$countLabel · ${subtotal.formatBytes()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // Location: the shared parent dir for multi-file bundles (trickplay /
    // subtitles live in their own subdirs; images share the downloads dir), or
    // the full file path for single-file categories.
    val location = if (entries.size == 1) {
        entries.first().path
    } else {
        // entries in a category share a parent (same subdir / same downloads dir).
        entries.firstOrNull()?.path?.let { File(it).parent } ?: ""
    }
    if (location.isNotBlank()) {
        Text(
            text = location,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }

    Spacer(Modifier.height(4.dp))
    entries.forEach { entry -> FileEntryRow(entry) }
}

@Composable
private fun FileEntryRow(entry: DownloadFileEntry) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = entry.sizeBytes.formatBytes(),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun categoryLabel(category: DownloadedFileCategory): String = when (category) {
    DownloadedFileCategory.MEDIA -> stringResource(R.string.detail_file_category_media)
    DownloadedFileCategory.SUBTITLE -> stringResource(R.string.detail_file_category_subtitle)
    DownloadedFileCategory.TRICKPLAY -> stringResource(R.string.detail_file_category_trickplay)
    DownloadedFileCategory.SEGMENT -> stringResource(R.string.detail_file_category_segment)
    DownloadedFileCategory.IMAGE -> stringResource(R.string.detail_file_category_image)
}

/** A label/value row whose value wraps across lines (for paths / file names). */
@Composable
private fun WrappedInfoLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Section-spanning title rendered outside a [SheetSection] (e.g. the Media info heading). */
@Composable
private fun SheetHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .semantics { heading() },
    )
}

/** "S1E2 · Series Name" style context for an episode; null for non-episodes. */
private fun episodeContext(item: MediaItem): String? {
    if (item.mediaType != MediaType.EPISODE) return null
    val parts = buildList {
        val s = item.seasonNumber
        val e = item.episodeNumber
        if (s != null && e != null) {
            add("S${s.toString().padStart(2, '0')}E${e.toString().padStart(2, '0')}")
        } else if (e != null) {
            add("E${e.toString().padStart(2, '0')}")
        }
    }
    val label = parts.joinToString(" · ")
    val series = item.seriesName?.takeIf { it.isNotBlank() }
    return listOfNotNull(label.takeIf { it.isNotBlank() }, series).joinToString(" · ").ifBlank { null }
}

/** Localized, human-readable label for a [MediaType]. */
@Composable
private fun mediaTypeLabel(type: MediaType): String = when (type) {
    MediaType.MOVIE -> "Movie"
    MediaType.SERIES -> "Series"
    MediaType.SEASON -> "Season"
    MediaType.EPISODE -> "Episode"
    MediaType.MUSIC -> "Music"
    MediaType.AUDIO -> "Audio"
    MediaType.ALBUM -> "Album"
    MediaType.ARTIST -> "Artist"
    MediaType.MUSIC_VIDEO -> "Music video"
    MediaType.COLLECTION -> "Collection"
    MediaType.PHOTO -> "Photo"
    MediaType.PHOTO_FOLDER -> "Photo folder"
    MediaType.LIVE_TV -> "Live TV"
    MediaType.CHANNEL -> "Channel"
    MediaType.UNKNOWN -> "Unknown"
}

private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
