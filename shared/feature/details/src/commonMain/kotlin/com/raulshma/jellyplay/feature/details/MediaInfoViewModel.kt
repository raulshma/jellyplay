package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the technical-info screen.
 *
 * A three-state sealed model avoids the nullable `detail!!` assertions the
 * former implementation used when it shared `DetailViewModel`.
 */
@Immutable
sealed interface MediaInfoUiState {
    data object Loading : MediaInfoUiState
    data class Success(val detail: MediaDetail) : MediaInfoUiState
    data class Error(val message: String) : MediaInfoUiState
}

/**
 * Dedicated ViewModel for the technical-info screen.
 *
 * Decoupled from [DetailViewModel] — injects only [MediaRepository] so the
 * technical-info screen no longer pulls in the detail screen's download /
 * Seerr / *arr orchestration graph.
 */
class MediaInfoViewModel constructor(
    private val mediaRepository: MediaRepository,
) : JellyPlayViewModel() {

    private val _uiState = MutableStateFlow<MediaInfoUiState>(MediaInfoUiState.Loading)
    val uiState: StateFlow<MediaInfoUiState> = _uiState.asStateFlow()

    fun load(itemId: String) {
        _uiState.value = MediaInfoUiState.Loading
        launch {
            mediaRepository.getMediaDetail(itemId)
                .onSuccess { detail -> _uiState.value = MediaInfoUiState.Success(detail) }
                .onFailure { err ->
                    _uiState.value = MediaInfoUiState.Error(err.message ?: "Failed to load media info")
                }
        }
    }
}
