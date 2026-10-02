package com.raulshma.jellyplay.feature.player.video.engine.mpv

import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import kotlin.math.roundToInt

/**
 * The ONE mpv `vf` (video filter chain) builder for the player's
 * video-effects sheet, applied by BOTH mpv engines — the desktop
 * `MpvDesktopEngine` (its former desktop-app-local `DesktopVideoEffectChain`,
 * the tested reference, moved here verbatim) and the Android
 * `MpvPlayerEngine` (whose `applyVideoFilters` inline twin this replaces).
 * Pure functions, no mpv handle: the engines apply the produced string as the
 * runtime `vf` property, which mpv re-inits live (and the rotation via the
 * separate `video-rotate` property).
 *
 * Lives in player-contract commonMain beside [MpvStyleMapping] /
 * [MpvStatsProjection] / [MpvSubtitleStyleApplier] — the mpv fold-applier
 * family — because it is pure commonMain policy over a `core.model` type (the
 * `MpvConfigMapping` precedent for going public: consumers stay the two
 * engine adapters and the pinning test; not a stable API surface).
 *
 * ## Shared effect → mpv filter parity table
 *
 * The Android MPV engine's former `applyVideoFilters` was the semantics
 * source; every filter was verified present in the bundled libmpv before
 * committing to it (binary scan of `tools/mpv/libmpv-2.dll` for the
 * filter-name strings + the live `vf` property probe in the engine tests —
 * `eq`, `unsharp`, lavfi `gblur` all resolve).
 *
 * | Shared effect ([VideoEffectsConfig]) | mpv equivalent (this chain) | Notes |
 * |---|---|---|
 * | brightness (−1..1, 0 neutral) | `eq=brightness=<v>` | one `eq` stage carries every non-neutral tonal knob, exactly like Android |
 * | contrast (0.5..2, 1 neutral) | `eq=contrast=<v>` | |
 * | saturation (0..3, 1 neutral) | `eq=saturation=<v>` | |
 * | hue (0..360°, 0 neutral) | `eq=hue=<v>` | value parity with Android (which writes the raw slider degrees); mpv wraps/clamps outside its own −180..180 comfort zone — same behavior on both platforms |
 * | redGain / greenGain / blueGain (0..2, 1 neutral) | `eq=gamma_r=<v>` / `gamma_g` / `gamma_b` | Android's own mapping: a channel *gain* knob is implemented as that channel's *gamma* — the name lies on both platforms alike, kept for parity |
 * | sharpness (0..1, 0 off) | `unsharp=5:5:<amount>` | amount = sharpness × 1.5 clamped 0.5..3.0 (Android's exact scaling; 5:5 is its fixed luma matrix) |
 * | gaussianBlur (0..10, 0 off) | `lavfi=[gblur=sigma=<blur/2>]` | sigma halved to keep the 0..10 slider sensible, Android's exact rule |
 * | rotationDegrees (−180..180) | NOT a filter — `video-rotate` property (see [rotationDegrees]) | rounded to the nearest 90° and normalized to 0..359; mpv rotates the whole output, filters cannot |
 *
 * Number formatting: `String.format` under a comma-decimal locale produces
 * `0,5` and mpv rejects the whole chain write. The desktop former `Locale.ROOT` spelling
 * and this commonMain [fmt] are byte-identical — [fmt] re-implements Java's
 * `%.2f` float semantics exactly (HALF_UP over the float's EXACT binary
 * expansion, sign kept even at zero — e.g. `0.145f → "0.14"` but
 * `0.075f → "0.08"`) without `java.util.Locale`. Declared unification: the
 * Android twin interpolated raw `Float.toString` values (`"0.2"`, `"0.05"`);
 * it now emits the desktop's two-decimal strings — an mpv-invisible spelling
 * change, the chain semantics and stage order untouched.
 *
 * Pinned once for both platforms by `MpvVideoEffectChainTest` (the moved
 * `DesktopVideoEffectChainTest`, same cases); the live property application
 * stays engine-tested (`MpvDesktopEngineVideoTest`).
 */
public object MpvVideoEffectChain {

