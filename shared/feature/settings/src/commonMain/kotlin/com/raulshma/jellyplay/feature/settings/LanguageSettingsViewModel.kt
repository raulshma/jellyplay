package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.LanguagePreferences
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.StateFlow

class LanguageSettingsViewModel(
    private val appLocaleSetter: AppLocaleSetter,
    private val store: UserPreferencesStore,
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    private val appearanceStore: com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore,
    private val editor: PreferencesEditor,
) : JellyPlayViewModel() {

    /** Language/subtitle-screen slice — recomposes this screen only on its field writes. */
    val preferences: StateFlow<LanguagePreferences> = projections.languagePreferences

    val showAdvancedSettings: StateFlow<Boolean> = appearanceStore.showAdvancedSettings

    fun setShowAdvancedSettings(enabled: Boolean) =
        editor.edit { appearance.setShowAdvancedSettings(enabled) }

    fun setPreferredAudioLanguage(language: String?) =
        editor.edit { subtitle.setPreferredAudioLanguage(language) }

    fun setPreferredSubtitleLanguage(language: String?) = editor.setPreferredSubtitleLanguage(language)

    fun setSubtitleStyle(style: SubtitleStyle) = editor.setSubtitleStyle(style)

    fun setHighContrastSubtitles(enabled: Boolean) =
        editor.edit { subtitle.setHighContrastSubtitles(enabled) }

    fun setSubtitlesForcedOnly(enabled: Boolean) = editor.setSubtitlesForcedOnly(enabled)

    fun setPgsSubtitleDirectPlay(enabled: Boolean) =
        editor.edit { playback.setPgsSubtitleDirectPlay(enabled) }

    fun setHdrSubtitleStyleEnabled(enabled: Boolean) =
        editor.edit { subtitle.setHdrSubtitleStyleEnabled(enabled) }

    fun setHdrSubtitleStyle(style: SubtitleStyle) =
        editor.edit { subtitle.setHdrSubtitleStyle(style) }

    fun setAppLanguage(language: String?) {
        launch {
            editor.edit { subtitle.setAppLanguage(language) }
            appLocaleSetter.setAppLocale(language)
        }
    }
}
