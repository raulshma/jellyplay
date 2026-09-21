package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import com.raulshma.jellyplay.core.model.NavigationCustomizationPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.StateFlow

class AppearanceSettingsViewModel(
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    advancedSettings: AdvancedSettingsGate,
    editor: PreferencesEditor,
) : SettingsSectionViewModel(advancedSettings, editor) {

    /** Appearance-screen slice — recomposes this screen only on appearance-field writes. */
    val preferences: StateFlow<AppearanceScreenPreferences> = projections.appearanceScreenPreferences

    /** Navigation-customization slice, consumed by the embedded `NavigationCustomizationGroup`. */
    val navigationCustomizationPreferences: StateFlow<NavigationCustomizationPreferences> =
        projections.navigationCustomizationPreferences
}
