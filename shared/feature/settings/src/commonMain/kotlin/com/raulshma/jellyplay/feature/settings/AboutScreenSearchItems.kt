package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_about
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_about_version_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_about_version_title

/**
 * Settings-search items for the "About" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to AboutScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val AboutSearchItems = listOf(
    SettingsSearchItem(
        id = "about_version",
        titleRes = Res.string.ss_about_version_title,
        subtitleRes = Res.string.ss_about_version_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_about,
        keywords = listOf("about", "version", "licenses", "open source", "developer"),
        route = Route.About,
        icon = Tabler.Outline.InfoCircle
    ),
)
