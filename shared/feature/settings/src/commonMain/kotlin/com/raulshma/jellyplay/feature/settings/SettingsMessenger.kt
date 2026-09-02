package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable

/**
 * One-shot user-feedback seam for the settings rows (clipboard-copied hints,
 * advanced-settings toggles). Android posts through the app-wide
 * UserMessageBus — that bus still lives in the legacy Android-only :core:ui
 * shim until its own conveyor move — while desktop has no message host yet,
 * so the actual returns null and messages drop (livetv conveyor's
 * LiveTvMessenger pattern; messages are already resolved [String]s at the
 * call site).
 */
internal interface SettingsMessenger {
    fun info(message: String)
}

@Composable
internal expect fun rememberSettingsMessenger(): SettingsMessenger?
