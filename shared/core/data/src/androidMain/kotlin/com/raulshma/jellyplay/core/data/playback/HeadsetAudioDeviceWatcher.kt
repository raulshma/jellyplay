package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * The `AudioDeviceCallback` chassis — the headphone-reconnect half for
 * devices `ACTION_HEADSET_PLUG` never reports. That broadcast is wired-only:
 * a Bluetooth (or BLE/USB-C headset) reconnect produces no plug event, so
 * resume-on-reconnect silently never fired for wireless headphones. This
 * watcher reports the same connected edge for every headphone-class output
 * route (wired headset/headphones, Bluetooth A2DP, BLE headset on S+, USB
 * headset), feeding [HeadsetResumePolicy] through the identical callback the
 * plug receiver uses — the decision machinery (opt-in pref, the pause
 * marker, the freshness window) is untouched.
 *
 * Register replays the ALREADY-connected devices once (the callback's
 * documented initial dispatch, the same sticky shape as `HEADSET_PLUG`);
 * that replay is inert there — a fresh registration has no pause marker
 * yet, so no spurious resume. Register is idempotent, release is safe when
 * never registered; a failed registration leaves no callback behind, so a
 * later register() retries — failures are swallowed either way, since
 * resume-on-insert is best-effort by contract, matching [HeadsetPlugReceiver].
 */
class HeadsetAudioDeviceWatcher(
    private val context: Context,
    private val onHeadsetConnected: () -> Unit,
) {

    private var callback: AudioDeviceCallback? = null

    fun register() {
        if (callback != null) return
        val audioManager = audioManager() ?: return
        val deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                if (addedDevices.any { isHeadphoneDevice(it) }) onHeadsetConnected()
            }
        }
        try {
            audioManager.registerAudioDeviceCallback(deviceCallback, null)
        } catch (_: Exception) {
            return
        }
        callback = deviceCallback
    }

    fun release() {
        val deviceCallback = callback ?: return
        try {
            audioManager()?.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Exception) {}
        callback = null
    }

    private fun audioManager(): AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    companion object {

        /**
         * The headphone-class output types the resume path treats as
         * "headphones are back". Plain `Int` signature so the host test can
         * pin the set without a device (the constants are compile-time).
         * `TYPE_BLE_HEADSET` is API 31+; the `Build` guard keeps the call
         * constant-foldable below S.
         */
        internal fun isHeadphoneDeviceType(type: Int): Boolean =
            type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    type == AudioDeviceInfo.TYPE_BLE_HEADSET)

        private fun isHeadphoneDevice(device: AudioDeviceInfo): Boolean =
            device.isSink && isHeadphoneDeviceType(device.type)
    }
}
