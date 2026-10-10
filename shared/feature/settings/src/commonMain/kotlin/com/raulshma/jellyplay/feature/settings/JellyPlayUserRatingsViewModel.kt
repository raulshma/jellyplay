package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.data.session.isAvailableNowOrProbe
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.network.api.JellyPlayUserDataRoutes
import com.raulshma.jellyplay.core.network.api.JellyPlayUserRating
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The "My ratings" screen's filter tabs — each maps 1:1 onto the plugin's
 * `userratings/mine?filter=` wire values (the contract's
 * `likes | dislikes | rated`).
 */
enum class JellyPlayUserRatingsFilter(val wire: String) {
    Likes("likes"),
    Dislikes("dislikes"),
    Rated("rated"),
}

/**
 * The screen's state: the loaded rows for the selected filter, plus the
 * in-flight flag (defaults true — the first composition must not flash the
 * empty-state text before the open-fetch lands).
 */
@Immutable
data class JellyPlayUserRatingsState(
    val filter: JellyPlayUserRatingsFilter = JellyPlayUserRatingsFilter.Likes,
    val ratings: List<JellyPlayUserRating> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * The companion-plugin "My ratings" screen's model (ADR 0010): the user's
 * synced likes / dislikes / ratings ([JellyPlayPluginFeatures.UserRatings]),
 * one fetch per filter tab.
 *
 * The screen is reachable ONLY through the settings root's capability-gated
 * entry (plugin probe AVAILABLE + the `user-ratings` feature key + the user's
 * per-feature toggle), but the api calls are STILL feature-gated here — the
 * gate is re-checked at every call, so the api client is never touched without
 * it (ADR 0010's gating rule; the capabilities contract can degrade
 * mid-session). The gate is the ONE seam ([JellyPlayFeatureGate.isAvailableNow]
 * — probe AND toggle); without the gate seam (direct-construction tests) the
 * probe-only read keeps the pre-toggle behavior.
 */
class JellyPlayUserRatingsViewModel(
    private val pluginApiClient: JellyPlayUserDataRoutes,
    private val statusStore: JellyPlayPluginStatusStore,
    private val featureGate: JellyPlayFeatureGate? = null,
) : JellyPlayViewModel() {

    private val _uiState = MutableStateFlow(JellyPlayUserRatingsState())
    val uiState: StateFlow<JellyPlayUserRatingsState> = _uiState.asStateFlow()

    /** Selects a filter tab and (re)loads its rows. Same-filter reselect refetches. */
    fun selectFilter(filter: JellyPlayUserRatingsFilter) {
        _uiState.value = JellyPlayUserRatingsState(filter = filter, isLoading = true)
        refresh()
    }

    /**
     * Reloads the selected filter's rows (the screen fires one per tab select
     * plus one on open). Failure keeps the empty list — the plugin contract's
     * silent-absence rule; a degraded gate simply never fetches.
     */
    fun refresh() {
        scope.launch {
            if (!ratingsGateOpen()) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            val filter = _uiState.value.filter
            pluginApiClient.getUserRatings(filter.wire)
                .onSuccess { ratings ->
                    // Stale-response guard: only the selected filter's fetch may land.
                    if (_uiState.value.filter != filter) return@onSuccess
                    _uiState.update { it.copy(ratings = ratings, isLoading = false) }
                }
                .onFailure {
                    if (_uiState.value.filter != filter) return@onFailure
                    _uiState.update { it.copy(ratings = emptyList(), isLoading = false) }
                }
        }
    }

    /** The ONE gate seam, or the probe-only fallback when the seam is unwired. */
    private suspend fun ratingsGateOpen(): Boolean =
        featureGate.isAvailableNowOrProbe(statusStore, JellyPlayPluginFeatures.UserRatings)
}
