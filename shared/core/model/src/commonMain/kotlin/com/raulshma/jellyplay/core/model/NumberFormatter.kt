package com.raulshma.jellyplay.core.model

/**
 * Fixed-precision decimal formatting that mirrors `String.format("%.Nf")` for
 * non-negative inputs (the only kind passed by current call sites: percentages,
 * framerates, bitrates, zoom factors, playback positions).
 *
 * Avoids the `Formatter` + `StringBuilder` + locale lookup cost of
 * `String.format`, which matters on hot paths like the player stats overlay
 * (~4 Hz, many rows) and per-seek MPV command strings.
 *
 * Rounding is HALF_UP (round-half-towards-positive-infinity), matching
 * `String.format`'s default `RoundingMode.HALF_UP` for non-negative values.
 */
fun formatFixed(value: Double, decimals: Int): String {
    require(decimals >= 0) { "decimals must be non-negative, was $decimals" }
    val scale = POW10[decimals]
    val scaled = value * scale
    val rounded = if (scaled >= 0) (scaled + 0.5).toLong() else (scaled - 0.5).toLong()
    val intPart = rounded / scale
    val fracPart = kotlin.math.abs(rounded % scale)
    val fracStr = fracPart.toString().padStart(decimals, '0')
    return "$intPart.$fracStr"
}

private val POW10 = longArrayOf(1L, 10L, 100L, 1_000L, 10_000L, 100_000L, 1_000_000L)
