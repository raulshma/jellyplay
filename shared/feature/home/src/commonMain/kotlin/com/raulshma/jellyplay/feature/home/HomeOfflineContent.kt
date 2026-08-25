package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import org.jetbrains.compose.resources.stringResource
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
import com.raulshma.jellyplay.core.ui.animation.lazyItemPlacementSpec
import com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator
import com.raulshma.jellyplay.core.ui.components.OfflineMediaCard
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import java.util.PriorityQueue
import com.raulshma.jellyplay.feature.home.generated.resources.home_your_downloads
import com.raulshma.jellyplay.feature.home.generated.resources.home_series
import com.raulshma.jellyplay.feature.home.generated.resources.home_recently_downloaded
import com.raulshma.jellyplay.feature.home.generated.resources.home_no_downloads_yet
import com.raulshma.jellyplay.feature.home.generated.resources.home_no_downloads_description
import com.raulshma.jellyplay.feature.home.generated.resources.home_music
import com.raulshma.jellyplay.feature.home.generated.resources.home_movies
import com.raulshma.jellyplay.feature.home.generated.resources.home_going_online
import com.raulshma.jellyplay.feature.home.generated.resources.home_go_online_action
import com.raulshma.jellyplay.feature.home.generated.resources.home_downloaded
import com.raulshma.jellyplay.feature.home.generated.resources.home_continue_watching
import com.raulshma.jellyplay.feature.home.generated.resources.Res

