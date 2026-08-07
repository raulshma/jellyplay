package com.raulshma.jellyplay.feature.library

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

enum class PhotoSortOption(val option: SortOption, val displayName: String) {
    DATE_ADDED(SortOption.DATE_ADDED, "Recently Added"),
    NAME(SortOption.SORT_NAME, "Name"),
    DATE_TAKEN(SortOption.PREMIERE_DATE, "Date Taken"),
}

@HiltViewModel
class PhotoAlbumViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
) : JellyPlayViewModel() {

    private val _parentId = stateFlow<String?>(null)
    private val _sortOption = stateFlow(PhotoSortOption.DATE_ADDED)

    var scrollPosition: Pair<Int, Int> = 0 to 0
        private set

    fun saveScrollPosition(index: Int, offset: Int) {
        scrollPosition = index to offset
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedItems: Flow<PagingData<MediaItem>> = combine(_parentId.flow, _sortOption.flow) { parentId, sort ->
        parentId to sort
    }.flatMapLatest { (parentId, sort) ->
        mediaRepository.getMediaItemsPaged(
            parentId = parentId,
            mediaTypes = listOf(MediaType.PHOTO),
            sortBy = sort.option.apiValue,
            sortOrder = sort.option.sortOrder,
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
        imageUrlProvider.getImageUrl(itemId, maxWidth = maxWidth)
}
