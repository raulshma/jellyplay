package com.raulshma.jellyplay.widget.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.LibraryRecommendationsSource
import com.raulshma.jellyplay.core.model.SeerrWidgetSource
import com.raulshma.jellyplay.core.model.WidgetConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [WidgetConfigActivity] with the current per-widget source
 * selection. The activity is parameterized by [WidgetKind] (passed via
 * the intent) so the same VM can drive both the library and Seerr
 * config screens.
 */
@HiltViewModel
class WidgetConfigViewModel @Inject constructor(
    private val userPreferencesStore: UserPreferencesStore,
) : ViewModel() {

    val state: StateFlow<WidgetConfig> = userPreferencesStore.widgetConfig
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            WidgetConfig(),
        )

    fun selectLibrarySource(source: LibraryRecommendationsSource) {
        viewModelScope.launch {
            val current = userPreferencesStore.widgetConfig.first()
            userPreferencesStore.setWidgetConfig(current.copy(librarySource = source))
        }
    }

    fun selectSeerrSource(source: SeerrWidgetSource) {
        viewModelScope.launch {
            val current = userPreferencesStore.widgetConfig.first()
            userPreferencesStore.setWidgetConfig(current.copy(seerrSource = source))
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
