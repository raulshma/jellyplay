package com.raulshma.jellyplay.feature.search

import com.raulshma.jellyplay.core.ui.components.PagedRefreshPhase
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [computeSearchSurface] — the render-branch fold that replaced the
 * search screen's six-term no-results predicate and refresh-state ladder.
 * Pure JVM over the fold's inputs, the `HomeSurfaceTest` pattern: every
 * precedence rule the screen used to decide inline is asserted here. The
 * paging sub-decision is delegated to the core:ui chassis's
 * [PagedRefreshPhase] (its `LoadState` mapping is pinned by the chassis's
 * own `PagedCollectionLadderTest`), so the fold consumes the resolved phase.
 */
class SearchSurfaceTest {

    private fun surface(
        itemCount: Int = 0,
        queryHasText: Boolean = true,
        refreshPhase: PagedRefreshPhase = PagedRefreshPhase.SETTLED,
        showSeerr: Boolean = false,
        showSeerrError: Boolean = false,
        showOffline: Boolean = false,
    ) = computeSearchSurface(
        itemCount = itemCount,
        queryHasText = queryHasText,
        refreshPhase = refreshPhase,
        showSeerr = showSeerr,
        showSeerrError = showSeerrError,
        showOffline = showOffline,
    )

    // ── NoResults ────────────────────────────────────────────────────────────

    @Test
    fun `a settled-at-zero query with nothing else shown is NoResults`() {
        assertEquals(SearchSurface.NoResults, surface(itemCount = 0, queryHasText = true))
    }

    @Test
    fun `offline results beat the empty state`() {
        // A query whose only matches are on-device downloads is not "no
        // results" — the on-device section renders instead.
        assertEquals(
            SearchSurface.Content(PagedRefreshPhase.SETTLED),
            surface(showOffline = true),
        )
    }

    @Test
    fun `a seerr pane beats the empty state`() {
        assertEquals(
            SearchSurface.Content(PagedRefreshPhase.SETTLED),
            surface(showSeerr = true),
        )
    }

    @Test
    fun `a seerr error strip beats the empty state`() {
        assertEquals(
            SearchSurface.Content(PagedRefreshPhase.SETTLED),
            surface(showSeerrError = true),
        )
    }

    @Test
    fun `an in-flight refresh beats the empty state`() {
        // Zero items mid-refresh is a pending answer, not "no results".
        assertEquals(
            SearchSurface.Content(PagedRefreshPhase.LOADING),
            surface(refreshPhase = PagedRefreshPhase.LOADING),
        )
    }

    @Test
    fun `a settled-at-zero refresh error with query text is NoResults, not the error screen`() {
        // The load-bearing corner the fold preserves from the pre-fold
        // first-branch-wins ordering: the empty state wins over the error
        // overlay (the retry affordance for a settled empty query is the
        // query itself, not a full-screen error).
        assertEquals(SearchSurface.NoResults, surface(refreshPhase = PagedRefreshPhase.ERROR))
    }

    // ── Initial ──────────────────────────────────────────────────────────────

    @Test
    fun `no query and no seerr pane is the discovery state`() {
        assertEquals(SearchSurface.Initial, surface(queryHasText = false))
    }

    @Test
    fun `a seerr pane beats the discovery state too`() {
        // Seerr results arrive from the header chip without a typed query;
        // the grid slot then renders Content (empty grid under the pane).
        assertEquals(
            SearchSurface.Content(PagedRefreshPhase.SETTLED),
            surface(queryHasText = false, showSeerr = true),
        )
    }

    // ── Content + the refresh ladder ─────────────────────────────────────────

    @Test
    fun `a non-empty query renders the grid`() {
        assertEquals(
            SearchSurface.Content(PagedRefreshPhase.SETTLED),
            surface(itemCount = 24),
        )
    }

    @Test
    fun `the refresh phase carries through to the content surface`() {
        assertEquals(
            SearchSurface.Content(PagedRefreshPhase.LOADING),
            surface(itemCount = 24, refreshPhase = PagedRefreshPhase.LOADING),
        )
        assertEquals(
            SearchSurface.Content(PagedRefreshPhase.ERROR),
            surface(itemCount = 24, refreshPhase = PagedRefreshPhase.ERROR),
        )
        assertEquals(
            SearchSurface.Content(PagedRefreshPhase.SETTLED),
            surface(itemCount = 24, refreshPhase = PagedRefreshPhase.SETTLED),
        )
    }
}
