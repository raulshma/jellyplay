package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.BadgeHd
import com.composables.icons.tabler.outline.DeviceFloppy
import com.composables.icons.tabler.outline.FileDescription
import com.composables.icons.tabler.outline.Folder
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.Music
import com.composables.icons.tabler.outline.Photo
import com.composables.icons.tabler.outline.Subtitles
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.model.DownloadAttachment
import com.raulshma.jellyplay.core.model.DownloadFileEntry
import com.raulshma.jellyplay.core.model.DownloadFileInventory
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.DownloadedFileCategory
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.SheetSection
import com.raulshma.jellyplay.core.ui.components.SheetTabRow
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.components.formatDurationFromTicks
import com.raulshma.jellyplay.core.ui.image.MediaImage
import java.io.File
import java.text.DateFormat
import java.util.Date
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_container
import com.raulshma.jellyplay.feature.details.generated.resources.detail_download_details_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_download_files_empty
import com.raulshma.jellyplay.feature.details.generated.resources.detail_download_files_loading
import com.raulshma.jellyplay.feature.details.generated.resources.detail_download_files_total
import com.raulshma.jellyplay.feature.details.generated.resources.detail_download_path
import com.raulshma.jellyplay.feature.details.generated.resources.detail_download_section_download
import com.raulshma.jellyplay.feature.details.generated.resources.detail_download_section_files
import com.raulshma.jellyplay.feature.details.generated.resources.detail_download_section_media
import com.raulshma.jellyplay.feature.details.generated.resources.detail_download_section_media_info
import com.raulshma.jellyplay.feature.details.generated.resources.detail_download_tab_media_info
import com.raulshma.jellyplay.feature.details.generated.resources.detail_download_tab_overview
import com.raulshma.jellyplay.feature.details.generated.resources.detail_download_tab_storage
import com.raulshma.jellyplay.feature.details.generated.resources.detail_downloaded_bytes
import com.raulshma.jellyplay.feature.details.generated.resources.detail_downloaded_date
import com.raulshma.jellyplay.feature.details.generated.resources.detail_episode_label
import com.raulshma.jellyplay.feature.details.generated.resources.detail_file_category_image
import com.raulshma.jellyplay.feature.details.generated.resources.detail_file_category_media
import com.raulshma.jellyplay.feature.details.generated.resources.detail_file_category_segment
import com.raulshma.jellyplay.feature.details.generated.resources.detail_file_category_subtitle
import com.raulshma.jellyplay.feature.details.generated.resources.detail_file_category_trickplay
import com.raulshma.jellyplay.feature.details.generated.resources.detail_file_present
import com.raulshma.jellyplay.feature.details.generated.resources.detail_file_size
import com.raulshma.jellyplay.feature.details.generated.resources.detail_media_info_no_info_description
import com.raulshma.jellyplay.feature.details.generated.resources.detail_media_source
import com.raulshma.jellyplay.feature.details.generated.resources.detail_media_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_media_type
import com.raulshma.jellyplay.feature.details.generated.resources.detail_original_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_progress
import com.raulshma.jellyplay.feature.details.generated.resources.detail_runtime
import com.raulshma.jellyplay.feature.details.generated.resources.detail_status
import com.raulshma.jellyplay.feature.details.generated.resources.detail_year
import com.raulshma.jellyplay.feature.details.generated.resources.detail_files_count
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource

