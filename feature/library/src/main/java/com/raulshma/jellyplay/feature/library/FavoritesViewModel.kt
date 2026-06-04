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
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : JellyPlayViewModel() {

    private val _mediaTypeFilter = stateFlow<MediaType?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedItems: Flow<PagingData<MediaItem>> = _mediaTypeFilter.flow.flatMapLatest { type ->
        mediaRepository.getFavoritesPaged(
            mediaTypes = type?.let { listOf(it) },
        )
    }.cachedIn(scope)

    val mediaTypeFilter = _mediaTypeFilter.flow

    fun setMediaTypeFilter(type: MediaType?) {
        _mediaTypeFilter.set(type)
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)
}
