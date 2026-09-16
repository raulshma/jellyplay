package com.raulshma.jellyplay.feature.editor

// formatOneDecimal folded onto core/ui's DateLabels seam (PlatformFormats.kt
// façade); only the editor-specific resource-pattern reads keep actuals.

internal actual fun formatIntPattern(pattern: String, value: Int): String = pattern.format(value)

internal actual fun formatStringPattern(pattern: String, value: String): String = pattern.format(value)
