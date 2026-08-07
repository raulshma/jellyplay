package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.network.RetryPolicy
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
                    val detail = detailResult.getOrThrow().item
                    PersonDetailUiState.Success(
                        name = detail.name,
                        filmography = itemsResult.getOrThrow(),
                        biography = detail.overview?.takeIf { it.isNotBlank() },
                        profileImageUrl = imageUrlProvider.getImageUrl(personId).takeIf { it.isNotBlank() },
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

    /**
     * Marks a filmography item played/unplayed on the server
     * and flips it in-place in [PersonDetailUiState.Success.filmography] so the
     * card's badge updates immediately; the next load reconciles the server
     * truth.
     */
    fun markItemPlayed(item: MediaItem, played: Boolean) {
        launch {
            val result = if (played) mediaRepository.markPlayed(item.id)
            else mediaRepository.markUnplayed(item.id)
            result.onSuccess {
                _uiState.update { state ->
                    if (state is PersonDetailUiState.Success) {
                        state.copy(
                            filmography = state.filmography.map {
                                if (it.id == item.id) it.copy(
                                    isPlayed = played,
                                    playbackPositionTicks = 0L,
                                ) else it
                            },
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }
}
