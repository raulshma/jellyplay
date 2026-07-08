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
        studioIds = listOf(studioId),
        sortBy = "SortName",
    ).cachedIn(scope)

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)
}
