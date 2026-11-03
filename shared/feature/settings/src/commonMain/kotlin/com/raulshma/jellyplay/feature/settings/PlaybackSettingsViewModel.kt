package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.playback.VideoPerformanceResolver
import com.raulshma.jellyplay.core.data.streaming.ConnectionSpeedTester
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.model.DevicePerformanceTier
import com.raulshma.jellyplay.core.model.MpvAudioDevice
import com.raulshma.jellyplay.core.model.PerformanceDetection
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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

/**
 * One-shot Re-detect outcome (issue #186 §5.10) emitted by
 * [PlaybackSettingsViewModel.redetectPerformance] and rendered by the
 * playback screen, which resolves the resource text and posts it to the
 * shared UserMessageBus (the [PrivacyUserMessage] screen-forward pattern).
 */
@Immutable
sealed interface PerformanceRedetectMessage {
    /** A fresh detection landed — carry the new tier for the snackbar text. */
    data class Detected(val tier: DevicePerformanceTier) : PerformanceRedetectMessage

    /** The re-detect threw (a failed probe itself lands MEDIUM + PROBE_FAILED). */
    data object Failed : PerformanceRedetectMessage
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
    /**
     * The playback performance-profile resolver (#186) — a core:data single
     * bound in the data-session Koin graph. `null` only in unit tests that
     * never exercise [redetectPerformance] (the Koin factory resolves it
     * with `getOrNull()`, mirroring the seams above).
     */
    private val videoPerformanceResolver: VideoPerformanceResolver? = null,
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

    // ── The performance-profile Re-detect action (issue #186 §5.10) ──────

    /**
     * The sticky detection cache, straight from the resolver (seeded null —
     * "Detecting…" until the store self-loads). A constant null flow where
     * no resolver is bound (unit-test constructions).
     */
    val performanceDetection: StateFlow<PerformanceDetection?> =
        videoPerformanceResolver?.detection ?: MutableStateFlow<PerformanceDetection?>(null)

    /** True while a user-initiated Re-detect runs — the action row's progress state. */
    private val _isRedetecting = MutableStateFlow(false)
    val isRedetecting: StateFlow<Boolean> = _isRedetecting.asStateFlow()

    /** One-shot Re-detect outcomes (buffered Channel, single collector, never replayed). */
    private val redetectMessageChannel = Channel<PerformanceRedetectMessage>(Channel.BUFFERED)
    val redetectMessages: Flow<PerformanceRedetectMessage> = redetectMessageChannel.receiveAsFlow()

    /** The in-flight Re-detect; a second tap while one runs is swallowed. */
    private var redetectJob: Job? = null

    /**
     * Runs the explicit Re-detect (§5.5): clears the sticky cache, probes,
     * saves, and updates the resolver's flows — then emits the snackbar
     * message with the new tier. A failed probe lands on the MEDIUM +
     * PROBE_FAILED safe middle inside [VideoPerformanceResolver.redetect];
     * only an unexpected throw surfaces as [PerformanceRedetectMessage.Failed].
     */
    fun redetectPerformance() {
        val resolver = videoPerformanceResolver ?: return
        if (redetectJob?.isActive == true) return
        _isRedetecting.value = true
        redetectJob = scope.launch {
            try {
                val detection = resolver.redetect()
                redetectMessageChannel.trySend(PerformanceRedetectMessage.Detected(detection.tier))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                redetectMessageChannel.trySend(PerformanceRedetectMessage.Failed)
            } finally {
                _isRedetecting.value = false
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
