package com.raulshma.jellyplay.feature.editor

// formatOneDecimal folded onto core/ui's DateLabels seam (PlatformFormats.kt
// façade); only the editor-specific resource-pattern reads keep actuals.

// The editor's resource patterns carry a single positional slot ("%1$d downloads"
// / "%1$sfps"), so literal slot substitution stands in for java.text formatting.
private val INT_SLOT = Regex("%(1\\$)?d")
private val STRING_SLOT = Regex("%(1\\$)?s")

internal actual fun formatIntPattern(pattern: String, value: Int): String =
    pattern.replace(INT_SLOT, value.toString())

internal actual fun formatStringPattern(pattern: String, value: String): String =
    pattern.replace(STRING_SLOT, value)
