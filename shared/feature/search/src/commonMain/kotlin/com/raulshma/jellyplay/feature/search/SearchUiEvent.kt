package com.raulshma.jellyplay.feature.search

import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem

/**
 * Every user intent the search screen can express. [SearchViewModel.onEvent]
 * is the single entry point (the HomeViewModel `HomeUiEvent` precedent): the
 * VM exposes no per-action command methods, so new intents are added here
 * (and routed once) rather than as new public members on the VM.
 */
sealed interface SearchUiEvent {
    /** Query typed, spoken, pasted, cleared, or re-run from history/suggestion. */
    data class Search(val query: String) : SearchUiEvent

    /** Replaces the whole filter set (the full filter sheet's Apply). */
    data class UpdateFilters(val filters: LibraryFilters) : SearchUiEvent

    /** Media-type chip toggle (the active-filter tags dismiss these too). */
    data class ToggleMediaType(val mediaType: MediaType) : SearchUiEvent

    /** Single-select sort sheet apply. */
    data class SetSortBy(val sortBy: SortOption) : SearchUiEvent

    /** Single-select played-status sheet apply. */
    data class SetPlayedStatus(val status: PlayedStatus) : SearchUiEvent

    /** Opens/closes the full filter sheet (the toggle closes it when open). */
    data object ToggleFilters : SearchUiEvent

    /** Clears the filter set and the stored blob. */
    data object ClearFilters : SearchUiEvent

    /** Deletes one "Recent Searches" row. */
    data class DeleteSearchHistoryItem(val id: Long) : SearchUiEvent

    /** Clears the whole search history (the confirm dialog's confirm arm). */
    data object ClearSearchHistory : SearchUiEvent

    /**
     * The paged search confirmed a non-empty result set for [query] — the
     * result gate before the query is persisted to "Recent Searches".
     */
    data class SearchResultsShown(val query: String) : SearchUiEvent

    /** Retry chip on the failed Seerr row. */
    data object RetrySeerrSearch : SearchUiEvent

    /**
     * Quick-action mark played/unplayed — intentionally silent: the paged
     * results are left untouched so the user keeps their scroll position.
     */
    data class MarkItemPlayed(val item: MediaItem, val played: Boolean) : SearchUiEvent

    /**
     * Long-press Download from a result card: inline start for single-stream
     * items; richer flows open the detail screen plainly via [onOpenDetail]
     * (this host's navigation cannot pre-present the series sheet).
     */
    data class DownloadItem(
        val item: MediaItem,
        val onOpenDetail: (itemId: String) -> Unit = {},
    ) : SearchUiEvent

    /** Quick-action Remove download — deletes the local copy only. */
    data class RemoveItemDownload(val item: MediaItem) : SearchUiEvent

    /** Seerr request-dialog confirm — pure forward to the request holder. */
    data class RequestSeerrMedia(
        val item: SeerrSearchItem,
        val seasons: List<Int>? = null,
        val serverId: Int? = null,
        val profileId: Int? = null,
        val rootFolder: String? = null,
        val tags: List<Int>? = null,
    ) : SearchUiEvent

    /** Opens the Seerr request dialog (holder-owned cascade). */
    data class OpenSeerrRequestDialog(val item: SeerrSearchItem) : SearchUiEvent

    /** Closes the dialog and clears the last request result. */
    data object DismissSeerrRequestDialog : SearchUiEvent

    /**
     * Prefetches Seerr details for a card about to navigate; [onDone] fires
     * when the prefetch settles so the caller can stop the card's shimmer.
     */
    data class PrefetchSeerrDetails(
        val tmdbId: Int,
        val mediaType: String,
        val onDone: () -> Unit = {},
    ) : SearchUiEvent
}
