package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
) : JellyPlayViewModel() {

    private val _uiState = MutableStateFlow<CollectionDetailUiState>(CollectionDetailUiState.Loading)
    val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()

    fun loadCollection(collectionId: String) {
        _uiState.value = CollectionDetailUiState.Loading
        launch {
            coroutineScope {
                val detailDeferred = async { mediaRepository.getMediaDetail(collectionId) }
                val itemsDeferred = async { mediaRepository.getCollectionItems(collectionId, limit = 100) }

                val detailResult = detailDeferred.await()
                val itemsResult = itemsDeferred.await()

                val failure = detailResult.exceptionOrNull() ?: itemsResult.exceptionOrNull()
                _uiState.value = if (failure == null) {
                    CollectionDetailUiState.Success(
                        detail = detailResult.getOrThrow(),
                        items = itemsResult.getOrThrow().items,
                    )
                } else {
                    CollectionDetailUiState.Error(failure.message ?: "Failed to load collection")
                }
            }
        }
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    fun getBackdropUrl(itemId: String): String =
        imageUrlProvider.getBackdropUrl(itemId)
}
