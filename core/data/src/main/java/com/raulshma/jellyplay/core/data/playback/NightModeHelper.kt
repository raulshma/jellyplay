package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.media3.common.C
import com.raulshma.jellyplay.core.model.EffectStrength

class NightModeHelper {

    private var enhancer: LoudnessEnhancer? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    var isEnabled: Boolean = false
        private set

    var strength: EffectStrength = EffectStrength.MODERATE
        private set

    private val targetGainForStrength: Int
        get() = when (strength) {
            EffectStrength.NONE -> 0
            EffectStrength.LOW -> 1500
            EffectStrength.MODERATE -> 3000
            EffectStrength.HIGH -> 4500
        }

    fun setStrength(strength: EffectStrength) {
        this.strength = strength
        if (isEnabled) {
            try {
                enhancer?.setTargetGain(targetGainForStrength)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set target gain on LoudnessEnhancer", e)
            }
        }
    }

    fun attach(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (audioSessionId == currentAudioSessionId && enhancer != null) return

        detach()
        currentAudioSessionId = audioSessionId

        enhancer = try {
            LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(targetGainForStrength)
                enabled = isEnabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create LoudnessEnhancer for night mode", e)
            null
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        try {
            enhancer?.apply {
                if (enabled) {
                    setTargetGain(targetGainForStrength)
                }
                this.enabled = enabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set enabled on LoudnessEnhancer", e)
        }
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
