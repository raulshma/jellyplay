package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.media3.common.C

class LoudnessEnhancerHelper {

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    var isEnabled: Boolean = false
        private set

    var gainmB: Int = 0
        private set

    fun setGain(gainmB: Int) {
        this.gainmB = gainmB
        if (isEnabled) {
            try {
                loudnessEnhancer?.setTargetGain(gainmB)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set gain on LoudnessEnhancer", e)
            }
        }
    }

    fun attach(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (audioSessionId == currentAudioSessionId && loudnessEnhancer != null) return

        detach()
        currentAudioSessionId = audioSessionId

        loudnessEnhancer = try {
            LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(gainmB)
                enabled = isEnabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create LoudnessEnhancer", e)
            null
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        try {
            loudnessEnhancer?.apply {
                if (enabled) {
                    setTargetGain(gainmB)
                }
                this.enabled = enabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to setEnabled on LoudnessEnhancer", e)
        }
    }

    fun detach() {
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    companion object {
        private const val TAG = "LoudnessEnhancerHelper"
    }
}
