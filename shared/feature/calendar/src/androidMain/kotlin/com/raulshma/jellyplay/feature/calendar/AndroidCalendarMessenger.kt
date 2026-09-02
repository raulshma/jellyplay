package com.raulshma.jellyplay.feature.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus

@Composable
internal actual fun rememberCalendarMessenger(): CalendarMessenger? {
    val bus = LocalUserMessageBus.current
    return remember(bus) {
        object : CalendarMessenger {
            override fun info(message: String) = bus.info(message)
        }
    }
}
