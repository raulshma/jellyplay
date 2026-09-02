package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Desktop actual of the [PlatformIntents] seam: URLs browse through the AWT
 * `Desktop`; there is no system share sheet (the send-logs row never reaches
 * [shareLogFile] because the [LogCollector] actual returns null and the screen
 * gates on it) and no per-app system notification settings surface.
 */
internal class DesktopPlatformIntents : PlatformIntents {
    override fun openUrl(url: String) {
        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
    }

    override fun shareLogFile(subject: String, chooserTitle: String, fileUri: String) {
        // No system share sheet on desktop — unreachable behind the null log gate.
    }

    override fun shareJson(subject: String, chooserTitle: String, body: String) {
        // No system share sheet on desktop — the screen already copied the
        // JSON to the clipboard before calling this, so the share degrades to
        // that clipboard copy.
    }

    override fun canOpenSystemNotificationSettings(): Boolean = false

    override fun openSystemNotificationSettings() {
        // No per-app system notification settings on desktop.
    }
}

@Composable
internal actual fun rememberPlatformIntents(): PlatformIntents =
    remember { DesktopPlatformIntents() }
