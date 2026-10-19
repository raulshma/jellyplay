package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class NotificationPreferences(
    val enabled: Boolean = false,
    val checkFrequency: CheckFrequency = CheckFrequency.EVERY_6_HOURS,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: Int = 1380,
    val quietHoursEnd: Int = 420,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val lightsEnabled: Boolean = true,
    val maxPerCheck: Int = 10,
    val libraryConfigs: Map<String, LibraryNotificationConfig> = emptyMap(),
    val respectSystemDnd: Boolean = true,
    /** Opt-in: route newly discovered episodes to the dedicated "New episodes" channel. */
    val newEpisodesEnabled: Boolean = false,
    /**
     * Per-**series** new-episode opt-ins, keyed by Jellyfin series id —
     * the direct analog of [libraryConfigs] at series granularity. The
     * periodic new-media check's second pass + the *arr calendar accelerator
     * both read this map; [newEpisodesEnabled] is the master switch that
     * gates the whole family (master OFF suppresses ALL series scheduling).
     */
    val seriesConfigs: Map<String, SeriesNotificationConfig> = emptyMap(),
)

/**
 * One series' new-episode notification state. [enabled] is the opt-in
 * flag (the management UI removes the entry entirely on opt-out);
 * [lastNotifiedEpisodeId] is the diff baseline — the newest episode id the
 * last check saw, so a check notifies only when a NEWER id appears (and on
 * the very first check after opt-in it is recorded WITHOUT notifying, the
 * worker's first-scan anti-spam rule at series granularity); [lastCheckAt]
 * is the wall-clock bookkeeping stamp of the last per-series check.
 */
@Immutable
@Serializable
data class SeriesNotificationConfig(
    val enabled: Boolean = true,
    val lastNotifiedEpisodeId: String? = null,
    val lastCheckAt: Long = 0L,
)

@Immutable
@Serializable
data class LibraryNotificationConfig(
    val enabled: Boolean = true,
    val mediaTypes: Set<String> = emptySet(),
)

@Immutable
@Serializable
enum class CheckFrequency(val intervalMinutes: Long, val displayName: String) {
    EVERY_HOUR(60, "Every hour"),
    EVERY_3_HOURS(180, "Every 3 hours"),
    EVERY_6_HOURS(360, "Every 6 hours"),
    EVERY_12_HOURS(720, "Every 12 hours"),
    EVERY_24_HOURS(1440, "Every day"),
}
