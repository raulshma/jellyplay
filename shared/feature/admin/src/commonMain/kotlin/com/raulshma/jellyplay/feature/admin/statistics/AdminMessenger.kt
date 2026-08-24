package com.raulshma.jellyplay.feature.admin.statistics

import androidx.compose.runtime.Composable

/**
 * One-shot user-feedback seam for the admin statistics screens (settings
 * conveyor's SettingsMessenger pattern). Android posts through the app-wide
 * UserMessageBus — that bus still lives in the legacy Android-only :core:ui
 * shim until its own conveyor move — while desktop has no message host yet,
 * so the actual returns null and messages drop. Messages are already resolved
 * [String]s at the call site.
 */
internal interface AdminMessenger {
    fun info(message: String)
}

@Composable
internal expect fun rememberAdminMessenger(): AdminMessenger?
