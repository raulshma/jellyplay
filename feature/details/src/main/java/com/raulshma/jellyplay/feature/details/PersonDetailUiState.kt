package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.MediaItem

/**
 * UI state for the person-detail screen.
 *
 * Three-state sealed model mirroring [MediaInfoUiState]: avoids the nullable
 * field reads (`viewModel.error!!`, separate `isLoading` flag) the former
 * `composeState`-per-field implementation forced on the screen.
 */
@Immutable
sealed interface PersonDetailUiState {
    data object Loading : PersonDetailUiState
    data class Success(
        val name: String,
        val filmography: List<MediaItem>,
        val biography: String? = null,
        val profileImageUrl: String? = null,
    ) : PersonDetailUiState
    data class Error(val message: String) : PersonDetailUiState
}
