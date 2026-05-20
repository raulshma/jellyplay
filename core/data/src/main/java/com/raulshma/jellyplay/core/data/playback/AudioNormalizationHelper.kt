package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.media3.common.C
import com.raulshma.jellyplay.core.model.AudioNormalizationMode

class AudioNormalizationHelper {

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    var isEnabled: Boolean = false
        private set

    var mode: AudioNormalizationMode = AudioNormalizationMode.NONE
        private set

    private val targetGainMbh: Int
        get() = when (mode) {
            AudioNormalizationMode.DYNAMIC -> 1500
            else -> 0
        }

    fun attach(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (audioSessionId == currentAudioSessionId && loudnessEnhancer != null) return

        detach()
        currentAudioSessionId = audioSessionId

        if (mode != AudioNormalizationMode.NONE) {
            loudnessEnhancer = try {
                LoudnessEnhancer(audioSessionId).apply {
                    setTargetGain(targetGainMbh)
                    enabled = isEnabled
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create LoudnessEnhancer for normalization", e)
                null
            }
        }
    }

    fun setMode(mode: AudioNormalizationMode) {
        this.mode = mode
        if (isEnabled && currentAudioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            detach()
            attach(currentAudioSessionId)
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        loudnessEnhancer?.enabled = enabled
    }

    fun detach() {
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    companion object {
        private const val TAG = "AudioNormalizationHelper"
    }
}
