package com.raulshma.jellyplay.feature.player.video

// KMP seam: android.graphics.Color's HSV pair, replaced by pure-Kotlin twins
// so the free-form subtitle color picker (SubtitleStyleControls' HSV dialog)
// stays commonMain. Identical math: hue [0..360), saturation/value [0..1],
// alpha fixed at 255 (the old HSVToColor(hsv) single-arg overload's default).
// Beside SubtitleStyleResolver (pure subtitle math home); pinned by
// SubtitleColorMathTest.

/**
 * Converts an ARGB color to HSV. Alpha is ignored; the result is a
 * `FloatArray(3)` of hue `[0..360)`, saturation `[0..1]`, value `[0..1]`.
 * Achromatic inputs (delta == 0) pin hue at 0; black (max == 0) pins
 * saturation at 0.
 */
internal fun subtitleColorToHsv(color: Int): FloatArray {
    val r = (color shr 16 and 0xFF) / 255f
    val g = (color shr 8 and 0xFF) / 255f
    val b = (color and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    val hue = if (h < 0f) h + 360f else h
    val saturation = if (max == 0f) 0f else delta / max
    return floatArrayOf(hue, saturation, max)
}

/**
 * Converts an HSV triple (hue `[0..360)`, saturation/value `[0..1]`) back to
 * an ARGB int with alpha forced to 255. Channel outputs are clamped to
 * `0..255` after truncation.
 */
internal fun subtitleHsvToColor(hsv: FloatArray): Int {
    val c = hsv[2] * hsv[1]
    val x = c * (1f - kotlin.math.abs((hsv[0] / 60f) % 2f - 1f))
    val m = hsv[2] - c
    val (r, g, b) = when {
        hsv[0] < 60f -> Triple(c, x, 0f)
        hsv[0] < 120f -> Triple(x, c, 0f)
        hsv[0] < 180f -> Triple(0f, c, x)
        hsv[0] < 240f -> Triple(0f, x, c)
        hsv[0] < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return (0xFF shl 24) or
        (((r + m) * 255f).toInt().coerceIn(0, 255) shl 16) or
        (((g + m) * 255f).toInt().coerceIn(0, 255) shl 8) or
        ((b + m) * 255f).toInt().coerceIn(0, 255)
}
