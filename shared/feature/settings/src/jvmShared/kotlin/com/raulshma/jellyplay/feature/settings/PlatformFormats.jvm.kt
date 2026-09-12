package com.raulshma.jellyplay.feature.settings

internal actual fun formatOneDecimal(value: Double): String = "%.1f".format(value)

internal actual fun formatTwoDecimals(value: Double): String = "%.2f".format(value)

internal actual fun formatIntPattern(pattern: String, value: Int): String = pattern.format(value)

internal actual fun formatSignedInt(value: Int): String = "%+d".format(value)
