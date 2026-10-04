package com.raulshma.jellyplay.feature.library.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.displayTitle
import com.raulshma.jellyplay.core.ui.components.libraryListSubtitle
import com.raulshma.jellyplay.core.ui.components.rememberEpisodeCardImage
import com.raulshma.jellyplay.core.ui.components.rememberProgressFraction
import com.raulshma.jellyplay.core.ui.components.rememberSeriesImageFallback
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import com.raulshma.jellyplay.core.ui.tv.TvFocusablePagingColumn
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.rememberInt
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.util.safeItemKey
import kotlinx.coroutines.flow.Flow

/**
 * The library screen's four flat view-mode contents, extracted verbatim from
 * the former inline [com.raulshma.jellyplay.feature.library.LibraryScreen]
 * `when (activeMode)` arms (the [com.raulshma.jellyplay.feature.library.GroupedLibraryContent]
 * grouped path was already separate).
 *
 * State OWNERSHIP stays at the screen root: the scaffold owns every scroll
 * state (`listState` / `gridState` / the hoisted `staggeredState`) because the
 * alphabet rail and the refresh reset drive them from the root, and passes the
 * active one down. Each composable owns only its branch-local focus wiring
 * (masonry's requesters + cursor index). Bodies are byte-identical to the
 * inline arms — same composables, same remember keys, same focus contract.
 */

/** LIST mode: one row per item over [TvFocusablePagingColumn]'s TV focus contract. */
@Composable
internal fun LibraryListContent(
    pagedItems: LazyPagingItems<MediaItem>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    refreshGeneration: Int,
    onItemClick: (itemId: String, mediaType: MediaType, parentId: String?, itemName: String) -> Unit,
    getImageUrl: (String) -> String,
    onFocusedItemChange: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // TvFocusablePagingColumn supplies the TV focus contract
    // (initial grab, cursor memory, refresh re-grab) the plain
    // LazyColumn never had — LIST mode used to open with focus
    // orphaned straight to the drawer rail.
    TvFocusablePagingColumn(
        itemCount = pagedItems.itemCount,
        key = pagedItems.safeItemKey { it.id },
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxSize(),
        refreshGeneration = refreshGeneration,
        contentType = { "mediaItem" },
        onFocusedIndexChange = { index ->
            pagedItems[index]?.let(onFocusedItemChange)
        },
    ) { index, itemModifier ->
        val item = pagedItems[index]
        if (item != null) {
            val memoizedClick = remember(item.id, item.mediaType, item.parentId, item.name) {
                { onItemClick(item.id, item.mediaType, item.parentId, item.name) }
            }
            val subtitle = remember(item.mediaType, item.seriesName, item.seasonNumber, item.episodeNumber, item.year) {
                // Episodes show an SxxExx + series context line (bold tag);
                // other types keep the year/type label. Shared with the
                // grouped list path via libraryListSubtitle.
                item.libraryListSubtitle()
            }
            // Seasons fall back to the parent series poster when the
            // season's own artwork 404s (shared with the grouped list).
            val fallbackUrls = item.rememberSeriesImageFallback(getImageUrl)
            Box(modifier = itemModifier) {
                LibraryListItem(
                    item = item,
                    title = item.displayTitle(),
                    subtitle = subtitle,
                    imageUrl = remember(item.id) { getImageUrl(item.id) },
                    fallbackUrls = fallbackUrls,
                    blurHash = item.blurHashes.primary,
                    onClick = memoizedClick,
                    modifier = Modifier,
                    sharedElementKey = "poster_${item.id}",
                )
            }
        }
    }
}

