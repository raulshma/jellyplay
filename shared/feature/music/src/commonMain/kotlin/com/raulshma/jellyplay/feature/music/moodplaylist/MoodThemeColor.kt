package com.raulshma.jellyplay.feature.music.moodplaylist

import androidx.compose.ui.graphics.Color

/**
 * Parses a mood playlist's `themeColorHex` ("#RRGGBB" / "#AARRGGBB" — the only
 * formats the presets emit) into a Compose [Color]: the commonMain stand-in
 * for the legacy `android.graphics.Color.parseColor` call, which has no KMP
 * equivalent. Anything else returns null so callers keep their
 * primary-container fallback — the same outcome the legacy try/catch produced.
 */
internal fun parseMoodThemeColor(hex: String): Color? {
    if (hex.length != 7 && hex.length != 9) return null
    if (hex.first() != '#') return null
    val argb = if (hex.length == 7) {
        0xFF000000L or (hex.substring(1).toLongOrNull(16) ?: return null)
    } else {
        hex.substring(1).toLongOrNull(16) ?: return null
    }
    return Color(argb.toInt())
}
