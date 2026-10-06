package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Devices
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_system
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sync_across_devices
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sync_now
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_sync_enabled_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_sync_enabled_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_sync_now_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_jellyplay_sync_now_title

/**
 * The JellyPlay companion-plugin sync rows (ADR 0010) — residual rows (the
 * knob lives on `ProfileSyncRepository`, no datastore spec): the opt-in
 * toggle and the manual sync-now action, rendered as one on-screen group on
 * the settings root, gated on the plugin's capabilities probe.
 *
 * Both rows carry `route = Route.Settings`: their search hits resolve to the
 * root screen's own rows, so a tap takes the NoOp+highlight arm of
 * `settingsResultClickAction` (the screensaver-rows precedent).
 */
internal object JellyPlaySyncRows {

    val SyncEnabled = SettingsRow(
        id = "jellyplay_sync_enabled",
        icon = Tabler.Outline.Devices,
        titleRes = Res.string.settings_sync_across_devices,
        searchTitleRes = Res.string.ss_jellyplay_sync_enabled_title,
        searchSubtitleRes = Res.string.ss_jellyplay_sync_enabled_subtitle,
        keywords = listOf("sync", "settings", "devices", "profile", "roam", "jellyplay", "plugin"),
        route = Route.Settings,
    )

    val SyncNow = SettingsRow(
        id = "jellyplay_sync_now",
        icon = Tabler.Outline.Refresh,
        titleRes = Res.string.settings_sync_now,
        searchTitleRes = Res.string.ss_jellyplay_sync_now_title,
        searchSubtitleRes = Res.string.ss_jellyplay_sync_now_subtitle,
        keywords = listOf("sync", "refresh", "pull", "push", "jellyplay", "plugin"),
        route = Route.Settings,
    )

    /** The whole declaration — the ratchet tests' vocabulary entry. */
    val all: List<SettingsRow> = listOf(SyncEnabled, SyncNow)
}

private val jellyPlaySyncSearchRoutes: Map<String, Route> = mapOf(
    JellyPlaySyncRows.SyncEnabled.id to Route.Settings,
    JellyPlaySyncRows.SyncNow.id to Route.Settings,
)

/** One on-screen group beside the system groups (capability-gated at emission). */
internal val JellyPlaySyncGroup =
    JellyPlaySyncRows.all.asRowGroup("jellyplay.sync", emptyList(), jellyPlaySyncSearchRoutes, CoreUiRes.string.ss_cat_system)
