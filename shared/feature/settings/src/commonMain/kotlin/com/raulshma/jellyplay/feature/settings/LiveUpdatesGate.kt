package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable

/**
 * The Android 16 "Live Updates" opt-in seam for the storage/downloads
 * settings: whether this device supports the progress-centric promoted
 * notifications (API 36+), whether the OS currently promotes ours, and the
 * deep link into the system grant screen. Null on platforms without the
 * concept (desktop) — the settings row renders nothing.
 */
internal interface LiveUpdatesGate {
    /** API 36+ — the row only exists when true. */
    fun isSupported(): Boolean

    /** The user has granted the promoted-notification special access. */
    fun isPromoted(): Boolean

    /** Deep-links the system grant screen (ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS). */
    fun openGrantScreen()
}

@Composable
internal expect fun rememberLiveUpdatesGate(): LiveUpdatesGate?
