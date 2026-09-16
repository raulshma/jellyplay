package com.raulshma.jellyplay.feature.shortcuts

/** Filter chip model: the unfiltered root or one category's slice. */
internal sealed interface ShortcutFilter {
    val key: String

    data object All : ShortcutFilter {
        override val key: String = "all"
    }

    data class Category(val category: ShortcutCategory) : ShortcutFilter {
        override val key: String get() = category.name
    }
}

/**
 * Compose-free list-policy core for [ShortcutsScreen] (the insights
 * `HeatmapGridModel` precedent): the query filter over resolved labels, the
 * category-display fold, the list-empty decision, and the empty-state
 * selection. Everything here is expressible without composition — the
 * screen's `produceState`/`remember` chrome, back-handler wiring, and
 * rendering stay in the screen and call into this policy.
 */
internal object ShortcutListPolicy {

    /** Which empty state the screen renders when [isListEmpty] holds. */
    internal sealed interface ShortcutEmptyState {
        data class NoResults(val query: String) : ShortcutEmptyState
        data object NoShortcuts : ShortcutEmptyState
    }

    /**
     * The query filter over the flattened catalog, matching against the
     * resolved title/description labels — case-insensitive `contains` on
     * BOTH fields, with the query trimmed first.
     *
     * Returns `null` (= unfiltered) in two cases the screen must NOT read as
     * "no results":
     *  - a blank query (search inactive — no filter applied at all), and
     *  - an empty label map: while the suspend compose-resources resolver
     *    hasn't landed its first value, a restored non-blank query
     *    (rememberSaveable survives process restore) would match zero labels
     *    and flash a false "No results" state — treat it as unfiltered until
     *    the resolve-once-per-items map arrives (same accepted-staleness
     *    class as HEAD's per-keystroke resolution delta).
     *
     * An item missing from the label map (brand-new items during the one
     * producer window before the map re-lands) matches `"" to ""` and is
     * therefore excluded — the documented self-healing one-frame delta.
     */
    fun filteredByQuery(
        allItems: List<ShortcutItem>,
        labels: Map<ShortcutItem, Pair<String, String>>,
        query: String,
    ): List<ShortcutItem>? {
        if (query.isBlank()) return null
        if (labels.isEmpty()) return null
        val q = query.trim().lowercase()
        return allItems.filter { item ->
            val (title, desc) = labels[item] ?: ("" to "")
            title.lowercase().contains(q) || desc.lowercase().contains(q)
        }
    }

    /**
     * The categories the list renders, folding the active filter over the
     * query result:
     *  - a non-null [queryFiltered] (search active) groups the matching
     *    items by category — an EMPTY list here is "no results", never the
     *    unfiltered catalog;
     *  - a null [queryFiltered] (search inactive) narrows the catalog map by
     *    the filter: All keeps it whole, a Category keeps only its key.
     */
    fun displayedCategories(
        categories: Map<ShortcutCategory, List<ShortcutItem>>,
        activeFilter: ShortcutFilter,
        queryFiltered: List<ShortcutItem>?,
    ): Map<ShortcutCategory, List<ShortcutItem>> =
        if (queryFiltered != null) {
            queryFiltered
                .filter { item ->
                    when (val f = activeFilter) {
                        ShortcutFilter.All -> true
                        is ShortcutFilter.Category -> item.category == f.category
                    }
                }
                .groupBy { it.category }
        } else {
            when (val f = activeFilter) {
                ShortcutFilter.All -> categories
                is ShortcutFilter.Category -> categories.filterKeys { it == f.category }
            }
        }

    /**
     * The list must render its empty state instead of sections: either
     * nothing to show at all, or every shown category's slice is empty.
     */
    fun isListEmpty(displayedCategories: Map<ShortcutCategory, List<ShortcutItem>>): Boolean =
        displayedCategories.isEmpty() || displayedCategories.values.all { it.isEmpty() }

    /**
     * Which empty state composable to render (only meaningful once
     * [isListEmpty] holds): the query's blankness — not the map shape —
     * decides "no results" vs "no shortcuts", so a restored query during the
     * unresolved-labels window (see [filteredByQuery]) shows the unfiltered
     * list rather than a false "No results" flash.
     */
    fun emptyStateFor(query: String): ShortcutEmptyState =
        if (query.isNotBlank()) ShortcutEmptyState.NoResults(query) else ShortcutEmptyState.NoShortcuts
}
