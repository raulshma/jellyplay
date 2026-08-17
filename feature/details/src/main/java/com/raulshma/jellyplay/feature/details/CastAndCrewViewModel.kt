package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Loads + partitions the full cast & crew for an item. Reuses the same cached
 * `getMediaDetail` path the detail screen warms (so opening Cast & Crew from a
 * loaded detail screen is usually a cache hit) and splits the flat `people`
 * list via [partitionCastAndCrew]. Mirrors [PersonDetailViewModel].
 */
@HiltViewModel
class CastAndCrewViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
) : JellyPlayViewModel() {

    private val _uiState = MutableStateFlow<CastAndCrewUiState>(CastAndCrewUiState.Loading)
    val uiState: StateFlow<CastAndCrewUiState> = _uiState.asStateFlow()

    fun load(itemId: String) {
        _uiState.value = CastAndCrewUiState.Loading
        launch {
            // No feature-level retry: the repository path already retries
            // (and coordinates retry with address failover) in the engine.
            val result = mediaRepository.getMediaDetail(itemId)
            _uiState.value = result.fold(
                onSuccess = { detail ->
                    val partition = partitionCastAndCrew(detail.people)
                    CastAndCrewUiState.Success(
                        title = detail.item.name,
                        cast = partition.cast,
                        crew = partition.crew,
                    )
                },
                onFailure = { CastAndCrewUiState.Error(it.message ?: "Failed to load") },
            )
        }
    }

    fun getImageUrl(personId: String): String = imageUrlProvider.getImageUrl(personId)
}
