package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.StateFlow

class PlaybackSettingsViewModel(
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    advancedSettings: AdvancedSettingsGate,
    editor: PreferencesEditor,
    private val watchNextRefresher: WatchNextRefresher,
) : SettingsSectionViewModel(advancedSettings, editor) {

    /** Playback-screen slice — recomposes this screen only on playback-field writes. */
    val preferences: StateFlow<PlaybackPreferences> = projections.playbackPreferences

    fun setAndroidTvWatchNextEnabled(enabled: Boolean) = editor.edit {
        playback.setAndroidTvWatchNextEnabled(enabled)
        watchNextRefresher.scheduleRefresh()
    }

    /**
     * Screen-level reset for the Playback settings screen. Resets every category
     * rendered here — the player/advanced prefs ([PreferenceResetCategory.PLAYBACK])
     * and the per-engine config ([PreferenceResetCategory.PLAYER_ENGINES]) — so the
     * whole screen returns to defaults in one action, mirroring the appearance
     * screen's reset but spanning both categories this screen owns.
     */
    fun resetPlaybackSettings() {
        editor.resetCategory(PreferenceResetCategory.PLAYBACK)
        editor.resetCategory(PreferenceResetCategory.PLAYER_ENGINES)
    }
}
