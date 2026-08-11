package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.ResyncResult

/**
 * UI-facing resync / re-download status surfaced on [DetailUiState.resyncState].
 *
 * Mirrors the shape previously defined at the bottom of
 * `feature/downloads/.../OfflineDetailViewModel.kt` so the unified detail screen
 * can render the same inline progress affordance. The states are owned by
 * [DetailViewModel] (which now hosts the resync / re-download actions); the
 * status is reset to [Idle] via [com.raulshma.jellyplay.feature.details.DetailViewModel.clearResyncState].
 *
 * The [Done] / [Error] result is distinct from the [com.raulshma.jellyplay.core.data.repository.DetailLoadState]
 * stream: resync/re-download is a user-initiated action whose outcome is shown
 * once and then cleared, not part of the snapshot load state.
 */
@Immutable
sealed interface ResyncUiState {
    data object Idle : ResyncUiState
    data object Working : ResyncUiState
    data class Done(val result: ResyncResult) : ResyncUiState
    data class Error(val message: String) : ResyncUiState
}
