package com.raulshma.jellyplay.core.data.playback

import android.media.AudioDeviceInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the headphone-class type set behind [HeadsetAudioDeviceWatcher]'s
 * connect edge — the resume-on-reconnect trigger must fire for every route
 * a headset reconnect can produce (wired, Bluetooth A2DP, BLE on S+, USB-C
 * headset) and for nothing else (speakers, HDMI, earpiece — a phone call's
 * route change must never read as "headphones are back").
 */
class HeadsetAudioDeviceWatcherTypeTest {

    @Test
    fun `every headphone-class route counts as connected headphones`() {
        assertTrue(HeadsetAudioDeviceWatcher.isHeadphoneDeviceType(AudioDeviceInfo.TYPE_WIRED_HEADSET))
        assertTrue(HeadsetAudioDeviceWatcher.isHeadphoneDeviceType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertTrue(HeadsetAudioDeviceWatcher.isHeadphoneDeviceType(AudioDeviceInfo.TYPE_USB_HEADSET))
        assertTrue(HeadsetAudioDeviceWatcher.isHeadphoneDeviceType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
    }

    @Test
    fun `non-headphone routes never count`() {
        assertFalse(HeadsetAudioDeviceWatcher.isHeadphoneDeviceType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertFalse(HeadsetAudioDeviceWatcher.isHeadphoneDeviceType(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
        assertFalse(HeadsetAudioDeviceWatcher.isHeadphoneDeviceType(AudioDeviceInfo.TYPE_HDMI))
        assertFalse(HeadsetAudioDeviceWatcher.isHeadphoneDeviceType(AudioDeviceInfo.TYPE_TELEPHONY))
        assertFalse(HeadsetAudioDeviceWatcher.isHeadphoneDeviceType(AudioDeviceInfo.TYPE_UNKNOWN))
    }

    @Test
    fun `bluetooth scoped routes stay exclusive to a2dp on pre-S`() {
        // TYPE_BLUETOOTH_SCO is the call-audio route: a call connecting must
        // not read as headphones returning (the duck directive owns calls).
        assertFalse(HeadsetAudioDeviceWatcher.isHeadphoneDeviceType(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
    }
}
