package com.raulshma.jellyplay.core.data.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * The `ACTION_HEADSET_PLUG` receiver chassis — the headphone-reconnect half
 * of the pair, the exact sibling of [BecomingNoisyPauseReceiver] (register
 * is idempotent, release is safe when never registered, registration
 * failures are swallowed — resume-on-insert is best-effort by contract).
 *
 * Fires [onHeadsetConnected] only for the connected edge (`state == 1`); the
 * unplugged edge is the becoming-noisy broadcast's job, and the disconnected
 * delivery is ignored. The decision machinery (opt-in pref, the pause marker,
 * the freshness window) lives in [HeadsetResumePolicy] — this chassis only
 * reports the edge.
 *
 * `HEADSET_PLUG` is a protected broadcast (system sender only) received
 * passively — no manifest entry, and the context registration mirrors
 * [BecomingNoisyPauseReceiver]'s: `RECEIVER_NOT_EXPORTED` on API 34+ (the
 * system is exempt from the sender check, so delivery is unaffected).
 */
class HeadsetPlugReceiver(
    private val context: Context,
    private val onHeadsetConnected: () -> Unit,
) {

    private var receiver: BroadcastReceiver? = null

    fun register() {
        if (receiver != null) return
        val broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_HEADSET_PLUG &&
                    intent.getIntExtra(EXTRA_STATE, EXTRA_STATE_DISCONNECTED) == EXTRA_STATE_CONNECTED
                ) {
                    onHeadsetConnected()
                }
            }
        }
        receiver = broadcastReceiver
        val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        try {
            context.registerReceiver(
                broadcastReceiver,
                filter,
                // Private receiver for a protected system broadcast — explicit
                // flag required on API 34+ (the BecomingNoisyPauseReceiver shape).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Context.RECEIVER_NOT_EXPORTED
                } else 0,
            )
        } catch (_: Exception) {}
    }

    fun release() {
        val broadcastReceiver = receiver ?: return
        try {
            context.unregisterReceiver(broadcastReceiver)
        } catch (_: Exception) {}
        receiver = null
    }

    companion object {
        /** `HEADSET_PLUG`'s sticky state extra (mirrors `AudioManager`'s names). */
        const val EXTRA_STATE = "state"

        /** A headset is plugged in. */
        const val EXTRA_STATE_CONNECTED = 1

        /** A headset is unplugged. */
        const val EXTRA_STATE_DISCONNECTED = 0
    }
}
