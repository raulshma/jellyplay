package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.data.search.MediaSearchEngine
import com.raulshma.jellyplay.core.ui.components.UndoableAction
import com.raulshma.jellyplay.core.ui.components.undoActionChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the home search bar's entire state surface: the live query, the
 * result slice, the active flag, the recent-history list and the
 * recoverable-action (undo) channel for history deletes. Everything here was
 * previously scattered across HomeViewModel fields and collectors; it is one
 * concern — "what the search bar shows" — and now has one owner.
 *
 * Recomposition architecture (unchanged by the move, see the HomeSearchState
 * KDoc): the per-keystroke query lives in its own [searchQuery] StateFlow
 * OUTSIDE the UI's state object, read only by a leaf, while [isSearchActive]
 * carries the rarely-flipping blank/nonblank signal to the orchestrator. The
 * VM re-exposes [searchQuery]/[searchHistory]/[undoActions] directly
 * (SearchViewModel style) and folds [searchState] + [isSearchActive] into
 * HomeUiState with two one-line collectors, so no UI call site changes.
 *
 * The inline-search kernel itself (debounce, cancel-and-replace, parallel
 * Jellyfin + gated Seerr fetch, result-gated history save) stays in
 * [MediaSearchEngine]; this holder only feeds it the query flow and folds its
 * preview emissions into [searchState]. History undo is presentation on top
 * of the engine primitives: delete/clear snapshot first, then re-record on
 * undo (the DB row id changes on re-insert; the query text is what matters).
 */
internal class HomeSearchStateHolder(
    /** The VM's scope: search collectors and history jobs die with the VM. */
    private val scope: CoroutineScope,
    private val mediaSearchEngine: MediaSearchEngine,
) {

    private val _searchQuery = MutableStateFlow("")

    /**
     * The live query string. Kept in lockstep with [searchState]'s lifecycle
     * (both are written by [updateSearchQuery]/[clearSearch]), but exposed
     * separately so the home screen can read it in a leaf composable without
     * recomposing the whole `MainHomeContent` body on every keystroke —
     * mirrors the `scrollFraction` deferral pattern in `HomeScrollState`.
     */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchState = MutableStateFlow(HomeSearchState())

    /** The results slice the search overlay renders (Jellyfin + Seerr + spinner). */
    val searchState: StateFlow<HomeSearchState> = _searchState.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)

    /** True while the search field holds a non-blank query (see class KDoc). */
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    /**
     * Recent searches for the active user — keyed on the active user and
     * gated by the hide-history preference inside
     * [MediaSearchEngine.recentHistory]. Exposed via stateIn (not a scope
     * collector) so the underlying Room flow is only collected while the
     * search overlay is actually on screen.
     */
    val searchHistory: StateFlow<List<SearchHistoryItem>> = mediaSearchEngine.recentHistory()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Recoverable-action snackbars for home (search-history delete/clear).
     * Home previously had no SnackbarHost at all */
    private val _undoActions = undoActionChannel()
    val undoActions = _undoActions.receiveAsFlow()

    init {
        // Feed the engine's debounced preview results into the search slice.
        // The local settings-search collector that used to sit beside it moved
        // to the UI layer (HomeTopDockScrim), which owns the Android Context.
        scope.launch {
            mediaSearchEngine.preview(_searchQuery).collect { state ->
                _searchState.update {
                    it.copy(
                        jellyfinResults = state.jellyfin,
                        seerrResults = state.seerr,
                        isSearching = state.isSearching,
                    )
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        // Only the rarely-changing blank/nonblank signal touches
        // [isSearchActive]. Writing the per-keystroke query string into the
        // UI state object would change its equality on every keystroke and
        // recompose the whole MainHomeContent body. The live query lives on
        // [searchQuery], read in a leaf.
        _isSearchActive.value = query.isNotBlank()
        if (query.isBlank()) {
            _searchState.update { it.copy(isSearching = false) }
        }
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchState.value = HomeSearchState()
        _isSearchActive.value = false
        _searchQuery.value = ""
    }

    fun deleteSearchHistoryItem(id: Long) {
        // Capture the query before deleting so Undo can re-save it (the DB row
        // id changes on re-insert; the query text is what matters).
        val item = searchHistory.value.firstOrNull { it.id == id }
        scope.launch {
            mediaSearchEngine.deleteHistoryItem(id)
            if (item != null) {
                _undoActions.trySend(
                    UndoableAction(
                        message = "Removed \"${item.query}\" from search history",
                        onUndo = {
                            scope.launch {
                                mediaSearchEngine.recordHistory(item.query, jellyfinHadResults = true)
                            }
                        },
                    ),
                )
            }
        }
    }

    fun clearSearchHistory() {
        scope.launch {
            // Snapshot before clearing so Undo can restore the full set.
            val snapshot = searchHistory.value
            mediaSearchEngine.clearHistory()
            if (snapshot.isNotEmpty()) {
                _undoActions.trySend(
                    UndoableAction(
                        message = "Cleared search history",
                        onUndo = {
                            scope.launch {
                                snapshot.forEach {
                                    mediaSearchEngine.recordHistory(it.query, jellyfinHadResults = true)
                                }
                            }
                        },
                    ),
                )
            }
        }
    }
}
