package com.raulshma.jellyplay.feature.downloads

import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
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
enum class OfflineLibrarySort(val label: String) {
    RECENT("Recent"),
    NAME("Name (A–Z)"),
    RATING("Rating"),
    SIZE("Size"),
}

/** Coarse media-type filter for the library tabs. */
enum class OfflineLibraryFilter(val label: String) {
    ALL("All"),
    VIDEOS("Videos"),
    MUSIC("Music"),
}

data class StorageSummary(val totalBytes: Long, val itemCount: Int)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class OfflineLibraryViewModel @Inject constructor(
    private val offlineRepository: OfflineRepository,
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
            OfflineLibrarySort.NAME -> matched.sortedBy { it.name.lowercase() }
            OfflineLibrarySort.RATING -> matched.sortedByDescending { it.communityRating ?: -1f }
            OfflineLibrarySort.SIZE -> matched.sortedByDescending { it.totalSizeBytes }
        }
    }
}
