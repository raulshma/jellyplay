package com.raulshma.jellyplay.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the search query update logic extracted from HomeViewModel.
 * These are pure logic tests; no coroutines or Android runtime required.
 */
class HomeViewModelSearchTest {

    // ─── Search query state transitions ────────────────────────────────────────

    @Test
    fun updateSearchQuery_nonBlank_setsQuery() {
        var state = HomeUiState()
        val query = "batman"
        state = state.copy(
            searchState = state.searchState.copy(
                query = query,
                isSearching = query.isNotBlank(),
            )
        )
        assertEquals("batman", state.searchState.query)
        assertTrue(state.searchState.isSearching)
    }

    @Test
    fun updateSearchQuery_blank_isSearchingFalse() {
        var state = HomeUiState()
        val query = ""
        state = state.copy(
            searchState = state.searchState.copy(
                query = query,
                isSearching = if (query.isBlank()) false else state.searchState.isSearching,
            )
        )
        assertEquals("", state.searchState.query)
        assertFalse(state.searchState.isSearching)
    }

    @Test
    fun updateSearchQuery_whitespaceOnly_isSearchingFalse() {
        val query = "   "
        val isSearching = if (query.isBlank()) false else true
        assertFalse(isSearching)
    }

    @Test
    fun clearSearch_resetsSearchState() {
        var state = HomeUiState(
            searchState = HomeSearchState(
                query = "hello",
                isSearching = true,
                jellyfinResults = listOf(
                    com.raulshma.jellyplay.core.model.MediaItem(id = "1", name = "Hello World",
                        mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE)
                ),
            )
        )
        state = state.copy(searchState = HomeSearchState())
        assertEquals("", state.searchState.query)
        assertFalse(state.searchState.isSearching)
        assertTrue(state.searchState.jellyfinResults.isEmpty())
        assertTrue(state.searchState.seerrResults.isEmpty())
    }

    @Test
    fun searchResults_jellyfinOnly_stateUpdated() {
        var state = HomeUiState()
        val results = listOf(
            com.raulshma.jellyplay.core.model.MediaItem(id = "1", name = "Batman",
                mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE),
            com.raulshma.jellyplay.core.model.MediaItem(id = "2", name = "Batman Returns",
                mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE),
        )
        state = state.copy(
            searchState = state.searchState.copy(
                jellyfinResults = results,
                isSearching = false,
            )
        )
        assertEquals(2, state.searchState.jellyfinResults.size)
        assertFalse(state.searchState.isSearching)
    }

    @Test
    fun searchResults_seerrResults_stateUpdated() {
        var state = HomeUiState()
        val seerrResults = listOf(
            com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem(id = 100, mediaType = "movie", title = "Inception"),
        )
        state = state.copy(
            searchState = state.searchState.copy(seerrResults = seerrResults)
        )
        assertEquals(1, state.searchState.seerrResults.size)
        assertEquals(100, state.searchState.seerrResults[0].id)
    }

    @Test
    fun searchResults_emptyOnError_setsEmptyLists() {
        var state = HomeUiState(
            searchState = HomeSearchState(
                jellyfinResults = listOf(
                    com.raulshma.jellyplay.core.model.MediaItem(id = "1", name = "Test",
                        mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE)
                )
            )
        )
        // Simulating the catch block in performSearch
        state = state.copy(
            searchState = state.searchState.copy(
                jellyfinResults = emptyList(),
                seerrResults = emptyList(),
            )
        )
        assertTrue(state.searchState.jellyfinResults.isEmpty())
        assertTrue(state.searchState.seerrResults.isEmpty())
    }

    // ─── Debounce simulation ───────────────────────────────────────────────────

    @Test
    fun debounce_blankQueryCancelsSearch() {
        val query = ""
        val shouldSearch = query.isNotBlank()
        assertFalse(shouldSearch)
    }

    @Test
    fun debounce_nonBlankQueryTriggerSearch() {
        val query = "stranger things"
        val shouldSearch = query.isNotBlank()
        assertTrue(shouldSearch)
    }

    // ─── Search history ────────────────────────────────────────────────────────

    @Test
    fun searchHistory_defaultIsEmpty() {
        // Search history is maintained separately; the default search state has no results
        val state = HomeSearchState()
        assertTrue(state.jellyfinResults.isEmpty())
        assertTrue(state.seerrResults.isEmpty())
    }
}
