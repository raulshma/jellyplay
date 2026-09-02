package com.raulshma.jellyplay.feature.arrqueue

import androidx.compose.runtime.Composable

/**
 * One-shot user-feedback seam for the *arr queue action messages. Android
 * posts through the app-wide UserMessageBus — that bus still lives in the
 * legacy Android-only :core:ui shim until its own conveyor move — while
 * desktop has no message host yet, so the actual returns null and messages
 * drop (livetv conveyor's LiveTvMessenger pattern; messages are already
 * resolved [String]s at the call site, so the deferred UiText resource-id
 * machinery stays legacy).
 */
internal interface ArrQueueMessenger {
    fun info(message: String)
    fun error(message: String)
}

@Composable
internal expect fun rememberArrQueueMessenger(): ArrQueueMessenger?
