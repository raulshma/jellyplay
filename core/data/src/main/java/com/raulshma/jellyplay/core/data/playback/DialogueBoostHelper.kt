package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.Equalizer
import android.util.Log
import androidx.media3.common.C
import com.raulshma.jellyplay.core.model.EffectStrength

class DialogueBoostHelper {

    private var equalizer: Equalizer? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var savedBandLevels: Map<Int, Short> = emptyMap()

    var isEnabled: Boolean = false
        private set

    var strength: EffectStrength = EffectStrength.MODERATE
        private set

    fun setStrength(strength: EffectStrength) {
        this.strength = strength
        if (isEnabled) {
            equalizer?.apply {
                val bandLevels = boostVocalBands()
                bandLevels.forEach { (band, level) ->
                    try { setBandLevel(band.toShort(), level.toShort()) } catch (_: Exception) {}
                }
            }
        }
    }

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

    private fun Equalizer.boostVocalBands(): Map<Int, Int> {
        val numBands = numberOfBands.toInt()
        val result = mutableMapOf<Int, Int>()
        val (coreVocal, upperHarmonics, lowMidWarmth) = when (strength) {
            EffectStrength.LOW -> Triple(300, 150, 100)
            EffectStrength.MODERATE -> Triple(600, 300, 200)
            EffectStrength.HIGH -> Triple(900, 450, 300)
        }

        for (i in 0 until numBands) {
            val centerFreq = getCenterFreq(i.toShort())
            val level = when {
                centerFreq in 1_000_000..4_000_000 -> coreVocal
                centerFreq in 4_000_000..8_000_000 -> upperHarmonics
                centerFreq in 500_000..1_000_000 -> lowMidWarmth
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
