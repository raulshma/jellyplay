package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.BassBoost
import android.util.Log
import androidx.media3.common.C
import com.raulshma.jellyplay.core.model.EffectStrength

class BassBoostHelper {

    private var bassBoost: BassBoost? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    var isEnabled: Boolean = false
        private set

    var strength: EffectStrength = EffectStrength.MODERATE
        private set

    private val strengthValue: Short
        get() = when (strength) {
            EffectStrength.NONE -> 0
            EffectStrength.LOW -> 400
            EffectStrength.MODERATE -> 700
            EffectStrength.HIGH -> 1000
        }

    fun setStrength(strength: EffectStrength) {
        this.strength = strength
        if (isEnabled) {
            try {
                bassBoost?.setStrength(strengthValue)
            } catch (_: Exception) {}
        }
    }

    fun attach(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (audioSessionId == currentAudioSessionId && bassBoost != null) return

        detach()
        currentAudioSessionId = audioSessionId

        bassBoost = try {
            BassBoost(0, audioSessionId).apply {
                setStrength(strengthValue)
                enabled = isEnabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create BassBoost", e)
            null
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        bassBoost?.enabled = enabled
    }

    fun detach() {
        bassBoost?.release()
        bassBoost = null
        currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    companion object {
        private const val TAG = "BassBoostHelper"
    }
}