    /**
     * Builds the full `vf` chain string for [config], or `null` when no
     * filter applies (caller clears the chain). Rotation is deliberately NOT
     * part of the chain — see [rotationDegrees].
     */
    public fun buildVfChain(config: VideoEffectsConfig): String? {
        val filters = mutableListOf<String>()

        // Tonal stage first (eq), then sharpen, then blur — the Android
        // engine's exact stage order.
        val hasBrightness = config.brightness != 0f
        val hasContrast = config.contrast != 1f
        val hasSaturation = config.saturation != 1f
        val hasHue = config.hue != 0f
        val hasRgbGain = config.redGain != 1f || config.greenGain != 1f || config.blueGain != 1f
        if (hasBrightness || hasContrast || hasSaturation || hasHue || hasRgbGain) {
            val eqParts = mutableListOf<String>()
            if (hasBrightness) eqParts += "brightness=${fmt(config.brightness)}"
            if (hasContrast) eqParts += "contrast=${fmt(config.contrast)}"
            if (hasSaturation) eqParts += "saturation=${fmt(config.saturation)}"
            if (hasHue) eqParts += "hue=${fmt(config.hue)}"
            if (config.redGain != 1f) eqParts += "gamma_r=${fmt(config.redGain)}"
            if (config.greenGain != 1f) eqParts += "gamma_g=${fmt(config.greenGain)}"
            if (config.blueGain != 1f) eqParts += "gamma_b=${fmt(config.blueGain)}"
            filters += "eq=${eqParts.joinToString(":")}"
        }

        if (config.sharpness > 0f) {
            filters += "unsharp=5:5:${fmt((config.sharpness * 1.5f).coerceIn(0.5f, 3.0f))}"
        }

        if (config.gaussianBlur > 0f) {
            filters += "lavfi=[gblur=sigma=${fmt(config.gaussianBlur / 2f)}]"
        }

        return filters.takeIf { it.isNotEmpty() }?.joinToString(",")
    }

    /**
     * Rotation as mpv's `video-rotate` property value: the raw degrees
     * rounded to the nearest multiple of 90 and normalized to 0..359 —
     * mpv only supports right-angle output rotation, and Android rounds
     * through the identical `round(x/90)*90 % 360` rule.
     */
    public fun rotationDegrees(config: VideoEffectsConfig): Int {
        val rawDiscrete = (config.rotationDegrees / 90f).roundToInt() * 90
        return ((rawDiscrete % 360) + 360) % 360
    }

    /**
     * The two-decimal, '.'-decimal-separator rendering of [value], byte
     * identical to the JVM's `"%.2f".format(Locale.ROOT, value)` over every
     * finite float — without `java.util.Locale` (unavailable in commonMain).
     *
     * Java's formatter rounds the float's EXACT binary expansion to two
     * decimals, HALF_UP (away from zero on exact ties), keeping the sign even
     * when the rounded magnitude is zero (`-0.001f → "-0.00"`). The shortest
     * `Float.toString` digits are NOT the rounding source (`0.145f` prints
     * `0.14` — its exact expansion is `0.14499999…` — while its shortest
     * repr is `"0.145"`), so this rounds the exact value: every IEEE-754
     * binary32 is `m × 2^e` for the 24-bit mantissa [toRawBits] exposes, and
     * the hundredths of that are `m × 100 / 2^d` — one Long quotient with a
     * HALF_UP remainder test (`2·r ≥ 2^d`), exact by construction. For
     * magnitudes so small the shifted divisor leaves the Long range the
     * value is `≪ 0.005` and cannot tie (a hundredths tie needs
     * `|value| = (2k+1)/200`), so it rounds to zero; huge magnitudes
     * (`e > 32`, i.e. `|value| ≥ 2^55`) cannot arise from any slider domain
     * this chain formats (−1..360) and degrade to the shortest repr.
     */
    private fun fmt(value: Float): String {
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
        val bits = value.toRawBits()
        val negative = bits < 0
        val mantissa: Long
        val exponent: Int
        val expField = (bits ushr 23) and 0xFF
        if (expField == 0) {
            mantissa = (bits and 0x007FFFFF).toLong()
            exponent = -149
        } else {
            mantissa = (bits and 0x007FFFFF).toLong() or 0x00800000L
            exponent = expField - 150
        }
        val hundredths: Long = when {
            exponent >= 0 -> mantissa * 100L shl exponent.coerceAtMost(32)
            else -> {
                val shift = -exponent
                val numerator = mantissa * 100L
                if (shift > 62) {
                    // divisor ≥ 2^63 > numerator·2: the quotient is zero and
                    // the remainder is below half — rounds to zero, no tie.
                    0L
                } else {
                    val divisor = 1L shl shift
                    val quotient = numerator / divisor
                    if (numerator % divisor * 2L >= divisor) quotient + 1 else quotient
                }
            }
        }
        val sign = if (negative) "-" else ""
        return "$sign${hundredths / 100}.${(hundredths % 100).let { if (it < 10) "0$it" else it.toString() }}"
    }
}