/** THUMB mode: 16:9 landscape card grid. */
@Composable
internal fun LibraryThumbContent(
    pagedItems: LazyPagingItems<MediaItem>,
    gridState: LazyGridState,
    thumbCellSize: Dp,
    contentPadding: PaddingValues,
    spacing: Dp,
    refreshGeneration: Int,
    onItemClick: (itemId: String, mediaType: MediaType, parentId: String?, itemName: String) -> Unit,
    getImageUrl: (String) -> String,
    getBackdropUrl: (String) -> String,
    onFocusedItemChange: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 16:9 landscape grid — wider cells than the poster grid
    // so backdrop thumbnails aren't tiny. One card per row
    // on compact widths, more on tablet/TV.
    TvFocusableGrid(
        itemCount = pagedItems.itemCount,
        key = pagedItems.safeItemKey { it.id },
        columns = GridCells.Adaptive(thumbCellSize),
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = modifier.fillMaxSize(),
        refreshGeneration = refreshGeneration,
        contentType = { "mediaItem" },
        onFocusedIndexChange = { index -> pagedItems[index]?.let(onFocusedItemChange) },
    ) { index, itemModifier ->
        val item = pagedItems[index]
        if (item != null) {
            val memoizedClick = remember(item.id, item.mediaType, item.parentId, item.name) {
                { onItemClick(item.id, item.mediaType, item.parentId, item.name) }
            }
            val itemProgress = item.rememberProgressFraction()
            // Seasons fall back to the parent series poster when the
            // season's own artwork 404s in the thumb view too.
            val fallbackUrls = item.rememberSeriesImageFallback(getImageUrl)
            Box(modifier = itemModifier) {
                ThumbCard(
                    item = item,
                    imageUrl = remember(item.id, item.blurHashes.backdrop) {
                        if (item.blurHashes.backdrop != null) {
                            getBackdropUrl(item.id)
                        } else {
                            getImageUrl(item.id)
                        }
                    },
                    fallbackUrls = fallbackUrls,
                    onClick = memoizedClick,
                    showProgress = itemProgress != null && itemProgress > 0f,
                    progressPercent = itemProgress ?: 0f,
                    blurHash = item.blurHashes.backdrop ?: item.blurHashes.primary,
                    modifier = Modifier,
                    sharedElementKey = "poster_${item.id}",
                )
            }
        }
    }
}

/** GRID mode: the 2:3 poster grid. */
@Composable
internal fun LibraryGridContent(
    pagedItems: LazyPagingItems<MediaItem>,
    gridState: LazyGridState,
    gridCellSize: Dp,
    contentPadding: PaddingValues,
    spacing: Dp,
    refreshGeneration: Int,
    onItemClick: (itemId: String, mediaType: MediaType, parentId: String?, itemName: String) -> Unit,
    getImageUrl: (String) -> String,
    photoFolderChildUrlsFor: (String) -> Flow<List<String>>,
    onFocusedItemChange: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    TvFocusableGrid(
        itemCount = pagedItems.itemCount,
        key = pagedItems.safeItemKey { it.id },
        columns = GridCells.Adaptive(gridCellSize),
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = modifier.fillMaxSize(),
        refreshGeneration = refreshGeneration,
        contentType = { "mediaItem" },
        onFocusedIndexChange = { index -> pagedItems[index]?.let(onFocusedItemChange) },
    ) { index, itemModifier ->
        val item = pagedItems[index]
        if (item != null) {
            val memoizedClick = remember(item.id, item.mediaType, item.parentId, item.name) {
                { onItemClick(item.id, item.mediaType, item.parentId, item.name) }
            }
            val itemProgress = item.rememberProgressFraction()
            // Per-item collection: only photo-folder cards subscribe,
            // and only the affected card recomposes on a prefetch merge.
            val photoFolderChildImageUrls by if (item.mediaType == MediaType.PHOTO_FOLDER) {
                remember(item.id) { photoFolderChildUrlsFor(item.id) }
                    .collectAsStateWithLifecycle(emptyList())
            } else {
                androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyList()) }
            }
            // Resolve the episode's parent-series poster (and badge)
            // the same way the home Latest row does, so episodes
            // render as series posters instead of landscape scene
            // grabs. See rememberEpisodeCardImage.
            val cardImage = com.raulshma.jellyplay.core.ui.components.rememberEpisodeCardImage(
                item = item,
                itemImageUrl = remember(item.id) { getImageUrl(item.id) },
                seriesPosterResolver = getImageUrl,
            )
            Box(modifier = itemModifier) {
                PosterCard(
                    item = item,
                    imageUrl = cardImage.imageUrl,
                    fallbackUrls = cardImage.fallbackUrls,
                    onClick = memoizedClick,
                    showProgress = itemProgress != null && itemProgress > 0f,
                    progressPercent = itemProgress ?: 0f,
                    blurHash = cardImage.blurHash,
                    sharedElementKey = "poster_${item.id}",
                    photoFolderChildImageUrls = photoFolderChildImageUrls,
                    showEpisodeSeriesBadge = cardImage.showSeriesBadge,
                    modifier = Modifier,
                )
            }
        }
    }
}

