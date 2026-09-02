package com.raulshma.jellyplay.feature.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus

@Composable
internal actual fun rememberDownloadsMessenger(): DownloadsMessenger? {
    val bus = LocalUserMessageBus.current
    return remember(bus) {
        object : DownloadsMessenger {
            override fun info(message: String) = bus.info(message)
            override fun error(message: String) = bus.error(message)
        }
    }
}
