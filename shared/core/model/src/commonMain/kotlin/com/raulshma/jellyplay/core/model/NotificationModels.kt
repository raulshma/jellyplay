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
