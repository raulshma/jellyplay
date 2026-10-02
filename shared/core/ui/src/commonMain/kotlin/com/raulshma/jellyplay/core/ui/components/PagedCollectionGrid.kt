package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import com.raulshma.jellyplay.core.ui.tv.TvGridCacheWindow
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.util.safeItemKey

/**
 * The paged-collection RENDERER trio promoted from the music module's
 * `PagedGrid.kt` (beside the decision chassis in [PagedCollectionLadder.kt]):
 * the generic `<T : Any>` ladder — refresh loading/error/empty/content rungs
 * (the chassis's [pagedCollectionRung] / [simpleCollectionRung] folds),
 * pull-to-refresh, the append footer ([pagedAppendRung]) and the TV-focusable
 * grid/list — composed once per variant, with only caller-supplied strings
 * (core:ui owns no feature vocabulary, so nothing here defaults to a
 * feature-local resource).
 *
 *  - [PagedCollectionGrid] — the paged grid (LazyPagingItems over a
 *    [TvFocusableGrid]);
 *  - [PagedCollectionList] — the paged list (LazyPagingItems over a
 *    LazyColumn, with the TV focus-on-launch grab);
 *  - [SimpleCollectionGrid] — the list-sourced twin (plain `List` +
 *    loading/error flags, no append footer).
 *
 * Layout knobs (columns/padding/arrangements/state) stay at the call sites so
 * each route family keeps its own grid geometry; only the ladder is shared.
 * Every paged collection screen renders through one of the three or, where
 * its body is not a plain grid (view-mode swaps, grouped content), renders
 * the chassis rung decisions directly.
 *
 * Current renderer adopters: the music module's `MusicCollectionGrid`,
 * `FavoritesScreen`, `StudioDetailScreen` and `PhotoAlbumScreen`.
 * `LibraryScreen` and `SearchScreen` deliberately keep screen-local content
 * renderers while still consuming the chassis rung decisions — Library
 * because its content branch is a four-view-mode AnimatedContent with
 * shared-element scopes, grouped mode, per-mode scroll states,
 * VM-event-routed refresh, a delayed initial spinner and an action-carrying
 * empty state (beside its gradient append footer); Search because it
 * overlays refresh loading/error on the settled grid and wraps items in
 * `AnimatedSearchItem` (beside its gradient append footer).
 */

/**
 * The paged-collection ladder, grid variant — refresh loading/error/empty
 * decisions ([pagedCollectionRung]), pull-to-refresh, the append footer
 * ([pagedAppendRung]) and the TV-focusable adaptive grid, composed once.
 *
 * Strings are caller-supplied (the empty/error vocabulary is the route's,
 * not core:ui's); [appendErrorFallbackMessage] defaults to
 * [errorFallbackMessage] when the load-more failure has no dedicated copy.
 * Per-site variants stay declared at the call sites: an
 * [initialLoadingContent] override (e.g. a delayed spinner), an
 * [appendFooter] override (the standard Loading spinner / Retry footer for
 * the whole overlay — pass a custom or empty lambda to vary or suppress),
 * and [extraContent] for in-grid items after the page (e.g. an in-flow
 * load-more footer).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun <T : Any> PagedCollectionGrid(
    items: LazyPagingItems<T>,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    columns: GridCells = GridCells.Adaptive(150.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    state: LazyGridState = rememberLazyGridState(cacheWindow = TvGridCacheWindow),
    onFocusedIndexChange: (Int) -> Unit = {},
    pullToRefresh: Boolean = true,
    /** Passed through to [PullToRefreshBox] — e.g. false on TV. */
    pullToRefreshEnabled: Boolean = true,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyDescription: String? = null,
    errorFallbackMessage: String,
    appendErrorFallbackMessage: String = errorFallbackMessage,
    /** Overrides the InitialLoading rung (default [ScreenLoadingState]) — e.g. a delayed spinner. */
    initialLoadingContent: (@Composable () -> Unit)? = null,
    appendFooter: (@Composable BoxScope.(PagedAppendRung) -> Unit)? = null,
    extraContent: LazyGridScope.() -> Unit = {},
    itemContent: @Composable (T, Modifier) -> Unit,
) {
    PagedLadder(
        items = items,
        modifier = modifier,
        pullToRefresh = pullToRefresh,
        pullToRefreshEnabled = pullToRefreshEnabled,
        emptyIcon = emptyIcon,
        emptyTitle = emptyTitle,
        emptyDescription = emptyDescription,
        errorFallbackMessage = errorFallbackMessage,
        appendErrorFallbackMessage = appendErrorFallbackMessage,
        initialLoadingContent = initialLoadingContent,
        appendFooter = appendFooter,
    ) {
        TvFocusableGrid(
            itemCount = items.itemCount,
            key = items.safeItemKey(itemKey),
            columns = columns,
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = verticalArrangement,
            modifier = Modifier.fillMaxSize(),
            contentType = { "pagedItem" },
            onFocusedIndexChange = onFocusedIndexChange,
            extraContent = extraContent,
        ) { index, itemModifier ->
            val item = items[index]
            if (item != null) {
                itemContent(item, itemModifier)
            }
        }
    }
}

