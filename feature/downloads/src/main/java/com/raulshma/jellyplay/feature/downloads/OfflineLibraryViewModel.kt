package com.raulshma.jellyplay.feature.downloads

import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.downloads.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** How the downloaded library is sorted. */
enum class OfflineLibrarySort(val labelRes: Int) {
    RECENT(R.string.downloads_sort_recent),
    NAME(R.string.downloads_sort_name),
    RATING(R.string.downloads_sort_rating),
    SIZE(R.string.downloads_sort_size),
}

/** Coarse media-type filter for the library tabs. */
enum class OfflineLibraryFilter(val labelRes: Int) {
    ALL(R.string.downloads_filter_all),
    VIDEOS(R.string.downloads_filter_videos),
    MUSIC(R.string.downloads_filter_music),
}

data class StorageSummary(val totalBytes: Long, val itemCount: Int)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class OfflineLibraryViewModel @Inject constructor(
    private val offlineRepository: OfflineRepository,
    private val mediaRepository: MediaRepository,
    @Suppress("unused") savedStateHandle: SavedStateHandle,
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
            applyQueryFilterAndSort(items, query, filter, sort)
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
     * sheet. Routes through [MediaRepository.markPlayed]/[MediaRepository.markUnplayed]
     * so the change is applied to the local offline DB AND enqueued into the
     * playback outbox for server sync on reconnect (or pushed immediately when
     * online) — mirroring the unified DetailViewModel.markSeasonPlayed.
     */
    fun markItemPlayed(item: MediaItem, played: Boolean) {
        launch {
            if (played) mediaRepository.markPlayed(item.id)
            else mediaRepository.markUnplayed(item.id)
        }
    }

    /**
     * Toggles favorite for a downloaded item. Routes through
     * [PlayedStateSync.toggleFavorite], so it works fully offline: the flip is
     * applied to the local offline store immediately and staged in the playback
     * outbox for delivery on reconnect.
     */
    fun toggleFavorite(item: MediaItem) {
        launch { mediaRepository.toggleFavorite(item.id) }
    }

    /**
     * Deletes a downloaded item from the long-press quick-action sheet. Routes by
     * media type: a series deletes the whole series download; anything else
     * deletes the single item. The reactive [offlineLibrary] flow refreshes on
     * its own once the row is gone.
     */
    fun delete(item: MediaItem) {
        launch {
            if (item.mediaType == MediaType.SERIES) {
                offlineRepository.deleteOfflineSeries(item.id)
            } else {
                offlineRepository.deleteOfflineItem(item.id)
            }
        }
    }
}
