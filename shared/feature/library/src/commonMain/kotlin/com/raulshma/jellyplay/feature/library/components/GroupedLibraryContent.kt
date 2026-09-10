package com.raulshma.jellyplay.feature.library.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.GroupBy
import com.raulshma.jellyplay.core.model.LibraryGrouper
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.sortedByCachedKey
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.displayTitle
import com.raulshma.jellyplay.core.ui.components.libraryListSubtitle
import com.raulshma.jellyplay.core.ui.components.rememberSeriesImageFallback
import com.raulshma.jellyplay.core.ui.components.rememberProgressFraction
import com.raulshma.jellyplay.core.ui.tv.TvFocusableColumn
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.TvGridCacheWindow
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.rememberInt
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.util.safeItemKey
import com.raulshma.jellyplay.feature.library.components.LibraryListItem

/**
 * A group key derived from a [MediaItem] for the active [GroupBy] dimension. Returns the
 * header label (and a stable bucket id) for the active [GroupBy] dimension.
 *
 * Returns an empty string for [GroupBy.NONE] instead of throwing: although the caller
 * only mounts this composable when grouping is active, the persisted [GroupBy] value
 * flows in asynchronously and can transiently resolve to NONE while a grouped view is
 * still mounted. Throwing here crashed the app on every library open once a non-NONE
 * value had been persisted (issue #113).
 *
 * Delegates to the pure, tested [LibraryGrouper] — the logic was lifted out of this
 * Composable so it can be unit-tested (it was the site of two #113 crashes).
 */
private fun MediaItem.groupKey(groupBy: GroupBy): String = LibraryGrouper.groupKey(this, groupBy)

/**
 * A flattened (header | item) row emitted into the grouped LazyColumn/LazyVerticalGrid.
 *
 * Each variant exposes a unique Compose lazy [key]: items key on their stable item id,
 * headers key on a monotonically increasing sequence id. The sequence id guarantees key
 * uniqueness even if the same group label appears in non-contiguous positions (which
 * previously produced duplicate keys and crashed the app — issue #113).
 */
private sealed interface GroupedRow {
    val key: Any

    /** A section header. [id] is unique within a single grouping pass. */
    data class Header(val label: String?, val id: Int) : GroupedRow {
        override val key: Any = "header_$id"
    }