/**
 * The paged-collection ladder, list variant — same refresh/empty/error
 * decisions, pull-to-refresh and append footer as [PagedCollectionGrid], over
 * a LazyColumn. [tvInitialFocusTag] wires the TV focus-on-launch grab (the
 * first row takes D-pad focus once data arrives).
 */
@Composable
fun <T : Any> PagedCollectionList(
    items: LazyPagingItems<T>,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    rowSpacing: Dp = 2.dp,
    pullToRefresh: Boolean = true,
    emptyIcon: ImageVector,
    emptyTitle: String,
    errorFallbackMessage: String,
    appendErrorFallbackMessage: String = errorFallbackMessage,
    tvInitialFocusTag: String? = null,
    itemContent: @Composable (T) -> Unit,
) {
    val listFocusRequester = remember { FocusRequester() }
    if (tvInitialFocusTag != null) {
        TvGrabInitialFocus(
            focusRequester = listFocusRequester,
            itemCount = items.itemCount,
            tag = tvInitialFocusTag,
        )
    }
    PagedLadder(
        items = items,
        modifier = modifier,
        pullToRefresh = pullToRefresh,
        pullToRefreshEnabled = true,
        emptyIcon = emptyIcon,
        emptyTitle = emptyTitle,
        emptyDescription = null,
        errorFallbackMessage = errorFallbackMessage,
        appendErrorFallbackMessage = appendErrorFallbackMessage,
        initialLoadingContent = null,
        appendFooter = null,
    ) {
        LazyColumn(
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(rowSpacing),
            modifier = Modifier
                .fillMaxSize()
                .tvFocusRestorer()
                .then(
                    if (tvInitialFocusTag != null) {
                        Modifier.focusRequester(listFocusRequester)
                    } else {
                        Modifier
                    },
                ),
        ) {
            items(
                count = items.itemCount,
                key = items.safeItemKey(itemKey),
                contentType = { "mediaItem" },
            ) { index ->
                val item = items[index] ?: return@items
                itemContent(item)
            }
        }
    }
}

/**
 * The paged-collection ladder, list-sourced variant — for collections whose
 * server read returns a plain list (no paging, so no append footer), decided
 * by [simpleCollectionRung]. [error] carries the load failure itself; a null
 * message renders [errorFallbackMessage]. Errors retry through [onRefresh]
 * (the caller's forced refresh).
 */
