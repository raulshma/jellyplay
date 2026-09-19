package com.raulshma.jellyplay.feature.library

import com.raulshma.jellyplay.core.model.GroupBy
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LibrarySectionContext
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.MediaItem

/**
 * Every user intent the library screen can express. [LibraryViewModel.onEvent]
 * is the single entry point (the HomeViewModel `HomeUiEvent` precedent): the
 * VM exposes no per-action command methods, so new intents are added here (and
 * routed once) rather than as new public members on the VM.
 */
sealed interface LibraryUiEvent {
    /** Manual refresh (pull-to-refresh, error retry) — bypasses the caches. */
    data object Refresh : LibraryUiEvent

    /** Section-mode entry ("See All" deep-link) — scopes the query to the section. */
    data class ConfigureSection(val ctx: LibrarySectionContext) : LibraryUiEvent

    /** Leaves section mode so the Library tab renders its default view. */
    data object ClearSectionMode : LibraryUiEvent

    /** Folder chip tap (`null` = the "All" chip). */
    data class SelectFolder(val folder: LibraryFolder?) : LibraryUiEvent

    /** Replaces the whole filter set (every sheet/tag/filter apply site). */
    data class UpdateFilters(val filters: LibraryFilters) : LibraryUiEvent

    /** Toolbar view-mode cycle tap (GRID → THUMB → LIST → MASONRY). */
    data class SetViewMode(val mode: LibraryViewMode) : LibraryUiEvent

    /** Poster-size slider drag (in-memory only until [PersistPosterSize]). */
    data class SetPosterSize(val size: Float) : LibraryUiEvent

    /** Poster-size slider released — persists the in-memory size. */
    data object PersistPosterSize : LibraryUiEvent

    /** Group-by sheet chip tap. */
    data class SetGroupBy(val groupBy: GroupBy) : LibraryUiEvent

    /** Opens/closes the full filter sheet (the toggle closes it when open). */
    data object ToggleFilters : LibraryUiEvent

    /** Music-toolbar Shuffle — transient RANDOM sort, nothing persisted. */
    data object ShuffleLibrary : LibraryUiEvent

    /** Clears the active filter set (Clear-all tag, empty-state action, back). */
    data object ClearFilters : LibraryUiEvent

    /** Top-bar Reset pill tap — gated by the confirm-reset preference. */
    data object ResetClicked : LibraryUiEvent

    /** Reset-confirmation dialog confirmed (optionally opting out forever). */
    data class ConfirmResetAll(val dontShowAgain: Boolean) : LibraryUiEvent

    /** Reset-confirmation dialog dismissed without resetting. */
    data object DismissResetDialog : LibraryUiEvent

    /**
     * Quick-action mark played/unplayed — intentionally silent: the paged grid
     * is left untouched so the user keeps their scroll position.
     */
    data class MarkItemPlayed(val item: MediaItem, val played: Boolean) : LibraryUiEvent

    /**
     * Long-press Download from a browse card. Single-stream items start
     * inline; series and other non-inline types open the detail screen via
     * [onOpenDetail] (`openDownloadSheet` pre-presents the series download
     * sheet there). Same callback-carries-navigation shape as HomeUiEvent.
     */
    data class DownloadItem(
        val item: MediaItem,
        val onOpenDetail: (itemId: String, openDownloadSheet: Boolean) -> Unit = { _, _ -> },
    ) : LibraryUiEvent

    /** Quick-action Remove download — deletes the local copy only. */
    data class RemoveItemDownload(val item: MediaItem) : LibraryUiEvent

    /** Prefetches the child-image URLs for the visible photo-folder cards. */
    data class PrefetchPhotoFolderChildUrls(val items: List<MediaItem>) : LibraryUiEvent
}
