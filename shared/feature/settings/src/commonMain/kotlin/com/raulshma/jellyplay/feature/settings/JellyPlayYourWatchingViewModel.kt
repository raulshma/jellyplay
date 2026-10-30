package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.data.session.isAvailableNowOrProbe
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.network.api.JellyPlayMyAnalytics
import com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsRoutes
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The "Your watching" screen's window chips — each maps 1:1 onto the
 * analytics `days` query parameter (the server clamps to 1..365).
 */
enum class JellyPlayYourWatchingWindow(val days: Int) {
    Seven(7),
    Thirty(30),
    Ninety(90),
}

/**
 * The screen's state: the selected window plus its aggregates. [analytics]
 * null AFTER a completed load = the quiet "not available" degrade (the
 * server's plugin predates the per-user analytics face or the `analytics`
 * key is absent — 404), never an error. [isLoading] defaults true so the
 * screen's first composition cannot flash the degrade before the open-fetch
 * lands (the SyncUiState pattern).
 */
@Immutable
data class JellyPlayYourWatchingState(
    val window: JellyPlayYourWatchingWindow = JellyPlayYourWatchingWindow.Thirty,
    val analytics: JellyPlayMyAnalytics? = null,
    val isLoading: Boolean = true,
)

/**
 * The companion-plugin "Your watching" screen's model: the signed-in user's
 * own play aggregates ([JellyPlayPluginFeatures.Analytics], the per-user face
 * of the admin analytics — any user may read it), one fetch per window chip.
 *
 * The screen is reachable ONLY through the settings root's capability-gated
 * entry (plugin probe AVAILABLE + the `analytics` feature key + the user's
 * per-feature toggle), but the api calls are STILL feature-gated here — the
 * gate is re-checked at every call, so the api client is never touched without
 * it (the plugin-gating contract; the capabilities contract can degrade
 * mid-session). The gate is the ONE seam ([JellyPlayFeatureGate.isAvailableNow]
 * — probe AND toggle); without the gate seam (direct-construction tests) the
 * probe-only read keeps the pre-toggle behavior.
 */
class JellyPlayYourWatchingViewModel(
    private val pluginApiClient: JellyPlayAnalyticsRoutes,
    private val statusStore: JellyPlayPluginStatusStore,
    private val featureGate: JellyPlayFeatureGate? = null,
) : JellyPlayViewModel() {

    private val _uiState = MutableStateFlow(JellyPlayYourWatchingState())
    val uiState: StateFlow<JellyPlayYourWatchingState> = _uiState.asStateFlow()

    /**
     * The tab dance's one holder: the gate, the stale-response guard and the
     * quiet-absence fold live there; this VM keeps only the window query and
     * the state shape.
     */
    private val tabLoader = PluginTabLoader(
        scope = scope,
        gateOpen = { featureGate.isAvailableNowOrProbe(statusStore, JellyPlayPluginFeatures.Analytics) },
        selected = { _uiState.value.window },
        fetch = { window -> pluginApiClient.getMyAnalytics(window.days) },
        isCurrent = { window -> _uiState.value.window == window },
        land = { analytics -> _uiState.update { it.copy(analytics = analytics, isLoading = false) } },
        quiet = null,
        onGateClosed = { _uiState.update { it.copy(isLoading = false) } },
    )

    /** Selects a window chip and (re)loads its aggregates. Same-window reselect refetches. */
    fun selectWindow(window: JellyPlayYourWatchingWindow) {
        _uiState.value = JellyPlayYourWatchingState(window = window, isLoading = true)
        refresh()
    }

    /**
     * Reloads the selected window's aggregates (the screen fires one per chip
     * select plus one on open). Failure AND a 404 both degrade to
     * the quiet null — the screen's "not available" state; a degraded gate
     * simply never fetches.
     */
    fun refresh() {
        tabLoader.refresh()
    }
}
