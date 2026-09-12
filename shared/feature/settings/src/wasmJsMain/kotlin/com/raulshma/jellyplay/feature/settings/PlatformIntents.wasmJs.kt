package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window

/**
 * Web actual of the [PlatformIntents] seam: external links open through
 * `window.open` (the browser's ACTION_VIEW counterpart); there is no system
 * share sheet (the send-logs row never reaches [shareLogFile] because the
 * wasm [LogCollector] is a no-op and the row is capability-gated off, and the
 * preset share degrades to the clipboard copy the screen already performed),
 * and no per-app system notification settings surface in a browser tab.
 */
internal class WasmPlatformIntents : PlatformIntents {
    override fun openUrl(url: String) {
        runCatching { window.open(url, target = "_blank") }
    }

    override fun shareLogFile(subject: String, chooserTitle: String, fileUri: String) {
        // No system share sheet on web — unreachable behind the null log gate.
    }

    override fun shareJson(subject: String, chooserTitle: String, body: String) {
        // No system share sheet on web — the screen already copied the JSON to
        // the clipboard before calling this, so the share degrades to that
        // clipboard copy.
    }

    override fun canOpenSystemNotificationSettings(): Boolean = false

    override fun openSystemNotificationSettings() {
        // No per-app system notification settings in a browser tab.
    }
}

@Composable
internal actual fun rememberPlatformIntents(): PlatformIntents =
    remember { WasmPlatformIntents() }
