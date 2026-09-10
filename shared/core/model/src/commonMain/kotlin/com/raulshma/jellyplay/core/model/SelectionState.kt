package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable

/**
 * The list-selection WRITE ALGEBRA shared by every bulk-action screen
 * (the [LibraryFilters] precedent): which ids are picked, whether selection
 * mode is on, and the three pure writes behind every screen's toggle / select
 * all / clear setters.
 *
 * Selection mode is never stored — [active] is always derived from the id set,
 * and each transform returns the new state with the derivation applied once,
 * so the `selectionMode = next.isNotEmpty()` copy can no longer drift or be
 * forgotten at a call site. The id type is generic (String row keys in the
 * *arr queue and downloads lists, Int ids in the Seerr requests list); each
 * screen keeps its own id mapping and passes the resulting ids in.
 *
 * This unifies only the state algebra. The three SelectionActionBar
 * composables' visual presentation is intentionally NOT unified here — their
 * drift is a product decision requiring screenshots.
 */
@Immutable
data class SelectionState<T>(val ids: Set<T> = emptySet()) {

    /** True while at least one id is picked — the screens' selection-mode flag. */
    val active: Boolean
        get() = ids.isNotEmpty()

    /** Copy with [id] added when missing, removed when present. */
    fun toggled(id: T): SelectionState<T> =
        SelectionState(if (id in ids) ids - id else ids + id)

    /** Copy with the selection emptied — the full reset write. */
    fun cleared(): SelectionState<T> = SelectionState()

    /**
     * Copy with every id in [candidates] picked (the list-wide select all).
     *
     * Declared delta vs the three former per-VM bodies: an EMPTY collection
     * yields an inactive state — the old VMs set `selectionMode = true`
     * unconditionally, showing a 0-count selection bar with every action
     * disabled. Selecting all of nothing now selects nothing.
     */
    fun selectAll(candidates: Collection<T>): SelectionState<T> = SelectionState(candidates.toSet())
}