/**
 * Full download-details bottom sheet for the media-detail screen. Opens when the
 * user taps [DownloadInfoCard] and consolidates everything about the on-device
 * item into an expressive multi-tab surface:
 *
 *  - **Overview** — Hero media card (poster/icon, title, specs, status pill), download
 *    lifecycle state, media identity, and watch progress.
 *  - **Media info** — Per-source video/audio/subtitle streams via [MediaSourceInfoSection].
 *  - **Storage & Files** — Storage distribution breakdown bar and on-disk files
 *    grouped by category with category icons, count badges, and monospaced paths.
 *
 * Designed using inspirations from the Library Screen, Media Detail Screen, and
 * Material Design 3 Navigation. Uses [TvSafeSheet] so D-pad focus works on TV.
 *
 * @param download the attached download, or null for a local origin with no
 *   per-item attachment.
 * @param item the unified detail item (identity + watch state).
 * @param mediaSources the (possibly probed) sources to render under Media info.
 * @param inventory the on-disk file inventory; null while it is being loaded.
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
    backdropUrl: String? = null,
    posterUrl: String? = null,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    TvSafeSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            SheetHeader(
                title = stringResource(Res.string.detail_download_details_title),
                icon = Tabler.Outline.DeviceFloppy,
                subtitle = item.name,
                onClose = onDismiss,
            )

            // ── Material 3 Expressive Navigation Tab Strip ──
            val tabs = listOf(
                SheetTabSpec(
                    label = stringResource(Res.string.detail_download_tab_overview),
                    icon = Tabler.Outline.InfoCircle,
                    count = null,
                ),
                SheetTabSpec(
                    label = stringResource(Res.string.detail_download_tab_media_info),
                    icon = Tabler.Outline.BadgeHd,
                    count = mediaSources.size.takeIf { it > 0 },
                ),
                SheetTabSpec(
                    label = stringResource(Res.string.detail_download_tab_storage),
                    icon = Tabler.Outline.FileDescription,
                    count = (inventory?.entries?.size ?: 0).takeIf { it > 0 },
                ),
            )
            SheetTabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, tab ->
                    val countLabel = tab.count?.let { " ($it)" } ?: ""
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = tab.label + countLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (selectedTabIndex != 0) {
                    HeroMediaSummaryBar(
                        item = item,
                        download = download,
                        posterUrl = posterUrl,
                        inventory = inventory,
                    )
                }

                when (selectedTabIndex) {
                    0 -> {
                        // ── Overview Tab ──
                        HeroMediaCard(
                            item = item,
                            download = download,
                            mediaSources = mediaSources,
                            backdropUrl = backdropUrl,
                            posterUrl = posterUrl,
                            inventory = inventory,
                        )

                        if (download != null) {
                            DownloadLifecycleSection(download)
                        }

                        MediaIdentitySection(item)
                    }
                    1 -> {
                        // ── Media Info Tab ──
                        if (mediaSources.isEmpty()) {
                            SheetSection(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                icon = Tabler.Outline.BadgeHd,
                                title = stringResource(Res.string.detail_download_section_media_info),
                            ) {
                                Text(
                                    text = stringResource(Res.string.detail_media_info_no_info_description),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            mediaSources.forEachIndexed { index, source ->
                                MediaSourceInfoSection(
                                    source = source,
                                    sourceIndex = index,
                                    totalSources = mediaSources.size,
                                )
                            }
                        }
                    }
                    2 -> {
                        // ── Storage & Files Tab ──
                        StorageBreakdownSection(
                            inventory = inventory,
                            isLoading = isLoadingInventory,
                        )

                        DownloadedFilesSection(
                            inventory = inventory,
                            isLoading = isLoadingInventory,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Hero media identity card inspired by the Media Detail Screen. Uses the media's
 * backdrop image as card background with gradient scrim and displays poster art/icon.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroMediaCard(
    item: MediaItem,
    download: DownloadAttachment?,
    mediaSources: List<MediaSource>,
    backdropUrl: String?,
    posterUrl: String?,
    inventory: DownloadFileInventory?,
) {
    val resolvedBackdropUrl = remember(backdropUrl, inventory, item.id) {
        backdropUrl?.takeIf { it.isNotBlank() }
            ?: inventory?.entries?.firstOrNull {
                it.category == DownloadedFileCategory.IMAGE &&
                    (it.path.contains("backdrop", ignoreCase = true) || it.displayName.contains("backdrop", ignoreCase = true))
            }?.path
            ?: ""
    }

    val resolvedPosterUrl = rememberResolvedPosterUrl(posterUrl, inventory, item.id)

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(ShapeCache.smooth20)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        if (resolvedBackdropUrl.isNotBlank()) {
            MediaImage(
                url = resolvedBackdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.60f),
                                Color.Black.copy(alpha = 0.92f),
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (resolvedPosterUrl.isNotBlank()) {
                    MediaImage(
                        url = resolvedPosterUrl,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 80.dp, height = 120.dp)
                            .clip(ShapeCache.smooth14),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 120.dp)
                            .clip(ShapeCache.smooth14)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = mediaTypeIcon(item.mediaType),
                            contentDescription = item.name,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (resolvedBackdropUrl.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    episodeContext(item)?.let { epContext ->
                        Text(
                            text = epContext,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (resolvedBackdropUrl.isNotBlank()) Color(0xFF90CAF9) else MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SpecPill(
                            text = mediaTypeLabel(item.mediaType),
                            textColor = if (resolvedBackdropUrl.isNotBlank()) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            containerColor = if (resolvedBackdropUrl.isNotBlank()) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        )

                        item.year?.let { y ->
                            SpecPill(
                                text = y.toString(),
                                textColor = if (resolvedBackdropUrl.isNotBlank()) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                containerColor = if (resolvedBackdropUrl.isNotBlank()) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            )
                        }

                        item.runTimeTicks?.takeIf { it > 0L }?.let { ticks ->
                            SpecPill(
                                text = formatDurationFromTicks(ticks),
                                textColor = if (resolvedBackdropUrl.isNotBlank()) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                containerColor = if (resolvedBackdropUrl.isNotBlank()) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            )
                        }

                        // Primary source container pill if available
                        mediaSources.firstOrNull()?.container?.takeIf { it.isNotBlank() }?.let { container ->
                            SpecPill(
                                text = container.uppercase(),
                                textColor = if (resolvedBackdropUrl.isNotBlank()) Color.White else MaterialTheme.colorScheme.primary,
                                containerColor = if (resolvedBackdropUrl.isNotBlank()) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            )
                        }
                    }

                    // Download Status Pill (if present)
                    if (download != null) {
                        val statusColor = statusBadgeColor(download.status)
                        Box(
                            modifier = Modifier
                                .clip(ShapeCache.smoothPill)
                                .background(statusColor.copy(alpha = 0.20f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = downloadStatusLabel(download.status).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Compact hero media header used when viewing technical tabs to keep context. */
