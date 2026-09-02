package com.raulshma.jellyplay.feature.livetv

import androidx.compose.runtime.Composable

/**
 * One-shot user-feedback seam for the channel-detail record/cancel messages.
 * Android posts through the app-wide UserMessageBus — that bus still lives in
 * the legacy Android-only :core:ui shim until its own conveyor move — while
 * desktop has no message host yet, so the actual returns null and messages
 * drop (library conveyor's UserMessenger pattern; messages are already
 * resolved [String]s at the call site, so the deferred UiText resource-id
 * machinery stays legacy).
 */
internal interface LiveTvMessenger {
    fun info(message: String)
    fun error(message: String)
}

@Composable
internal expect fun rememberLiveTvMessenger(): LiveTvMessenger?
