package com.raulshma.jellyplay.feature.music.artists

import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

enum class ArtistSortOption(val label: String, val sortBy: String) {
    NAME("Name", "SortName"),
    DATE_ADDED("Date Added", "DateCreated"),
    DATE_PLAYED("Date Played", "DatePlayed"),
    RANDOM("Random", "Random"),
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : JellyPlayViewModel() {

    private val _selectedSort = composeState(ArtistSortOption.NAME)
    val selectedSort: ArtistSortOption get() = _selectedSort.value

    private val sortFlow = MutableStateFlow(_selectedSort.value)

    val artists: Flow<PagingData<MediaItem>> = sortFlow.flatMapLatest { sort ->
        mediaRepository.getMediaItemsPaged(
            mediaTypes = listOf(MediaType.ARTIST),
            sortBy = sort.sortBy,
        )
    }.cachedIn(scope)

    fun setSort(sort: ArtistSortOption) {
        _selectedSort.value = sort
        sortFlow.value = sort
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 300)
}