@Composable
fun <T : Any> SimpleCollectionGrid(
    items: List<T>,
    itemKey: (T) -> Any,
    isLoading: Boolean,
    error: Throwable?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    columns: GridCells = GridCells.Adaptive(150.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    pullToRefresh: Boolean = true,
    emptyIcon: ImageVector,
    emptyTitle: String,
    errorFallbackMessage: String,
    itemContent: @Composable (Int, T, Modifier) -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        val body: @Composable () -> Unit = {
            when (simpleCollectionRung(isLoading, error, items.size)) {
                PagedCollectionRung.InitialLoading -> ScreenLoadingState()
                PagedCollectionRung.RefreshError -> ErrorScreen(
                    message = error?.message ?: errorFallbackMessage,
                    onRetry = onRefresh,
                )
                PagedCollectionRung.Empty -> ScreenEmptyState(
                    icon = emptyIcon,
                    title = emptyTitle,
                )
                PagedCollectionRung.Content -> TvFocusableGrid(
                    items = items,
                    key = itemKey,
                    columns = columns,
                    contentPadding = contentPadding,
                    horizontalArrangement = horizontalArrangement,
                    verticalArrangement = verticalArrangement,
                    modifier = Modifier.fillMaxSize(),
                    contentType = { "collectionItem" },
                ) { index, item, itemModifier ->
                    itemContent(index, item, itemModifier)
                }
            }
        }
        OptionalPullToRefreshBox(
            pullToRefresh = pullToRefresh,
            pullToRefreshEnabled = true,
            isRefreshing = isLoading && items.isNotEmpty(),
            onRefresh = onRefresh,
        ) {
            body()
        }
    }
}

/**
 * The pull-to-refresh contract both ladders share: the wheel-safe box when
 * enabled, a plain full-size box when not. The caller owns the refreshing
 * predicate (both ladders refresh only while stale content exists).
 */
@Composable
private fun OptionalPullToRefreshBox(
    pullToRefresh: Boolean,
    pullToRefreshEnabled: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    if (pullToRefresh) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            enabled = pullToRefreshEnabled,
            modifier = Modifier.fillMaxSize(),
        ) {
            content()
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), content = content)
    }
}

/**
 * The shared refresh ladder both paged variants render through: the
 * [pagedCollectionRung] full-screen decisions plus the [pagedAppendRung]
 * bottom overlay (the standard Loading spinner / Retry footer, or the
 * caller's [appendFooter] override), wrapped in the wheel-safe
 * pull-to-refresh box (refreshing only while stale pages exist — a first
 * load shows [ScreenLoadingState] instead).
 */
@Composable
private fun <T : Any> PagedLadder(
    items: LazyPagingItems<T>,
    modifier: Modifier = Modifier,
    pullToRefresh: Boolean,
    pullToRefreshEnabled: Boolean,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyDescription: String?,
    errorFallbackMessage: String,
    appendErrorFallbackMessage: String,
    initialLoadingContent: (@Composable () -> Unit)?,
    appendFooter: (@Composable BoxScope.(PagedAppendRung) -> Unit)?,
    loadedContent: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        val refreshPhase = items.loadState.refresh.toPagedRefreshPhase()
        val footer: @Composable BoxScope.(PagedAppendRung) -> Unit =
            appendFooter ?: { rung ->
                when (rung) {
                    PagedAppendRung.Hidden -> Unit
                    PagedAppendRung.Loading -> JellyPlayLoadingIndicator(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    )
                    PagedAppendRung.Retry -> AppendErrorFooter(
                        message = (items.loadState.append as LoadState.Error).error.message
                            ?: appendErrorFallbackMessage,
                        onRetry = { items.retry() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    )
                }
            }
        val body: @Composable BoxScope.() -> Unit = {
            when (pagedCollectionRung(refreshPhase, items.itemCount)) {
                PagedCollectionRung.InitialLoading -> initialLoadingContent?.invoke() ?: ScreenLoadingState()
                PagedCollectionRung.RefreshError -> ErrorScreen(
                    message = (items.loadState.refresh as LoadState.Error).error.message
                        ?: errorFallbackMessage,
                    onRetry = { items.refresh() },
                )
                PagedCollectionRung.Empty -> ScreenEmptyState(
                    icon = emptyIcon,
                    title = emptyTitle,
                    description = emptyDescription,
                )
                PagedCollectionRung.Content -> loadedContent()
            }
            footer(pagedAppendRung(items.loadState.append.toPagedRefreshPhase()))
        }
        OptionalPullToRefreshBox(
            pullToRefresh = pullToRefresh,
            pullToRefreshEnabled = pullToRefreshEnabled,
            isRefreshing = items.loadState.refresh is LoadState.Loading && items.itemCount > 0,
            onRefresh = { items.refresh() },
        ) {
            body()
        }
    }
}
