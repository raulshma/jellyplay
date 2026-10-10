package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_security
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_allow_remote_control
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_allow_remote_control_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_authorize_device
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_authorize_device_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_lock_timer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_lock_timer_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_biometric_unlock
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pin_for_player_lock
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pin_lock
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_remote_display_content
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_remote_display_content_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_security
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_lock_timer_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_lock_timer_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_biometric_lock_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_biometric_lock_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pin_for_player_lock_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pin_for_player_lock_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pin_lock_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pin_lock_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_quick_connect_authorize_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_quick_connect_authorize_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_remote_control_enabled_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_remote_control_enabled_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_remote_display_content_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_remote_display_content_title

/**
 * The security domain's fused row declarations — the feature-side single home
 * of every security row's presentation, ordering, and capability (the
 * [AppearanceRows] template). Each [SettingsRow] replaces the trio the domain
 * used to declare per row: the `SettingsSearchBinding` entry, the
 * `SettingsRowRecord` entry, and the `SecuritySettingsIds` holder constant
 * (all retired).
 *
 * Every row is a HAND-MAINTAINED residual (the knobs live in spec-less
 * stores) and carries its full hand search faces; the PIN rows ride the
 * app-lock capability (desktop's lock gate is Android-only), the biometric
 * row its gate-aware capability, the auto-lock timer compounds app-lock with
 * the advanced toggle — and the shipped count quirk rows
 * (`pin_for_player_lock`, quick-connect, remote-control, remote-display)
 * declare [RowAdmission.ContentGated]: never counted, always admitted for
 * emission (the screen's own `if`s carry their content gates). The projection
 * ([List.toSearchItems]/[List.asRowGroup]) fails fast at catalog init on any
 * drift; search results, catalog order, group membership and per-gate
 * visibility are byte-identical to the retired declarations.
 */
internal object SecurityRows {

    // -- The "Security" group's seven rows, in catalog order. Only the lock-group rows declare count gates; the four single-row/outside-the-lock-total rows are [RowAdmission.ContentGated] — the shipped count quirk (they render behind the pin toggle / in their own groups but have never been admitted into the lock-group total). --

    val PinLock = SettingsRow(
        id = "pin_lock",
        icon = Tabler.Outline.Lock,
        titleRes = Res.string.settings_pin_lock,
        searchTitleRes = Res.string.ss_pin_lock_title,
        searchSubtitleRes = Res.string.ss_pin_lock_subtitle,
        keywords = listOf("pin", "lock", "code", "password", "security"),
        route = Route.SecuritySettings(),
        platforms = platformsForCapability(settingsCapabilities.supportsAppLock),
        gate = RowAdmission.Platform(RowAdmissionCapability.AppLock),
    )

    val BiometricLock = SettingsRow(
        id = "biometric_lock",
        icon = Tabler.Outline.Fingerprint,
        titleRes = Res.string.settings_biometric_unlock,
        searchTitleRes = Res.string.ss_biometric_lock_title,
        searchSubtitleRes = Res.string.ss_biometric_lock_subtitle,
        keywords = listOf("biometric", "fingerprint", "face lock", "iris", "sensors"),
        route = Route.SecuritySettings(),
        platforms = platformsForCapability(settingsCapabilities.supportsBiometric),
        gate = RowAdmission.Platform(RowAdmissionCapability.Biometric),
    )

    val PinForPlayerLock = SettingsRow(
        id = "pin_for_player_lock",
        icon = Tabler.Outline.Key,
        titleRes = Res.string.settings_pin_for_player_lock,
        searchTitleRes = Res.string.ss_pin_for_player_lock_title,
        searchSubtitleRes = Res.string.ss_pin_for_player_lock_subtitle,
        keywords = listOf("pin", "player", "lock", "unlock", "screen lock"),
        route = Route.SecuritySettings(),
        platforms = platformsForCapability(settingsCapabilities.supportsAppLock),
        gate = RowAdmission.ContentGated,
    )

    val QuickConnectAuthorize = SettingsRow(
        id = "quick_connect_authorize",
        icon = Tabler.Outline.Bolt,
        titleRes = Res.string.settings_authorize_device,
        subtitleRes = Res.string.settings_authorize_device_subtitle,
        searchTitleRes = Res.string.ss_quick_connect_authorize_title,
        searchSubtitleRes = Res.string.ss_quick_connect_authorize_subtitle,
        keywords = listOf("quick connect", "authorize", "approve", "code", "device", "pair"),
        route = Route.SecuritySettings(),
        gate = RowAdmission.ContentGated,
    )

