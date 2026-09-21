package com.raulshma.jellyplay.feature.editor

// formatOneDecimal folded onto core/ui's DateLabels seam (PlatformFormats.kt
// façade); formatIntPattern onto core/ui's shared pattern seam
// (PlatformTime.kt); only the editor-specific string-slot read keeps an actual.

internal actual fun formatStringPattern(pattern: String, value: String): String = pattern.format(value)
