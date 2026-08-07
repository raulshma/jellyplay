package com.raulshma.jellyplay.feature.music.albums

import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

enum class AlbumSortOption(val option: SortOption, val label: String) {
    NAME(SortOption.SORT_NAME, "Name"),
    DATE_ADDED(SortOption.DATE_ADDED, "Date Added"),
    DATE_PLAYED(SortOption.DATE_PLAYED, "Date Played"),
    RANDOM(SortOption.RANDOM, "Random"),
    YEAR(SortOption.YEAR_DESC, "Year"),
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
) : JellyPlayViewModel() {

    private val sortFlow = MutableStateFlow(AlbumSortOption.NAME)

    val selectedSort: StateFlow<AlbumSortOption> = sortFlow.asStateFlow()

    val albums: Flow<PagingData<MediaItem>> = sortFlow.flatMapLatest { sort ->
        mediaRepository.getMediaItemsPaged(
            mediaTypes = listOf(MediaType.ALBUM),
            sortBy = sort.option.apiValue,
            sortOrder = sort.option.sortOrder,
        )
    }.cachedIn(scope)

    fun setSort(sort: AlbumSortOption) {
        sortFlow.value = sort
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH)
}
