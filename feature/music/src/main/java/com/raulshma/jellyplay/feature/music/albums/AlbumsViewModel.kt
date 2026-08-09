package com.raulshma.jellyplay.feature.music.albums

import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.music.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/**
 * Sort options shared across the music collection screens (albums tab,
 * artists tab, tracks tab, and the standalone Albums screen). Each entry
 * pairs a Jellyfin [SortOption] with its localized label resource so the
 * same enum drives both the query and the menu UI.
 */
enum class MusicSortOption(val option: SortOption, val labelRes: Int) {
    NAME(SortOption.SORT_NAME, R.string.music_sort_name),
    DATE_ADDED(SortOption.DATE_ADDED, R.string.music_sort_date_added),
    DATE_PLAYED(SortOption.DATE_PLAYED, R.string.music_sort_date_played),
    RANDOM(SortOption.RANDOM, R.string.music_sort_random),
    YEAR(SortOption.YEAR_DESC, R.string.music_sort_year),
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
) : JellyPlayViewModel() {

    private val sortFlow = MutableStateFlow(MusicSortOption.NAME)

    val selectedSort: StateFlow<MusicSortOption> = sortFlow.asStateFlow()

    val albums: Flow<PagingData<MediaItem>> = sortFlow.flatMapLatest { sort ->
        mediaRepository.getMediaItemsPaged(
            filters = com.raulshma.jellyplay.core.model.LibraryFilters(
                mediaTypes = listOf(MediaType.ALBUM),
                sortBy = sort.option,
            ),
        )
    }.cachedIn(scope)

    fun setSort(sort: MusicSortOption) {
        sortFlow.value = sort
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH)
}
