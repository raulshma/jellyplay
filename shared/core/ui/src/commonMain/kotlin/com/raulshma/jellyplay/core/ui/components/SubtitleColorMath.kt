package com.raulshma.jellyplay.core.ui.components

// Pure-Kotlin HSV + hex color math shared by the subtitle free-form color
// picker (SubtitleFreeFormColorPickerDialog). Lives in core-ui so both the
// player's subtitle style sheet and the Settings subtitle rows can use it;
// identical math to android.graphics.Color's HSV pair (hue [0..360),
// saturation/value [0..1], alpha fixed at 255 — the old HSVToColor(hsv)
// single-arg overload's default).

/**
 * Converts an ARGB color to HSV. Alpha is ignored; the result is a
 * `FloatArray(3)` of hue `[0..360)`, saturation `[0..1]`, value `[0..1]`.
 * Achromatic inputs (delta == 0) pin hue at 0; black (max == 0) pins
 * saturation at 0.
 */
fun subtitleColorToHsv(color: Int): FloatArray {
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
fun subtitleHsvToColor(hsv: FloatArray): Int {
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

/**
 * Parses a hex color string into an ARGB int, or null when malformed.
 * Accepts an optional leading `#` (or `0x`) and 3, 6, or 8 hex digits —
 * 3-digit expands like CSS (`#F00` → `#FFFF0000`), 6-digit is opaque,
 * 8-digit is `AARRGGBB`. Tolerates surrounding whitespace. Paste-friendly
 * for values copied straight out of an mpv.conf line.
 */
fun parseHexColorOrNull(input: String?): Int? {
    var s = input?.trim()?.removePrefix("#")?.removePrefix("0x") ?: return null
    if (s.length == 3 && s.all { it.isHex() }) {
        s = s.map { "$it$it" }.joinToString("")
    }
    if (s.length !in listOf(6, 8) || s.any { !it.isHex() }) return null
    val value = s.toLong(16).toInt()
    return if (s.length == 6) 0xFF shl 24 or value else value
}

private fun Char.isHex(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

/**
 * Formats an ARGB int as a hex string — `#RRGGBB` when opaque, `#AARRGGBB`
 * otherwise (mpv's `#AARRGGBB` order, alpha first).
 */
fun formatHexColor(color: Int): String {
    val alpha = color ushr 24
    val rgb = color and 0x00FFFFFF
    return if (alpha == 0xFF) {
        "#%06X".format(rgb)
    } else {
        "#%08X".format(color)
    }
}
