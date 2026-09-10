package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.Equalizer
import android.util.Log
import androidx.media3.common.C
import com.raulshma.jellyplay.core.model.EqualizerSettings

/**
 * 10-band graphic equalizer that wraps [android.media.audiofx.Equalizer].
 *
 * Supports custom per-band level adjustments (-15 dB to +15 dB).
 *
 * Also supports **additive band offsets** via [setBandOffsets] — used to
 * coordinate multiple effects that ride on the same underlying
 * `android.media.audiofx.Equalizer` (notably [DialogueBoostHelper]).
 *
 * Background: Android's `Equalizer(priority=0, sessionId)` is a
 * system-global effect per audio session — there is exactly ONE
 * priority-0 equalizer per session regardless of how many `Equalizer`
 * objects the app constructs. Two helpers that each open their own
 * `Equalizer(0, sid)` therefore write through the same system effect
 * and clobber each other. To avoid that, all such effects in JellyPlay
 * route through this single helper: the user's [EqualizerSettings]
 * provide the per-band base levels, and [setBandOffsets] overlays
 * additional millibel deltas on top (e.g. dialogue-boost vocal-band
 * gains). The applied level for band `i` is
 * `coerceIn(userLevel_i + offset_i, minLevel, maxLevel)`.
 *
 * Safe to call [attach] multiple times — previous instances are released automatically.
 */
class EqualizerHelper(
    private val equalizerFactory: (Int) -> Equalizer = ::defaultEqualizer,
) {

    private var equalizer: Equalizer? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    var isEnabled: Boolean = false
        private set

    private var currentSettings: EqualizerSettings = EqualizerSettings()

    /**
     * Per-band offsets in millibels, overlaid on top of [currentSettings]
     * at apply time. See class kdoc for the rationale. Empty means no
     * overlay — the user's EQ settings pass through unchanged.
     */
    private var bandOffsets: Map<Int, Int> = emptyMap()

    fun attach(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (audioSessionId == currentAudioSessionId && equalizer != null) return

        detach()
        currentAudioSessionId = audioSessionId

        equalizer = try {
            equalizerFactory(audioSessionId).apply {
                applySettings(currentSettings)
                enabled = isEnabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create Equalizer", e)
            null
        }
    }

    /**
     * Apply the equalizer / dialogue-boost co-enabling rule and set the
     * underlying effect's enabled state.
     *
     * Android exposes exactly one priority-0 `Equalizer` per audio session,
     * so the user-facing EQ and [DialogueBoostHelper] share it (the boost
     * overlay is layered on top via [setBandOffsets]). The underlying effect
     * must stay **enabled while EITHER is on**; when both are off, the engine
     * is free to disable it so the system effect is not held open needlessly.
     *
     * Folding this rule in here means the three apply sites
     * (`AudioEffectsProcessor`, `AudioEffectChain`, `MpvPlayerEngine`) cannot
     * drift — they pass the two user-facing flags and this method owns the
     * `||`. Previously the rule lived in a companion `shouldEnableFor` that
     * each site had to remember to call, and callers then re-derived the
     * same `||` to decide follow-up work. The resolved flag is returned so
     * callers reuse it instead of re-deriving the rule at each call site
     * (the "rule in three places" smell the original fold left behind).
     */
    fun setEnabled(equalizerEnabled: Boolean, dialogueBoostEnabled: Boolean = false): Boolean {
        val enabled = equalizerEnabled || dialogueBoostEnabled
        isEnabled = enabled
        equalizer?.enabled = enabled
        return enabled
    }

    fun setSettings(settings: EqualizerSettings) {
        currentSettings = settings
        equalizer?.applySettings(settings)
    }

    /**
     * Overlay per-band offsets on top of the user's EQ settings and
     * re-apply. Pass an empty map to clear the overlay. The user's base
     * levels ([currentSettings]) are preserved; only the applied levels
     * are recomputed.
     *
     * No-op if no `Equalizer` is currently attached.
     */
    fun setBandOffsets(offsets: Map<Int, Int>) {
        bandOffsets = offsets
        equalizer?.applySettings(currentSettings)
    }

    /**
     * Returns the center frequencies (in Hz) of the bands on the
     * currently-attached `Equalizer`, or an empty list if nothing is
     * attached. Callers (e.g. [DialogueBoostHelper]) use this to map
     * frequency-band semantics to band indices without opening their
     * own `Equalizer`.
     */
    fun getBandCenterFrequencies(): List<Int> {
        val eq = equalizer ?: return emptyList()
        return try {
            val count = eq.numberOfBands.toInt()
            (0 until count).map { eq.getCenterFreq(it.toShort()) / 1000 }
        } catch (_: Exception) {
            emptyList()
        }
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
            // then add any overlay offset (also in mB) and clamp.
            val offset = bandOffsets[index] ?: 0
            val mB = (level * 100 + offset).coerceIn(minLevel.toInt(), maxLevel.toInt())
            try {
                setBandLevel(index.toShort(), mB.toShort())
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    fun getBandFrequencies(audioSessionId: Int): List<Int> {
        // Construct the native Equalizer in a try/finally so its native handle
        // is released even if numberOfBands/getCenterFreq throw after
        // construction. android.media.audiofx.* native objects are not GC'd
        // until release(); a throw between construct and release would leak it.
        return try {
            val eq = equalizerFactory(audioSessionId)
            try {
                val count = eq.numberOfBands.toInt()
                (0 until count).map { eq.getCenterFreq(it.toShort()) / 1000 }
            } finally {
                eq.release()
            }
        } catch (_: Exception) {
            EqualizerSettings.BAND_FREQUENCIES
        }
    }

    companion object {
        fun defaultEqualizer(audioSessionId: Int): Equalizer = Equalizer(0, audioSessionId)
        private const val TAG = "EqualizerHelper"
    }
}
