package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.ExperimentalPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ExperimentalSettingsViewModel @Inject constructor(
    private val store: UserPreferencesStore,
    private val editor: PreferencesEditor,
) : JellyPlayViewModel() {

    /** Experimental-screen slice — recomposes this screen only when enabled features change. */
    val preferences: StateFlow<ExperimentalPreferences> = store.experimentalPreferences

    val showAdvancedSettings: StateFlow<Boolean> = store.preferences
        .map { it.showAdvancedSettings }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    fun setShowAdvancedSettings(enabled: Boolean) =
        editor.edit { setShowAdvancedSettings(enabled) }

    fun setExperimentalFeatureEnabled(feature: ExperimentalFeature, enabled: Boolean) {
        val current = preferences.value.enabledExperimentalFeatures
        val updated = if (enabled) current + feature else current - feature
        editor.edit { setEnabledExperimentalFeatures(updated) }
    }
}
