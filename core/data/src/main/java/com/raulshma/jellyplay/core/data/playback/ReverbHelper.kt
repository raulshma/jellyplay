package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.PresetReverb
import android.util.Log
import androidx.media3.common.C
import com.raulshma.jellyplay.core.model.ReverbPreset

class ReverbHelper {

    private var reverb: PresetReverb? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    var isEnabled: Boolean = false
        private set

    var preset: ReverbPreset = ReverbPreset.NONE
        private set

    fun setPreset(preset: ReverbPreset) {
        this.preset = preset
        if (preset == ReverbPreset.NONE) {
            setEnabled(false)
            return
        }
        if (currentAudioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            val oldReverb = reverb
            reverb = try {
                val targetPreset = this@ReverbHelper.preset
                PresetReverb(0, currentAudioSessionId).apply {
                    setPreset(targetPreset.androidPreset)
                    enabled = this@ReverbHelper.isEnabled
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create PresetReverb", e)
                null
            }
            oldReverb?.release()
            if (reverb != null) {
                setEnabled(true)
            }
        }
    }

    fun attach(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (audioSessionId == currentAudioSessionId && reverb != null) return
        if (preset == ReverbPreset.NONE) return

        detach()
        currentAudioSessionId = audioSessionId

        reverb = try {
            val targetPreset = this@ReverbHelper.preset
            PresetReverb(0, audioSessionId).apply {
                setPreset(targetPreset.androidPreset)
                enabled = this@ReverbHelper.isEnabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create PresetReverb", e)
            null
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        reverb?.enabled = enabled
    }

    fun detach() {
        reverb?.release()
        reverb = null
        currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    companion object {
        private const val TAG = "ReverbHelper"
    }
}