    /** A media item row. */
    data class Item(val item: MediaItem) : GroupedRow {
        override val key: Any = "item_${item.id}"
    }
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
@OptIn(ExperimentalFoundationApi::class)
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
    /** Bumped by the host on completed refreshes; resets the cursor and re-grabs focus on TV. */
    refreshGeneration: Int = 0,
) {
    // NOTE: do not throw for GroupBy.NONE here. The persisted group-by flows in
    // asynchronously (loadLayoutPrefs) and can transiently be NONE while a grouped
    // view is mounted; throwing crashed the app (issue #113). The caller still guards
    // `if (groupBy != GroupBy.NONE)` before composing this, but if NONE does slip
    // through we render the items ungrouped rather than crashing.
    // Flatten the loaded snapshot into an ordered list of (header | item) rows
    // so the grid can emit header items spanning all columns. Recomputed via
    // derivedStateOf so it only re-evaluates when the snapshot actually changes.
    //
    // The snapshot is first sorted by the active group dimension so groups come out
    // contiguous. Without this, grouping a snapshot that is server-sorted by a
    // different dimension (e.g. Name) scattered the same group key across the list
    // and produced duplicate `"header_${key}"` lazy keys → IllegalArgumentException
    // crash (issue #113). sortedBy is stable so the server order is preserved within
    // each group. Header rows carry a monotonically increasing sequence id so their
    // lazy keys are unique regardless of the input order — a defensive guarantee
    // against future regressions in the grouping logic.
    val rows by remember(pagedItems, groupBy) {
        derivedStateOf {
            val items = pagedItems.itemSnapshotList.items
            if (groupBy == GroupBy.NONE) {
                // Degenerate case: render every item with no headers.
                items.map { item -> GroupedRow.Item(item) }
            } else {
                val ordered = items.sortedByCachedKey { it.groupKey(groupBy) }
                buildList<GroupedRow> {
                    var lastHeader: String? = null
                    var headerSeq = 0
                    for (item in ordered) {
                        val header = item.groupKey(groupBy)
                        if (header != lastHeader) {
                            add(GroupedRow.Header(label = header, id = headerSeq++))
                            lastHeader = header
                        }
                        add(GroupedRow.Item(item))
                    }
                }
            }
        }
    }

    if (viewMode == LibraryViewMode.LIST) {
        // TvFocusableColumn supplies the TV focus contract (initial grab once rows exist, cursor
        // memory, refresh re-grab) the plain LazyColumn never had.
        TvFocusableColumn(
            items = rows,
            key = { it.key },
            contentPadding = gridPadding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = modifier.fillMaxSize(),
            refreshGeneration = refreshGeneration,
            contentType = { idx, row -> if (row is GroupedRow.Item) "mediaItem" else "header" },
            onFocusedIndexChange = { idx ->
                (rows.getOrNull(idx) as? GroupedRow.Item)?.let { onFocusedItemChange?.invoke(it.item) }
            },
        ) { idx, row, itemModifier ->
            if (row is GroupedRow.Item) {
                val item = row.item
                val memoizedClick = remember(item.id, item.mediaType, item.parentId, item.name) {
                    { onItemClick(item.id, item.mediaType, item.parentId, item.name) }
                }
                val subtitle = remember(item.mediaType, item.seriesName, item.seasonNumber, item.episodeNumber, item.year) {
                    // Episodes show an SxxExx + series context line (bold tag); all
                    // other types fall back to the year/type label. Shared with the
                    // ungrouped list path via libraryListSubtitle.
                    item.libraryListSubtitle()
                }
                // Seasons fall back to the parent series poster when the season's
                // own artwork 404s (shared with the ungrouped list path).
                val fallbackUrls = item.rememberSeriesImageFallback(getImageUrl)
                LibraryListItem(
                    title = item.displayTitle(),
                    subtitle = subtitle,
                    imageUrl = remember(item.id) { getImageUrl(item.id) },
                    fallbackUrls = fallbackUrls,
                    blurHash = item.blurHashes.primary,
                    onClick = memoizedClick,
                    modifier = itemModifier,
                )
            } else {
                GroupHeader(label = (row as GroupedRow.Header).label.orEmpty())
            }
        }
    } else {
        // No staggered/span-aware TvFocusable* primitive exists (headers span the full width), so
        // the TV focus contract is wired manually — same modifier order as TvFocusableGrid.
        val groupedGridState = rememberLazyGridState(cacheWindow = TvGridCacheWindow)
        val groupRequester = remember { FocusRequester() }
        val fallbackRequester = remember { FocusRequester() }
        var focusedRowIdx by rememberInt()
        TvGrabInitialFocus(
            focusRequester = fallbackRequester,
            itemCount = rows.size,
            tag = "library_grouped_init",
            refreshGeneration = refreshGeneration,
        )
        LaunchedEffect(refreshGeneration) {
            if (refreshGeneration > 0) focusedRowIdx = 0
        }
        // The cursor may point at a header row (headers occupy row indexes too); the fallback
        // anchor must always be an item row, falling back to the first one.
        val firstItemIdx = rows.indexOfFirst { it is GroupedRow.Item }
        val anchorIdx = if (focusedRowIdx in rows.indices && rows[focusedRowIdx] is GroupedRow.Item) {
            focusedRowIdx
        } else {
            firstItemIdx.coerceAtLeast(0)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(gridCellSize),
            state = groupedGridState,
            contentPadding = gridPadding,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = modifier
                .fillMaxSize()
                .focusGroup()
                .tvFocusRestorer(fallbackRequester)
                .focusRequester(groupRequester),
        ) {
            items(count = rows.size, key = { idx -> rows[idx].key }, contentType = { idx ->
                if (rows[idx] is GroupedRow.Item) "mediaItem" else "header"
            }, span = { idx ->
                if (rows[idx] is GroupedRow.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1)
            }) { idx ->
                val row = rows[idx]
                if (row is GroupedRow.Item) {
                    val item = row.item
                    val memoizedClick = remember(item.id, item.mediaType, item.parentId, item.name) {
                        { onItemClick(item.id, item.mediaType, item.parentId, item.name) }
                    }
                    val itemProgress = item.rememberProgressFraction()
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
                        modifier = Modifier
                            .ifElse(idx == anchorIdx, Modifier.focusRequester(fallbackRequester))
                            .onFocusChanged {
                                if (it.isFocused || it.hasFocus) {
                                    focusedRowIdx = idx
                                    onFocusedItemChange?.invoke(item)
                                }
                            },
                    )
                } else {
                    GroupHeader(label = (row as GroupedRow.Header).label.orEmpty())
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
