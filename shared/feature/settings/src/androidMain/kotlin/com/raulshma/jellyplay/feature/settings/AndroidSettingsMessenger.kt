package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus

@Composable
internal actual fun rememberSettingsMessenger(): SettingsMessenger? {
    val bus = LocalUserMessageBus.current
    return remember(bus) {
        object : SettingsMessenger {
            override fun info(message: String) = bus.info(message)
        }
    }
}
