package com.raulshma.jellyplay.feature.settings

/**
 * Desktop actual of the [LogCollector] seam: no logcat equivalent to capture,
 * so collection always reports unavailable — the send-logs share is gated off
 * (the About screen's `uri != null` check never passes).
 */
internal class DesktopLogCollector : LogCollector {
    override fun collectLogs(appVersion: String, buildType: String, serverAddress: String?): String? =
        null
}
