package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.data.offline.OfflineDeleteActions
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import com.raulshma.jellyplay.feature.downloads.generated.resources.Res
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_filter_all
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_filter_music
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_filter_videos
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_sort_name
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_sort_rating
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_sort_recent
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_sort_size

/** How the downloaded library is sorted. */
enum class OfflineLibrarySort(val labelRes: StringResource) {
    RECENT(Res.string.downloads_sort_recent),
    NAME(Res.string.downloads_sort_name),
    RATING(Res.string.downloads_sort_rating),
    SIZE(Res.string.downloads_sort_size),
}

/** Coarse media-type filter for the library tabs. */
enum class OfflineLibraryFilter(val labelRes: StringResource) {
    ALL(Res.string.downloads_filter_all),
    VIDEOS(Res.string.downloads_filter_videos),
    MUSIC(Res.string.downloads_filter_music),
}

data class StorageSummary(val totalBytes: Long, val itemCount: Int)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class OfflineLibraryViewModel(
    private val offlineRepository: OfflineRepository,
    private val userDataMutator: UserDataMutator,
) : JellyPlayViewModel() {

    private val _isLoading = composeState(true)
    val isLoading: Boolean get() = _isLoading.value

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(value: String) { _query.value = value }

    private val _sort = MutableStateFlow(OfflineLibrarySort.RECENT)
    val sort: StateFlow<OfflineLibrarySort> = _sort.asStateFlow()
    fun setSort(value: OfflineLibrarySort) { _sort.value = value }

    private val _filter = MutableStateFlow(OfflineLibraryFilter.ALL)
    val filter: StateFlow<OfflineLibraryFilter> = _filter.asStateFlow()
    fun setFilter(value: OfflineLibraryFilter) { _filter.value = value }

    /**
     * Single subscription to the raw offline library; both [offlineLibrary] and
     * [storageSummary] derive from this shared upstream so the Room query and
     * the per-entity mapping run once per change instead of twice.
     */
    private val rawLibrary: StateFlow<List<OfflineMediaItem>> =
        offlineRepository.getOfflineLibrary()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /**
     * Library items after applying the active search query, filter and sort.
     * The query is debounced so typing doesn't re-filter on every keystroke.
     */
    val offlineLibrary: StateFlow<List<OfflineMediaItem>> =
        combine(
            rawLibrary,
            _query.debounce(180),
            combine(_sort, _filter) { s, f -> s to f },
        ) { items, query, (sort, filter) ->
            _isLoading.value = false
            // Filter/sort of the full offline library stays off the Main
            // dispatcher — only a concern for very large libraries, but free.
            withContext(Dispatchers.Default) {
                applyQueryFilterAndSort(items, query, filter, sort)
            }
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** Total storage used by all completed downloads, plus the item count. */
    val storageSummary: StateFlow<StorageSummary> =
        rawLibrary.map { items ->
            StorageSummary(totalBytes = items.sumOf { it.totalSizeBytes }, itemCount = items.size)
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StorageSummary(0L, 0),
        )

    private fun applyQueryFilterAndSort(
        items: List<OfflineMediaItem>,
        query: String,
        filter: OfflineLibraryFilter,
        sort: OfflineLibrarySort,
    ): List<OfflineMediaItem> {
        val filtered = when (filter) {
            OfflineLibraryFilter.VIDEOS ->
                items.filter { it.mediaType == MediaType.SERIES || it.mediaType == MediaType.MOVIE }
            OfflineLibraryFilter.MUSIC ->
                items.filter {
                    it.mediaType == MediaType.AUDIO || it.mediaType == MediaType.MUSIC || it.mediaType == MediaType.ALBUM
                }
            OfflineLibraryFilter.ALL -> items
        }
        val q = query.trim()
        val matched = if (q.length < 2) {
            filtered
        } else {
            filtered.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.seriesName?.contains(q, ignoreCase = true) == true ||
                    it.seasonName?.contains(q, ignoreCase = true) == true
            }
        }
        return when (sort) {
            OfflineLibrarySort.RECENT -> matched.sortedByDescending { it.createdAt }
            OfflineLibrarySort.NAME -> matched.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            OfflineLibrarySort.RATING -> matched.sortedByDescending { it.communityRating ?: -1f }
            OfflineLibrarySort.SIZE -> matched.sortedByDescending { it.totalSizeBytes }
        }
    }

    /**
     * Toggles played state for a downloaded item from the long-press quick-action
     * sheet. Routes through [UserDataMutator.setPlayed] (silent — the reactive
     * [offlineLibrary] Room flow refreshes on its own) so the change is applied
     * to the local offline DB AND enqueued into the playback outbox for server
     * sync on reconnect (or pushed immediately when online).
     */
    fun markItemPlayed(item: MediaItem, played: Boolean) {
        launch {
            userDataMutator.setPlayed(item.id, played)
        }
    }

    /**
     * Toggles favorite for a downloaded item. Routes through
     * [UserDataMutator.setFavorite], so it works fully offline: the flip is
     * applied to the local offline store immediately and staged in the playback
     * outbox for delivery on reconnect. Silent — Room refreshes the badge.
     */
    fun toggleFavorite(item: MediaItem) {
        launch { userDataMutator.setFavorite(item.id) }
    }

    /**
     * Shared offline-delete module (core/data) — the same routing the detail
     * screen and offline home use. Providers default to empty (this screen
     * never batch-deletes episodes) and `onContentMutated` stays a no-op: the
     * reactive [offlineLibrary] Room flow refreshes on its own once rows are
     * gone.
     */
    private val deleteActions = OfflineDeleteActions(
        scope = scope,
        offlineRepository = offlineRepository,
    )

    /**
     * Deletes a downloaded item from the long-press quick-action sheet:
     * [OfflineDeleteActions.deleteDownload] routes a series to the whole-series
     * delete and anything else to the single-item delete.
     */
    fun delete(item: MediaItem) {
        deleteActions.deleteDownload(item)
    }
}
