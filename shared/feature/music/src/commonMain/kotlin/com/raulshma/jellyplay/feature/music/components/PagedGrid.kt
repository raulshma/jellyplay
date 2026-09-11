package com.raulshma.jellyplay.feature.music.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Search
import com.raulshma.jellyplay.core.ui.components.AppendErrorFooter
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.HeaderStatus
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.PullToRefreshBox
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.util.safeItemKey
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_failed_load
import com.raulshma.jellyplay.feature.music.generated.resources.music_failed_load_more
import com.raulshma.jellyplay.feature.music.generated.resources.music_nothing_found

/**
 * The paging refresh phase the ladder decisions read. A own-able vocabulary
 * (rather than taking [LoadState] directly) because paging-common's
 * `LoadState.NotLoading` instance is not constructible outside the library —
 * the sealed mapping in [LoadState.toPagedRefreshPhase] is compiler-checked.
 */
enum class PagedRefreshPhase { LOADING, ERROR, SETTLED }

/** The one sealed mapping from the paging refresh/append [LoadState]. */
fun LoadState.toPagedRefreshPhase(): PagedRefreshPhase = when (this) {
    is LoadState.Error -> PagedRefreshPhase.ERROR
    is LoadState.Loading -> PagedRefreshPhase.LOADING
    is LoadState.NotLoading -> PagedRefreshPhase.SETTLED
}

/**
 * The pure refresh-ladder decision behind [PagedGrid]/[PagedList]: which
 * full-screen rung to render from the paging refresh phase and the live
 * item count. Content wins over the spinner once pages exist — a re-sort or
 * pull-to-refresh over cached pages shows the stale content plus the refresh
 * affordance, not a blank screen. Pinned by `PagedCollectionLadderTest`.
 */
sealed interface PagedCollectionRung {
    /** Refresh in flight with nothing to show yet — full-screen loading. */
    data object InitialLoading : PagedCollectionRung

    /** Refresh failed — full-screen error with retry (even over stale pages). */
    data object RefreshError : PagedCollectionRung

    /** Refresh settled with no items — empty state. */
    data object Empty : PagedCollectionRung

    /** Items to show — the collection body (grid or list). */
    data object Content : PagedCollectionRung
}

fun pagedCollectionRung(refresh: PagedRefreshPhase, itemCount: Int): PagedCollectionRung = when {
    refresh == PagedRefreshPhase.ERROR -> PagedCollectionRung.RefreshError
    refresh == PagedRefreshPhase.LOADING && itemCount == 0 -> PagedCollectionRung.InitialLoading
    itemCount == 0 -> PagedCollectionRung.Empty
    else -> PagedCollectionRung.Content
}

/**
 * The list-sourced twin of [pagedCollectionRung] — the same rung vocabulary
 * over [SimpleListCollection][com.raulshma.jellyplay.feature.music.collection.SimpleListCollection]
 * state. Declared precedence difference: an in-flight load shows the
 * full-screen spinner even over stale items (the list twin keeps no
 * content-over-refresh carry; the header status and pull-to-refresh affordance
 * still render), and an error rung shows only once loading settles. Pinned by
 * `PagedCollectionLadderTest`.
 */
fun simpleCollectionRung(isLoading: Boolean, error: Throwable?, itemCount: Int): PagedCollectionRung = when {
    isLoading -> PagedCollectionRung.InitialLoading
    error != null -> PagedCollectionRung.RefreshError
    itemCount == 0 -> PagedCollectionRung.Empty
    else -> PagedCollectionRung.Content
}

/**
 * The pure append-rung decision: what the load-more footer renders while the
 * next page streams in. Retry means the caller must re-invoke
 * [LazyPagingItems.retry] (append failures retry the page, not the whole
 * query — the refresh call belongs to the pull-to-refresh/error screens
 * only). Pinned by `PagedCollectionLadderTest`.
 */
