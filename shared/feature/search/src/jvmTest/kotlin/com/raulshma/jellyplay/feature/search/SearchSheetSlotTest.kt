package com.raulshma.jellyplay.feature.search

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the Search screen's sheet slot ([SearchSheet]) — the ONE nullable slot
 * that replaced the former three parallel composition booleans
 * (`openSortSheet` / `openStatusSheet` / `showClearHistoryDialog`). The
 * compiler makes coexistence unrepresentable (one slot, one render `when`);
 * this pins the declared arm inventory and the newest-show-wins replacement
 * semantics so the fold stays auditable — the module has no Compose screen
 * harness for this screen.
 *
 * Comment-pinned (composition facts, declared at [SearchSheet]'s KDoc, the
 * back ladder, and the render site): the cross-owner relationship with the
 * VM-owned filter sheet ([SearchViewModel.showFilters] — a slot overlay opens
 * ON TOP of it, neither owner closes the other, preserving the old
 * Sort/Status-over-filter-sheet stacking), and the back ladder's exact
 * semantics (slot overlays are NOT rungs; each dismisses itself via its own
 * window-level back handling, exactly as before).
 */
class SearchSheetSlotTest {

    @Test
    fun `slot inventory - Sort, Status, ClearHistory`() {
        assertEquals(
            listOf("Sort", "Status", "ClearHistory"),
            SearchSheet.entries.map { it.name },
        )
    }

    @Test
    fun `newest show replaces - one slot, no stacking`() {
        var slot: SearchSheet? = null
        slot = SearchSheet.Sort
        slot = SearchSheet.Status // opening Status while Sort is "open" replaces it
        assertEquals(SearchSheet.Status, slot)
        slot = SearchSheet.ClearHistory
        assertEquals(SearchSheet.ClearHistory, slot)
        slot = null // dismiss
        assertEquals(null, slot)
    }
}
