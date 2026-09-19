package com.raulshma.jellyplay.feature.music.components

import androidx.paging.LoadState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the collection ladder's pure decisions — the refresh rung the
 * grid/list variants render and the append rung driving the load-more
 * footer. These replace the three hand-rolled ladders (browse PagedGrid's
 * refresh-only subset, the browse tracks page's copy, the standalone
 * screens' per-screen `when` blocks). The [LoadState] → [PagedRefreshPhase]
 * mapping is compiler-checked (`LoadState.NotLoading` is not constructible
 * outside paging); the constructible arms are pinned here too.
 */
class PagedCollectionLadderTest {

    @Test
    fun refreshLadder_blankScreenResolvesPerLoadStateAndCount() {
        // First load, nothing yet → full-screen loading.
        assertEquals(PagedCollectionRung.InitialLoading, pagedCollectionRung(PagedRefreshPhase.LOADING, 0))
        // Refresh over cached pages → content stays visible (the PTR spinner
        // and header status carry the progress instead of a blank screen).
        assertEquals(PagedCollectionRung.Content, pagedCollectionRung(PagedRefreshPhase.LOADING, 5))
        // Refresh failure → full-screen error, even over stale pages.
        assertEquals(PagedCollectionRung.RefreshError, pagedCollectionRung(PagedRefreshPhase.ERROR, 0))
        assertEquals(PagedCollectionRung.RefreshError, pagedCollectionRung(PagedRefreshPhase.ERROR, 5))
        // Settled with nothing → empty state.
        assertEquals(PagedCollectionRung.Empty, pagedCollectionRung(PagedRefreshPhase.SETTLED, 0))
        // Settled with data → the collection body.
        assertEquals(PagedCollectionRung.Content, pagedCollectionRung(PagedRefreshPhase.SETTLED, 5))
    }

    @Test
    fun appendLadder_errorIsARetryNotARefresh() {
        assertEquals(PagedAppendRung.Hidden, pagedAppendRung(PagedRefreshPhase.SETTLED))
        assertEquals(PagedAppendRung.Loading, pagedAppendRung(PagedRefreshPhase.LOADING))
        assertEquals(PagedAppendRung.Retry, pagedAppendRung(PagedRefreshPhase.ERROR))
    }

    @Test
    fun simpleLadder_resolvesPerLoadingErrorAndCount() {
        val boom = RuntimeException("boom")
        // In-flight load → full-screen spinner, even over stale items (the
        // list twin's declared precedence difference from the paged ladder).
        assertEquals(PagedCollectionRung.InitialLoading, simpleCollectionRung(isLoading = true, error = null, itemCount = 0))
        assertEquals(PagedCollectionRung.InitialLoading, simpleCollectionRung(isLoading = true, error = null, itemCount = 5))
        // Settled failure → error rung, message or not.
        assertEquals(PagedCollectionRung.RefreshError, simpleCollectionRung(isLoading = false, error = boom, itemCount = 0))
        assertEquals(PagedCollectionRung.RefreshError, simpleCollectionRung(isLoading = false, error = RuntimeException(), itemCount = 5))
        // Settled with nothing → empty; settled with data → the grid body.
        assertEquals(PagedCollectionRung.Empty, simpleCollectionRung(isLoading = false, error = null, itemCount = 0))
        assertEquals(PagedCollectionRung.Content, simpleCollectionRung(isLoading = false, error = null, itemCount = 5))
    }

    @Test
    fun loadStateMapping_constructibleArmsMatchTheirPhase() {
        assertEquals(PagedRefreshPhase.LOADING, LoadState.Loading.toPagedRefreshPhase())
        assertEquals(PagedRefreshPhase.ERROR, LoadState.Error(RuntimeException("boom")).toPagedRefreshPhase())
    }
}
