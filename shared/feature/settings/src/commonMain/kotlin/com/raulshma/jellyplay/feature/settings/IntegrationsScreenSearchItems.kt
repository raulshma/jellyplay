package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.withHighlightSettingId
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_integrations
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_integrations_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_integrations_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_seerr_settings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_seerr_settings_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_provider_settings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_provider_settings_title

/**
 * Settings-search items for the "Account → integrations hub entries" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to IntegrationsScreen and its Seerr / subtitle-provider drill-ins. Aggregated in [SettingsSearchCatalog].
 */
internal val IntegrationsSearchItems = listOf(
    SettingsSearchItem(
        id = "seerr_settings",
        titleRes = Res.string.ss_seerr_settings_title,
        subtitleRes = Res.string.ss_seerr_settings_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_integrations,
        keywords = listOf("seerr", "jellyseerr", "request", "movies", "shows", "approve", "integration"),
        route = Route.SeerrSettings(),
        icon = Tabler.Outline.Puzzle
    ),
    SettingsSearchItem(
        id = "integrations",
        titleRes = Res.string.ss_integrations_title,
        subtitleRes = Res.string.ss_integrations_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_integrations,
        keywords = listOf("integrations", "jellyseerr", "overseerr", "arr", "sonarr", "radarr", "request"),
        route = Route.Integrations().withHighlightSettingId("integrations"),
        icon = Tabler.Outline.Plug
    ),
    SettingsSearchItem(
        id = "subtitle_provider_settings",
        titleRes = Res.string.ss_subtitle_provider_settings_title,
        subtitleRes = Res.string.ss_subtitle_provider_settings_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_integrations,
        keywords = listOf("subtitle", "provider", "opensubtitles", "opensubtitles.com", "tvsubs", "manager", "extensions"),
        route = Route.Integrations().withHighlightSettingId("subtitle_provider_settings"),
        icon = Tabler.Outline.Language,
        isAdvanced = true
    ),
)
