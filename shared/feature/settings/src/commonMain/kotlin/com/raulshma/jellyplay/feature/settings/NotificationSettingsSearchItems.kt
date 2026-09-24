package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.Tabler
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_notifications
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_check_frequency
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_enable_notifications
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_per_check
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_notification_lights
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quiet_end
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quiet_hours
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quiet_start
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_respect_system_dnd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sound
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_system_notification_settings
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_vibrate
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_max_per_check_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_max_per_check_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_notification_check_frequency_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_notification_check_frequency_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_notification_libraries_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_notification_libraries_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_notification_lights_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_notification_lights_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_notification_sound_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_notification_sound_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_notification_vibrate_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_notification_vibrate_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_notifications_enable_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_notifications_enable_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_quiet_end_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_quiet_end_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_quiet_hours_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_quiet_hours_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_quiet_start_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_quiet_start_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_respect_system_dnd_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_respect_system_dnd_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_system_notification_settings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_system_notification_settings_title

/**
 * The single-source row ids of this file's settings-search declarations.
 * Every consumer — the `SettingsSearchItem` declarations below, the screen
 * rows' `highlighted` comparisons, the admissions keys and the row-total
 * derivations — references these constants, so each id literal exists
 * exactly once. The values are the persisted deep-link/recents contract:
 * they change only deliberately, here.
 */
internal object NotificationSettingsIds {
    const val NOTIFICATIONS_ENABLE = "notifications_enable"
    const val RESPECT_SYSTEM_DND = "respect_system_dnd"
    const val SYSTEM_NOTIFICATION_SETTINGS = "system_notification_settings"
    const val NOTIFICATION_CHECK_FREQUENCY = "notification_check_frequency"
    const val QUIET_HOURS = "quiet_hours"
    const val QUIET_START = "quiet_start"
    const val QUIET_END = "quiet_end"
    const val NOTIFICATION_SOUND = "notification_sound"
    const val NOTIFICATION_VIBRATE = "notification_vibrate"
    const val NOTIFICATION_LIGHTS = "notification_lights"
    const val MAX_PER_CHECK = "max_per_check"
    const val NOTIFICATION_LIBRARIES = "notification_libraries"
}

/**
 * Settings-search items for the "Notifications" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to NotificationSettingsScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val NotificationSettingsRowRecords = listOf(
    SettingsRowRecord(
        id = NotificationSettingsIds.NOTIFICATIONS_ENABLE,
        titleRes = Res.string.settings_enable_notifications,
        searchTitleRes = Res.string.ss_notifications_enable_title,
        searchSubtitleRes = Res.string.ss_notifications_enable_subtitle,
        keywords = listOf("notifications", "frequency", "bell", "check frequency", "alerts"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.Bell
    ),
    SettingsRowRecord(
        id = NotificationSettingsIds.RESPECT_SYSTEM_DND,
        titleRes = Res.string.settings_respect_system_dnd,
        searchTitleRes = Res.string.ss_respect_system_dnd_title,
        searchSubtitleRes = Res.string.ss_respect_system_dnd_subtitle,
        keywords = listOf("dnd", "do not disturb", "quiet", "silent", "notification policy"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.BellOff,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = NotificationSettingsIds.SYSTEM_NOTIFICATION_SETTINGS,
        titleRes = Res.string.settings_system_notification_settings,
        searchTitleRes = Res.string.ss_system_notification_settings_title,
        searchSubtitleRes = Res.string.ss_system_notification_settings_subtitle,
        keywords = listOf("system", "notification", "channel", "settings", "customize"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.Settings,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = NotificationSettingsIds.NOTIFICATION_CHECK_FREQUENCY,
        titleRes = Res.string.settings_check_frequency,
        searchTitleRes = Res.string.ss_notification_check_frequency_title,
        searchSubtitleRes = Res.string.ss_notification_check_frequency_subtitle,
        keywords = listOf("notification", "check", "frequency", "interval", "polling", "new media"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.Clock
    ),
    SettingsRowRecord(
        id = NotificationSettingsIds.QUIET_HOURS,
        titleRes = Res.string.settings_quiet_hours,
        searchTitleRes = Res.string.ss_quiet_hours_title,
        searchSubtitleRes = Res.string.ss_quiet_hours_subtitle,
        keywords = listOf("quiet hours", "suppress", "silent", "night", "do not disturb"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.Moon,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = NotificationSettingsIds.QUIET_START,
        titleRes = Res.string.settings_quiet_start,
        searchTitleRes = Res.string.ss_quiet_start_title,
        searchSubtitleRes = Res.string.ss_quiet_start_subtitle,
        keywords = listOf("quiet hours", "start", "begin", "night", "silent"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.Sunset,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = NotificationSettingsIds.QUIET_END,
        titleRes = Res.string.settings_quiet_end,
        searchTitleRes = Res.string.ss_quiet_end_title,
        searchSubtitleRes = Res.string.ss_quiet_end_subtitle,
        keywords = listOf("quiet hours", "end", "morning", "silent"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.Sunrise,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = NotificationSettingsIds.NOTIFICATION_SOUND,
        titleRes = Res.string.settings_sound,
        searchTitleRes = Res.string.ss_notification_sound_title,
        searchSubtitleRes = Res.string.ss_notification_sound_subtitle,
        keywords = listOf("notification", "sound", "audio", "alert", "tone"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.Volume
    ),
    SettingsRowRecord(
        id = NotificationSettingsIds.NOTIFICATION_VIBRATE,
        titleRes = Res.string.settings_vibrate,
        searchTitleRes = Res.string.ss_notification_vibrate_title,
        searchSubtitleRes = Res.string.ss_notification_vibrate_subtitle,
        keywords = listOf("notification", "vibrate", "vibration", "haptic", "buzz"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.PhoneCall
    ),
    SettingsRowRecord(
        id = NotificationSettingsIds.NOTIFICATION_LIGHTS,
        titleRes = Res.string.settings_notification_lights,
        searchTitleRes = Res.string.ss_notification_lights_title,
        searchSubtitleRes = Res.string.ss_notification_lights_subtitle,
        keywords = listOf("notification", "lights", "led", "pulse", "blink"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.Bulb
    ),
    SettingsRowRecord(
        id = NotificationSettingsIds.MAX_PER_CHECK,
        titleRes = Res.string.settings_max_per_check,
        searchTitleRes = Res.string.ss_max_per_check_title,
        searchSubtitleRes = Res.string.ss_max_per_check_subtitle,
        keywords = listOf("max", "per check", "batch", "items", "limit", "notification"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.LetterCase,
        isAdvanced = true
    ),
    SettingsRowRecord(
        id = NotificationSettingsIds.NOTIFICATION_LIBRARIES,
        titleRes = Res.string.settings_libraries,
        searchTitleRes = Res.string.ss_notification_libraries_title,
        searchSubtitleRes = Res.string.ss_notification_libraries_subtitle,
        keywords = listOf("notification", "libraries", "folders", "monitor", "per library"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.Folders,
        isAdvanced = true
    ))

/** The catalog projection of `NotificationSettingsRowRecords`: the search faces + the shared category. */
internal val NotificationSettingsSearchItems: List<SettingsSearchItem> = NotificationSettingsRowRecords.toSearchItems(CoreUiRes.string.ss_cat_notifications).androidOnly()


