package com.raulshma.jellyplay.feature.admin.statistics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus

@Composable
internal actual fun rememberAdminMessenger(): AdminMessenger? {
    val bus = LocalUserMessageBus.current
    return remember(bus) {
        object : AdminMessenger {
            override fun info(message: String) = bus.info(message)
        }
    }
}
