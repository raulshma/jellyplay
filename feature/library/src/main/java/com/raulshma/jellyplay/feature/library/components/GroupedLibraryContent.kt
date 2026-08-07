package com.raulshma.jellyplay.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.GroupBy
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.progressFraction
import com.raulshma.jellyplay.core.ui.util.safeItemKey
import com.raulshma.jellyplay.feature.library.components.LibraryListItem

/**
 * A group key derived from a [MediaItem] for client-side grouping. Returns the
 * header label (and a stable bucket id) for the active [GroupBy] dimension.
 */
private fun MediaItem.groupKey(groupBy: GroupBy): String = when (groupBy) {
    GroupBy.NONE -> error("unreachable: GroupedLibraryContent requires a non-NONE GroupBy")
    GroupBy.NAME -> (name.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#').toString()
    GroupBy.TYPE -> mediaType.name
    GroupBy.GENRE -> genres.firstOrNull() ?: "Unknown"
    GroupBy.YEAR -> year?.toString() ?: "Unknown"
}

/**
 * Renders the library items grouped under translucent section headers when
 * [groupBy] is non-NONE. The caller ([com.raulshma.jellyplay.feature.library.LibraryScreen])
 * only invokes this composable when grouping is active — the ungrouped path is
 * rendered by the caller — enforced by [require].
 *
 * Grouping is **client-side over the loaded snapshot**: groups recompute as
 * pages append (positions shift on load), mirroring the existing alphabet-jump
 * rail approach. Headers are non-sticky for robustness (the experimental grid
 * sticky-header API proved janky with paging); this matches the documented
 * fallback in the feature plan.
 *
 * Only GRID and LIST support grouping here; THUMB and MASONRY fall through to
 * GRID-style grouping (the toolbar's view-cycle still works, the grouping just
 * renders in poster form).
 */
@Composable
fun GroupedLibraryContent(
    pagedItems: LazyPagingItems<MediaItem>,
    viewMode: LibraryViewMode,
    groupBy: GroupBy,
    gridCellSize: androidx.compose.ui.unit.Dp,
    spacing: androidx.compose.ui.unit.Dp,
    gridPadding: PaddingValues,
    onItemClick: (itemId: String, mediaType: MediaType, parentId: String?, itemName: String) -> Unit,
    getImageUrl: (String) -> String,
    onFocusedItemChange: ((MediaItem?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    require(groupBy != GroupBy.NONE) { "GroupedLibraryContent requires a non-NONE GroupBy" }
    // Flatten the loaded snapshot into an ordered list of (header | item) rows
    // so the grid can emit header items spanning all columns. Recomputed via
    // derivedStateOf so it only re-evaluates when the snapshot actually changes.
    val rows by remember(pagedItems, groupBy) {
        derivedStateOf {
            val items = pagedItems.itemSnapshotList.items
            buildList<Pair<String?, MediaItem?>> {
                var lastHeader: String? = null
                for (item in items) {
                    val header = item.groupKey(groupBy)
                    if (header != lastHeader) {
                        add(header to null)
                        lastHeader = header
                    }
                    add(null to item)
                }
            }
        }
    }

    if (viewMode == LibraryViewMode.LIST) {
        LazyColumn(
            contentPadding = gridPadding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = modifier.fillMaxSize(),
        ) {
            items(count = rows.size, key = { idx ->
                val (header, item) = rows[idx]
                if (item != null) "item_${item.id}" else "header_${header}"
            }, contentType = { idx -> if (rows[idx].second != null) "mediaItem" else "header" }) { idx ->
                val (header, item) = rows[idx]
                if (item != null) {
                    val memoizedClick = remember(item.id, item.mediaType, item.parentId, item.name) {
                        { onItemClick(item.id, item.mediaType, item.parentId, item.name) }
                    }
                    val subtitle = remember(item.year, item.mediaType) {
                        buildString {
                            if (item.year != null) append("${item.year}")
                            val typeLabel = when (item.mediaType) {
                                MediaType.EPISODE -> "Episode"
                                MediaType.SERIES -> "Series"
                                MediaType.MOVIE -> "Movie"
                                MediaType.AUDIO -> "Audio"
                                MediaType.MUSIC -> "Music"
                                MediaType.PHOTO, MediaType.PHOTO_FOLDER -> "Photo"
                                else -> null
                            }
                            if (typeLabel != null) {
                                if (isNotEmpty()) append(" · ")
                                append(typeLabel)
                            }
                        }
                    }
                    LibraryListItem(
                        title = item.name,
                        subtitle = subtitle,
                        imageUrl = remember(item.id) { getImageUrl(item.id) },
                        blurHash = item.blurHashes.primary,
                        onClick = memoizedClick,
                        modifier = Modifier.onFocusChanged {
                            if (it.isFocused || it.hasFocus) onFocusedItemChange?.invoke(item)
                        },
                    )
                } else {
                    GroupHeader(label = header.orEmpty())
                }
            }
        }
    } else {
        val groupedGridState = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(gridCellSize),
            state = groupedGridState,
            contentPadding = gridPadding,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = modifier.fillMaxSize(),
        ) {
            items(count = rows.size, key = { idx ->
                val (header, item) = rows[idx]
                if (item != null) "item_${item.id}" else "header_${header}"
            }, contentType = { idx -> if (rows[idx].second != null) "mediaItem" else "header" }, span = { idx ->
                if (rows[idx].second == null) GridItemSpan(maxLineSpan) else GridItemSpan(1)
            }) { idx ->
                val (header, item) = rows[idx]
                if (item != null) {
                    val memoizedClick = remember(item.id, item.mediaType, item.parentId, item.name) {
                        { onItemClick(item.id, item.mediaType, item.parentId, item.name) }
                    }
                    val itemProgress = item.progressFraction()
                    val cardImage = com.raulshma.jellyplay.core.ui.components.rememberEpisodeCardImage(
                        item = item,
                        itemImageUrl = remember(item.id) { getImageUrl(item.id) },
                        seriesPosterResolver = getImageUrl,
                    )
                    PosterCard(
                        item = item,
                        imageUrl = cardImage.imageUrl,
                        fallbackUrls = cardImage.fallbackUrls,
                        onClick = memoizedClick,
                        showProgress = itemProgress != null && itemProgress > 0f,
                        progressPercent = itemProgress ?: 0f,
                        blurHash = cardImage.blurHash,
                        sharedElementKey = "poster_${item.id}",
                        showEpisodeSeriesBadge = cardImage.showSeriesBadge,
                        modifier = Modifier.onFocusChanged {
                            if (it.isFocused || it.hasFocus) onFocusedItemChange?.invoke(item)
                        },
                    )
                } else {
                    GroupHeader(label = header.orEmpty())
                }
            }
        }
    }
}

/**
 * A translucent rounded pill section header. Spans the full grid
 * width via [GridItemSpan]/a fillMaxWidth in the list.
 */
@Composable
private fun GroupHeader(label: String) {
    val isLight = LocalIsLightTheme.current
    val bg = if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.10f)
    Surface(
        color = bg,
        shape = ShapeCache.smooth12,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
