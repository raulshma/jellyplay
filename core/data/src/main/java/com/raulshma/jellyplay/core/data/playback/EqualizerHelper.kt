package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.Equalizer
import android.util.Log
import androidx.media3.common.C
import com.raulshma.jellyplay.core.model.EqualizerSettings

/**
 * 10-band graphic equalizer that wraps [android.media.audiofx.Equalizer].
 *
 * Supports custom per-band level adjustments (-15 dB to +15 dB).
 * Safe to call [attach] multiple times — previous instances are released automatically.
 */
class EqualizerHelper {

    private var equalizer: Equalizer? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    var isEnabled: Boolean = false
        private set

    private var currentSettings: EqualizerSettings = EqualizerSettings()

    fun attach(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (audioSessionId == currentAudioSessionId && equalizer != null) return

        detach()
        currentAudioSessionId = audioSessionId

        equalizer = try {
            Equalizer(0, audioSessionId).apply {
                applySettings(currentSettings)
                enabled = isEnabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create Equalizer", e)
            null
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        equalizer?.enabled = enabled
    }

    fun setSettings(settings: EqualizerSettings) {
        currentSettings = settings
        equalizer?.applySettings(settings)
    }

    fun detach() {
        equalizer?.release()
        equalizer = null
        currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    private fun Equalizer.applySettings(settings: EqualizerSettings) {
        val numBands = numberOfBands.toInt()
        val minLevel = bandLevelRange[0] // typically -1500 mB
        val maxLevel = bandLevelRange[1] // typically +1500 mB
        val range = maxLevel - minLevel

        settings.bandLevels.forEachIndexed { index, level ->
            if (index >= numBands) return@forEachIndexed
            // level is in dB, range [-15, 15]; convert to millibels (* 100)
            val mB = (level * 100).coerceIn(minLevel.toInt(), maxLevel.toInt())
            try {
                setBandLevel(index.toShort(), mB.toShort())
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    fun getBandFrequencies(audioSessionId: Int): List<Int> {
        return try {
            val eq = Equalizer(0, audioSessionId)
            val count = eq.numberOfBands.toInt()
            val freqs = (0 until count).map { eq.getCenterFreq(it.toShort()) / 1000 }
            eq.release()
            freqs
        } catch (_: Exception) {
            EqualizerSettings.BAND_FREQUENCIES
        }
    }

    companion object {
        private const val TAG = "EqualizerHelper"
    }
}
