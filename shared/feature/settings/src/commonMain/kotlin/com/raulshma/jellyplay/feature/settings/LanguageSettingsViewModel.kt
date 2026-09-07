package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.LanguagePreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.StateFlow

class LanguageSettingsViewModel(
    private val appLocaleSetter: AppLocaleSetter,
    private val store: UserPreferencesStore,
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    private val advancedSettings: AdvancedSettingsGate,
    private val editor: PreferencesEditor,
) : JellyPlayViewModel() {

    /** Language/subtitle-screen slice — recomposes this screen only on its field writes. */
    val preferences: StateFlow<LanguagePreferences> = projections.languagePreferences

    val showAdvancedSettings: StateFlow<Boolean> = advancedSettings.showAdvancedSettings

    fun setShowAdvancedSettings(enabled: Boolean) = advancedSettings.setShowAdvancedSettings(enabled)

    /**
     * Single write command for this screen: `edit { it.subtitle.setSubtitleStyle(style) }`.
     * Fire-and-forget on the same application scope [PreferencesEditor.edit] uses.
     */
    fun edit(transform: suspend (PreferencesEditScope) -> Unit) = editor.edit { transform(this) }

    fun setAppLanguage(language: String?) {
        launch {
            editor.edit { subtitle.setAppLanguage(language) }
            appLocaleSetter.setAppLocale(language)
        }
    }
}
