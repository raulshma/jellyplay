package com.raulshma.jellyplay.widget.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.LibraryRecommendationsSource
import com.raulshma.jellyplay.core.model.SeerrWidgetSource
import com.raulshma.jellyplay.core.model.WidgetConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs [WidgetConfigActivity] with the current per-widget source
 * selection. The activity is parameterized by [WidgetKind] (passed via
 * the intent) so the same VM can drive both the library and Seerr
 * config screens.
 *
 * Supports per-widget configuration via [appWidgetId]. Each widget
 * instance can have its own independent configuration.
 */
class WidgetConfigViewModel(
    private val widgetDataStore: WidgetDataStore,
) : ViewModel() {

    private var appWidgetId: Int = -1

    val state: StateFlow<WidgetConfig> = widgetDataStore.widgetConfig
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            WidgetConfig(),
        )

    /**
     * Initialize the ViewModel with the specific widget ID for per-widget config.
     * Must be called before any state is collected.
     */
    fun initWidgetId(widgetId: Int) {
        appWidgetId = widgetId
    }

    /**
     * Returns a StateFlow for the specific widget's configuration.
     * Falls back to the global config if no per-widget config exists.
     */
    fun getWidgetConfig(): StateFlow<WidgetConfig> {
        return if (appWidgetId != -1) {
            widgetDataStore.getWidgetConfigForId(appWidgetId)
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    WidgetConfig(),
                )
        } else {
            state
        }
    }

    fun selectLibrarySource(source: LibraryRecommendationsSource) =
        update { it.copy(librarySource = source) }

    fun selectSeerrSource(source: SeerrWidgetSource) =
        update { it.copy(seerrSource = source) }

    fun selectContinueWatchingCount(count: Int) = update {
        it.copy(
            continueWatchingItemCount = count.coerceIn(
                WidgetConfig.MIN_CONTINUE_WATCHING_ITEM_COUNT,
                WidgetConfig.MAX_CONTINUE_WATCHING_ITEM_COUNT,
            ),
        )
    }

    fun setNowPlayingShowArtwork(show: Boolean) =
        update { it.copy(nowPlayingShowArtwork = show) }

    fun setNowPlayingShowProgress(show: Boolean) =
        update { it.copy(nowPlayingShowProgress = show) }

    /**
     * One home for the read-copy-write + per-widget-or-global persist shape that
     * every setter above used to duplicate. Routes the updated [WidgetConfig]
     * to the per-instance store when [appWidgetId] is set, otherwise to the
     * global default.
     */
    private fun update(transform: (WidgetConfig) -> WidgetConfig) {
        viewModelScope.launch {
            val updated = getWidgetConfig().first().let(transform)
            if (appWidgetId != -1) {
                widgetDataStore.setWidgetConfigForId(appWidgetId, updated)
            } else {
                widgetDataStore.setWidgetConfig(updated)
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

enum class WidgetKind {
    LIBRARY,
    SEERR,
    CONTINUE_WATCHING,
    NOW_PLAYING,
    ;
}
