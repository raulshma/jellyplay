package com.raulshma.jellyplay.feature.editor

internal actual fun formatOneDecimal(value: Double): String = "%.1f".format(value)

internal actual fun formatIntPattern(pattern: String, value: Int): String = pattern.format(value)

internal actual fun formatStringPattern(pattern: String, value: String): String = pattern.format(value)
