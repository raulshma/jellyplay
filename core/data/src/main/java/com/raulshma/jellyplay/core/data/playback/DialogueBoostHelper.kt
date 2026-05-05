package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.Equalizer
import android.util.Log
import androidx.media3.common.C

/**
 * Dialogue Boost DSP processor that enhances vocal clarity by boosting
 * frequencies in the human voice range (approximately 1 kHz – 4 kHz).
 *
 * This helper wraps [android.media.audiofx.Equalizer] and is designed to be
 * attached to an ExoPlayer audio session. It is safe to call [attach] multiple
 * times — previous instances are released automatically.
 */
class DialogueBoostHelper {

    private var equalizer: Equalizer? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var savedBandLevels: Map<Int, Short> = emptyMap()

    var isEnabled: Boolean = false
        private set

    fun attach(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (audioSessionId == currentAudioSessionId && equalizer != null) return

        detach()
        currentAudioSessionId = audioSessionId

        equalizer = try {
            Equalizer(0, audioSessionId).apply {
                savedBandLevels = (0 until numberOfBands.toInt()).associateWith { band ->
                    getBandLevel(band.toShort())
                }
                val bandLevels = boostVocalBands()
                bandLevels.forEach { (band, level) ->
                    try {
                        setBandLevel(band.toShort(), level.toShort())
                    } catch (_: Exception) {}
                }
                enabled = isEnabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create Equalizer for dialogue boost", e)
            null
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (enabled) {
            equalizer?.apply {
                val bandLevels = boostVocalBands()
                bandLevels.forEach { (band, level) ->
                    try { setBandLevel(band.toShort(), level.toShort()) } catch (_: Exception) {}
                }
                this.enabled = true
            }
        } else {
            equalizer?.apply {
                savedBandLevels.forEach { (band, level) ->
                    try { setBandLevel(band.toShort(), level) } catch (_: Exception) {}
                }
                this.enabled = false
            }
        }
    }

    fun detach() {
        equalizer?.release()
        equalizer = null
        currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
        savedBandLevels = emptyMap()
    }

    /**
     * Computes which bands to boost and by how much.
     * Targets the 1 kHz – 4 kHz range with a gentle +6 dB shelf.
     */
    private fun Equalizer.boostVocalBands(): Map<Int, Int> {
        val numBands = numberOfBands.toInt()
        val result = mutableMapOf<Int, Int>()

        for (i in 0 until numBands) {
            val centerFreq = getCenterFreq(i.toShort()) // Hz
            val level = when {
                // Core vocal range: 1 kHz – 4 kHz  → +6 dB
                centerFreq in 1_000_000..4_000_000 -> +600
                // Upper harmonics / presence: 4 kHz – 8 kHz → +3 dB
                centerFreq in 4_000_000..8_000_000 -> +300
                // Low-mid warmth: 500 Hz – 1 kHz → +2 dB
                centerFreq in 500_000..1_000_000 -> +200
                else -> 0
            }
            if (level != 0) {
                result[i] = level
            }
        }
        return result
    }

    companion object {
        private const val TAG = "DialogueBoostHelper"
    }
}
