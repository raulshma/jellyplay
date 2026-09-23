package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.Tabler
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_integrations
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.withHighlightSettingId
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations_subtitles
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_seerr_integration
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_integrations_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_integrations_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_seerr_settings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_seerr_settings_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_provider_settings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_subtitle_provider_settings_title

/**
 * The single-source row ids of this file's settings-search declarations.
 * Every consumer — the `SettingsSearchItem` declarations below, the screen
 * rows' `highlighted` comparisons, the admissions keys and the row-total
 * derivations — references these constants, so each id literal exists
 * exactly once. The values are the persisted deep-link/recents contract:
 * they change only deliberately, here.
 */
internal object IntegrationsScreenIds {
    const val SEERR_SETTINGS = "seerr_settings"
    const val INTEGRATIONS = "integrations"
    const val SUBTITLE_PROVIDER_SETTINGS = "subtitle_provider_settings"
}

/**
 * Settings-search items for the "Account → integrations hub entries" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to IntegrationsScreen and its Seerr / subtitle-provider drill-ins. Aggregated in [SettingsSearchCatalog].
 */
internal val IntegrationsRowRecords = listOf(
    SettingsRowRecord(
        id = IntegrationsScreenIds.SEERR_SETTINGS,
        titleRes = Res.string.settings_seerr_integration,
        searchTitleRes = Res.string.ss_seerr_settings_title,
        searchSubtitleRes = Res.string.ss_seerr_settings_subtitle,
        keywords = listOf("seerr", "jellyseerr", "request", "movies", "shows", "approve", "integration"),
        route = Route.SeerrSettings(),
        icon = Tabler.Outline.Puzzle
    ),
    SettingsRowRecord(
        id = IntegrationsScreenIds.INTEGRATIONS,
        titleRes = Res.string.settings_integrations,
        searchTitleRes = Res.string.ss_integrations_title,
        searchSubtitleRes = Res.string.ss_integrations_subtitle,
        keywords = listOf("integrations", "jellyseerr", "overseerr", "arr", "sonarr", "radarr", "request"),
        route = Route.Integrations().withHighlightSettingId("integrations"),
        icon = Tabler.Outline.Plug
    ),
    SettingsRowRecord(
        id = IntegrationsScreenIds.SUBTITLE_PROVIDER_SETTINGS,
        titleRes = Res.string.settings_integrations_subtitles,
        searchTitleRes = Res.string.ss_subtitle_provider_settings_title,
        searchSubtitleRes = Res.string.ss_subtitle_provider_settings_subtitle,
        keywords = listOf("subtitle", "provider", "opensubtitles", "opensubtitles.com", "tvsubs", "manager", "extensions"),
        route = Route.Integrations().withHighlightSettingId("subtitle_provider_settings"),
        icon = Tabler.Outline.Language,
        isAdvanced = true
    ))

/** The catalog projection of `IntegrationsRowRecords`: the search faces + the shared category. */
internal val IntegrationsSearchItems: List<SettingsSearchItem> = IntegrationsRowRecords.toSearchItems(CoreUiRes.string.ss_cat_integrations)