/**
 * Offline home — shown on the Home screen when the app is offline (or the
 * online fetch failed but downloads exist). Replaces the old flat row list
 * with a sectioned layout that mirrors the online home: Continue Watching,
 * Recently Downloaded, then per-type rows (Movies / Series / Music). Wires up
 * the previously-dormant "Go online" affordance.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    /** True while the manual offline→online transition is in flight. Swaps the
     *  Go-online button for a disabled spinner so the tap isn't silent. */
    isGoingOnline: Boolean = false,
) {
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val rowCardWidth = adaptiveInfo.rowCardWidth(isTv)

    if (offlineLibrary.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(backgroundColor), contentAlignment = Alignment.Center) {
            ScreenEmptyState(
                icon = Tabler.Outline.Download,
                title = stringResource(Res.string.home_no_downloads_yet),
                description = stringResource(Res.string.home_no_downloads_description),
                actionLabel = stringResource(Res.string.home_go_online_action),
                onAction = onGoOnline,
                actionLoading = isGoingOnline,
            )
        }
        return
    }

    // Single-pass partition + accumulate. Previously this was six independent
    // full-library traversals (continue-watching filter+sort, recent sort+take,
    // movies/series/music filters, totalBytes sum) = O(6n) + 6 intermediate
    // lists on every offline-library emission. Now O(n) for the partition,
    // byte sum, and top-10 recent tracking; the continue-watching sort runs on
    // its (small) partition input only.
    val offlineSections = remember(offlineLibrary) {
        val continueWatching = ArrayList<OfflineMediaItem>()
        val movies = ArrayList<OfflineMediaItem>()
        val series = ArrayList<OfflineMediaItem>()
        val music = ArrayList<OfflineMediaItem>()
        var totalBytes = 0L
        // Bounded min-heap of size RECENT_LIMIT tracks the newest items during
        // the single pass, replacing the prior O(n log n) full-library sort
        // with an effectively O(n) pass (n log 10). Comparing by createdAt so
        // the heap root is the smallest (oldest) of the current top-10.
        val recentHeap = PriorityQueue<OfflineMediaItem>(compareBy { it.createdAt })
        for (item in offlineLibrary) {
            totalBytes += item.totalSizeBytes
            if (item.playedPercentage in 1.0..94.99) continueWatching += item
            when (item.mediaType) {
                MediaType.MOVIE -> movies += item
                MediaType.SERIES -> series += item
                MediaType.AUDIO, MediaType.MUSIC, MediaType.ALBUM -> music += item
                // Other types (PHOTO, PHOTO_FOLDER, etc.) have no home row here.
                else -> Unit
            }
            if (recentHeap.size < RECENT_LIMIT) {
                recentHeap.add(item)
            } else {
                val oldest = recentHeap.peek()
                if (oldest != null && item.createdAt > oldest.createdAt) {
                    recentHeap.poll()
                    recentHeap.add(item)
                }
            }
        }
        // Preserve original comparators for sort stability.
        continueWatching.sortWith(
            compareByDescending<OfflineMediaItem> { it.lastPlayedDate ?: "" }
                .thenByDescending { it.createdAt }
        )
        // Drain the heap newest-first. sortedByDescending is over ≤10 items.
        val recent = recentHeap.sortedByDescending { it.createdAt }
        OfflineSections(
            continueWatching = continueWatching,
            recent = recent,
            movies = movies,
            series = series,
            music = music,
            totalBytes = totalBytes,
        )
    }
    val continueWatching = offlineSections.continueWatching
    val recent = offlineSections.recent
    val movies = offlineSections.movies
    val series = offlineSections.series
    val music = offlineSections.music
    val totalBytes = offlineSections.totalBytes

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
                    text = stringResource(Res.string.home_your_downloads),
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
                OutlinedButton(
                    onClick = onGoOnline,
                    enabled = !isGoingOnline,
                    modifier = Modifier.clip(ShapeCache.smooth16),
                ) {
                    if (isGoingOnline) {
                        JellyPlayCircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(Res.string.home_going_online))
                    } else {
                        Icon(Tabler.Outline.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(Res.string.home_go_online_action))
                    }
                }
            }
        }

        if (continueWatching.isNotEmpty()) {
            item(key = "offline_continue") {
                OfflineSection(
                    title = stringResource(Res.string.home_continue_watching),
                    items = continueWatching,
                    cardWidth = rowCardWidth,
                    contentPad = contentPadding,
                    onItemClick = onOfflineItemClick,
                )
            }
        }

        item(key = "offline_recent") {
            OfflineSection(
                title = stringResource(Res.string.home_recently_downloaded),
                items = recent,
                cardWidth = rowCardWidth,
                contentPad = contentPadding,
                onItemClick = onOfflineItemClick,
            )
        }

        if (movies.isNotEmpty()) {
            item(key = "offline_movies") {
                OfflineSection(title = stringResource(Res.string.home_movies), items = movies, cardWidth = rowCardWidth, contentPad = contentPadding, onItemClick = onOfflineItemClick)
            }
        }
        if (series.isNotEmpty()) {
            item(key = "offline_series") {
                OfflineSection(title = stringResource(Res.string.home_series), items = series, cardWidth = rowCardWidth, contentPad = contentPadding, onItemClick = onOfflineItemClick)
            }
        }
        if (music.isNotEmpty()) {
            item(key = "offline_music") {
                OfflineSection(title = stringResource(Res.string.home_music), items = music, cardWidth = rowCardWidth, contentPad = contentPadding, onItemClick = onOfflineItemClick)
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
            modifier = Modifier
                .focusGroup()
                .tvFocusRestorer(),
        ) {
            items(items, key = { "offline_${it.id}" }, contentType = { "offlineItem" }) { item ->
                val placementSpec = lazyItemPlacementSpec()
                OfflineMediaCard(
                    item = item,
                    onClick = { onItemClick(item.id, item.mediaType) },
                    modifier = Modifier.animateItem(placementSpec = placementSpec).width(cardWidth),
                    // On the offline home every card is downloaded by definition,
                    // so the "Downloaded" status badge would be redundant.
                    showStatusBadge = false,
                )
            }
        }
    }
}

/** Number of items shown in the "Recently Downloaded" row. */
private const val RECENT_LIMIT = 10

/**
 * Holder for the single-pass partition of the offline library, replacing
 * six independent full-library traversals. See [OfflineHomeContent].
 */
private data class OfflineSections(
    val continueWatching: List<OfflineMediaItem>,
    val recent: List<OfflineMediaItem>,
    val movies: List<OfflineMediaItem>,
    val series: List<OfflineMediaItem>,
    val music: List<OfflineMediaItem>,
    val totalBytes: Long,
)

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
        text = stringResource(Res.string.home_downloaded),
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
            .focusGroup()
            .tvFocusRestorer()
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
            val placementSpec = lazyItemPlacementSpec()
            OfflineMediaCard(
                item = offlineItem,
                onClick = onOfflineLibraryClick,
                modifier = Modifier.animateItem(placementSpec = placementSpec).width(cardWidth),
            )
        }
    }
}
