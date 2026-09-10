package com.raulshma.jellyplay.feature.requests

import com.raulshma.jellyplay.core.model.seerr.SeerrRequestFilter
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestSort
import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * Pins the [RequestsFilterState] write algebra — the pure transforms behind
 * every [RequestsViewModel] filter setter (and the page-1 reset they all
 * carry via [withFilterState]). The setters used to hand-roll `copy(field,
 * currentPage = 1)` six times over.
 */
class RequestsFilterStateTest {

    /** A non-default state so each transform's untouched axes are visible. */
    private val state = RequestsFilterState(
        filter = SeerrRequestFilter.APPROVED,
        mediaType = "movie",
        sort = SeerrRequestSort.MODIFIED,
        sortDirection = "asc",
        showMyRequestsOnly = true,
        searchQuery = "alien",
    )

    @Test
    fun withFilter_replaces_only_the_filter() {
        assertEquals(
            state.copy(filter = SeerrRequestFilter.PENDING),
            state.withFilter(SeerrRequestFilter.PENDING),
        )
    }

    @Test
    fun withSort_replaces_only_the_sort() {
        assertEquals(
            state.copy(sort = SeerrRequestSort.ADDED),
            state.withSort(SeerrRequestSort.ADDED),
        )
    }

    @Test
    fun withSortDirectionToggled_flips_both_ways() {
        assertEquals(state.copy(sortDirection = "desc"), state.withSortDirectionToggled())
        assertEquals(
            state.copy(sortDirection = "asc"),
            state.withSortDirectionToggled().withSortDirectionToggled(),
        )
    }

    @Test
    fun withMediaType_replaces_or_clears_the_restriction() {
        assertEquals(state.copy(mediaType = "tv"), state.withMediaType("tv"))
        assertEquals(state.copy(mediaType = null), state.withMediaType(null))
    }

    @Test
    fun withMyRequestsOnlyToggled_flips_the_flag() {
        assertEquals(state.copy(showMyRequestsOnly = false), state.withMyRequestsOnlyToggled())
    }

    @Test
    fun withSearchQuery_replaces_the_term() {
        assertEquals(state.copy(searchQuery = ""), state.withSearchQuery(""))
    }

    @Test
    fun cleared_returns_the_default_state() {
        assertEquals(RequestsFilterState(), state.cleared())
    }

    @Test
    fun withFilterState_maps_the_axes_and_resets_pagination() {
        val uiState = RequestsUiState(currentPage = 4)

        val next = uiState.withFilterState { it.withSort(SeerrRequestSort.ADDED).withMediaType(null) }

        assertEquals(1, next.currentPage)
        assertEquals(SeerrRequestSort.ADDED, next.sort)
        assertEquals(null, next.mediaType)
        // Untouched axes survive the write.
        assertEquals(SeerrRequestFilter.PENDING, next.filter)
    }

    @Test
    fun withFilterState_folds_every_filter_axis() {
        // Every axis non-default, transform to defaults: if the fold dropped
        // an axis from its copy, that axis would keep its non-default value
        // and this would fail. Guards the hand-copied field list in the fold
        // against silently lagging a new RequestsFilterState field.
        val uiState = RequestsUiState(
            currentPage = 4,
            filter = SeerrRequestFilter.APPROVED,
            mediaType = "movie",
            sort = SeerrRequestSort.MODIFIED,
            sortDirection = "asc",
            showMyRequestsOnly = true,
            searchQuery = "alien",
        )

        val next = uiState.withFilterState { it.cleared() }

        assertEquals(
            RequestsFilterState(),
            RequestsFilterState(
                filter = next.filter,
                mediaType = next.mediaType,
                sort = next.sort,
                sortDirection = next.sortDirection,
                showMyRequestsOnly = next.showMyRequestsOnly,
                searchQuery = next.searchQuery,
            ),
        )
        assertEquals(1, next.currentPage)
    }
}
