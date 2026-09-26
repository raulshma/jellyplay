package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable

/**
 * The Android 16 "Live Updates" opt-in seam for the storage/downloads
 * settings: whether the OS currently promotes our progress-centric
 * notifications, and the deep link into the system grant screen. Null where
 * the concept doesn't exist (below API 36, desktop) — the settings row
 * renders nothing and "supported" is already encoded by the gate existing.
 */
internal interface LiveUpdatesGate {
    /** The user has granted the promoted-notification special access. */
    fun isPromoted(): Boolean

    /** Deep-links the system grant screen (ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS). */
    fun openGrantScreen()
}

@Composable
internal expect fun rememberLiveUpdatesGate(): LiveUpdatesGate?
