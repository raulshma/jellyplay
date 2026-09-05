package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.AudioPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.StateFlow

class AudioSettingsViewModel(
    private val store: UserPreferencesStore,
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    private val appearanceStore: com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore,
    private val editor: PreferencesEditor,
    private val audioCacheClearer: AudioCacheClearer,
) : JellyPlayViewModel() {

    /** Audio-screen slice — recomposes this screen only on audio-field writes. */
    val preferences: StateFlow<AudioPreferences> = projections.audioPreferences

    private val advancedSettings = AdvancedSettingsGate(appearanceStore, editor)

    val showAdvancedSettings: StateFlow<Boolean> = advancedSettings.showAdvancedSettings

    fun setShowAdvancedSettings(enabled: Boolean) = advancedSettings.setShowAdvancedSettings(enabled)

    /**
     * Single write command for this screen: `edit { it.audioEffects.setEqualizerEnabled(true) }`.
     * Fire-and-forget on the same application scope [PreferencesEditor.edit] uses.
     */
    fun edit(transform: suspend (PreferencesEditScope) -> Unit) = editor.edit { transform(this) }

    fun clearAudioCache() {
        launch { audioCacheClearer.clear() }
    }
}
