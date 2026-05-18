package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import androidx.media3.common.C
import com.raulshma.jellyplay.core.model.ChannelMixMode

class ChannelMixHelper {

    private var virtualizer: Virtualizer? = null
    private var equalizer: Equalizer? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    var isEnabled: Boolean = false
        private set

    var mode: ChannelMixMode = ChannelMixMode.AUTO
        private set

    fun attach(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (audioSessionId == currentAudioSessionId && (virtualizer != null || equalizer != null)) return

        detach()
        currentAudioSessionId = audioSessionId

        when (mode) {
            ChannelMixMode.SURROUND_UPMIX -> {
                virtualizer = try {
                    Virtualizer(0, audioSessionId).apply {
                        setStrength(800.toShort())
                        enabled = isEnabled
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create Virtualizer for surround upmix", e)
                    null
                }
            }
            ChannelMixMode.MONO -> {
                equalizer = try {
                    Equalizer(0, audioSessionId).apply {
                        val numBands = numberOfBands.toInt()
                        val midLevel = (bandLevelRange[0] + bandLevelRange[1]) / 2
                        for (i in 0 until numBands) {
                            try { setBandLevel(i.toShort(), midLevel.toShort()) } catch (_: Exception) {}
                        }
                        enabled = isEnabled
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create Equalizer for mono mix", e)
                    null
                }
            }
            ChannelMixMode.STEREO_DOWNMIX,
            ChannelMixMode.AUTO -> {
            }
        }
    }

    fun setMode(mode: ChannelMixMode) {
        this.mode = mode
        if (isEnabled && currentAudioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            detach()
            attach(currentAudioSessionId)
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        virtualizer?.enabled = enabled
        equalizer?.enabled = enabled
    }

    fun detach() {
        virtualizer?.release()
        virtualizer = null
        equalizer?.release()
        equalizer = null
        currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    companion object {
        private const val TAG = "ChannelMixHelper"
    }
}
