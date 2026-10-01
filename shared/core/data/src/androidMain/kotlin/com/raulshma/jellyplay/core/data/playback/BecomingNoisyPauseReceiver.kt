package com.raulshma.jellyplay.core.data.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build

/**
 * The ACTION_AUDIO_BECOMING_NOISY receiver chassis — the headphone-unplug
 * half of the deleted `PlayerAudioLifecycle`, restored as the ONE home after
 * the video focus slice briefly duplicated it in the two video-family
 * players. Registers a private receiver for the system broadcast; on unplug
 * it invokes [pauseTarget] (which re-reads the live engine/player on every
 * broadcast so engine swaps and teardown stay correct). Register is
 * idempotent, release is safe when never registered, and registration
 * failures are swallowed — unplug-pause is best-effort by legacy contract.
 */
class BecomingNoisyPauseReceiver(
    private val context: Context,
    private val pauseTarget: () -> Unit,
) {

    private var receiver: BroadcastReceiver? = null

    fun register() {
        if (receiver != null) return
        val broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    pauseTarget()
                }
            }
        }
        receiver = broadcastReceiver
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        try {
            context.registerReceiver(
                broadcastReceiver,
                filter,
                // Private receiver for a system broadcast — explicit flag required on API 34+.
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
}