sealed interface PagedAppendRung {
    /** No append in flight — no footer. */
    data object Hidden : PagedAppendRung

    /** Next page loading — spinner footer. */
    data object Loading : PagedAppendRung

    /** Next page failed — error footer wired to [LazyPagingItems.retry]. */
    data object Retry : PagedAppendRung
}

fun pagedAppendRung(append: PagedRefreshPhase): PagedAppendRung = when (append) {
    PagedRefreshPhase.LOADING -> PagedAppendRung.Loading
    PagedRefreshPhase.ERROR -> PagedAppendRung.Retry
    PagedRefreshPhase.SETTLED -> PagedAppendRung.Hidden
}

/**
 * Derives the scaffold [HeaderStatus] for a paged collection — the one place
 * the refresh load states map onto the header indicator. Screens place the
 * indicator in their own scaffold actions (route-family visuals); the policy
 * lives here once.
 */
@Composable
fun <T : Any> rememberPagedCollectionStatus(items: LazyPagingItems<T>): HeaderStatus {
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    return resolveHeaderStatus(
        isLoading = items.loadState.refresh is LoadState.Loading,
        hasError = items.loadState.refresh is LoadState.Error,
        networkStatus = networkStatus,
    )
}

/**
 * [rememberPagedCollectionStatus]'s twin for list-sourced collections
 * ([SimpleListCollection] state, e.g. the genres/playlists tabs): same
 * resolve policy over the collection's loading/error flags.
 */
@Composable
fun rememberSimpleCollectionStatus(isLoading: Boolean, hasError: Boolean): HeaderStatus {
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    return resolveHeaderStatus(
        isLoading = isLoading,
        hasError = hasError,
        networkStatus = networkStatus,
    )
}

/**
 * The ONE paged-collection ladder for the music module, grid variant —
 * refresh loading/error/empty decisions ([pagedCollectionRung]), pull-to-
 * refresh, the append footer ([pagedAppendRung]) and the TV-focusable
 * adaptive grid, composed once. Every paged music grid (browse tab pages and
 * the standalone screens) renders through this; the browse pages previously
 * hand-rolled a refresh-only subset without pull-to-refresh or the append
 * footer — folding here is a declared pure addition on that side.
 *
 * Layout knobs (columns/padding/arrangements) stay at the call sites so each
 * route family keeps its own grid geometry; only the ladder is shared.
 */
@Composable
fun <T : Any> PagedGrid(
    items: LazyPagingItems<T>,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    columns: GridCells = GridCells.Adaptive(150.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    pullToRefresh: Boolean = true,
    emptyIcon: ImageVector = Tabler.Outline.Search,
    emptyTitle: String = stringResource(Res.string.music_nothing_found),
    errorFallbackMessage: String = stringResource(Res.string.music_failed_load),
    itemContent: @Composable (T, Modifier) -> Unit,
) {
    PagedLadder(
        items = items,
        modifier = modifier,
        pullToRefresh = pullToRefresh,
        emptyIcon = emptyIcon,
        emptyTitle = emptyTitle,
        errorFallbackMessage = errorFallbackMessage,
    ) {
        TvFocusableGrid(
            itemCount = items.itemCount,
            key = items.safeItemKey(itemKey),
            columns = columns,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = verticalArrangement,
            modifier = Modifier.fillMaxSize(),
            contentType = { "pagedItem" },
        ) { index, itemModifier ->
            val item = items[index]
            if (item != null) {
                itemContent(item, itemModifier)
            }
        }
    }
}

/**
 * The ONE paged-collection ladder, list variant — same refresh/empty/error
 * decisions, pull-to-refresh and append footer as [PagedGrid], over a
 * LazyColumn. [tvInitialFocusTag] wires the TV focus-on-launch grab (the
 * first row takes D-pad focus once data arrives). Replaces the browse tracks
 * page's hand-rolled refresh ladder and the standalone tracks screen's
 * boilerplate.
 */
@Composable
fun <T : Any> PagedList(
    items: LazyPagingItems<T>,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    rowSpacing: Dp = 2.dp,
    pullToRefresh: Boolean = true,
    emptyIcon: ImageVector = Tabler.Outline.Search,
    emptyTitle: String = stringResource(Res.string.music_nothing_found),
    errorFallbackMessage: String = stringResource(Res.string.music_failed_load),
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
        emptyIcon = emptyIcon,
        emptyTitle = emptyTitle,
        errorFallbackMessage = errorFallbackMessage,
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
 * The pull-to-refresh contract both ladders share: the module's wheel-safe
 * box when enabled, a plain full-size box when not. The caller owns the
 * refreshing predicate (both ladders refresh only while stale content exists).
 */
@Composable
private fun OptionalPullToRefreshBox(
    pullToRefresh: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    if (pullToRefresh) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            content()
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), content = content)
    }
}

