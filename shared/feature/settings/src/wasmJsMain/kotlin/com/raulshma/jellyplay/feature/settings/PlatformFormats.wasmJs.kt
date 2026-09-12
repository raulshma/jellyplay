package com.raulshma.jellyplay.feature.settings

import kotlin.math.round

internal actual fun formatOneDecimal(value: Double): String {
    // HALF_UP at the first decimal through integer math ("%.1f" replacement,
    // the editor's PlatformFormats wasm actual shape), sign applied
    // symmetrically.
    val magnitude = round(kotlin.math.abs(value) * 10).toLong()
    val rendered = "${magnitude / 10}.${magnitude % 10}"
    return if (value < 0) "-$rendered" else rendered
}

internal actual fun formatTwoDecimals(value: Double): String {
    val magnitude = round(kotlin.math.abs(value) * 100).toLong()
    val whole = magnitude / 100
    val frac = (magnitude % 100).toString().padStart(2, '0')
    val rendered = "$whole.$frac"
    return if (value < 0) "-$rendered" else rendered
}

// The settings resource patterns carry a single positional slot ("%1$d min"),
// so literal slot substitution stands in for java.text formatting.
private val INT_SLOT = Regex("%(1\$)?d")

internal actual fun formatIntPattern(pattern: String, value: Int): String =
    pattern.replace(INT_SLOT, value.toString())

internal actual fun formatSignedInt(value: Int): String =
    if (value >= 0) "+$value" else value.toString()
