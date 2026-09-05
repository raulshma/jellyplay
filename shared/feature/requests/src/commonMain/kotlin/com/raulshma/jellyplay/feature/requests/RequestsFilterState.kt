package com.raulshma.jellyplay.feature.requests

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestFilter
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestSort

/**
 * The six request-filter fields that travel together from
 * [RequestsUiState] into [RequestsFilterBar]. Kept as a value so the bar's
 * signature is one parameter (plus the callbacks) rather than six loose ones.
 *
 * This is also the filter WRITE ALGEBRA (the [com.raulshma.jellyplay.core.model.LibraryFilters]
 * precedent): the `withX` transforms below are the single policy behind every
 * setter — each returns the new filter state, and the caller pairs it with
 * the page-1 reset via [withFilters]. The VM's setters used to hand-roll
 * `copy(field, currentPage = 1)` six times over.
 */
@Immutable
data class RequestsFilterState(
    val filter: SeerrRequestFilter = SeerrRequestFilter.PENDING,
    val mediaType: String? = null,
    val sort: SeerrRequestSort = SeerrRequestSort.ADDED,
    val sortDirection: String = "desc",
    val showMyRequestsOnly: Boolean = false,
    val searchQuery: String = "",
) {
    /** Copy with the single-select request [filter] applied. */
    fun withFilter(filter: SeerrRequestFilter): RequestsFilterState = copy(filter = filter)

    /** Copy with the single-select [sort] applied. */
    fun withSort(sort: SeerrRequestSort): RequestsFilterState = copy(sort = sort)

    /** Copy with the sort direction flipped (desc <-> asc). */
    fun withSortDirectionToggled(): RequestsFilterState =
        copy(sortDirection = if (sortDirection == "desc") "asc" else "desc")

    /** Copy with the media-type restriction replaced ([null] = all types). */
    fun withMediaType(mediaType: String?): RequestsFilterState = copy(mediaType = mediaType)

    /** Copy with the my-requests-only flag flipped. */
    fun withMyRequestsOnlyToggled(): RequestsFilterState =
        copy(showMyRequestsOnly = !showMyRequestsOnly)

    /** Copy with the free-text [query] replaced. */
    fun withSearchQuery(query: String): RequestsFilterState = copy(searchQuery = query)

    /** Copy with every axis back at its default — the full reset write. */
    fun cleared(): RequestsFilterState = RequestsFilterState()
}

/**
 * Copy of the ui state with the filter axes mapped through [transform] and
 * pagination reset to page 1 — the single write shape every filter change
 * carries (the read side is [RequestsUiState.filters]). Lives beside the
 * value type so the read/write pair can be unit-tested without the VM.
 */
internal fun RequestsUiState.withFilters(
    transform: (RequestsFilterState) -> RequestsFilterState,
): RequestsUiState {
    val next = transform(filters)
    return copy(
        filter = next.filter,
        mediaType = next.mediaType,
        sort = next.sort,
        sortDirection = next.sortDirection,
        showMyRequestsOnly = next.showMyRequestsOnly,
        searchQuery = next.searchQuery,
        currentPage = 1,
    )
}
