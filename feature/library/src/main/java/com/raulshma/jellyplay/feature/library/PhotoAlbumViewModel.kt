package com.raulshma.jellyplay.feature.library

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

enum class PhotoSortOption(val displayName: String, val apiValue: String) {
    DATE_ADDED("Recently Added", "DateCreated,SortName"),
    NAME("Name", "SortName"),
    DATE_TAKEN("Date Taken", "PremiereDate,SortName"),
}

@HiltViewModel
class PhotoAlbumViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : JellyPlayViewModel() {

    private val _parentId = stateFlow<String?>(null)
    private val _sortOption = stateFlow(PhotoSortOption.DATE_ADDED)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedItems: Flow<PagingData<MediaItem>> = combine(_parentId.flow, _sortOption.flow) { parentId, sort ->
        parentId to sort
    }.flatMapLatest { (parentId, sort) ->
        mediaRepository.getMediaItemsPaged(
            parentId = parentId,
            mediaTypes = listOf(MediaType.PHOTO),
            sortBy = sort.apiValue,
        )
    }.cachedIn(scope)

    val sortOption = _sortOption.flow

    fun setParentId(parentId: String) {
        _parentId.set(parentId)
    }

    fun setSortOption(option: PhotoSortOption) {
        _sortOption.set(option)
    }

    fun getImageUrl(itemId: String, maxWidth: Int = 400): String =
        playbackRepository.getImageUrl(itemId, maxWidth = maxWidth)
}
