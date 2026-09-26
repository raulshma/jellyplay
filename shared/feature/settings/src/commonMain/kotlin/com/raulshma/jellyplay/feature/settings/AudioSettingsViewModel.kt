package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.model.AudioPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.StateFlow

class AudioSettingsViewModel(
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    advancedSettings: AdvancedSettingsGate,
    editor: PreferencesEditor,
    private val audioCacheClearer: AudioCacheClearer,
) : SettingsSectionViewModel(advancedSettings, editor) {

    /** Audio-screen slice — recomposes this screen only on audio-field writes. */
    val preferences: StateFlow<AudioPreferences> = projections.audioPreferences

    fun clearAudioCache() {
        launch { audioCacheClearer.clear() }
    }
}
