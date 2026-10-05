package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_notifications
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_check_frequency
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_check_frequency_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_enable_notifications
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_enable_notifications_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_per_check
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_per_check_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_new_episodes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_new_episodes_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_notification_lights
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_notification_lights_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_notifications
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quiet_end
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quiet_end_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quiet_hours
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quiet_hours_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quiet_start
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quiet_start_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_respect_system_dnd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_respect_system_dnd_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sound
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sound_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_system_notification_settings
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_system_notification_settings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_vibrate
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_vibrate_subtitle
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
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_new_episodes_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_new_episodes_title
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
 * The notifications domain's fused row declarations — the feature-side single
 * home of every notification row's presentation, ordering, and capability
 * (the [AppearanceRows] template). Each [SettingsRow] replaces the trio the
 * domain used to declare per row: the `SettingsSearchBinding` entry, the
 * `SettingsRowRecord` entry, and the `NotificationSettingsIds` holder
 * constant (all retired).
 *
 * Every row is a HAND-MAINTAINED residual (the knobs live in the spec-less
 * notification store) and carries its full hand search faces; every id
 * declares its gate — the master toggle [RowAdmission.Always], the five rows
 * behind it ride it, the quiet-hours trio compounds the quiet-hours toggle,
 * the rest ride advanced mode, and the system-settings row the
 * platform-intent capability. The whole list is Android-only (no desktop
 * notification backend exists — the rows carry the platform tag the retired
 * list-level `androidOnly()` applied). The projection
 * ([List.toSearchItems]/[List.asRowGroup]) fails fast at catalog init on any
 * drift; search results, catalog order, group membership and per-gate
 * visibility are byte-identical to the retired declarations.
 */
internal object NotificationRows {

    // -- The "Notifications" group's thirteen rows — every id declares its gate (the strict shape) — in catalog order (all Android-only: no desktop notification backend exists). --

    val NotificationsEnable = SettingsRow(
        id = "notifications_enable",
        icon = Tabler.Outline.Bell,
        titleRes = Res.string.settings_enable_notifications,
        subtitleRes = Res.string.settings_enable_notifications_subtitle,
        searchTitleRes = Res.string.ss_notifications_enable_title,
        searchSubtitleRes = Res.string.ss_notifications_enable_subtitle,
        keywords = listOf("notifications", "frequency", "bell", "check frequency", "alerts"),
        route = Route.NotificationSettings(),
        platforms = ANDROID_ONLY_PLATFORMS,
        gate = RowAdmission.Always,
    )

    val RespectSystemDnd = SettingsRow(
        id = "respect_system_dnd",
        icon = Tabler.Outline.BellOff,
        titleRes = Res.string.settings_respect_system_dnd,
        subtitleRes = Res.string.settings_respect_system_dnd_subtitle,
        searchTitleRes = Res.string.ss_respect_system_dnd_title,
        searchSubtitleRes = Res.string.ss_respect_system_dnd_subtitle,
        keywords = listOf("dnd", "do not disturb", "quiet", "silent", "notification policy"),
        route = Route.NotificationSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
        gate = RowAdmission.All(RowAdmission.WhenOn(NotificationRows.NotificationsEnable.id), RowAdmission.Advanced),
    )

    val SystemNotificationSettings = SettingsRow(
        id = "system_notification_settings",
        icon = Tabler.Outline.Settings,
        titleRes = Res.string.settings_system_notification_settings,
        subtitleRes = Res.string.settings_system_notification_settings_subtitle,
        searchTitleRes = Res.string.ss_system_notification_settings_title,
        searchSubtitleRes = Res.string.ss_system_notification_settings_subtitle,
        keywords = listOf("system", "notification", "channel", "settings", "customize"),
        route = Route.NotificationSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
        gate = RowAdmission.All(RowAdmission.WhenOn(NotificationRows.NotificationsEnable.id), RowAdmission.Advanced, RowAdmission.Platform(RowAdmissionCapability.SystemNotificationSettings)),
    )

    val NotificationCheckFrequency = SettingsRow(
        id = "notification_check_frequency",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_check_frequency,
        subtitleRes = Res.string.settings_check_frequency_subtitle,
        searchTitleRes = Res.string.ss_notification_check_frequency_title,
        searchSubtitleRes = Res.string.ss_notification_check_frequency_subtitle,
        keywords = listOf("notification", "check", "frequency", "interval", "polling", "new media"),
        route = Route.NotificationSettings(),
        platforms = ANDROID_ONLY_PLATFORMS,
        gate = RowAdmission.WhenOn(NotificationRows.NotificationsEnable.id),
    )

