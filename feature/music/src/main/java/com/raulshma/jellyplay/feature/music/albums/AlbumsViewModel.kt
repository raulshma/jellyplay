package com.raulshma.jellyplay.feature.music.albums

import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

enum class AlbumSortOption(val label: String, val sortBy: String) {
    NAME("Name", "SortName"),
    DATE_ADDED("Date Added", "DateCreated"),
    DATE_PLAYED("Date Played", "DatePlayed"),
    RANDOM("Random", "Random"),
    YEAR("Year", "ProductionYear"),
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
) : JellyPlayViewModel() {

    private val _selectedSort = composeState(AlbumSortOption.NAME)
    val selectedSort: AlbumSortOption get() = _selectedSort.value

    private val sortFlow = MutableStateFlow(_selectedSort.value)

    val albums: Flow<PagingData<MediaItem>> = sortFlow.flatMapLatest { sort ->
        mediaRepository.getMediaItemsPaged(
            mediaTypes = listOf(MediaType.ALBUM),
            sortBy = sort.sortBy,
        )
    }.cachedIn(scope)

    fun setSort(sort: AlbumSortOption) {
        _selectedSort.value = sort
        sortFlow.value = sort
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH)
}
