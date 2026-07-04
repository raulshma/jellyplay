package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertCircle
import com.composables.icons.tabler.outline.DeviceFloppy
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Wifi
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.adaptive.rowCardWidth
import com.raulshma.jellyplay.core.ui.components.OfflineMediaCard
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode

/**
 * Offline home — shown on the Home screen when the app is offline (or the
 * online fetch failed but downloads exist). Replaces the old flat row list
 * with a sectioned layout that mirrors the online home: Continue Watching,
 * Recently Downloaded, then per-type rows (Movies / Series / Music). Wires up
 * the previously-dormant "Go online" affordance.
 */
@Composable
fun OfflineHomeContent(
    offlineLibrary: List<OfflineMediaItem>,
    onItemClick: () -> Unit,
    contentPadding: Dp,
    backgroundColor: Color,
    onGoOnline: () -> Unit,
    onOfflineItemClick: (itemId: String, mediaType: MediaType) -> Unit = { _, _ -> onItemClick() },
    /**
     * Optional non-blocking banner shown above the offline content. Used to surface the
     * "online fetch failed, showing downloads" fallback so the implicit switch isn't silent.
     */
    statusMessage: String? = null,
) {
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val rowCardWidth = adaptiveInfo.rowCardWidth(isTv)

    if (offlineLibrary.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(backgroundColor), contentAlignment = Alignment.Center) {
            ScreenEmptyState(
                icon = Tabler.Outline.Download,
                title = "No downloads yet",
                description = "Download media while online to access it offline.",
                actionLabel = "Go online",
                onAction = onGoOnline,
            )
        }
        return
    }

    // Pre-compute sections. Only non-empty ones render.
    val continueWatching = remember(offlineLibrary) {
        offlineLibrary
            .filter { it.playedPercentage in 1.0..94.99 }
            .sortedWith(compareByDescending<OfflineMediaItem> { it.lastPlayedDate ?: "" }.thenByDescending { it.createdAt })
    }
    val recent = remember(offlineLibrary) {
        offlineLibrary.sortedByDescending { it.createdAt }.take(10)
    }
    val movies = remember(offlineLibrary) { offlineLibrary.filter { it.mediaType == MediaType.MOVIE } }
    val series = remember(offlineLibrary) { offlineLibrary.filter { it.mediaType == MediaType.SERIES } }
    val music = remember(offlineLibrary) {
        offlineLibrary.filter { it.mediaType == MediaType.AUDIO || it.mediaType == MediaType.MUSIC || it.mediaType == MediaType.ALBUM }
    }
    val totalBytes = remember(offlineLibrary) { offlineLibrary.sumOf { it.totalSizeBytes } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentPadding = PaddingValues(top = 120.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        // Non-blocking status banner (e.g. "couldn't reach server — showing downloads").
        if (statusMessage != null) {
            item(key = "offline_status_banner") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = contentPadding)
                        .padding(bottom = 12.dp)
                        .clip(ShapeCache.smooth16)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Tabler.Outline.AlertCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        // Header: title + storage summary + Go online.
        item(key = "offline_header") {
            Column(modifier = Modifier.padding(horizontal = contentPadding)) {
                Text(
                    text = "Your Downloads",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(
                        Tabler.Outline.DeviceFloppy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    val summary = buildList {
                        add("${offlineLibrary.size} item${if (offlineLibrary.size == 1) "" else "s"}")
                        if (totalBytes > 0) add(totalBytes.formatBytes())
                    }.joinToString(" · ")
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onGoOnline, modifier = Modifier.clip(ShapeCache.smooth16)) {
                    Icon(Tabler.Outline.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Go online")
                }
            }
        }

        if (continueWatching.isNotEmpty()) {
            item(key = "offline_continue") {
                OfflineSection(
                    title = "Continue Watching",
                    items = continueWatching,
                    cardWidth = rowCardWidth,
                    contentPad = contentPadding,
                    onItemClick = onOfflineItemClick,
                )
            }
        }

        item(key = "offline_recent") {
            OfflineSection(
                title = "Recently Downloaded",
                items = recent,
                cardWidth = rowCardWidth,
                contentPad = contentPadding,
                onItemClick = onOfflineItemClick,
            )
        }

        if (movies.isNotEmpty()) {
            item(key = "offline_movies") {
                OfflineSection(title = "Movies", items = movies, cardWidth = rowCardWidth, contentPad = contentPadding, onItemClick = onOfflineItemClick)
            }
        }
        if (series.isNotEmpty()) {
            item(key = "offline_series") {
                OfflineSection(title = "Series", items = series, cardWidth = rowCardWidth, contentPad = contentPadding, onItemClick = onOfflineItemClick)
            }
        }
        if (music.isNotEmpty()) {
            item(key = "offline_music") {
                OfflineSection(title = "Music", items = music, cardWidth = rowCardWidth, contentPad = contentPadding, onItemClick = onOfflineItemClick)
            }
        }
    }
}

@Composable
private fun OfflineSection(
    title: String,
    items: List<OfflineMediaItem>,
    cardWidth: Dp,
    contentPad: Dp,
    onItemClick: (itemId: String, mediaType: MediaType) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = contentPad, bottom = 12.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = contentPad),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { "offline_${it.id}" }, contentType = { "offlineItem" }) { item ->
                OfflineMediaCard(
                    item = item,
                    onClick = { onItemClick(item.id, item.mediaType) },
                    modifier = Modifier.width(cardWidth),
                    // On the offline home every card is downloaded by definition,
                    // so the "Downloaded" status badge would be redundant.
                    showStatusBadge = false,
                )
            }
        }
    }
}

/**
 * Inline "Downloaded" row shown on the online home. Upgraded from the old
 * bespoke poster to the shared [OfflineMediaCard] so it matches the rest of
 * the offline surfaces.
 */
@Composable
fun DownloadedSection(
    offlineLibrary: List<OfflineMediaItem>,
    onOfflineLibraryClick: () -> Unit,
    contentPad: Dp,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val cardWidth = adaptiveInfo.rowCardWidth(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)

    Text(
        text = "Downloaded",
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(start = contentPad, top = 24.dp, bottom = 8.dp),
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = contentPad, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        items(
            count = offlineLibrary.size,
            key = { index -> "offline_${offlineLibrary[index].id}" },
            contentType = { "offlineItem" },
        ) { index ->
            val offlineItem = offlineLibrary[index]
            OfflineMediaCard(
                item = offlineItem,
                onClick = onOfflineLibraryClick,
                modifier = Modifier.width(cardWidth),
            )
        }
    }
}
