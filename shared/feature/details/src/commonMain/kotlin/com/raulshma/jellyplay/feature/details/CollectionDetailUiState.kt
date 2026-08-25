package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem

/**
 * UI state for the collection-detail screen.
 *
 * Three-state sealed model mirroring [MediaInfoUiState]: avoids the nullable
 * field reads the former `composeState`-per-field implementation forced on the
 * screen.
 */
@Immutable
sealed interface CollectionDetailUiState {
    data object Loading : CollectionDetailUiState
    data class Success(
        val detail: MediaDetail,
        val items: List<MediaItem>,
    ) : CollectionDetailUiState
    data class Error(val message: String) : CollectionDetailUiState
}
