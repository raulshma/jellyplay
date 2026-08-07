package com.raulshma.jellyplay.widget.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.LibraryRecommendationsSource
import com.raulshma.jellyplay.core.model.SeerrWidgetSource
import com.raulshma.jellyplay.core.model.WidgetConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [WidgetConfigActivity] with the current per-widget source
 * selection. The activity is parameterized by [WidgetKind] (passed via
 * the intent) so the same VM can drive both the library and Seerr
 * config screens.
 *
 * Supports per-widget configuration via [appWidgetId]. Each widget
 * instance can have its own independent configuration.
 */
@HiltViewModel
class WidgetConfigViewModel @Inject constructor(
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

    fun selectLibrarySource(source: LibraryRecommendationsSource) {
        viewModelScope.launch {
            val current = getWidgetConfig().first()
            val updated = current.copy(librarySource = source)
            if (appWidgetId != -1) {
                widgetDataStore.setWidgetConfigForId(appWidgetId, updated)
            } else {
                widgetDataStore.setWidgetConfig(updated)
            }
        }
    }

    fun selectSeerrSource(source: SeerrWidgetSource) {
        viewModelScope.launch {
            val current = getWidgetConfig().first()
            val updated = current.copy(seerrSource = source)
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
    ;
}
