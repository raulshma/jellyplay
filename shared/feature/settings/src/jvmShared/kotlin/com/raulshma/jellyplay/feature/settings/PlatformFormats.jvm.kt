package com.raulshma.jellyplay.feature.settings

// formatOneDecimal folded onto core/ui's DurationFormatter seam (public
// formatOneDecimal); formatIntPattern onto core/ui's shared pattern seam
// (PlatformTime.kt) — both actuals deleted from here.

internal actual fun formatTwoDecimals(value: Double): String = "%.2f".format(value)

internal actual fun formatSignedInt(value: Int): String = "%+d".format(value)
