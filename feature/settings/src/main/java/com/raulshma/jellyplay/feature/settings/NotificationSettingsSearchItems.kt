package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem

/**
 * Settings-search items for the "Notifications" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to NotificationSettingsScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val NotificationSettingsSearchItems = listOf(
    SettingsSearchItem(
        id = "notifications_enable",
        titleRes = R.string.ss_notifications_enable_title,
        subtitleRes = R.string.ss_notifications_enable_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_notifications,
        keywords = listOf("notifications", "frequency", "bell", "check frequency", "alerts"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.Bell
    ),
    SettingsSearchItem(
        id = "respect_system_dnd",
        titleRes = R.string.ss_respect_system_dnd_title,
        subtitleRes = R.string.ss_respect_system_dnd_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_notifications,
        keywords = listOf("dnd", "do not disturb", "quiet", "silent", "notification policy"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.BellOff,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "system_notification_settings",
        titleRes = R.string.ss_system_notification_settings_title,
        subtitleRes = R.string.ss_system_notification_settings_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_notifications,
        keywords = listOf("system", "notification", "channel", "settings", "customize"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.Settings,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "notification_check_frequency",
        titleRes = R.string.ss_notification_check_frequency_title,
        subtitleRes = R.string.ss_notification_check_frequency_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_notifications,
        keywords = listOf("notification", "check", "frequency", "interval", "polling", "new media"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.Clock
    ),
    SettingsSearchItem(
        id = "quiet_hours",
        titleRes = R.string.ss_quiet_hours_title,
        subtitleRes = R.string.ss_quiet_hours_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_notifications,
        keywords = listOf("quiet hours", "suppress", "silent", "night", "do not disturb"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.Moon,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "quiet_start",
        titleRes = R.string.ss_quiet_start_title,
        subtitleRes = R.string.ss_quiet_start_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_notifications,
        keywords = listOf("quiet hours", "start", "begin", "night", "silent"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.Sunset,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "quiet_end",
        titleRes = R.string.ss_quiet_end_title,
        subtitleRes = R.string.ss_quiet_end_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_notifications,
        keywords = listOf("quiet hours", "end", "morning", "silent"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.Sunrise,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "notification_sound",
        titleRes = R.string.ss_notification_sound_title,
        subtitleRes = R.string.ss_notification_sound_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_notifications,
        keywords = listOf("notification", "sound", "audio", "alert", "tone"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.Volume
    ),
    SettingsSearchItem(
        id = "notification_vibrate",
        titleRes = R.string.ss_notification_vibrate_title,
        subtitleRes = R.string.ss_notification_vibrate_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_notifications,
        keywords = listOf("notification", "vibrate", "vibration", "haptic", "buzz"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.PhoneCall
    ),
    SettingsSearchItem(
        id = "notification_lights",
        titleRes = R.string.ss_notification_lights_title,
        subtitleRes = R.string.ss_notification_lights_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_notifications,
        keywords = listOf("notification", "lights", "led", "pulse", "blink"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.Bulb
    ),
    SettingsSearchItem(
        id = "max_per_check",
        titleRes = R.string.ss_max_per_check_title,
        subtitleRes = R.string.ss_max_per_check_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_notifications,
        keywords = listOf("max", "per check", "batch", "items", "limit", "notification"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.LetterCase,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "notification_libraries",
        titleRes = R.string.ss_notification_libraries_title,
        subtitleRes = R.string.ss_notification_libraries_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_notifications,
        keywords = listOf("notification", "libraries", "folders", "monitor", "per library"),
        route = Route.NotificationSettings(),
        icon = Tabler.Outline.Folders,
        isAdvanced = true
    ),
)
