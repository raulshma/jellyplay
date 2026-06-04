package com.raulshma.jellyplay.feature.music.albums

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
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
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {

    var selectedSort by mutableStateOf(AlbumSortOption.NAME)
        private set

    private val sortFlow = MutableStateFlow(selectedSort)

    val albums: Flow<PagingData<MediaItem>> = sortFlow.flatMapLatest { sort ->
        mediaRepository.getMediaItemsPaged(
            mediaTypes = listOf(MediaType.ALBUM),
            sortBy = sort.sortBy,
        )
    }.cachedIn(viewModelScope)

    fun setSort(sort: AlbumSortOption) {
        selectedSort = sort
        sortFlow.value = sort
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 300)
}
