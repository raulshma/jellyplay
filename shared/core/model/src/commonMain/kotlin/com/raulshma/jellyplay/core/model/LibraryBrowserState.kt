package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable

/**
 * Factory-default poster-size multiplier (matches the toolbar slider's 1.0 center).
 *
 * Promoted to core/model from a private VM const so [LibraryBrowserState] and
 * [LibraryBrowserReducer] can reference the canonical default. A separate
 * same-named const in [com.raulshma.jellyplay.core.datastore.library.LibraryStore]
 * predates this one and remains the store's own default; they intentionally agree.
 */
const val DEFAULT_POSTER_SIZE: Float = 1.0f

/**
 * One immutable value type owning the entire library *browser* view-state as a
 * consistent unit: which folder, which filters, which view mode, grouping,
 * poster size, the active section context, and the toolbar title.
 *
 * This collapses the swarm of individual StateFlows that previously lived in
 * `LibraryViewModel` (the 18-flow "swarm"). The invariants the VM used to enforce
 * by hand-scattered guards now live as properties/derivation rules:
 *
 * - "is the user in a See-All deep-link section?" → [isSection] (derived from
 *   [sectionContext]), replacing the hand-written `_sectionContext.value == null`
 *   guards at three persistence sites.
 * - view-mode precedence ("per-folder override > collectionType default > global")
 *   → a single rule in [LibraryBrowserReducer.resolveViewMode], replacing the two
 *   divergent derivation paths in the old VM.
 *
 * "Synthetic folder" — a section-mode `parentId` that must never be written as a
 * per-library key — becomes [isSection], not a guard at every persist site.
 */
@Immutable
data class LibraryBrowserState(
    val folder: LibraryFolder? = null,
    val filters: LibraryFilters = LibraryFilters(),
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
    val posterSize: Float = DEFAULT_POSTER_SIZE,
    val groupBy: GroupBy = GroupBy.NONE,
    val sectionContext: LibrarySectionContext? = null,
    val title: String? = null,
) {
    /** True while a section (See-All deep link) is active — gates per-folder persistence. */
    val isSection: Boolean get() = sectionContext != null
}