    val RemoteControlEnabled = SettingsRow(
        id = "remote_control_enabled",
        icon = Tabler.Outline.Cast,
        titleRes = Res.string.settings_allow_remote_control,
        subtitleRes = Res.string.settings_allow_remote_control_subtitle,
        searchTitleRes = Res.string.ss_remote_control_enabled_title,
        searchSubtitleRes = Res.string.ss_remote_control_enabled_subtitle,
        keywords = listOf("remote", "control", "cast", "play to", "external control", "receive commands"),
        route = Route.SecuritySettings(),
        gate = RowAdmission.ContentGated,
    )

    val RemoteDisplayContentEnabled = SettingsRow(
        id = "remote_display_content_enabled",
        icon = Tabler.Outline.DeviceTv,
        titleRes = Res.string.settings_remote_display_content,
        subtitleRes = Res.string.settings_remote_display_content_subtitle,
        searchTitleRes = Res.string.ss_remote_display_content_title,
        searchSubtitleRes = Res.string.ss_remote_display_content_subtitle,
        keywords = listOf("remote", "display content", "browse", "remote browse", "cast", "details"),
        route = Route.SecuritySettings(),
        gate = RowAdmission.ContentGated,
    )

    val AutoLockTimer = SettingsRow(
        id = "auto_lock_timer",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_auto_lock_timer,
        subtitleRes = Res.string.settings_auto_lock_timer_subtitle,
        searchTitleRes = Res.string.ss_auto_lock_timer_title,
        searchSubtitleRes = Res.string.ss_auto_lock_timer_subtitle,
        keywords = listOf("auto lock", "timer", "lock", "timeout", "delay", "security"),
        route = Route.SecuritySettings(),
        isAdvanced = true,
        platforms = platformsForCapability(settingsCapabilities.supportsAppLock),
        gate = RowAdmission.All(RowAdmission.Platform(RowAdmissionCapability.AppLock), RowAdmission.Advanced),
    )

    /**
     * Every fused security row — the ratchet's vocabulary. A computed accessor
     * (not an initializer): the group row lists are top-level vals declared
     * later in this file, and an eager field would turn the
     * object-to-file-facade initialization order into a cycle.
     */
    val all: List<SettingsRow>
        get() = SecurityRowsList
}

// ---------------------------------------------------------------------
// The spec-derived derivation inputs: the searchable semantics live on the
// datastore-side spec declarations where they exist; the ordered row lists
// below are the spine — presentation faces, catalog order, gates.
// ---------------------------------------------------------------------

private val searchRoutes: Map<String, Route> = emptyMap()

private val securityCategory = CoreUiRes.string.ss_cat_security

internal val SecurityRowsList: List<SettingsRow> = listOf(
    SecurityRows.PinLock,
    SecurityRows.BiometricLock,
    SecurityRows.PinForPlayerLock,
    SecurityRows.QuickConnectAuthorize,
    SecurityRows.RemoteControlEnabled,
    SecurityRows.RemoteDisplayContentEnabled,
    SecurityRows.AutoLockTimer,
)

/**
 * The screen groups — items AND per-row admissions derive from the row
 * lists above in one act ([List.asRowGroup]), so the declaration is the
 * single home of the groups' order, faces and gates.
 */
internal val SecurityGroup =
    SecurityRowsList.asRowGroup("security", emptyList(), searchRoutes, securityCategory)

// The catalog projections, kept as named vals — the search/catalog-order
// pins (SpecDerivedSearchItemsTest, SettingsSearchCatalogTest) read these
// lists.

internal val SecuritySettingsSearchItems: List<SettingsSearchItem> = SecurityGroup.items

// -- The domain's root-screen entrance declaration --

/** The security domain's root-screen entrance — the ONE ordered declaration that drives both the settings root's `item_security` section emission (icon/title/route id) and its entrance-step index (spliced into [SETTINGS_ENTRANCE_SECTIONS] at this render position). */
internal val SecurityEntrance = SettingsEntranceSectionRow(
    key = "item_security",
    rowId = "security",
    icon = Tabler.Outline.Lock,
    titleRes = Res.string.settings_security,
)
