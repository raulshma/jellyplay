package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.PersonRef

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
        /**
         * itemId → the person's own credited entry, precomputed by the
         * ViewModel off the filmography's projected People. Empty when the
         * fetch carried no role data — the cards and the group chip row
         * degrade to today's render.
         */
        val filmographyCredits: Map<String, PersonInfo> = emptyMap(),
        /** The "frequently appears with" row — self excluded, capped, server order. */
        val costars: List<PersonRef> = emptyList(),
        val biography: String? = null,
        val profileImageUrl: String? = null,
    ) : PersonDetailUiState
    data class Error(val message: String) : PersonDetailUiState
}
