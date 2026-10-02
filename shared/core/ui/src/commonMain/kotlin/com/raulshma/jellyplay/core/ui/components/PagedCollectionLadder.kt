package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems

/**
 * The shared paged-collection ladder chassis: the phase vocabulary, the pure
 * refresh/append rung decisions and the header-status derivations that every
 * paged collection screen renders through. Promoted from the music module's
 * `PagedGrid.kt`; the renderer trio built on these decisions
 * ([PagedCollectionGrid]/[PagedCollectionList]/[SimpleCollectionGrid] — see
 * `PagedCollectionGrid.kt`) lives beside it, and screens whose body is not a
 * plain grid (view-mode swaps, grouped content) render these rungs directly.
 */

/**
 * The paging refresh phase the ladder decisions read. An own-able vocabulary
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
 * The pure refresh-ladder decision behind the paged collection ladders: which
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
 * over plain list collection state (a `List` plus loading/error flags, e.g.
 * the music module's SimpleListCollection genres/playlists tabs). Declared
 * precedence difference: an in-flight load shows the full-screen spinner even
 * over stale items (the list twin keeps no content-over-refresh carry; the
 * header status and pull-to-refresh affordance still render), and an error
 * rung shows only once loading settles. Pinned by `PagedCollectionLadderTest`.
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
 * [rememberPagedCollectionStatus]'s twin for list-sourced collections (a
 * loading/error flag pair instead of paging load states): same resolve policy
 * over the collection's flags.
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
