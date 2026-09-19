package com.raulshma.jellyplay.core.ui.viewmodel

/**
 * The one append-page decision core (the recorded `PageAppender` deferral,
 * landed together with its per-site interleaved-completion tests): the three
 * facts every hand-copied append-page ladder drifted on — the in-flight
 * guard (double-fire suppression), the terminal `hasMore` gate, and the
 * page-index math. Call sites: Requests' page navigation (guards on
 * `isLoading`) and admin Stats Detail's `loadMore` (guards on
 * `isLoadingMore && hasMoreItems`); the former Logs defect was fixed earlier
 * and stays where it is.
 *
 * Deliberately STATELESS: both call sites keep their in-flight flag in ui
 * state (Requests' `isLoading` doubles as the cold-load flag; Stats Detail's
 * `isLoadingMore` is raised by its load-ladder start arm), so the guard
 * READS the site's flag instead of owning a second one that could disagree
 * with what the screen renders. What the appender owns is the decision,
 * written once — including Requests' declared delta: a Next tap landing
 * mid-flight used to bump the page field while the suppressed fetch left
 * the rows behind, so the pager silently desynced; routing the tap through
 * the guard suppresses the bump with the fetch.
 */
object PageAppender {

    /**
     * The next page to fetch, or `null` when the append must not fire:
     * suppressed while a load is in flight ([inFlight] — double-fire
     * suppression) or once the pager is terminal ([hasMore] false).
     */
    fun nextPageOrNull(currentPage: Int, inFlight: Boolean, hasMore: Boolean): Int? =
        if (inFlight || !hasMore) null else currentPage + 1

    /**
     * Offset of [page]'s first item for skip-based paged endpoints
     * (1-based pages: page 1 skips 0).
     */
    fun skipForPage(page: Int, pageSize: Int): Int = (page - 1) * pageSize
}
