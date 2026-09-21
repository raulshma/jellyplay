package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.ExperimentalPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.StateFlow

class ExperimentalSettingsViewModel(
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    advancedSettings: AdvancedSettingsGate,
    editor: PreferencesEditor,
) : SettingsSectionViewModel(advancedSettings, editor) {

    /** Experimental-screen slice — recomposes this screen only when enabled features change. */
    val preferences: StateFlow<ExperimentalPreferences> = projections.experimentalPreferences

    fun setExperimentalFeatureEnabled(feature: ExperimentalFeature, enabled: Boolean) {
        val current = preferences.value.enabledExperimentalFeatures
        val updated = if (enabled) current + feature else current - feature
        editor.edit { experimental.setEnabledExperimentalFeatures(updated) }
    }
}