@Composable
private fun HeroMediaSummaryBar(
    item: MediaItem,
    download: DownloadAttachment?,
    posterUrl: String?,
    inventory: DownloadFileInventory?,
) {
    val resolvedPosterUrl = rememberResolvedPosterUrl(posterUrl, inventory, item.id)

    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (resolvedPosterUrl.isNotBlank()) {
            MediaImage(
                url = resolvedPosterUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 36.dp, height = 54.dp)
                    .clip(ShapeCache.smooth8),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 54.dp)
                    .clip(ShapeCache.smooth8)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = mediaTypeIcon(item.mediaType),
                    contentDescription = item.name,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            episodeContext(item)?.let { epContext ->
                Text(
                    text = epContext,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Static definition of one [Tab] in the sheet's tab strip. */
private data class SheetTabSpec(
    val label: String,
    val icon: ImageVector,
    val count: Int?,
)

/**
 * Resolves the best poster art available: the explicit [posterUrl] when set,
 * otherwise the first IMAGE-category inventory entry whose path or display
 * name matches "poster"/"primary".
 */
@Composable
private fun rememberResolvedPosterUrl(
    posterUrl: String?,
    inventory: DownloadFileInventory?,
    itemId: String,
): String = remember(posterUrl, inventory, itemId) {
    posterUrl?.takeIf { it.isNotBlank() }
        ?: inventory?.entries?.firstOrNull {
            it.category == DownloadedFileCategory.IMAGE &&
                (it.path.contains("poster", ignoreCase = true) ||
                 it.displayName.contains("poster", ignoreCase = true) ||
                 it.path.contains("primary", ignoreCase = true))
        }?.path
        ?: ""
}

/** Placeholder icon for media that has no downloadable poster art. */
private fun mediaTypeIcon(type: MediaType): ImageVector = when (type) {
    MediaType.AUDIO, MediaType.MUSIC, MediaType.ALBUM -> Tabler.Outline.Music
    else -> Tabler.Outline.Movie
}

@Composable
private fun SpecPill(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = Modifier
            .clip(ShapeCache.smoothPill)
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
    }
}

@Composable
private fun DownloadLifecycleSection(download: DownloadAttachment) {
    SheetSection(
        modifier = Modifier.padding(horizontal = 16.dp),
        icon = Tabler.Outline.DeviceFloppy,
        title = stringResource(Res.string.detail_download_section_download),
    ) {
        InfoLine(
            label = stringResource(Res.string.detail_status),
            value = downloadStatusLabel(download.status),
        )
        InfoLine(
            label = stringResource(Res.string.detail_file_size),
            value = download.totalSizeBytes.formatBytes().takeIf { it.isNotBlank() } ?: "—",
        )
        if (download.downloadedBytes > 0 && download.totalSizeBytes > 0 &&
            download.downloadedBytes < download.totalSizeBytes
        ) {
            InfoLine(
                label = stringResource(Res.string.detail_downloaded_bytes),
                value = download.downloadedBytes.formatBytes().takeIf { it.isNotBlank() } ?: "—",
            )
            val progressPercentStr = formatPercentage(download.downloadedBytes, download.totalSizeBytes)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { download.downloadedBytes.toFloat() / download.totalSizeBytes },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(ShapeCache.smoothPill),
                color = StatusColors.requested,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            InfoLine(
                label = stringResource(Res.string.detail_progress),
                value = progressPercentStr,
            )
        }
        InfoLine(
            label = stringResource(Res.string.detail_downloaded_date),
            value = if (download.createdAtEpochMillis > 0) {
                formatDate(download.createdAtEpochMillis)
            } else "—",
        )
        download.container?.takeIf { it.isNotBlank() }?.let { container ->
            InfoLine(label = stringResource(Res.string.detail_container), value = container.uppercase())
        }
        InfoLine(
            label = stringResource(Res.string.detail_file_present),
            value = if (download.isCompletedFilePresent) "Yes" else "No",
        )
        download.mediaSourceId?.takeIf { it.isNotBlank() }?.let { sourceId ->
            InfoLine(label = stringResource(Res.string.detail_media_source), value = sourceId)
        }
        download.downloadPath?.takeIf { it.isNotBlank() }?.let { path ->
            WrappedInfoLine(
                label = stringResource(Res.string.detail_download_path),
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
        title = stringResource(Res.string.detail_download_section_media),
    ) {
        InfoLine(label = stringResource(Res.string.detail_media_title), value = item.name)
        item.originalTitle?.takeIf { it.isNotBlank() && it != item.name }?.let { original ->
            InfoLine(label = stringResource(Res.string.detail_original_title), value = original)
        }
        InfoLine(label = stringResource(Res.string.detail_media_type), value = mediaTypeLabel(item.mediaType))
        item.year?.let { year ->
            InfoLine(label = stringResource(Res.string.detail_year), value = year.toString())
        }
        episodeContext(item)?.let { context ->
            InfoLine(label = stringResource(Res.string.detail_episode_label), value = context)
        }
        item.runTimeTicks?.takeIf { it > 0L }?.let { ticks ->
            InfoLine(
                label = stringResource(Res.string.detail_runtime),
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

/** Visual storage distribution bar showing relative disk consumption across categories. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageBreakdownSection(
    inventory: DownloadFileInventory?,
    isLoading: Boolean,
) {
    if (inventory == null || inventory.entries.isEmpty() || isLoading) return

    val categorySizes = remember(inventory) {
        inventory.entries.groupBy { it.category }.mapValues { (_, entries) -> entries.sumOf { it.sizeBytes } }
    }
    val totalSize = inventory.totalSizeBytes.coerceAtLeast(1L)

    SheetSection(
        modifier = Modifier.padding(horizontal = 16.dp),
        icon = Tabler.Outline.Folder,
        title = stringResource(Res.string.detail_download_files_total) + " · " + inventory.totalSizeBytes.formatBytes(),
    ) {
        // Multi-segment progress bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(ShapeCache.smoothPill)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        ) {
            DownloadedFileCategory.entries.forEach { category ->
                val size = categorySizes[category] ?: 0L
                if (size > 0L) {
                    val weight = (size.toFloat() / totalSize.toFloat()).coerceAtLeast(0.01f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(weight)
                            .background(categoryColor(category)),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Legend row
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DownloadedFileCategory.entries.forEach { category ->
                val size = categorySizes[category] ?: 0L
                if (size > 0L) {
                    val percentStr = formatPercentage(size, totalSize)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(ShapeCache.smoothPill)
                                .background(categoryColor(category)),
                        )
                        Text(
                            text = "${categoryLabel(category)} (${size.formatBytes()} · $percentStr)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun categoryColor(category: DownloadedFileCategory): Color = when (category) {
    DownloadedFileCategory.MEDIA -> MaterialTheme.colorScheme.primary
    DownloadedFileCategory.SUBTITLE -> MaterialTheme.colorScheme.secondary
    DownloadedFileCategory.TRICKPLAY -> MaterialTheme.colorScheme.tertiary
    DownloadedFileCategory.IMAGE -> StatusColors.info
    DownloadedFileCategory.SEGMENT -> MaterialTheme.colorScheme.outline
}

private fun categoryIcon(category: DownloadedFileCategory): ImageVector = when (category) {
    DownloadedFileCategory.MEDIA -> Tabler.Outline.Movie
    DownloadedFileCategory.SUBTITLE -> Tabler.Outline.Subtitles
    DownloadedFileCategory.TRICKPLAY -> Tabler.Outline.DeviceFloppy
    DownloadedFileCategory.IMAGE -> Tabler.Outline.Photo
    DownloadedFileCategory.SEGMENT -> Tabler.Outline.FileDescription
}

@Composable
private fun DownloadedFilesSection(
    inventory: DownloadFileInventory?,
    isLoading: Boolean,
) {
    SheetSection(
        modifier = Modifier.padding(horizontal = 16.dp),
        icon = Tabler.Outline.FileDescription,
        title = stringResource(Res.string.detail_download_section_files),
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
                        text = stringResource(Res.string.detail_download_files_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            inventory == null || inventory.entries.isEmpty() -> {
                Text(
                    text = stringResource(Res.string.detail_download_files_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                InfoLine(
                    label = stringResource(Res.string.detail_download_files_total),
                    value = inventory.totalSizeBytes.formatBytes(),
                )
                // Group by category preserving the enum's stable order (MEDIA → IMAGE).
                val groupedEntries = remember(inventory) {
                    inventory.entries
                        .groupBy { it.category }
                        .toSortedMap(compareBy { it.ordinal })
                }
                groupedEntries.forEach { (category, entries) ->
                    Spacer(Modifier.height(10.dp))
                    CategoryGroup(category = category, entries = entries)
                }
            }
        }
    }
}

@Composable
private fun CategoryGroup(
    category: DownloadedFileCategory,
    entries: List<DownloadFileEntry>,
) {
    val subtotal = entries.sumOf { it.sizeBytes }
    val countLabel = pluralStringResource(
        Res.plurals.detail_files_count,
        entries.size,
        entries.size,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(ShapeCache.smooth8)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = categoryIcon(category),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = categoryLabel(category),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$countLabel · ${subtotal.formatBytes()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    val location = if (entries.size == 1) {
        entries.first().path
    } else {
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
    DownloadedFileCategory.MEDIA -> stringResource(Res.string.detail_file_category_media)
    DownloadedFileCategory.SUBTITLE -> stringResource(Res.string.detail_file_category_subtitle)
    DownloadedFileCategory.TRICKPLAY -> stringResource(Res.string.detail_file_category_trickplay)
    DownloadedFileCategory.SEGMENT -> stringResource(Res.string.detail_file_category_segment)
    DownloadedFileCategory.IMAGE -> stringResource(Res.string.detail_file_category_image)
}

@Composable
private fun statusBadgeColor(status: DownloadStatus): Color = when (status) {
    DownloadStatus.COMPLETED -> StatusColors.available
    DownloadStatus.DOWNLOADING -> StatusColors.requested
    DownloadStatus.PAUSED -> StatusColors.pending
    DownloadStatus.QUEUED, DownloadStatus.PENDING -> StatusColors.info
    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> StatusColors.error
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

/** Formats byte size ratio to a percentage string with 2 decimal places precision (`%.2f%%`). */
internal fun formatPercentage(value: Long, total: Long): String {
    if (total <= 0L || value <= 0L) return "0.00%"
    val percent = (value.toDouble() / total.toDouble()) * 100.0
    return when {
        percent >= 100.0 -> "100.00%"
        percent < 0.01 -> "<0.01%"
        else -> String.format(java.util.Locale.getDefault(), "%.2f%%", percent)
    }
}

