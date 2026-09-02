package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The desktop mpv `vf` (video filter chain) builder for the player's
 * video-effects sheet (wave 17B) — the video twin of wave 14C's
 * [DesktopAudioEffectChain], and the mpv-side twin of the Android
 * `MpvPlayerEngine.applyVideoFilters` half. Pure functions, no mpv handle:
 * [MpvDesktopEngine] applies the produced string as the runtime `vf`
 * property, which mpv re-inits live (and the rotation via the separate
 * `video-rotate` property).
 *
 * ## Shared effect → mpv filter parity table
 *
 * The Android MPV engine's `applyVideoFilters` was the semantics source;
 * every filter was verified present in the bundled libmpv before committing
 * to it (binary scan of `tools/mpv/libmpv-2.dll` for the filter-name
 * strings + the live `vf` property probe in the wave-17B engine tests —
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
 * Every number is formatted with [Locale.ROOT] — the wave-14C review lesson:
 * `String.format` under a comma-decimal locale produces `0,5` and mpv
 * rejects the whole chain write.
 */
internal object DesktopVideoEffectChain {

    /**
     * Builds the full `vf` chain string for [config], or `null` when no
     * filter applies (caller clears the chain). Rotation is deliberately NOT
     * part of the chain — see [rotationDegrees].
     */
    fun buildVfChain(config: VideoEffectsConfig): String? {
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
    fun rotationDegrees(config: VideoEffectsConfig): Int {
        val rawDiscrete = (config.rotationDegrees / 90f).roundToInt() * 90
        return ((rawDiscrete % 360) + 360) % 360
    }

    /** Locale-ROOT, two decimals — slider steps are ≥ 0.05 everywhere. */
    private fun fmt(value: Float): String = "%.2f".format(Locale.ROOT, value)
}
