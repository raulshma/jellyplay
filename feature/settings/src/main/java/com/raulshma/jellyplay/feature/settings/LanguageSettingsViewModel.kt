package com.raulshma.jellyplay.feature.settings

import android.content.Context
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.LanguagePreferences
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LanguageSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: UserPreferencesStore,
    private val editor: PreferencesEditor,
) : JellyPlayViewModel() {

    /** Language/subtitle-screen slice — recomposes this screen only on its field writes. */
    val preferences: StateFlow<LanguagePreferences> = store.languagePreferences

    val showAdvancedSettings: StateFlow<Boolean> = store.preferences
        .map { it.showAdvancedSettings }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    fun setShowAdvancedSettings(enabled: Boolean) =
        editor.edit { setShowAdvancedSettings(enabled) }

    fun setPreferredAudioLanguage(language: String?) =
        editor.edit { setPreferredAudioLanguage(language) }

    fun setPreferredSubtitleLanguage(language: String?) = editor.setPreferredSubtitleLanguage(language)

    fun setSubtitleStyle(style: SubtitleStyle) = editor.setSubtitleStyle(style)

    fun setHighContrastSubtitles(enabled: Boolean) =
        editor.edit { setHighContrastSubtitles(enabled) }

    fun setSubtitlesForcedOnly(enabled: Boolean) = editor.setSubtitlesForcedOnly(enabled)

    fun setPgsSubtitleDirectPlay(enabled: Boolean) =
        editor.edit { setPgsSubtitleDirectPlay(enabled) }

    fun setHdrSubtitleStyleEnabled(enabled: Boolean) =
        editor.edit { setHdrSubtitleStyleEnabled(enabled) }

    fun setHdrSubtitleStyle(style: SubtitleStyle) =
        editor.edit { setHdrSubtitleStyle(style) }

    fun setAppLanguage(language: String?) {
        launch {
            editor.edit { setAppLanguage(language) }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
                localeManager?.applicationLocales = if (language != null) {
                    android.os.LocaleList.forLanguageTags(language)
                } else {
                    android.os.LocaleList.getEmptyLocaleList()
                }
            } else {
                com.raulshma.jellyplay.core.ui.components.LocaleApplier.apply(context, language)
            }
        }
    }
}
