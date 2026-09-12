package com.raulshma.jellyplay.feature.player.audio

import kotlin.math.round

internal actual fun formatOneDecimal(value: Double): String {
    // HALF_UP at the first decimal through integer math, sign applied
    // symmetrically (the editor's PlatformFormats wasm actual shape).
    val magnitude = round(kotlin.math.abs(value) * 10).toLong()
    val rendered = "${magnitude / 10}.${magnitude % 10}"
    return if (value < 0) "-$rendered" else rendered
}
