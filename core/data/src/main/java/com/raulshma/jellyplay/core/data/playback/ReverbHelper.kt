package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.PresetReverb
import com.raulshma.jellyplay.core.model.ReverbPreset


/**
 * Preset-reverb wrapper. Effect-specific state is the [preset]; the lifecycle
 * skeleton lives in [AudioFxHelper]. Attach is skipped while the preset is
 * NONE (nothing to apply), and changing the preset mid-session re-opens the
 * underlying effect so the new preset takes.
 */
open class ReverbHelper(
    private val effectFactory: (Int) -> PresetReverb = ::defaultPresetReverb,
) : AudioFxHelper<PresetReverb>(TAG) {

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
        // Construct first, then configure inside createSafely so a throw during
        // setPreset still releases the native PresetReverb handle — otherwise
        // the object never reaches `fx` and detach()/releaseFx() can't free it.
        val target = this.preset
        return createSafely(audioSessionId, effectFactory) { it.setPreset(target.androidPreset) }
    }

    companion object {
        fun defaultPresetReverb(audioSessionId: Int): PresetReverb = PresetReverb(0, audioSessionId)
        private const val TAG = "ReverbHelper"
    }
}
