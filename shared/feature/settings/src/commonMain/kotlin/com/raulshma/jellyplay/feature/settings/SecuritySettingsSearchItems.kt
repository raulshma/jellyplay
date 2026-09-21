package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_security
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
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

/**
 * The single-source row ids of this file's settings-search declarations.
 * Every consumer — the `SettingsSearchItem` declarations below, the screen
 * rows' `highlighted` comparisons, the admissions keys and the row-total
 * derivations — references these constants, so each id literal exists
 * exactly once. The values are the persisted deep-link/recents contract:
 * they change only deliberately, here.
 */
internal object SecuritySettingsIds {
    const val PIN_LOCK = "pin_lock"
    const val BIOMETRIC_LOCK = "biometric_lock"
    const val PIN_FOR_PLAYER_LOCK = "pin_for_player_lock"
    const val QUICK_CONNECT_AUTHORIZE = "quick_connect_authorize"
    const val REMOTE_CONTROL_ENABLED = "remote_control_enabled"
    const val AUTO_LOCK_TIMER = "auto_lock_timer"
}

/**
 * Settings-search items for the "Security" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to SecuritySettingsScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val SecuritySettingsSearchItems = listOf(
    SettingsSearchItem(
        id = SecuritySettingsIds.PIN_LOCK,
        titleRes = Res.string.ss_pin_lock_title,
        subtitleRes = Res.string.ss_pin_lock_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_security,
        keywords = listOf("pin", "lock", "code", "password", "security"),
        route = Route.SecuritySettings(),
        icon = Tabler.Outline.Lock
    ),
    SettingsSearchItem(
        id = SecuritySettingsIds.BIOMETRIC_LOCK,
        titleRes = Res.string.ss_biometric_lock_title,
        subtitleRes = Res.string.ss_biometric_lock_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_security,
        keywords = listOf("biometric", "fingerprint", "face lock", "iris", "sensors"),
        route = Route.SecuritySettings(),
        icon = Tabler.Outline.Fingerprint,
        platforms = platformsForCapability(settingsCapabilities.supportsBiometric),
    ),
    SettingsSearchItem(
        id = SecuritySettingsIds.PIN_FOR_PLAYER_LOCK,
        titleRes = Res.string.ss_pin_for_player_lock_title,
        subtitleRes = Res.string.ss_pin_for_player_lock_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_security,
        keywords = listOf("pin", "player", "lock", "unlock", "screen lock"),
        route = Route.SecuritySettings(),
        icon = Tabler.Outline.Key
    ),
    SettingsSearchItem(
        id = SecuritySettingsIds.QUICK_CONNECT_AUTHORIZE,
        titleRes = Res.string.ss_quick_connect_authorize_title,
        subtitleRes = Res.string.ss_quick_connect_authorize_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_security,
        keywords = listOf("quick connect", "authorize", "approve", "code", "device", "pair"),
        route = Route.SecuritySettings(),
        icon = Tabler.Outline.Bolt
    ),
    SettingsSearchItem(
        id = SecuritySettingsIds.REMOTE_CONTROL_ENABLED,
        titleRes = Res.string.ss_remote_control_enabled_title,
        subtitleRes = Res.string.ss_remote_control_enabled_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_security,
        keywords = listOf("remote", "control", "cast", "play to", "external control", "receive commands"),
        route = Route.SecuritySettings(),
        icon = Tabler.Outline.Cast
    ),
    SettingsSearchItem(
        id = SecuritySettingsIds.AUTO_LOCK_TIMER,
        titleRes = Res.string.ss_auto_lock_timer_title,
        subtitleRes = Res.string.ss_auto_lock_timer_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_security,
        keywords = listOf("auto lock", "timer", "lock", "timeout", "delay", "security"),
        route = Route.SecuritySettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
)

/**
 * The security group's per-id declared row admissions — the single gate both
 * `securityScreenRowTotal` and SecuritySettingsScreen's emission `if`s read.
 * Only the lock-group rows declare gates (the biometric flag is the screen's
 * gate-aware computed value); `pin_for_player_lock` — the shipped count quirk
 * — and the quick-connect/remote-control rows (counted in their own
 * single-row groups) stay undeclared, and the strict default counts nothing
 * undeclared.
 */
internal val SecurityRowAdmissions: Map<String, RowAdmission> = mapOf(
    SecuritySettingsIds.PIN_LOCK to RowAdmission.Always,
    SecuritySettingsIds.BIOMETRIC_LOCK to RowAdmission.Platform(RowAdmissionCapability.Biometric),
    SecuritySettingsIds.AUTO_LOCK_TIMER to RowAdmission.Advanced,
)
