package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.ExperimentalPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.StateFlow

class ExperimentalSettingsViewModel(
    private val store: UserPreferencesStore,
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    private val appearanceStore: com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore,
    private val editor: PreferencesEditor,
) : JellyPlayViewModel() {

    /** Experimental-screen slice — recomposes this screen only when enabled features change. */
    val preferences: StateFlow<ExperimentalPreferences> = projections.experimentalPreferences

    private val advancedSettings = AdvancedSettingsGate(appearanceStore, editor)

    val showAdvancedSettings: StateFlow<Boolean> = advancedSettings.showAdvancedSettings

    fun setShowAdvancedSettings(enabled: Boolean) = advancedSettings.setShowAdvancedSettings(enabled)

    fun setExperimentalFeatureEnabled(feature: ExperimentalFeature, enabled: Boolean) {
        val current = preferences.value.enabledExperimentalFeatures
        val updated = if (enabled) current + feature else current - feature
        editor.edit { experimental.setEnabledExperimentalFeatures(updated) }
    }
}
