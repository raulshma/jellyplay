package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.Virtualizer
import android.util.Log
import androidx.media3.common.C

class VirtualizerHelper {

    private var virtualizer: Virtualizer? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    var isEnabled: Boolean = false
        private set

    var strength: Int = 500
        private set

    fun setStrength(strength: Int) {
        this.strength = strength.coerceIn(0, 1000)
        if (isEnabled) {
            try {
                virtualizer?.setStrength(this.strength.toShort())
            } catch (_: Exception) {}
        }
    }

    fun attach(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (audioSessionId == currentAudioSessionId && virtualizer != null) return

        detach()
        currentAudioSessionId = audioSessionId

        virtualizer = try {
            Virtualizer(0, audioSessionId).apply {
                setStrength(strength.coerceIn(0, 1000).toShort())
                enabled = isEnabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create Virtualizer", e)
            null
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        virtualizer?.enabled = enabled
    }

    fun detach() {
        virtualizer?.release()
        virtualizer = null
        currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    companion object {
        private const val TAG = "VirtualizerHelper"
    }
}