/**
 * The ONE ladder, list-sourced variant — for the music collections whose
 * server read returns a plain list (genres, playlists;
 * [com.raulshma.jellyplay.feature.music.collection.SimpleListCollection]).
 * Same rung vocabulary and pull-to-refresh contract as [PagedGrid] minus the
 * paging append footer, decided by [simpleCollectionRung]. [error] carries the
 * load failure itself; a null message renders [errorFallbackMessage] — pass
 * the collection kind's declared `errorFallbackRes` string. Errors retry
 * through [onRefresh] (the collection's forced refresh).
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
    emptyIcon: ImageVector = Tabler.Outline.Search,
    emptyTitle: String = stringResource(Res.string.music_nothing_found),
    errorFallbackMessage: String = stringResource(Res.string.music_failed_load),
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
            isRefreshing = isLoading && items.isNotEmpty(),
            onRefresh = onRefresh,
        ) {
            body()
        }
    }
}

/**
 * The shared refresh ladder all three variants render through: the
 * [pagedCollectionRung] full-screen decisions plus the [pagedAppendRung]
 * bottom overlay, wrapped in the module's wheel-safe pull-to-refresh box
 * (refreshing only while stale pages exist — a first load shows
 * [ScreenLoadingState] instead).
 */
@Composable
private fun <T : Any> PagedLadder(
    items: LazyPagingItems<T>,
    modifier: Modifier = Modifier,
    pullToRefresh: Boolean,
    emptyIcon: ImageVector,
    emptyTitle: String,
    errorFallbackMessage: String,
    loadedContent: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        val refreshPhase = items.loadState.refresh.toPagedRefreshPhase()
        val body: @Composable BoxScope.() -> Unit = {
            when (pagedCollectionRung(refreshPhase, items.itemCount)) {
                PagedCollectionRung.InitialLoading -> ScreenLoadingState()
                PagedCollectionRung.RefreshError -> ErrorScreen(
                    message = (items.loadState.refresh as LoadState.Error).error.localizedMessage
                        ?: errorFallbackMessage,
                    onRetry = { items.refresh() },
                )
                PagedCollectionRung.Empty -> ScreenEmptyState(
                    icon = emptyIcon,
                    title = emptyTitle,
                )
                PagedCollectionRung.Content -> loadedContent()
            }
            when (pagedAppendRung(items.loadState.append.toPagedRefreshPhase())) {
                PagedAppendRung.Hidden -> Unit
                PagedAppendRung.Loading -> JellyPlayLoadingIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
                PagedAppendRung.Retry -> AppendErrorFooter(
                    message = (items.loadState.append as LoadState.Error).error.localizedMessage
                        ?: stringResource(Res.string.music_failed_load_more),
                    onRetry = { items.retry() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            }
        }
        OptionalPullToRefreshBox(
            pullToRefresh = pullToRefresh,
            isRefreshing = items.loadState.refresh is LoadState.Loading && items.itemCount > 0,
            onRefresh = { items.refresh() },
        ) {
            body()
        }
    }
}
