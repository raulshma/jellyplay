package com.raulshma.jellyplay.feature.calendar

import androidx.compose.runtime.Composable

/**
 * One-shot user-feedback seam for the calendar's no-detail tap message. Android
 * posts through the app-wide UserMessageBus — that bus still lives in the legacy
 * Android-only :core:ui shim until its own conveyor move — while desktop has no
 * message host yet, so the actual returns null and the message drops (livetv
 * conveyor's LiveTvMessenger pattern; the message is already a resolved [String]
 * at the call site, so the deferred UiText resource-id machinery stays legacy).
 */
internal interface CalendarMessenger {
    fun info(message: String)
}

@Composable
internal expect fun rememberCalendarMessenger(): CalendarMessenger?
