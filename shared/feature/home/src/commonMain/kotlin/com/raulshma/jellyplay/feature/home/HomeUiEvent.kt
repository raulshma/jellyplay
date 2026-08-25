package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem

/**
 * Every user intent the home screen can express. [HomeViewModel.onEvent] is
 * the single entry point — the VM exposes no per-action command methods, so
 * new intents are added here (and routed once) rather than as new public
 * members on the VM.
 */
sealed interface HomeUiEvent {
    data object Refresh : HomeUiEvent
    data object PullToRefresh : HomeUiEvent
    data object ToggleOfflineMode : HomeUiEvent
    data object SyncNow : HomeUiEvent
    data class UpdateSearchQuery(val query: String) : HomeUiEvent
    data object ClearSearch : HomeUiEvent
    data class SelectSeerrRequestItem(val item: SeerrSearchItem?) : HomeUiEvent
    data class RequestSeerrMedia(
        val item: SeerrSearchItem,
        val seasons: List<Int>? = null,
        val serverId: Int? = null,
        val profileId: Int? = null,
        val rootFolder: String? = null,
        val tags: List<Int>? = null,
    ) : HomeUiEvent
    data object ClearRequestResult : HomeUiEvent
    data class LoadSeerrServiceDetails(val mediaType: String) : HomeUiEvent
    data class LoadTvSeasons(val tmdbId: Int) : HomeUiEvent
    data object DismissNewsletterBanner : HomeUiEvent

    /** Quick-user switch from the app-bar switcher — see [HomeViewModel.onEvent]. */
    data class SwitchUser(val userId: String) : HomeUiEvent

    /** Quick-action mark played — flips the card optimistically in every section. */
    data class MarkItemPlayed(val item: MediaItem) : HomeUiEvent

    /** Quick-action mark unplayed — the unplayed counterpart of [MarkItemPlayed]. */
    data class MarkItemUnplayed(val item: MediaItem) : HomeUiEvent

    /** Quick-action delete of a downloaded (non-series) item. */
    data class DeleteOfflineMedia(val item: MediaItem) : HomeUiEvent

    /** Opens the delete-episodes sheet for a downloaded series card. */
    data class RequestSeriesDelete(val series: MediaItem) : HomeUiEvent

    /** Closes the delete-episodes sheet. */
    data object DismissSeriesDelete : HomeUiEvent

    /** Deletes the selected episodes of the open delete-episodes sheet. */
    data class DeleteOfflineEpisodes(val episodeIds: Set<String>) : HomeUiEvent

    /** Deletes the entire series of the open delete-episodes sheet and closes it. */
    data class DeleteOfflineSeries(val seriesId: String) : HomeUiEvent

    /**
     * Prefetches Seerr service details for a card about to open the request
     * dialog. [onDone] fires when the prefetch settles so the caller can
     * stop the card's shimmer.
     */
    data class PrefetchSeerrDetails(
        val tmdbId: Int,
        val mediaType: String,
        val onDone: () -> Unit = {},
    ) : HomeUiEvent

    /** Deletes one search-history row (undo re-records it). */
    data class DeleteSearchHistoryItem(val id: Long) : HomeUiEvent

    /** Clears the search history (undo re-records the snapshot). */
    data object ClearSearchHistory : HomeUiEvent

    /**
     * A settings search result was tapped from the home search bar. Enables
     * advanced settings first when the target is hidden; navigation itself
     * stays with the caller.
     */
    data class SettingsResultClicked(val item: ResolvedSettingsItem) : HomeUiEvent

    /** Hides a series from the Next Up row. */
    data class ExcludeSeriesFromNextUp(val seriesId: String) : HomeUiEvent

    /** Toggles a home section's visibility from the inline section-config sheet. */
    data class SetSectionVisible(val type: HomeSectionType, val visible: Boolean) : HomeUiEvent

    /** Moves a home section up/down from the inline section-config sheet. */
    data class MoveSection(val type: HomeSectionType, val up: Boolean) : HomeUiEvent

    /** Toggles a per-library section (LATEST_MEDIA) from the inline section-config sheet. */
    data class SetLibrarySectionVisible(
        val libraryId: String,
        val type: HomeSectionType,
        val visible: Boolean,
    ) : HomeUiEvent

    /** Prefetches the child-image URLs for the visible photo-folder rows. */
    data class PrefetchPhotoFolderChildUrls(val items: List<MediaItem>) : HomeUiEvent

    /** Resolves pending-sync row metadata while the sync details sheet is open. */
    data class EnsurePendingItemDetails(val itemIds: Collection<String>) : HomeUiEvent
}
