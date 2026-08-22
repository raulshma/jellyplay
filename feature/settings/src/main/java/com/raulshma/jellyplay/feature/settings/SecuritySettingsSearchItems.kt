package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem

/**
 * Settings-search items for the "Security" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to SecuritySettingsScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val SecuritySettingsSearchItems = listOf(
    SettingsSearchItem(
        id = "pin_lock",
        titleRes = R.string.ss_pin_lock_title,
        subtitleRes = R.string.ss_pin_lock_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_security,
        keywords = listOf("pin", "lock", "code", "password", "security"),
        route = Route.SecuritySettings(),
        icon = Tabler.Outline.Lock
    ),
    SettingsSearchItem(
        id = "biometric_lock",
        titleRes = R.string.ss_biometric_lock_title,
        subtitleRes = R.string.ss_biometric_lock_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_security,
        keywords = listOf("biometric", "fingerprint", "face lock", "iris", "sensors"),
        route = Route.SecuritySettings(),
        icon = Tabler.Outline.Fingerprint
    ),
    SettingsSearchItem(
        id = "pin_for_player_lock",
        titleRes = R.string.ss_pin_for_player_lock_title,
        subtitleRes = R.string.ss_pin_for_player_lock_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_security,
        keywords = listOf("pin", "player", "lock", "unlock", "screen lock"),
        route = Route.SecuritySettings(),
        icon = Tabler.Outline.Key
    ),
    SettingsSearchItem(
        id = "quick_connect_authorize",
        titleRes = R.string.ss_quick_connect_authorize_title,
        subtitleRes = R.string.ss_quick_connect_authorize_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_security,
        keywords = listOf("quick connect", "authorize", "approve", "code", "device", "pair"),
        route = Route.SecuritySettings(),
        icon = Tabler.Outline.Bolt
    ),
    SettingsSearchItem(
        id = "remote_control_enabled",
        titleRes = R.string.ss_remote_control_enabled_title,
        subtitleRes = R.string.ss_remote_control_enabled_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_security,
        keywords = listOf("remote", "control", "cast", "play to", "external control", "receive commands"),
        route = Route.SecuritySettings(),
        icon = Tabler.Outline.Cast
    ),
    SettingsSearchItem(
        id = "auto_lock_timer",
        titleRes = R.string.ss_auto_lock_timer_title,
        subtitleRes = R.string.ss_auto_lock_timer_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_security,
        keywords = listOf("auto lock", "timer", "lock", "timeout", "delay", "security"),
        route = Route.SecuritySettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
)
