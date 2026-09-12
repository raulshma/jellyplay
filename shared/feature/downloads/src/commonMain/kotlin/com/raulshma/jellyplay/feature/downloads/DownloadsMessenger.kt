package com.raulshma.jellyplay.feature.downloads

import androidx.compose.runtime.Composable

/**
 * One-shot user-feedback seam for the downloads delete messages. Android posts
 * through the app-wide UserMessageBus — that bus lives in
 * shared:core:ui (the cutover dissolved the legacy :core:ui module) — while desktop has
 * no message host yet, so the actual returns null and messages drop (livetv
 * conveyor's LiveTvMessenger pattern; messages are already resolved [String]s
 * at the call site, so the deferred UiText resource-id machinery stays legacy).
 */
internal interface DownloadsMessenger {
    fun info(message: String)
    fun error(message: String)
}

@Composable
internal expect fun rememberDownloadsMessenger(): DownloadsMessenger?
