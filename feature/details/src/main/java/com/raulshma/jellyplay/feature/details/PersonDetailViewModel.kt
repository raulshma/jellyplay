package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.network.RetryPolicy
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
) : JellyPlayViewModel() {

    private val _uiState = MutableStateFlow<PersonDetailUiState>(PersonDetailUiState.Loading)
    val uiState: StateFlow<PersonDetailUiState> = _uiState.asStateFlow()

    fun loadPerson(personId: String) {
        _uiState.value = PersonDetailUiState.Loading
        launch {
            coroutineScope {
                val detailDeferred = async {
                    RetryPolicy.executeWithRetry { mediaRepository.getMediaDetail(personId) }
                }
                val itemsDeferred = async {
                    RetryPolicy.executeWithRetry { mediaRepository.getItemsByPerson(personId) }
                }

                val detailResult = detailDeferred.await()
                val itemsResult = itemsDeferred.await()

                _uiState.value = if (detailResult.isSuccess && itemsResult.isSuccess) {
                    PersonDetailUiState.Success(
                        name = detailResult.getOrThrow().item.name,
                        filmography = itemsResult.getOrThrow(),
                    )
                } else {
                    val detailError = detailResult.exceptionOrNull()?.message
                    val itemsError = itemsResult.exceptionOrNull()?.message
                    PersonDetailUiState.Error(itemsError ?: detailError ?: "Failed to load")
                }
            }
        }
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)
}
