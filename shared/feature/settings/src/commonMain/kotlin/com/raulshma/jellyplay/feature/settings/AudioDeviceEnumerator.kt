package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.MpvAudioDevice

/**
 * Platform seam behind the mpv "Audio Device" settings row: the
 * enumeration runs through a throwaway idle mpv context (`audio-device-list`
 * node read), which only the desktop binary's libmpv binding can do — so the
 * app composition root provides the enumerating impl at the Koin edge
 * (desktop: `DesktopAudioDeviceEnumerator`, bound in DesktopPlayerModule;
 * Android: no binding at all — the row is structurally hidden through
 * `SettingsCapabilities.supportsAudioDeviceSelection`, so nothing resolves
 * this seam on the platform where it would return nothing useful).
 *
 * The ViewModel caches the result (device enumeration spins up a short-lived
 * mpv core — not something to repeat per picker open), so this is called on
 * demand, once per settings visit.
 */
fun interface AudioDeviceEnumerator {
    suspend fun enumerateAudioDevices(): List<MpvAudioDevice>
}