    val QuietHours = SettingsRow(
        id = "quiet_hours",
        icon = Tabler.Outline.Moon,
        titleRes = Res.string.settings_quiet_hours,
        subtitleRes = Res.string.settings_quiet_hours_subtitle,
        searchTitleRes = Res.string.ss_quiet_hours_title,
        searchSubtitleRes = Res.string.ss_quiet_hours_subtitle,
        keywords = listOf("quiet hours", "suppress", "silent", "night", "do not disturb"),
        route = Route.NotificationSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
        gate = RowAdmission.All(RowAdmission.WhenOn(NotificationRows.NotificationsEnable.id), RowAdmission.Advanced),
    )

    val QuietStart = SettingsRow(
        id = "quiet_start",
        icon = Tabler.Outline.Sunset,
        titleRes = Res.string.settings_quiet_start,
        subtitleRes = Res.string.settings_quiet_start_subtitle,
        searchTitleRes = Res.string.ss_quiet_start_title,
        searchSubtitleRes = Res.string.ss_quiet_start_subtitle,
        keywords = listOf("quiet hours", "start", "begin", "night", "silent"),
        route = Route.NotificationSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
        gate = RowAdmission.All(RowAdmission.WhenOn(NotificationRows.NotificationsEnable.id), RowAdmission.Advanced, RowAdmission.WhenOn(NotificationRows.QuietHours.id)),
    )

    val QuietEnd = SettingsRow(
        id = "quiet_end",
        icon = Tabler.Outline.Sunrise,
        titleRes = Res.string.settings_quiet_end,
        subtitleRes = Res.string.settings_quiet_end_subtitle,
        searchTitleRes = Res.string.ss_quiet_end_title,
        searchSubtitleRes = Res.string.ss_quiet_end_subtitle,
        keywords = listOf("quiet hours", "end", "morning", "silent"),
        route = Route.NotificationSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
        gate = RowAdmission.All(RowAdmission.WhenOn(NotificationRows.NotificationsEnable.id), RowAdmission.Advanced, RowAdmission.WhenOn(NotificationRows.QuietHours.id)),
    )

    val NotificationSound = SettingsRow(
        id = "notification_sound",
        icon = Tabler.Outline.Volume,
        titleRes = Res.string.settings_sound,
        subtitleRes = Res.string.settings_sound_subtitle,
        searchTitleRes = Res.string.ss_notification_sound_title,
        searchSubtitleRes = Res.string.ss_notification_sound_subtitle,
        keywords = listOf("notification", "sound", "audio", "alert", "tone"),
        route = Route.NotificationSettings(),
        platforms = ANDROID_ONLY_PLATFORMS,
        gate = RowAdmission.WhenOn(NotificationRows.NotificationsEnable.id),
    )

    val NotificationVibrate = SettingsRow(
        id = "notification_vibrate",
        icon = Tabler.Outline.PhoneCall,
        titleRes = Res.string.settings_vibrate,
        subtitleRes = Res.string.settings_vibrate_subtitle,
        searchTitleRes = Res.string.ss_notification_vibrate_title,
        searchSubtitleRes = Res.string.ss_notification_vibrate_subtitle,
        keywords = listOf("notification", "vibrate", "vibration", "haptic", "buzz"),
        route = Route.NotificationSettings(),
        platforms = ANDROID_ONLY_PLATFORMS,
        gate = RowAdmission.WhenOn(NotificationRows.NotificationsEnable.id),
    )

    val NotificationLights = SettingsRow(
        id = "notification_lights",
        icon = Tabler.Outline.Bulb,
        titleRes = Res.string.settings_notification_lights,
        subtitleRes = Res.string.settings_notification_lights_subtitle,
        searchTitleRes = Res.string.ss_notification_lights_title,
        searchSubtitleRes = Res.string.ss_notification_lights_subtitle,
        keywords = listOf("notification", "lights", "led", "pulse", "blink"),
        route = Route.NotificationSettings(),
        platforms = ANDROID_ONLY_PLATFORMS,
        gate = RowAdmission.WhenOn(NotificationRows.NotificationsEnable.id),
    )