/**
 * The notification group's per-id declared row admissions — the single gate
 * both `rowTotalFor` (the screen's total) and NotificationSettingsScreen's
 * emission `if`s read: the four rows behind the master toggle ride it, the
 * quiet-hours trio additionally the quiet-hours toggle, the rest ride advanced
 * mode, and the system-settings row the platform-intent capability. The master
 * toggle declares [RowAdmission.Always] — the derivation counts strictly, so
 * every declared id states its gate explicitly.
 */
internal val NotificationRowAdmissions: Map<String, RowAdmission> = mapOf(
    NotificationSettingsIds.NOTIFICATIONS_ENABLE to RowAdmission.Always,
    NotificationSettingsIds.NOTIFICATION_CHECK_FREQUENCY to
        RowAdmission.WhenOn(NotificationSettingsIds.NOTIFICATIONS_ENABLE),
    NotificationSettingsIds.NOTIFICATION_SOUND to
        RowAdmission.WhenOn(NotificationSettingsIds.NOTIFICATIONS_ENABLE),
    NotificationSettingsIds.NOTIFICATION_VIBRATE to
        RowAdmission.WhenOn(NotificationSettingsIds.NOTIFICATIONS_ENABLE),
    NotificationSettingsIds.NOTIFICATION_LIGHTS to
        RowAdmission.WhenOn(NotificationSettingsIds.NOTIFICATIONS_ENABLE),
    NotificationSettingsIds.QUIET_HOURS to RowAdmission.All(
        RowAdmission.WhenOn(NotificationSettingsIds.NOTIFICATIONS_ENABLE),
        RowAdmission.Advanced,
    ),
    NotificationSettingsIds.QUIET_START to RowAdmission.All(
        RowAdmission.WhenOn(NotificationSettingsIds.NOTIFICATIONS_ENABLE),
        RowAdmission.Advanced,
        RowAdmission.WhenOn(NotificationSettingsIds.QUIET_HOURS),
    ),
    NotificationSettingsIds.QUIET_END to RowAdmission.All(
        RowAdmission.WhenOn(NotificationSettingsIds.NOTIFICATIONS_ENABLE),
        RowAdmission.Advanced,
        RowAdmission.WhenOn(NotificationSettingsIds.QUIET_HOURS),
    ),
    NotificationSettingsIds.RESPECT_SYSTEM_DND to RowAdmission.All(
        RowAdmission.WhenOn(NotificationSettingsIds.NOTIFICATIONS_ENABLE),
        RowAdmission.Advanced,
    ),
    NotificationSettingsIds.MAX_PER_CHECK to RowAdmission.All(
        RowAdmission.WhenOn(NotificationSettingsIds.NOTIFICATIONS_ENABLE),
        RowAdmission.Advanced,
    ),
    NotificationSettingsIds.NOTIFICATION_LIBRARIES to RowAdmission.All(
        RowAdmission.WhenOn(NotificationSettingsIds.NOTIFICATIONS_ENABLE),
        RowAdmission.Advanced,
    ),
    NotificationSettingsIds.SYSTEM_NOTIFICATION_SETTINGS to RowAdmission.All(
        RowAdmission.WhenOn(NotificationSettingsIds.NOTIFICATIONS_ENABLE),
        RowAdmission.Advanced,
        RowAdmission.Platform(RowAdmissionCapability.SystemNotificationSettings),
    ),
)
