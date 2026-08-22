package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.withHighlightSettingId
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem

/**
 * Settings-search items for the "Account → integrations hub entries" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to IntegrationsScreen and its Seerr / subtitle-provider drill-ins. Aggregated in [SettingsSearchCatalog].
 */
internal val IntegrationsSearchItems = listOf(
    SettingsSearchItem(
        id = "seerr_settings",
        titleRes = R.string.ss_seerr_settings_title,
        subtitleRes = R.string.ss_seerr_settings_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_integrations,
        keywords = listOf("seerr", "jellyseerr", "request", "movies", "shows", "approve", "integration"),
        route = Route.SeerrSettings(),
        icon = Tabler.Outline.Puzzle
    ),
    SettingsSearchItem(
        id = "integrations",
        titleRes = R.string.ss_integrations_title,
        subtitleRes = R.string.ss_integrations_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_integrations,
        keywords = listOf("integrations", "jellyseerr", "overseerr", "arr", "sonarr", "radarr", "request"),
        route = Route.Integrations().withHighlightSettingId("integrations"),
        icon = Tabler.Outline.Plug
    ),
    SettingsSearchItem(
        id = "subtitle_provider_settings",
        titleRes = R.string.ss_subtitle_provider_settings_title,
        subtitleRes = R.string.ss_subtitle_provider_settings_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_integrations,
        keywords = listOf("subtitle", "provider", "opensubtitles", "opensubtitles.com", "tvsubs", "manager", "extensions"),
        route = Route.Integrations().withHighlightSettingId("subtitle_provider_settings"),
        icon = Tabler.Outline.Language,
        isAdvanced = true
    ),
)
