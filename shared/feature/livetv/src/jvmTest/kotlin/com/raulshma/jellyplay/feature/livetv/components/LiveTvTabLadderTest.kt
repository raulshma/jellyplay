package com.raulshma.jellyplay.feature.livetv.components

import com.raulshma.jellyplay.core.ui.components.PagedCollectionRung
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the ONE livetv tab-ladder rung decision behind [LiveTvTabScaffold]
 * (the former five hand-copied per-screen `when` ladders — Channels had no
 * loading rung at all). The precedence must stay byte-equal to core:ui's
 * `pagedCollectionRung` (the load-ladder cohort's decision shape): an error
 * over an EMPTY tab wins over the spinner, both arms gate on emptiness so a
 * refresh over live content never blanks it, and a settled-empty tab is the
 * empty rung.
 */
class LiveTvTabLadderTest {

    @Test
    fun `initial load over an empty tab is the loading rung`() {
        assertEquals(
            PagedCollectionRung.InitialLoading,
            liveTvTabRung(isLoading = true, hasError = false, isEmpty = true),
        )
    }

    @Test
    fun `error over an empty tab is the error rung and wins over the spinner`() {
        assertEquals(
            PagedCollectionRung.RefreshError,
            liveTvTabRung(isLoading = true, hasError = true, isEmpty = true),
        )
        assertEquals(
            PagedCollectionRung.RefreshError,
            liveTvTabRung(isLoading = false, hasError = true, isEmpty = true),
        )
    }

    @Test
    fun `a settled-empty tab is the empty rung`() {
        assertEquals(
            PagedCollectionRung.Empty,
            liveTvTabRung(isLoading = false, hasError = false, isEmpty = true),
        )
    }

    @Test
    fun `content wins over loading and error once items exist`() {
        assertEquals(
            PagedCollectionRung.Content,
            liveTvTabRung(isLoading = false, hasError = false, isEmpty = false),
        )
        // Refresh-over-content (pull-to-refresh, re-entry reload).
        assertEquals(
            PagedCollectionRung.Content,
            liveTvTabRung(isLoading = true, hasError = false, isEmpty = false),
        )
        // A refresh failure over live content keeps the content — the error
        // rung wins only over an empty tab (the Recordings delete-failure
        // shape: the failure surfaces if the list later empties).
        assertEquals(
            PagedCollectionRung.Content,
            liveTvTabRung(isLoading = false, hasError = true, isEmpty = false),
        )
    }

    @Test
    fun `schedule's two-list shape rides the same isEmpty fact`() {
        // One section populated → content, whatever the other does.
        assertEquals(
            PagedCollectionRung.Content,
            liveTvTabRung(isLoading = false, hasError = false, isEmpty = false),
        )
        // Both sections empty → the rungs behave as one-list tabs.
        assertEquals(
            PagedCollectionRung.InitialLoading,
            liveTvTabRung(isLoading = true, hasError = false, isEmpty = true),
        )
    }
}
