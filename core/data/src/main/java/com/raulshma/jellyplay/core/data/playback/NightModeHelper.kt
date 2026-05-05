package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.media3.common.C

class NightModeHelper {

    private var enhancer: LoudnessEnhancer? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    var isEnabled: Boolean = false
        private set

    var targetGain: Int = 3000
        private set

    fun setTargetGain(gain: Int) {
        targetGain = gain
        enhancer?.setTargetGain(gain.toInt())
    }

    fun attach(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (audioSessionId == currentAudioSessionId && enhancer != null) return

        detach()
        currentAudioSessionId = audioSessionId

        enhancer = try {
            LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(targetGain.toInt())
                enabled = isEnabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create LoudnessEnhancer for night mode", e)
            null
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        enhancer?.enabled = enabled
    }

    fun detach() {
        enhancer?.release()
        enhancer = null
        currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    companion object {
        private const val TAG = "NightModeHelper"
    }
}
