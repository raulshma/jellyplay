package com.raulshma.jellyplay.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class StudioDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
) : JellyPlayViewModel() {

    private val studioId: String = savedStateHandle[Route.StudioDetail::studioId.name] ?: ""
    private val studioName: String = savedStateHandle[Route.StudioDetail::studioName.name] ?: ""

    val items: Flow<PagingData<MediaItem>> = mediaRepository.getMediaItemsPaged(
        filters = com.raulshma.jellyplay.core.model.LibraryFilters(
            sortBy = com.raulshma.jellyplay.core.model.SortOption.SORT_NAME,
        ),
        studioIds = listOf(studioId),
    ).cachedIn(scope)

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    /**
     * Marks the item played/unplayed on the server. Intentionally
     * silent: the paged grid is left untouched so the user keeps their scroll
     * position — the badge updates on the next natural data refresh.
     */
    fun markItemPlayed(item: MediaItem, played: Boolean) {
        launch {
            if (played) mediaRepository.markPlayed(item.id) else mediaRepository.markUnplayed(item.id)
        }
    }
}
