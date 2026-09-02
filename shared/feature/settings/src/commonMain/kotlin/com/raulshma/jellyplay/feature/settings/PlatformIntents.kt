package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable

/**
 * Platform seam for the Android `Intent`-backed actions the settings screens
 * inline (V3 settings conveyor): external links (ACTION_VIEW), the send-logs
 * share sheet (ACTION_SEND chooser over a collected log-file stream), the
 * home-layout preset JSON share (ACTION_SEND EXTRA_TEXT), and the
 * drill-through to the system's per-app notification settings. Desktop's
 * [rememberPlatformIntents] actual browses URLs via AWT `Desktop` and reports
 * no notification-settings surface; the log share never fires there because
 * the [LogCollector] actual returns null and the screen gates on it, and the
 * preset share degrades to the clipboard copy the screen already performed.
 */
internal interface PlatformIntents {
    /** Opens [url] in the platform browser (Android ACTION_VIEW). */
    fun openUrl(url: String)

    /**
     * Shares a collected log file (Android ACTION_SEND chooser with
     * EXTRA_STREAM + FLAG_GRANT_READ_URI_PERMISSION). [fileUri] is the opaque
     * shareable reference produced by [LogCollector.collectLogs].
     */
    fun shareLogFile(subject: String, chooserTitle: String, fileUri: String)

    /**
     * Shares a home-layout preset as JSON text (Android ACTION_SEND chooser
     * with EXTRA_SUBJECT + EXTRA_TEXT, type `application/json`).
     */
    fun shareJson(subject: String, chooserTitle: String, body: String)

    /** Whether [openSystemNotificationSettings] can run on this platform. */
    fun canOpenSystemNotificationSettings(): Boolean

    /** Opens the system's notification settings for this app. */
    fun openSystemNotificationSettings()
}

/** Composition-scoped [PlatformIntents] pick for the current platform. */
@Composable
internal expect fun rememberPlatformIntents(): PlatformIntents
