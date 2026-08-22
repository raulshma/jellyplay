package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem

/**
 * Settings-search items for the "Experimental" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to ExperimentalSettingsScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val ExperimentalSettingsSearchItems = listOf(
    SettingsSearchItem(
        id = "experimental",
        titleRes = R.string.ss_experimental_title,
        subtitleRes = R.string.ss_experimental_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_experimental,
        keywords = listOf("experimental", "beta", "labs", "preview", "early access", "developer"),
        route = Route.ExperimentalSettings(),
        icon = Tabler.Outline.Flask
    ),
    SettingsSearchItem(
        id = "HOME_CARD_CLIPPING",
        titleRes = R.string.ss_HOME_CARD_CLIPPING_title,
        subtitleRes = R.string.ss_HOME_CARD_CLIPPING_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_experimental,
        keywords = listOf("home", "card", "clipping", "render", "experimental"),
        route = Route.ExperimentalSettings(),
        icon = Tabler.Outline.Photo,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "MEDIA_CARD_PEEK",
        titleRes = R.string.ss_MEDIA_CARD_PEEK_title,
        subtitleRes = R.string.ss_MEDIA_CARD_PEEK_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_experimental,
        keywords = listOf("press", "hold", "peek", "preview", "media card", "long press", "experimental"),
        route = Route.ExperimentalSettings(),
        icon = Tabler.Outline.HandFinger,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "DIRECT_ARR_INTEGRATION",
        titleRes = R.string.ss_DIRECT_ARR_INTEGRATION_title,
        subtitleRes = R.string.ss_DIRECT_ARR_INTEGRATION_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_experimental,
        keywords = listOf("radarr", "sonarr", "arr", "download", "queue", "calendar", "coming soon", "grabbed", "imported"),
        route = Route.ExperimentalSettings(),
        icon = Tabler.Outline.Download
    ),
    SettingsSearchItem(
        id = "arr_settings",
        titleRes = R.string.ss_arr_settings_title,
        subtitleRes = R.string.ss_arr_settings_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_integrations,
        keywords = listOf("radarr", "sonarr", "arr", "servers", "api key", "integration"),
        route = Route.ArrSettings(),
        icon = Tabler.Outline.Download
    ),
)
