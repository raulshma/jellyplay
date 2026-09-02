package com.raulshma.jellyplay.core.ui.components

/**
 * Logging seam for shared components (android.util.Log on Android, stderr on
 * desktop). Keep call sites non-critical: warnings only.
 */
internal expect fun logUiWarning(tag: String, message: String, error: Throwable? = null)

internal expect fun logUiDebug(tag: String, message: String)
