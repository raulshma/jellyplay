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
 * The single-source row ids of this file's settings-search declarations.
 * Every consumer — the `SettingsSearchItem` declarations below, the screen
 * rows' `highlighted` comparisons, the admissions keys and the row-total
 * derivations — references these constants, so each id literal exists
 * exactly once. The values are the persisted deep-link/recents contract:
 * they change only deliberately, here.
 */
internal object AboutScreenIds {
    const val ABOUT_VERSION = "about_version"
}

/**
 * Settings-search items for the "About" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to AboutScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val AboutSearchItems = listOf(
    SettingsSearchItem(
        id = AboutScreenIds.ABOUT_VERSION,
        titleRes = Res.string.ss_about_version_title,
        subtitleRes = Res.string.ss_about_version_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_about,
        keywords = listOf("about", "version", "licenses", "open source", "developer"),
        route = Route.About,
        icon = Tabler.Outline.InfoCircle
    ),
)