/**
 * MASONRY mode: staggered grid with manually wired TV focus (no
 * TvFocusable* analogue exists). [staggeredState] is OWNED BY THE SCREEN ROOT
 * (the alphabet rail drives it); this composable owns only the branch-local
 * focus requesters + cursor index.
 */
@Composable
internal fun LibraryMasonryContent(
    pagedItems: LazyPagingItems<MediaItem>,
    staggeredState: LazyStaggeredGridState,
    gridCellSize: Dp,
    contentPadding: PaddingValues,
    spacing: Dp,
    refreshGeneration: Int,
    onItemClick: (itemId: String, mediaType: MediaType, parentId: String?, itemName: String) -> Unit,
    getImageUrl: (String) -> String,
    onFocusedItemChange: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Staggered grid — posters keep their own aspect ratio and
    // pack like a masonry wall. Most useful in mixed-type
    // libraries; in a pure poster library it reads like the
    // regular grid (posters are uniform 2:3). No staggered
    // TvFocusable* analogue exists, so the TV focus contract
    // (group + restorer + fallback + initial/refresh grab) is
    // wired manually here, matching TvFocusableGrid's order.
    // State is hoisted to the screen root so the alphabet rail
    // can drive it.
    val masonryGroupRequester = remember { FocusRequester() }
    val masonryFallbackRequester = remember { FocusRequester() }
    var masonryFocusedIndex by rememberInt()
    TvGrabInitialFocus(
        focusRequester = masonryFallbackRequester,
        itemCount = pagedItems.itemCount,
        tag = "library_masonry_init",
        refreshGeneration = refreshGeneration,
    )
    LaunchedEffect(refreshGeneration) {
        if (refreshGeneration > 0) masonryFocusedIndex = 0
    }
    val masonryCurrentIndex =
        masonryFocusedIndex.coerceIn(0, (pagedItems.itemCount - 1).coerceAtLeast(0))
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(gridCellSize),
        state = staggeredState,
        contentPadding = contentPadding,
        verticalItemSpacing = spacing,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        modifier = modifier
            .fillMaxSize()
            // Restorer wraps the group (before
            // focusGroup) — same ordering contract
            // as TvFocusableGrid; see its docs.
            .tvFocusRestorer(masonryFallbackRequester)
            .focusGroup()
            .focusRequester(masonryGroupRequester),
    ) {
        items(
            count = pagedItems.itemCount,
            key = pagedItems.safeItemKey { it.id },
            contentType = { "mediaItem" },
        ) { index ->
            val item = pagedItems[index]
            if (item != null) {
                val memoizedClick = remember(item.id, item.mediaType, item.parentId, item.name) {
                    { onItemClick(item.id, item.mediaType, item.parentId, item.name) }
                }
                val itemProgress = item.rememberProgressFraction()
                val cardImage = com.raulshma.jellyplay.core.ui.components.rememberEpisodeCardImage(
                    item = item,
                    itemImageUrl = remember(item.id) { getImageUrl(item.id) },
                    seriesPosterResolver = getImageUrl,
                )
                Box(
                    modifier = Modifier
                        .ifElse(
                            index == masonryCurrentIndex,
                            Modifier.focusRequester(masonryFallbackRequester),
                        )
                        .onFocusChanged {
                            if (it.isFocused || it.hasFocus) {
                                masonryFocusedIndex = index
                                onFocusedItemChange(item)
                            }
                        },
                ) {
                    PosterCard(
                        item = item,
                        imageUrl = cardImage.imageUrl,
                        fallbackUrls = cardImage.fallbackUrls,
                        onClick = memoizedClick,
                        // Intrinsic Primary ratio of the image this card
                        // actually shows — this is what makes masonry
                        // stagger: square and portrait posters get
                        // different card heights instead of all being
                        // forced to the 2:3 grid shape.
                        aspectRatio = cardImage.aspectRatio,
                        showProgress = itemProgress != null && itemProgress > 0f,
                        progressPercent = itemProgress ?: 0f,
                        blurHash = cardImage.blurHash,
                        sharedElementKey = "poster_${item.id}",
                        showEpisodeSeriesBadge = cardImage.showSeriesBadge,
                    )
                }
            }
        }
    }
}
