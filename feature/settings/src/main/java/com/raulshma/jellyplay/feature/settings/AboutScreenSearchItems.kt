package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem

/**
 * Settings-search items for the "About" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to AboutScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val AboutSearchItems = listOf(
    SettingsSearchItem(
        id = "about_version",
        titleRes = R.string.ss_about_version_title,
        subtitleRes = R.string.ss_about_version_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_about,
        keywords = listOf("about", "version", "licenses", "open source", "developer"),
        route = Route.About,
        icon = Tabler.Outline.InfoCircle
    ),
)
