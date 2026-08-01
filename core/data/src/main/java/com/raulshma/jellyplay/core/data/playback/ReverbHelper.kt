package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.PresetReverb
import com.raulshma.jellyplay.core.model.ReverbPreset


/**
 * Preset-reverb wrapper. Effect-specific state is the [preset]; the lifecycle
 * skeleton lives in [AudioFxHelper]. Attach is skipped while the preset is
 * NONE (nothing to apply), and changing the preset mid-session re-opens the
 * underlying effect so the new preset takes.
 */
class ReverbHelper : AudioFxHelper<PresetReverb>(TAG) {

    var preset: ReverbPreset = ReverbPreset.NONE
        private set

    fun setPreset(preset: ReverbPreset) {
        this.preset = preset
        if (preset == ReverbPreset.NONE) {
            setEnabled(false)
            return
        }
        // Re-open the underlying effect on the current session so the new
        // preset takes immediately. detach() releases the prior instance and
        // clears the remembered session id, so the re-attach isn't short-
        // circuited and create() re-reads the preset.
        val sid = attachedAudioSessionId
        if (sid != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
            detach()
            attach(sid)
            if (fx != null) setEnabled(true)
        }
    }

    override fun shouldAttach(): Boolean = preset != ReverbPreset.NONE

    override fun create(audioSessionId: Int): PresetReverb? {
        // Construct first, then configure inside try/catch so a throw during
        // setPreset still releases the native PresetReverb handle — otherwise
        // the object never reaches `fx` and detach()/releaseFx() can't free it.
        val target = this.preset
        val fx = PresetReverb(0, audioSessionId)
        return try {
            fx.setPreset(target.androidPreset)
            fx
        } catch (e: Exception) {
            runCatching { fx.release() }
            throw e
        }
    }

    companion object {
        private const val TAG = "ReverbHelper"
    }
}
