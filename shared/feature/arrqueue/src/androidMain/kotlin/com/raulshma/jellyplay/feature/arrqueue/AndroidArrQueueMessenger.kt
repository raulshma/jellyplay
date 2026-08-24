package com.raulshma.jellyplay.feature.arrqueue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus

@Composable
internal actual fun rememberArrQueueMessenger(): ArrQueueMessenger? {
    val bus = LocalUserMessageBus.current
    return remember(bus) {
        object : ArrQueueMessenger {
            override fun info(message: String) = bus.info(message)
            override fun error(message: String) = bus.error(message)
        }
    }
}