    val NotificationNewEpisodes = SettingsRow(
        id = "notification_new_episodes",
        icon = Tabler.Outline.DeviceTv,
        titleRes = Res.string.settings_new_episodes,
        subtitleRes = Res.string.settings_new_episodes_subtitle,
        searchTitleRes = Res.string.ss_new_episodes_title,
        searchSubtitleRes = Res.string.ss_new_episodes_subtitle,
        keywords = listOf("new episodes", "episode", "episodes", "season", "series", "tv", "notification"),
        route = Route.NotificationSettings(),
        platforms = ANDROID_ONLY_PLATFORMS,
        gate = RowAdmission.WhenOn(NotificationRows.NotificationsEnable.id),
    )

    val MaxPerCheck = SettingsRow(
        id = "max_per_check",
        icon = Tabler.Outline.LetterCase,
        titleRes = Res.string.settings_max_per_check,
        subtitleRes = Res.string.settings_max_per_check_subtitle,
        searchTitleRes = Res.string.ss_max_per_check_title,
        searchSubtitleRes = Res.string.ss_max_per_check_subtitle,
        keywords = listOf("max", "per check", "batch", "items", "limit", "notification"),
        route = Route.NotificationSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
        gate = RowAdmission.All(RowAdmission.WhenOn(NotificationRows.NotificationsEnable.id), RowAdmission.Advanced),
    )

    val NotificationLibraries = SettingsRow(
        id = "notification_libraries",
        icon = Tabler.Outline.Folders,
        titleRes = Res.string.settings_libraries,
        searchTitleRes = Res.string.ss_notification_libraries_title,
        searchSubtitleRes = Res.string.ss_notification_libraries_subtitle,
        keywords = listOf("notification", "libraries", "folders", "monitor", "per library"),
        route = Route.NotificationSettings(),
        isAdvanced = true,
        platforms = ANDROID_ONLY_PLATFORMS,
        gate = RowAdmission.All(RowAdmission.WhenOn(NotificationRows.NotificationsEnable.id), RowAdmission.Advanced),
    )

    /**
     * Every fused notifications row — the ratchet's vocabulary. A computed accessor
     * (not an initializer): the group row lists are top-level vals declared
     * later in this file, and an eager field would turn the
     * object-to-file-facade initialization order into a cycle.
     */
    val all: List<SettingsRow>
        get() = NotificationRowsList
}

// ---------------------------------------------------------------------
// The spec-derived derivation inputs: the searchable semantics live on the
// datastore-side spec declarations where they exist; the ordered row lists
// below are the spine — presentation faces, catalog order, gates.
// ---------------------------------------------------------------------

private val searchRoutes: Map<String, Route> = emptyMap()

private val notificationCategory = CoreUiRes.string.ss_cat_notifications

internal val NotificationRowsList: List<SettingsRow> = listOf(
    NotificationRows.NotificationsEnable,
    NotificationRows.RespectSystemDnd,
    NotificationRows.SystemNotificationSettings,
    NotificationRows.NotificationCheckFrequency,
    NotificationRows.QuietHours,
    NotificationRows.QuietStart,
    NotificationRows.QuietEnd,
    NotificationRows.NotificationSound,
    NotificationRows.NotificationVibrate,
    NotificationRows.NotificationLights,
    NotificationRows.NotificationNewEpisodes,
    NotificationRows.MaxPerCheck,
    NotificationRows.NotificationLibraries,
)

/**
 * The screen groups — items AND per-row admissions derive from the row
 * lists above in one act ([List.asRowGroup]), so the declaration is the
 * single home of the groups' order, faces and gates.
 */
internal val NotificationGroup =
    NotificationRowsList.asRowGroup("notifications", emptyList(), searchRoutes, notificationCategory)

// The catalog projections, kept as named vals — the search/catalog-order
// pins (SpecDerivedSearchItemsTest, SettingsSearchCatalogTest) read these
// lists.

internal val NotificationSettingsSearchItems: List<SettingsSearchItem> = NotificationGroup.items

// -- The domain's root-screen entrance declaration --

/** The notifications domain's root-screen entrance — the ONE ordered declaration that drives both the settings root's `item_notifications` section emission (icon/title/route id) and its entrance-step index (spliced into [SETTINGS_ENTRANCE_SECTIONS] at this render position). */
internal val NotificationEntrance = SettingsEntranceSectionRow(
    key = "item_notifications",
    rowId = "notifications",
    icon = Tabler.Outline.Bell,
    titleRes = Res.string.settings_notifications,
)
