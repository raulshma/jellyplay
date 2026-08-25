package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.ResyncResult

/**
 * UI-facing resync / re-download status published by [ResyncActions.state] and
 * surfaced inline on the detail screen (freshness banner + resync sheet).
 *
 * Mirrors the shape previously defined at the bottom of
 * `feature/downloads/.../OfflineDetailViewModel.kt` so the unified detail screen
 * can render the same inline progress affordance. The status is reset to
 * [Idle] via [ResyncActions.clearResyncState].
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
