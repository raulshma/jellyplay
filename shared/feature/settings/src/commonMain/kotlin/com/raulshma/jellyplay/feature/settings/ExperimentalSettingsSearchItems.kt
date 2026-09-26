package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalPreferenceSpecs
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
 * The single-source row ids of this file's settings-search declarations.
 * Every consumer — the `SettingsSearchItem` declarations below, the screen
 * rows' `highlighted` comparisons, the admissions keys and the row-total
 * derivations — references these constants, so each id literal exists
 * exactly once. The values are the persisted deep-link/recents contract:
 * they change only deliberately, here.
 */
internal object ExperimentalSettingsIds {
    const val EXPERIMENTAL = "experimental"
    const val HOME_CARD_CLIPPING = "HOME_CARD_CLIPPING"
    const val MEDIA_CARD_PEEK = "MEDIA_CARD_PEEK"
    const val DIRECT_ARR_INTEGRATION = "DIRECT_ARR_INTEGRATION"
    const val ARR_SETTINGS = "arr_settings"
}

/**
 * Settings-search items for the "Experimental" group, derived (Stage A
 * pilot) from the domain's spec declarations in
 * [ExperimentalPreferenceSpecs.searchEntries]: the semantics (ids, keywords,
 * category keys, isAdvanced, platform rules, route kinds) are declared once
 * next to
 * [com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore];
 * this file is only the id → resource/icon binding table, the domain's
 * routeKind → Route map, plus the derivation call. The ids below are
 * IDENTICAL to the retired hand-written list (the screen entry, the three
 * feature toggles, the *arr settings hub) so deep-linking,
 * `SettingsScreenGroups` membership and the
 * `SettingsCatalogScreenContractTest` source scan keep passing unchanged.
 * Aggregated in [SettingsSearchCatalog] via [SettingsScreenGroups].
 */
private val searchRoutes: Map<String, Route> = mapOf(
    ExperimentalPreferenceSpecs.ROUTE_EXPERIMENTAL_SETTINGS to Route.ExperimentalSettings(),
    ExperimentalPreferenceSpecs.ROUTE_ARR_SETTINGS to Route.ArrSettings(),
)

internal val ExperimentalSettingsSearchItems: List<SettingsSearchItem> =
    ExperimentalPreferenceSpecs.searchEntries.toSettingsSearchItems(
        routes = searchRoutes,
        bindings = listOf(
            SettingsSearchBinding(
                id = ExperimentalSettingsIds.EXPERIMENTAL,
                titleRes = Res.string.ss_experimental_title,
                subtitleRes = Res.string.ss_experimental_subtitle,
                categoryRes = CoreUiRes.string.ss_cat_experimental,
                icon = Tabler.Outline.Flask,
            ),
            SettingsSearchBinding(
                id = ExperimentalSettingsIds.HOME_CARD_CLIPPING,
                titleRes = Res.string.ss_HOME_CARD_CLIPPING_title,
                subtitleRes = Res.string.ss_HOME_CARD_CLIPPING_subtitle,
                categoryRes = CoreUiRes.string.ss_cat_experimental,
                icon = Tabler.Outline.Photo,
            ),
            SettingsSearchBinding(
                id = ExperimentalSettingsIds.MEDIA_CARD_PEEK,
                titleRes = Res.string.ss_MEDIA_CARD_PEEK_title,
                subtitleRes = Res.string.ss_MEDIA_CARD_PEEK_subtitle,
                categoryRes = CoreUiRes.string.ss_cat_experimental,
                icon = Tabler.Outline.HandFinger,
            ),
            SettingsSearchBinding(
                id = ExperimentalSettingsIds.DIRECT_ARR_INTEGRATION,
                titleRes = Res.string.ss_DIRECT_ARR_INTEGRATION_title,
                subtitleRes = Res.string.ss_DIRECT_ARR_INTEGRATION_subtitle,
                categoryRes = CoreUiRes.string.ss_cat_experimental,
                icon = Tabler.Outline.Download,
            ),
            SettingsSearchBinding(
                id = ExperimentalSettingsIds.ARR_SETTINGS,
                titleRes = Res.string.ss_arr_settings_title,
                subtitleRes = Res.string.ss_arr_settings_subtitle,
                categoryRes = CoreUiRes.string.ss_cat_integrations,
                icon = Tabler.Outline.Download,
            ),
        ),
    )
