package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_experimental
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_integrations
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_DIRECT_ARR_INTEGRATION_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_DIRECT_ARR_INTEGRATION_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_HOME_CARD_CLIPPING_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_HOME_CARD_CLIPPING_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_MEDIA_CARD_PEEK_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_MEDIA_CARD_PEEK_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_arr_settings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_arr_settings_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_experimental_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_experimental_title

/**
 * Settings-search items for the "Experimental" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to ExperimentalSettingsScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val ExperimentalSettingsSearchItems = listOf(
    SettingsSearchItem(
        id = "experimental",
        titleRes = Res.string.ss_experimental_title,
        subtitleRes = Res.string.ss_experimental_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_experimental,
        keywords = listOf("experimental", "beta", "labs", "preview", "early access", "developer"),
        route = Route.ExperimentalSettings(),
        icon = Tabler.Outline.Flask
    ),
    SettingsSearchItem(
        id = "HOME_CARD_CLIPPING",
        titleRes = Res.string.ss_HOME_CARD_CLIPPING_title,
        subtitleRes = Res.string.ss_HOME_CARD_CLIPPING_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_experimental,
        keywords = listOf("home", "card", "clipping", "render", "experimental"),
        route = Route.ExperimentalSettings(),
        icon = Tabler.Outline.Photo,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "MEDIA_CARD_PEEK",
        titleRes = Res.string.ss_MEDIA_CARD_PEEK_title,
        subtitleRes = Res.string.ss_MEDIA_CARD_PEEK_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_experimental,
        keywords = listOf("press", "hold", "peek", "preview", "media card", "long press", "experimental"),
        route = Route.ExperimentalSettings(),
        icon = Tabler.Outline.HandFinger,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "DIRECT_ARR_INTEGRATION",
        titleRes = Res.string.ss_DIRECT_ARR_INTEGRATION_title,
        subtitleRes = Res.string.ss_DIRECT_ARR_INTEGRATION_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_experimental,
        keywords = listOf("radarr", "sonarr", "arr", "download", "queue", "calendar", "coming soon", "grabbed", "imported"),
        route = Route.ExperimentalSettings(),
        icon = Tabler.Outline.Download
    ),
    SettingsSearchItem(
        id = "arr_settings",
        titleRes = Res.string.ss_arr_settings_title,
        subtitleRes = Res.string.ss_arr_settings_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_integrations,
        keywords = listOf("radarr", "sonarr", "arr", "servers", "api key", "integration"),
        route = Route.ArrSettings(),
        icon = Tabler.Outline.Download
    ),
)
