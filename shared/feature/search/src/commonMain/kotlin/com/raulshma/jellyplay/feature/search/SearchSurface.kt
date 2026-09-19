package com.raulshma.jellyplay.feature.search

/**
 * WHICH surface the search screen's library-content slot renders, folded ONCE
 * by [computeSearchSurface] — the pure successor of the compound branch
 * conditions that used to open the slot's render `when` (the six-term
 * no-results predicate plus the refresh-state ladder that followed the grid).
 * The `HomeSurface` precedent: the screen's `when` is exhaustive over the
 * result and owns rendering only, so the whole branch policy is assertable
 * JVM-side ([SearchSurfaceTest]) without any Compose stack.
 *
 * The Seerr pane, the Seerr error strip and the on-device section render
 * independently ABOVE this slot (`if`s, not branches); their visibility only
 * GATES [NoResults] here — a query whose matches live entirely in Seerr or
 * on-device is not "no results".
 *
 * Precedence is fixed: NoResults → Initial → Content. Note the load-bearing
 * corner the fold preserves: a settled-at-zero refresh ERROR with query text
 * is [NoResults], not [SearchSurface.RefreshPhase.ERROR] — the empty state
 * wins over the error overlay exactly as the pre-fold first-branch-wins
 * ordering did.
 */
internal sealed interface SearchSurface {

    /**
     * No results for a typed query: the pager settled at zero (not
     * refreshing) and no pane claimed the matches. Renders the no-results
     * empty state + the did-you-mean heuristic.
     */
    data object NoResults : SearchSurface

    /**
     * No query typed yet and no Seerr pane: the discovery state — suggestion
     * row + recent searches, or the plain "search your library" prompt when
     * both are empty.
     */
    data object Initial : SearchSurface

    /**
     * The results grid — also the deliberate fall-through while a query's
     * refresh is in flight or while a Seerr/offline pane owns the matches
     * (the grid itself is empty then; the panes render above). Carries the
     * pager's refresh phase for the overlay ladder.
     */
    data class Content(val refresh: RefreshPhase) : SearchSurface

    /**
     * The pager's refresh phase, stacked OVER the grid by the screen:
     * [LOADING] a centered progress bar, [ERROR] the full error screen with
     * retry, [IDLE] nothing extra.
     */
    enum class RefreshPhase { LOADING, ERROR, IDLE }
}

/**
 * THE fold — pure over the slot's inputs, no Compose types. `itemCount`/
 * `isRefreshing`/`refreshFailed` come from the collected `LazyPagingItems`;
 * the pane flags are the screen's derived visibilities.
 */
internal fun computeSearchSurface(
    itemCount: Int,
    queryHasText: Boolean,
    isRefreshing: Boolean,
    refreshFailed: Boolean,
    showSeerr: Boolean,
    showSeerrError: Boolean,
    showOffline: Boolean,
): SearchSurface = when {
    itemCount == 0 && queryHasText && !isRefreshing && !showSeerr && !showSeerrError && !showOffline ->
        SearchSurface.NoResults

    !queryHasText && !showSeerr -> SearchSurface.Initial

    else -> SearchSurface.Content(
        when {
            isRefreshing -> SearchSurface.RefreshPhase.LOADING
            refreshFailed -> SearchSurface.RefreshPhase.ERROR
            else -> SearchSurface.RefreshPhase.IDLE
        }
    )
}
