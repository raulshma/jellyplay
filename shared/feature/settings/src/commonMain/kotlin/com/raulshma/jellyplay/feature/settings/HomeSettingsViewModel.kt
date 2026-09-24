package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.model.HomeScreenPreferences
import kotlinx.coroutines.flow.StateFlow

class HomeSettingsViewModel(
    projections: PreferenceProjections,
    advancedSettings: AdvancedSettingsGate,
    editor: PreferencesEditor,
) : SettingsSectionViewModel(advancedSettings, editor) {

    /** Home-screen slice — recomposes this screen only on home-discovery writes. */
    val preferences: StateFlow<HomeScreenPreferences> = projections.homeScreenPreferences
}
