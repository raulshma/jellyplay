package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.data.playback.EffectStrengthMapping
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.feature.player.video.engine.AudioEffectsConfig
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * The desktop mpv `af` (audio filter chain) builder for the audio player's
 * effect stack (wave 14C) — the mpv-side twin of the Android
 * `AudioEffectChain`/`AudioEffectsProcessor` DSP half. Pure functions, no
 * mpv handle: [MpvDesktopEngine] applies the produced strings as runtime
 * properties (`af`, `audio-channels`, `pitch`), which mpv re-inits live.
 *
 * ## Android effect → mpv filter parity table
 *
 * Every Android application path (`AudioEffectsProcessor` for the media3
 * audio player + the mpv branches of Android's `MpvPlayerEngine`) was the
 * semantics source; filters were probed present in the bundled libmpv
 * (`tools/mpv`, full libavfilter build) before committing to them.
 *
 * | Android effect (mechanism) | mpv equivalent (this chain) | Notes |
 * |---|---|---|
 * | Equalizer (priority-0 `Equalizer`, 10 bands) | one `lavfi=[equalizer=f=<Hz>:t=q:w=1:g=<dB>]` peaking stage per non-zero band, `EqualizerSettings.BAND_FREQUENCIES` | band levels are MILLIBELS everywhere in the shared model (preset tables like 600 = 6 dB, UI slider ±1500 with a `/100.0` dB label, dialogue offsets in mB), so `g = level_mB / 100` clamped ±15 dB. Divergence, deliberate: Android's `EqualizerHelper` multiplies the mB levels by ANOTHER 100 and clamps, which pins every non-zero band at full-scale ±15 dB — the desktop applies the levels the UI actually labels |
 * | Dialogue boost (EQ vocal offsets + 80 Hz high-pass in-sink) | offsets folded into the EQ stages (same ±15 dB clamp as `EqualizerHelper` intends) + `lavfi=[highpass=f=80]` | the Android MPV path already used `highpass=f=80`; the co-enable rule (EQ live while EITHER EQ or boost is on) is preserved |
 * | Bass boost (`BassBoost` strength 0..1000) | `lavfi=[bass=g=<dB>:f=100]` low-shelf, `strength/1000 * 9 dB` | Android's strength scale has no exact filter analog; linear 0–9 dB mapping (LOW 3.6 / MODERATE 6.3 / HIGH 9.0), documented approximation |
 * | Virtualizer (`Virtualizer` strength 0..1000) | `lavfi=[extrastereo=m=<1 + strength/1000>]` | stereo-image widening; `crossfeed` (binaural) was rejected as a different effect. Documented approximation |
 * | Reverb (`PresetReverb` presets 1..6) | `lavfi=[aecho=<preset table>]` | no true reverb filter in libavfilter (`afir` needs an IR file); echo-delay tables approximate room→hall→plate. Documented approximation |
 * | Night mode (`player.volume` cut + `LoudnessEnhancer` boost) | single `lavfi=[volume=<net dB>]` (`20·log10(attenuation) + documented gain dB`) | same `EffectStrengthMapping` attenuation table. Its gain table (`nightModeGainMb`) is DOCUMENTED as +1.5/+3.0/+4.5 dB, but the constants (1500/3000/4500 mB) are a 10× literal `LoudnessEnhancer` gain (+30 dB at MODERATE — audibly broken as a night mode); the desktop applies the documented dB labels. Micro-divergence: the engine `volume` property (user slider) is NOT clobbered — Android overwrites it |
 * | ReplayGain TRACK/ALBUM (`ReplayGainAudioProcessor` per-track gain + pre-amp) | `lavfi=[volume=<effective dB>]` from [AudioEffectsConfig.replayGainEffectiveDb] | the host manager pre-computes the Android rule (`trackGain ?: 0 + preAmp`; ALBUM+shuffled → 0) |
 * | ReplayGain DYNAMIC (`DynamicsCompressorAudioProcessor`) | `acompressor=ratio=3:threshold=0.05:attack=10:release=200` | exact parameter match — these ARE the defaults the Android processor documents itself against, and the same string the Android MPV path emits |
 * | Channel mix (in-sink ITU BS.775 matrix) | mpv `audio-channels` property (`stereo`/`mono`/`5.1`/`auto`) | the Android MPV path's own mapping (`channelMixModeToAudioChannels`), not an `af` filter |
 * | L/R balance (`BalanceAudioProcessor` per-channel gains) | `lavfi=[pan=stereo|c0=c0*<gL>|c1=c1*<gR>]` | only when the live output is STEREO: `pan` pins the output layout, so a fixed layout string would force downmixes/upmixes. Mono = no-op (Android deactivates too); other layouts = documented cut |
 * | Pitch (media3 `PlaybackParameters(speed, 2^(st/12))`) | mpv `pitch` runtime property = `2^(st/12)` | set via [pitchRatio], not in the `af` chain; `rubberband` filter exists in this build but the native property is live-settable without a chain rebuild |
 * | Visualizer (`AudioVisualizerHelper` fft/waveform taps) | none | no mpv equivalent without an in-sink PCM tap; the desktop manager keeps `fftData`/`waveformData` empty (declared divergence, unchanged) |
 */
internal object DesktopAudioEffectChain {

    /** Android `DynamicsCompressorAudioProcessor` default params, as the Android MPV path emits them. */
    internal const val DYNAMIC_COMPRESSOR_FILTER: String =
        "acompressor=ratio=3:threshold=0.05:attack=10:release=200"

    /** Below the ~85 Hz fundamental of the lowest male voice (HighPassFilterAudioProcessor default). */
    internal const val DIALOGUE_HIGH_PASS_FILTER: String = "highpass=f=80"

    /**
     * Builds the full `af` chain string for [config], or `null` when nothing
     * is active (caller clears the chain). [outputChannelCount] is the LIVE
     * mpv output channel count (`audio-params/channel-count`), `null` when
     * unknown (no file loaded / demuxer not resolved yet) — the balance stage
     * needs it because `pan` pins the output layout.
     */
    fun buildAfChain(config: AudioEffectsConfig, outputChannelCount: Int?): String? {
        val filters = mutableListOf<String>()

        // Gain staging first (Android applies normalization/volume before
        // tonal stages), then tone, then spatial, balance LAST (steers the
        // final mix).
        if (config.nightModeEnabled) {
            filters += "lavfi=[volume=${"%.2f".format(Locale.ROOT, nightModeNetDb(config))}dB]"
        }

        when (config.audioNormalizationMode) {
            AudioNormalizationMode.DYNAMIC -> filters += "lavfi=[$DYNAMIC_COMPRESSOR_FILTER]"
            AudioNormalizationMode.TRACK,
            AudioNormalizationMode.ALBUM,
            -> {
                val db = config.replayGainEffectiveDb
                if (db != null && abs(db) > GAIN_EPSILON) {
                    filters += "lavfi=[volume=${"%.2f".format(Locale.ROOT, db)}dB]"
                }
            }
            AudioNormalizationMode.NONE -> Unit
        }

        // EQ + dialogue boost share the stages exactly like they share the
        // Android priority-0 Equalizer: enabled while EITHER is on, user
        // levels + (when boosting) vocal offsets folded per band. Levels and
        // offsets are both MILLIBELS (see the parity table).
        val eqLive = config.equalizerEnabled || config.dialogueBoostEnabled
        if (eqLive) {
            val offsets = if (config.dialogueBoostEnabled) {
                dialogueOffsets(config.dialogueBoostStrength)
            } else {
                emptyMap()
            }
            config.equalizerSettings.bandLevels.forEachIndexed { index, levelMb ->
                val mB = (levelMb + (offsets[index] ?: 0)).coerceIn(-1500, 1500)
                if (mB != 0) {
                    val freq = EqualizerSettings.BAND_FREQUENCIES.getOrElse(index) { 1000 }
                    filters += "lavfi=[equalizer=f=${freq}:t=q:w=1:g=${"%.2f".format(Locale.ROOT, mB / 100.0)}]"
                }
            }
        }
        if (config.dialogueBoostEnabled) {
            filters += "lavfi=[$DIALOGUE_HIGH_PASS_FILTER]"
        }

        if (config.bassBoostEnabled) {
            filters += "lavfi=[bass=g=${"%.1f".format(Locale.ROOT, bassGainDb(config.bassBoostStrength))}:f=100]"
        }

        if (config.virtualizerEnabled) {
            filters += "lavfi=[extrastereo=m=${"%.2f".format(Locale.ROOT, virtualizerWidth(config.virtualizerStrength))}]"
        }

        reverbEcho(config.reverbPreset)?.let { filters += "lavfi=[aecho=$it]" }

        if (config.lrBalance != 0f && outputChannelCount == STEREO_CHANNEL_COUNT) {
            balancePan(config.lrBalance)?.let { filters += "lavfi=[$it]" }
        }

        return filters.takeIf { it.isNotEmpty() }?.joinToString(",")
    }

    /**
     * Channel-mix mode → mpv `audio-channels` value. Exact copy of the
     * Android MPV path's `channelMixModeToAudioChannels`.
     */
    fun channelMixToAudioChannels(mode: ChannelMixMode, enabled: Boolean): String =
        if (!enabled) {
            "auto"
        } else {
            when (mode) {
                ChannelMixMode.STEREO_DOWNMIX -> "stereo"
                ChannelMixMode.MONO -> "mono"
                ChannelMixMode.SURROUND_UPMIX -> "5.1"
                ChannelMixMode.AUTO -> "auto"
            }
        }

    /**
     * Pitch shift ratio for the mpv `pitch` property:
     * `2^(semitones / 12)`, clamped to mpv's documented `0.01..100` range.
     */
    fun pitchRatio(semitones: Float): Double {
        if (abs(semitones) < GAIN_EPSILON) return 1.0
        return 2.0.pow(semitones / 12.0).coerceIn(0.01, 100.0)
    }

    /**
     * Dialogue-boost vocal-band offsets in millibels —
     * `DialogueBoostHelper.computeOffsets` evaluated over the shared band
     * list (the Android helper derives the same values from the attached
     * equalizer's center frequencies, which are these bands).
     */
    internal fun dialogueOffsets(strength: EffectStrength): Map<Int, Int> {
        val (coreVocal, upperHarmonics, lowMidWarmth) = when (strength) {
            EffectStrength.NONE -> Triple(0, 0, 0)
            EffectStrength.LOW -> Triple(300, 150, 100)
            EffectStrength.MODERATE -> Triple(600, 300, 200)
            EffectStrength.HIGH -> Triple(900, 450, 300)
        }
        val result = mutableMapOf<Int, Int>()
        EqualizerSettings.BAND_FREQUENCIES.forEachIndexed { band, freqHz ->
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

    /**
     * Night-mode net gain in dB: the `EffectStrengthMapping` volume cut +
     * the DOCUMENTED loudness-compensation labels (+1.5/+3.0/+4.5 dB).
     * `nightModeGainMb`'s mB constants are the documented dB ×1000 (the
     * same 10× literal the parity table calls out — LOW=1500 for 1.5 dB),
     * so /1000 recovers the label. Do NOT "reconcile" this to /100: the
     * labels read as mB/100 would be a +30 dB boost at MODERATE.
     */
    internal fun nightModeNetDb(config: AudioEffectsConfig): Double =
        twentyLog10(EffectStrengthMapping.nightModeVolumeAttenuation(config.nightModeStrength)) +
            EffectStrengthMapping.nightModeGainMb(config.nightModeStrength) / 1000.0

    /** Bass low-shelf gain: `strength/1000 * 9 dB` (see parity table). */
    internal fun bassGainDb(strength: EffectStrength): Double =
        when (strength) {
            EffectStrength.NONE -> 0.0
            EffectStrength.LOW -> 3.6
            EffectStrength.MODERATE -> 6.3
            EffectStrength.HIGH -> 9.0
        }

    /** Stereo-width multiplier for `extrastereo`: 1 + strength/1000, clamped 1..2. */
    internal fun virtualizerWidth(strength: Int): Double =
        (1.0 + strength.coerceIn(0, 1000) / 1000.0).coerceIn(1.0, 2.0)

    /**
     * Preset-reverb → `aecho` in-gain:in-gain:delays:decays tables,
     * ordered small room → large hall → plate (documented approximation of
     * Android's `PresetReverb`; no true reverb filter exists in libavfilter).
     */
    internal fun reverbEcho(preset: ReverbPreset): String? = when (preset) {
        ReverbPreset.NONE -> null
        ReverbPreset.SMALL_ROOM -> "0.85:0.88:40:0.30"
        ReverbPreset.MEDIUM_ROOM -> "0.80:0.90:60|70:0.35|0.25"
        ReverbPreset.LARGE_ROOM -> "0.75:0.90:90|130:0.40|0.30"
        ReverbPreset.MEDIUM_HALL -> "0.70:0.85:120|180|240:0.35|0.28|0.18"
        ReverbPreset.LARGE_HALL -> "0.65:0.85:180|260|340:0.40|0.30|0.20"
        ReverbPreset.PLATE -> "0.90:0.85:60|120:0.50|0.35"
    }

    /**
     * L/R balance → `pan` stage for a STEREO output, with the exact
     * `BalanceAudioProcessor` gain rule (`+` attenuates the left side).
     * Returns null for anything but stereo — `pan` pins the output layout.
     */
    internal fun balancePan(balance: Float): String? {
        val b = balance.coerceIn(-1f, 1f)
        if (abs(b) < GAIN_EPSILON) return null
        val left = if (b >= 0f) 1f - b else 1f
        val right = if (b >= 0f) 1f else 1f + b
        return "pan=stereo|c0=c0*${"%.3f".format(Locale.ROOT, left)}|c1=c1*${"%.3f".format(Locale.ROOT, right)}"
    }

    private fun twentyLog10(v: Float): Double = 20.0 * log10(v.toDouble())

    private const val STEREO_CHANNEL_COUNT = 2

    /** Below this many dB a gain stage is a no-op (ALBUM+shuffled → 0 parity). */
    internal const val GAIN_EPSILON = 0.001f
}
