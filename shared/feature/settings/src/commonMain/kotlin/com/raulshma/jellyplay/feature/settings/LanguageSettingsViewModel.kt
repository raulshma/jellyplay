package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.model.LanguagePreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.StateFlow

class LanguageSettingsViewModel(
    private val appLocaleSetter: AppLocaleSetter,
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    advancedSettings: AdvancedSettingsGate,
    editor: PreferencesEditor,
) : SettingsSectionViewModel(advancedSettings, editor) {

    /** Language/subtitle-screen slice — recomposes this screen only on its field writes. */
    val preferences: StateFlow<LanguagePreferences> = projections.languagePreferences

    fun setAppLanguage(language: String?) {
        launch {
            editor.edit { subtitle.setAppLanguage(language) }
            appLocaleSetter.setAppLocale(language)
        }
    }
}
