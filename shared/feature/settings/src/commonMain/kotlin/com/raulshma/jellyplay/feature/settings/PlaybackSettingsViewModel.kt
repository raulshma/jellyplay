package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.model.MpvAudioDevice
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.StateFlow

class PlaybackSettingsViewModel(
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    advancedSettings: AdvancedSettingsGate,
    editor: PreferencesEditor,
    private val watchNextRefresher: WatchNextRefresher,
    /**
     * Desktop-only mpv audio-device enumeration seam — `null` where
     * the platform binds no enumerator (Android), which is exactly where
     * `SettingsCapabilities.supportsAudioDeviceSelection` hides the row, so
     * nothing on that platform reaches [audioDevices]. Koin resolves it via
     * `getOrNull()`.
     */
    private val audioDeviceEnumerator: AudioDeviceEnumerator? = null,
) : SettingsSectionViewModel(advancedSettings, editor) {

    /** Playback-screen slice — recomposes this screen only on playback-field writes. */
    val preferences: StateFlow<PlaybackPreferences> = projections.playbackPreferences

    fun setAndroidTvWatchNextEnabled(enabled: Boolean) = editor.edit {
        playback.setAndroidTvWatchNextEnabled(enabled)
        watchNextRefresher.scheduleRefresh()
    }

    /**
     * The mpv audio devices for the Engine group's device picker: enumerated
     * on first ask (the seam spins up a throwaway mpv context) and cached for
     * the rest of this ViewModel's life, so re-opening the picker within a
     * settings visit costs nothing. Empty on platforms without the seam.
     */
    suspend fun audioDevices(): List<MpvAudioDevice> {
        audioDevicesCache?.let { return it }
        val devices = audioDeviceEnumerator?.enumerateAudioDevices().orEmpty()
        audioDevicesCache = devices
        return devices
    }

    private var audioDevicesCache: List<MpvAudioDevice>? = null

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
