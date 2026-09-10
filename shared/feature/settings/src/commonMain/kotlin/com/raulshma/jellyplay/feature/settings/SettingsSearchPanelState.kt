package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem

/**
 * The TV focus choreography's settle delays, hoisted beside the panel state
 * they serve. Values are the exact anonymous literals the screen used before
 * the hoist — the focus requester stays a composable concern; only the waits
 * are named:
 *  - [SEARCH_FIELD_FOCUS_DELAY_MS]: after opening the panel, the search field
 *    only exists once the active branch composes — wait for it, then focus.
 *  - [TV_INITIAL_FOCUS_DELAY_MS]: on first TV entry, before focusing the
 *    search bar (the first frame must settle so the requester has a node).
 *  - [TV_HIGHLIGHT_REFOCUS_DELAY_MS]: on TV re-entry with a pending search
 *    highlight, wait out the scroll-into-view before clearing the highlight.
 */
internal const val SEARCH_FIELD_FOCUS_DELAY_MS = 100L
internal const val TV_INITIAL_FOCUS_DELAY_MS = 150L
internal const val TV_HIGHLIGHT_REFOCUS_DELAY_MS = 1000L

/**
 * The settings root screen's search-panel state machine, plain-class JVM-testable
 * like [ReorderState] (Compose snapshot state is fine on the JVM; the gesture and
 * focus wiring stay in the composable).
 *
 * Owns the five loose pieces of panel state the screen used to scatter across
 * `remember` vars — query, active flag, category filter, the filtered display
 * list and the recent-settings mirror — plus their interactions:
 * open → type ([onQueryChange]) → filter ([selectCategory]) → tap-through
 * ([recordRecent] + [dismiss]) → back-key dismiss, and the recents
 * add/dedupe/clear commands.
 *
 * **Recents ownership.** The persisted authority is the [SettingsRecentsStore]
 * behind the ViewModel (its `addRecent` dedupes, prepends and trims). This
 * holder keeps the display mirror: the composable re-seeds it from the
 * store-backed flow ([submitRecents], the [ReorderState.submitOrder]
 * re-sync shape) and taps update it optimistically ([recordRecent]) with the
 * same dedupe+prepend policy the store applies, so the panel never renders a
 * pre-tap list when it is reopened before the store echo lands. Persistence
 * itself is forwarded to the injected callbacks.
 *
 * [isSearchFocused] deliberately stays a composable-local `remember` — it is
 * transient focus UI, not panel state.
 */
internal class SettingsSearchPanelState(
    private val recordRecentSink: (String) -> Unit,
    private val clearRecentsSink: () -> Unit,
) {

    /** The live search query (blank = the recents/browse branch). */
    var searchQuery by mutableStateOf("")
        private set

    /** Whether the panel is expanded (the screen swaps its header for it). */
    var isSearchActive by mutableStateOf(false)
        private set

    /** The category chip filter; `null` = "All". */
    var selectedCategory by mutableStateOf<String?>(null)
        private set

    /**
     * The recent-setting ids, most-recent first — the display mirror seeded
     * from the store flow and updated by [recordRecent].
     */
    var recentIds by mutableStateOf(emptyList<String>())
        private set

    /** Expands the panel (the query/category state is already reset by [dismiss]). */
    fun open() {
        isSearchActive = true
    }

    /** Handles search-field input. */
    fun onQueryChange(query: String) {
        searchQuery = query
    }

    /** Clears the query via the inline ✕ affordance (the panel stays open). */
    fun clearQuery() {
        searchQuery = ""
    }

    /** Applies the "All" chip — clears the category filter. */
    fun selectAllCategories() {
        selectedCategory = null
    }

    /** Applies a category chip; tapping the active chip toggles the filter off. */
    fun toggleCategory(category: String) {
        selectedCategory = if (selectedCategory == category) null else category
    }

    /**
     * Collapses the panel and resets the query + filter — the shared exit path
     * minus the focus hand-back (the composable re-focuses the list itself,
     * and the post-navigation collapse must NOT steal focus).
     */
    fun dismiss() {
        isSearchActive = false
        searchQuery = ""
        selectedCategory = null
    }

    /** Filters the catalog matches down to the selected category, if any. */
    fun displayItems(filtered: List<ResolvedSettingsItem>): List<ResolvedSettingsItem> =
        if (selectedCategory != null) {
            filtered.filter { it.category == selectedCategory }
        } else {
            filtered
        }

    /**
     * Re-seeds the recents mirror from the store-backed flow. Same-policy
     * convergence with [recordRecent]: the store's echo (dedup + prepend +
     * trim) and the optimistic update agree, so re-seeding is a no-op
     * visually.
     */
    fun submitRecents(ids: List<String>) {
        recentIds = ids
    }

    /**
     * Records a tapped setting: optimistically dedupes + prepends the mirror
     * (most-recent first — the store's policy) and forwards the persistence
     * to [recordRecentSink]. Destructive action ids never reach here — the
     * caller filters via the recorded click decision.
     */
    fun recordRecent(id: String) {
        recentIds = listOf(id) + recentIds.filterNot { it == id }
        recordRecentSink(id)
    }

    /** Clears the recents mirror and forwards the store clear. */
    fun clearRecents() {
        recentIds = emptyList()
        clearRecentsSink()
    }
}
