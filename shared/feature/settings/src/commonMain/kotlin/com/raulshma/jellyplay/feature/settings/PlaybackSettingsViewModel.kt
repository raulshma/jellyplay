package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.streaming.ConnectionSpeedTester
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.model.MpvAudioDevice
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The connection-speed probe's outcome as data — the advanced-video action
 * row renders the localized strings. [Measured] carries the raw rate plus
 * the tier [ConnectionSpeedTester.proposedQualityTier] proposes.
 */
sealed interface SpeedTestState {
    data object Running : SpeedTestState
    data object Failed : SpeedTestState
    data class Measured(val mbps: Float, val suggestedTier: StreamingQuality) : SpeedTestState
}

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
    /**
     * The connection-speed probe (#9) — a core:data single; `null` only in
     * unit tests that never exercise [runSpeedTest] (the Koin factory
     * resolves it with `getOrNull()`, mirroring the audio-device seam's
     * hand-written declaration above).
     */
    private val connectionSpeedTester: ConnectionSpeedTester? = null,
) : SettingsSectionViewModel(advancedSettings, editor) {

    /** Playback-screen slice — recomposes this screen only on playback-field writes. */
    val preferences: StateFlow<PlaybackPreferences> = projections.playbackPreferences

    /**
     * The connection-speed probe's outcome — [SpeedTestState.Running] while
     * the probe runs, then [SpeedTestState.Measured] (rate + suggested tier)
     * or [SpeedTestState.Failed] when no sample completed. The advanced-video
     * `speed_test` action row renders it.
     */
    var speedTestResult: SpeedTestState? by mutableStateOf(null)
        private set

    /** The in-flight probe; a second tap while one runs is swallowed. */
    private var speedTestJob: Job? = null

    /**
     * Runs the connection-speed probe (two bounded downloads, best sample
     * kept — see [ConnectionSpeedTester]): flips [speedTestResult] to
     * [SpeedTestState.Running] immediately, then to [SpeedTestState.Measured]
     * on success or [SpeedTestState.Failed] when the probe yields nothing.
     */
    fun runSpeedTest() {
        val tester = connectionSpeedTester ?: return
        if (speedTestJob?.isActive == true) return
        speedTestResult = SpeedTestState.Running
        speedTestJob = scope.launch {
            val mbps = tester.measureMbps()
            speedTestResult = if (mbps == null) {
                SpeedTestState.Failed
            } else {
                SpeedTestState.Measured(mbps, ConnectionSpeedTester.proposedQualityTier(mbps))
            }
        }
    }

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
