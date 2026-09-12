package com.raulshma.jellyplay.feature.editor

internal actual fun formatOneDecimal(value: Double): String {
    // HALF_UP at the first decimal through integer math ("%.1f" replacement,
    // core:ui PlatformTime shape), sign applied symmetrically.
    val magnitude = kotlin.math.round(kotlin.math.abs(value) * 10).toLong()
    val rendered = "${magnitude / 10}.${magnitude % 10}"
    return if (value < 0) "-$rendered" else rendered
}

// The editor's resource patterns carry a single positional slot ("%1$d downloads"
// / "%1$sfps"), so literal slot substitution stands in for java.text formatting.
private val INT_SLOT = Regex("%(1\\$)?d")
private val STRING_SLOT = Regex("%(1\\$)?s")

internal actual fun formatIntPattern(pattern: String, value: Int): String =
    pattern.replace(INT_SLOT, value.toString())

internal actual fun formatStringPattern(pattern: String, value: String): String =
    pattern.replace(STRING_SLOT, value)
