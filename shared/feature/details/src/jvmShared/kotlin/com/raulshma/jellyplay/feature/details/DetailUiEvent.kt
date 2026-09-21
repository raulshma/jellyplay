package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaItem

/**
 * Every user intent the media-detail screen can express (the home feature's
 * `HomeUiEvent`/`onEvent` precedent; this module's [ManageSeriesUiEvent] is
 * the in-module template). [DetailViewModel.onEvent] is the single entry
 * point — the VM exposes no per-action command methods, so new intents are
 * added here (and routed once) rather than as new public members on the VM.
 *
 * Two surfaces deliberately stay OFF this funnel (the same carve-out the VM's
 * class KDoc declares):
 * - The eight deep helper seams the screen drives directly —
 *   [DetailViewModel.downloads], [DetailViewModel.playlists],
 *   [DetailViewModel.watchLater], [DetailViewModel.collections],
 *   [DetailViewModel.resync], [DetailViewModel.offline],
 *   [DetailViewModel.watchParty], [DetailViewModel.seerrRequests]. They are
 *   extracted modules with their own command surfaces; wrapping their calls
 *   in events would be shallow pass-throughs.
 * - The read side: the observable flows (uiState, preferences, messages,
 *   canManageSeries, quickActionDownloadedIds), the pure image-URL getters,
 *   the click-time selected-*-index reads, and the storage probe — queries,
 *   not commands.
 */
sealed interface DetailUiEvent {
    /** Loads (or re-navigates to) the item with [itemId] — screen entry and the error-state Retry. */
    data class LoadItem(val itemId: String) : DetailUiEvent

    /** Pull-to-refresh: re-resolves the current item, keeping the loaded content visible. */
    data object ForceRefresh : DetailUiEvent

    /** On-demand fetch of one not-yet-fetched season's episodes (provider-side merge). */
    data class LoadEpisodesForSeason(val seriesId: String, val seasonId: String) : DetailUiEvent

    /** Selects the REMOTE subtitle stream index (null = none) for the current item. */
    data class SelectSubtitle(val index: Int?) : DetailUiEvent

    /** Selects the REMOTE audio stream index (null = none) for the current item. */
    data class SelectAudio(val index: Int?) : DetailUiEvent

    /** Persists a manifest-backed LOCAL-subtitle selection for the current item (offline playback). */
    data class SelectLocalSubtitle(val index: Int?) : DetailUiEvent

    /** Persists the season-episode sort order app-wide (read back via [DetailViewModel.preferences]). */
    data class SetEpisodesDescending(val descending: Boolean) : DetailUiEvent

    /** Toggles the compact vertical episode list preference (mobile only, persisted app-wide). */
    data class SetCompactEpisodeList(val enabled: Boolean) : DetailUiEvent

    /**
     * Plays the current album's queue starting at [startIndex]. Carried
     * explicitly (no default) so every dispatch site states the entry track
     * it means — the pre-fold fun defaulted it to 0.
     */
    data class PlayAlbum(val startIndex: Int) : DetailUiEvent

    /** Starts a Jellyfin instant mix seeded off the current audio item (fire-and-forget). */
    data object StartInstantMix : DetailUiEvent

    /** Toggles favorite on the CURRENT detail item (optimistic; failure emits a snackbar). */
    data object ToggleFavorite : DetailUiEvent

    /** Marks the current item played (the write clears its resume position). */
    data object MarkPlayed : DetailUiEvent

    /** Marks the current item unplayed (the write clears its resume position). */
    data object MarkUnplayed : DetailUiEvent

    /** Marks a row item (related/collection/episode card) played/unplayed without leaving the screen. */
    data class MarkRowItemPlayed(val item: MediaItem, val played: Boolean) : DetailUiEvent

    /** Marks every episode in one season played (confirm-gated at the screen). */
    data class MarkSeasonPlayed(val seasonId: String) : DetailUiEvent

    /** Marks every episode in one season unplayed (confirm-gated at the screen). */
    data class MarkSeasonUnplayed(val seasonId: String) : DetailUiEvent

    /**
     * Long-press Download from a detail row card (related/collection/episode).
     * Single-stream items start inline; series and other richer flows open
     * the target's detail screen via [onOpenDetail] — navigation stays with
     * the caller, the same callback-carries-navigation shape as the home
     * feature's `HomeUiEvent.DownloadItem`.
     */
    data class DownloadRowItem(
        val item: MediaItem,
        val onOpenDetail: (itemId: String) -> Unit,
    ) : DetailUiEvent

    /** Long-press Remove download from a detail row card — deletes the local copy only. */
    data class RemoveRowItemDownload(val item: MediaItem) : DetailUiEvent

    /** Hides the current item's series from the home Next Up row (snackbar confirms). */
    data object HideFromNextUp : DetailUiEvent

    /** Re-includes the current item's series in the home Next Up row (snackbar confirms). */
    data object ShowFromNextUp : DetailUiEvent

    /** Hides the current item from the home Continue Watching row (snackbar confirms). */
    data object HideFromContinueWatching : DetailUiEvent

    /** Re-includes the current item in the home Continue Watching row (snackbar confirms). */
    data object ShowFromContinueWatching : DetailUiEvent

    /** Silently pins the last-viewed season tab for a series so re-entry reopens on it. */
    data class SetLastViewedSeason(val seriesId: String, val seasonId: String) : DetailUiEvent

    /** Toggles the detail screen's own Up Next section (the ⋮ menu pair). */
    data class SetShowDetailUpNext(val enabled: Boolean) : DetailUiEvent
}
