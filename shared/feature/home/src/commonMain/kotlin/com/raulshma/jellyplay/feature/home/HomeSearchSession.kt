package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The search SESSION half of the home search bar: who owns "is the search
 * surface open" and the teardown choreography when it closes.
 *
 * The data half (query, results, history, undo) already lives in the VM's
 * [HomeSearchStateHolder]; what was missing was an owner for the expanded
 * flag and the close ORDERING — collapse the surface → clear the query →
 * drop keyboard focus. That triple used to be hand-duplicated at seven call
 * sites (the BackHandler, three result-click lambdas, the dock's clear and
 * back paths), and a new site that skipped a step stranded the keyboard.
 * Here it is one method whose sequence is pinned by [HomeSearchSessionTest].
 *
 * [close] takes the focus-clearing as a parameter (rather than holding a
 * FocusManager) so the composable read of [LocalFocusManager][androidx.compose.ui.platform.LocalFocusManager]
 * stays at the call site — fresh per invocation, never a remembered stale
 * instance — while the ORDERING stays here.
 *
 * `isExpanded` is snapshot state so the orchestrator's derived
 * isSearchFocused and the dock's onSearchExpanded slot observe it directly.
 */
@Stable
internal class HomeSearchSession(
    private val onEvent: (HomeUiEvent) -> Unit,
) {
    var isExpanded: Boolean by mutableStateOf(false)
        private set

    /** Opens the search surface (the dock's collapsed search affordance). */
    fun open() {
        isExpanded = true
    }

    /**
     * The ONE search teardown: collapse the surface, clear the query, then
     * drop keyboard focus — in that order, so the field never shows stale
     * results and the keyboard never outlives the surface.
     */
    fun close(clearFocus: () -> Unit) {
        isExpanded = false
        onEvent(HomeUiEvent.ClearSearch)
        clearFocus()
    }

    /**
     * The result-click shape: [close] the session, then run the [action]
     * (navigation, settings deep-link) against the now-settled home surface.
     */
    fun closeThen(clearFocus: () -> Unit, action: () -> Unit) {
        close(clearFocus)
        action()
    }
}
