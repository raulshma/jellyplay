package com.raulshma.jellyplay.feature.editor

// formatOneDecimal folded onto core/ui's public DurationFormatter seam
// (the façade here is gone); formatIntPattern onto core/ui's shared pattern
// seam (PlatformTime.kt); only the editor-specific string-slot read keeps an
// actual.

internal actual fun formatStringPattern(pattern: String, value: String): String = pattern.format(value)
