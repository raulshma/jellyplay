package com.raulshma.jellyplay.feature.music.collection

import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_sort_date_added
import com.raulshma.jellyplay.feature.music.generated.resources.music_sort_date_played
import com.raulshma.jellyplay.feature.music.generated.resources.music_sort_name
import com.raulshma.jellyplay.feature.music.generated.resources.music_sort_random
import com.raulshma.jellyplay.feature.music.generated.resources.music_sort_year
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import org.jetbrains.compose.resources.StringResource

/**
 * Sort options shared across the music collection screens (albums tab,
 * artists tab, tracks tab, and the browse screen's per-tab pagers). Each entry
 * pairs a Jellyfin [SortOption] with its localized label resource so the
 * same enum drives both the query and the menu UI.
 */
enum class MusicSortOption(val option: SortOption, val labelRes: StringResource) {
    NAME(SortOption.SORT_NAME, Res.string.music_sort_name),
    DATE_ADDED(SortOption.DATE_ADDED, Res.string.music_sort_date_added),
    DATE_PLAYED(SortOption.DATE_PLAYED, Res.string.music_sort_date_played),
    RANDOM(SortOption.RANDOM, Res.string.music_sort_random),
    YEAR(SortOption.YEAR_DESC, Res.string.music_sort_year),
}

/**
 * ONE sorted paged music collection, written once for every screen listing
 * artists, albums or tracks (the tab ViewModels and the browse screen's three
 * pagers). Owns the whole "sort → paged query" seam: the sort [StateFlow],
 * [setSort], and the paged [items] flow that re-queries
 * [MediaRepository.getMediaItemsPaged] with `LibraryFilters(mediaTypes =
 * listOf([mediaType]), sortBy = …)` whenever the sort changes, cached in
 * [scope]. The owning ViewModel is a thin adapter exposing [selectedSort] and
 * [items] under its own names.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SortedPagedCollection(
    private val mediaRepository: MediaRepository,
    scope: CoroutineScope,
    private val mediaType: MediaType,
    initialSort: MusicSortOption = MusicSortOption.NAME,
) {

    private val sortFlow = MutableStateFlow(initialSort)

    /** Currently selected sort — collect as state in screens. */
    val selectedSort: StateFlow<MusicSortOption> = sortFlow.asStateFlow()

    val items: Flow<PagingData<MediaItem>> = sortFlow.flatMapLatest { sort ->
        mediaRepository.getMediaItemsPaged(
            filters = LibraryFilters(
                mediaTypes = listOf(mediaType),
                sortBy = sort.option,
            ),
        )
    }.cachedIn(scope)

    fun setSort(sort: MusicSortOption) {
        sortFlow.value = sort
    }
}
