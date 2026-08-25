package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.PersonInfo

/**
 * UI state for the Cast & Crew screen. Three-state sealed model mirroring
 * [PersonDetailUiState] — avoids nullable field reads and the separate
 * `isLoading` flag the old per-field pattern forced.
 */
@Immutable
sealed interface CastAndCrewUiState {
    data object Loading : CastAndCrewUiState
    data class Success(
        val title: String,
        val cast: List<PersonInfo>,
        val crew: List<PersonInfo>,
    ) : CastAndCrewUiState
    data class Error(val message: String) : CastAndCrewUiState
}
