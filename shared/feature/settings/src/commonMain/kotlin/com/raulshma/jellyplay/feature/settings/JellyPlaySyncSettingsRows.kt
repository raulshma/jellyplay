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

/**
 * The JellyPlay companion-plugin sync rows — residual rows (the
 * knob lives on `ProfileSyncRepository`, no datastore spec): the opt-in
 * toggle and the manual sync-now action. The on-screen face lives on the
 * [Route.JellyPlaySync] screen ([JellyPlaySyncScreen]); these declarations
 * stay the search catalog's single home — a search hit navigates to the sync
 * screen (their `route`).
 */
internal object JellyPlaySyncRows {

    val SyncEnabled = SettingsRow(
        id = "jellyplay_sync_enabled",
        icon = Tabler.Outline.Devices,
        titleRes = Res.string.settings_sync_across_devices,
        searchTitleRes = Res.string.ss_jellyplay_sync_enabled_title,
        searchSubtitleRes = Res.string.ss_jellyplay_sync_enabled_subtitle,
        keywords = listOf("sync", "settings", "devices", "profile", "roam", "jellyplay", "plugin"),
        route = Route.JellyPlaySync,
    )

    val SyncNow = SettingsRow(
        id = "jellyplay_sync_now",
        icon = Tabler.Outline.Refresh,
        titleRes = Res.string.settings_sync_now,
        searchSubtitleRes = Res.string.ss_jellyplay_sync_now_subtitle,
        keywords = listOf("sync", "refresh", "pull", "push", "jellyplay", "plugin"),
        route = Route.JellyPlaySync,
    )

    /** The whole declaration — the tests' vocabulary entry. */
    val all: List<SettingsRow> = listOf(SyncEnabled, SyncNow)
}

private val jellyPlaySyncSearchRoutes: Map<String, Route> = mapOf(
    JellyPlaySyncRows.SyncEnabled.id to Route.JellyPlaySync,
    JellyPlaySyncRows.SyncNow.id to Route.JellyPlaySync,
)

/** One on-screen group beside the system groups (capability-gated at emission). */
internal val JellyPlaySyncGroup =
    JellyPlaySyncRows.all.asRowGroup("jellyplay.sync", emptyList(), jellyPlaySyncSearchRoutes, CoreUiRes.string.ss_cat_system)
