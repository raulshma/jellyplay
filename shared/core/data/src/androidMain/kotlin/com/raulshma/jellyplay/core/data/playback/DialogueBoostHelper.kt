package com.raulshma.jellyplay.core.data.playback

import android.util.Log
import com.raulshma.jellyplay.core.model.EffectStrength

/**
 * Boosts vocal-range frequencies (1–8 kHz) by overlaying additive
 * millibel offsets on top of the user's EQ settings, **and** enables a
 * sub-bass high-pass filter ([HighPassFilterAudioProcessor]) to cut
 * rumble below the voice band for clearer speech.
 *
 * **Threading note:** all audio-effect mutations in JellyPlay happen
 * on the playback thread; this helper is not thread-safe.
 *
 * ## Why this delegates to [EqualizerHelper]
 *
 * Android's `android.media.audiofx.Equalizer(priority=0, sessionId)` is
 * a system-global effect per audio session: there is exactly ONE
 * priority-0 equalizer per session, regardless of how many `Equalizer`
 * objects the app constructs. Previously this helper owned its own
 * `Equalizer(0, sid)` alongside [EqualizerHelper]'s, and the two
 * clobbered each other's band writes — whichever applied last won, and
 * the snapshot this helper took on attach was already EQ-modified. The
 * resulting frequency response was non-deterministic and disabling one
 * effect could clobber the other.
 *
 * This helper now holds no `Equalizer` of its own. It computes the
 * vocal-band boost as a `bandIndex → millibel-offset` map and hands
 * the overlay to its paired [EqualizerHelper] via
 * [EqualizerHelper.setBandOffsets]. The host is responsible for
 * attaching/enabling the shared [EqualizerHelper] before invoking
 * [setEnabled]. See [AudioEffectsProcessor.applyDialogueBoost] /
 * `ExoPlayerEngine.applyAudioEffects` / `MpvPlayerEngine` for the
 * canonical wiring.
 *
 * ## Voice-band de-noise
 *
 * When enabled, this helper also enables an optional
 * [HighPassFilterAudioProcessor] (default 80 Hz cutoff, below the ~85 Hz
 * fundamental of the lowest male voice). The host owns the processor
 * instance and passes it here; `null` disables the de-noise stage while
 * keeping the EQ boost. On the MPV path there is no in-sink processor —
 * the host applies an equivalent `highpass=f=80` `af` filter instead.
 *
 * The legacy `attach(audioSessionId)` / `detach()` methods are kept as
 * no-ops so existing call sites compile unchanged.
 */
class DialogueBoostHelper(
    private val equalizerHelper: EqualizerHelper,
    /**
     * Optional sub-bass high-pass filter owned by the host. When
     * non-null it is enabled/disabled in lockstep with this helper to
     * cut rumble below the voice band. `null` on paths that have no
     * in-sink processor (e.g. MPV, which uses an `af` filter instead).
     */
    private val highPassFilter: HighPassFilterAudioProcessor? = null,
) {

    var isEnabled: Boolean = false
        private set

    var strength: EffectStrength = EffectStrength.MODERATE
        private set

    fun setStrength(strength: EffectStrength) {
        this.strength = strength
        if (isEnabled) applyOffsets()
    }

    /**
     * Kept for source compatibility with hosts that previously
     * attached this helper to an audio session directly. The
     * underlying `Equalizer` is owned by the paired
     * [equalizerHelper]; the host is responsible for calling
     * [EqualizerHelper.attach].
     */
    fun attach(@Suppress("UNUSED_PARAMETER") audioSessionId: Int) {
        // No-op — delegates to equalizerHelper.
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (enabled) {
            applyOffsets()
            highPassFilter?.setEnabled(true)
        } else {
            clearOffsets()
            highPassFilter?.setEnabled(false)
        }
    }

    /**
     * Kept for source compatibility. Does not release the underlying
     * `Equalizer`; the host owns that lifecycle via [equalizerHelper].
     * Clears any overlay this helper applied so a subsequent attach
     * starts from a clean state.
     */
    fun detach() {
        if (isEnabled) clearOffsets()
        highPassFilter?.setEnabled(false)
        isEnabled = false
    }

    private fun applyOffsets() {
        val freqs = equalizerHelper.getBandCenterFrequencies()
        if (freqs.isEmpty()) {
            Log.w(TAG, "EqualizerHelper has no attached bands; skipping dialogue-boost overlay")
            return
        }
        val offsets = computeOffsets(freqs)
        equalizerHelper.setBandOffsets(offsets)
    }

    private fun clearOffsets() {
        equalizerHelper.setBandOffsets(emptyMap())
    }

    /**
     * Pure function: maps a list of band center frequencies (in Hz) to
     * millibel offsets that boost the vocal range. Exposed for testing.
     */
    internal fun computeOffsets(centerFreqsHz: List<Int>): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        val (coreVocal, upperHarmonics, lowMidWarmth) = when (strength) {
            EffectStrength.NONE -> Triple(0, 0, 0)
            EffectStrength.LOW -> Triple(300, 150, 100)
            EffectStrength.MODERATE -> Triple(600, 300, 200)
            EffectStrength.HIGH -> Triple(900, 450, 300)
        }

        centerFreqsHz.forEachIndexed { band, freqHz ->
            // Equalizer.getCenterFreq returns millihertz; the helper
            // converts to Hz before exposing, so we work in Hz here.
            // Vocal range maps to 1–4 kHz core, 4–8 kHz presence; the
            // 500 Hz–1 kHz band gets a small warmth lift to keep
            // male voices from thinning. Ranges are non-overlapping
            // (order-independent): the 4 kHz boundary belongs to core
            // vocal, so harmonics start at 4_001.
            val level = when (freqHz) {
                in 500..999 -> lowMidWarmth
                in 1_000..4_000 -> coreVocal
                in 4_001..8_000 -> upperHarmonics
                else -> 0
            }
            if (level != 0) result[band] = level
        }
        return result
    }

    companion object {
        private const val TAG = "DialogueBoostHelper"
    }
}
