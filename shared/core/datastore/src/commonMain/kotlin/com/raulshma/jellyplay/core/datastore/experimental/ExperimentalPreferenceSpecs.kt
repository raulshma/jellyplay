package com.raulshma.jellyplay.core.datastore.experimental

import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.UpdateDismissPeriod

/**
 * The Experimental domain's single preference declaration (Stage A pilot of
 * the spec machinery): one [PreferenceSpec] per knob this store owns — the
 * canonical key name, value type, default, reset category, and (where the
 * knob is searchable) its settings-search metadata as plain data.
 *
 * [ExperimentalStore] binds each spec to its existing key / read / setter
 * machinery via `Knob.of`, so the specs add a declaration without changing
 * persistence: key names, encodings and file layout are exactly the
 * store's own. The settings feature derives its search catalog rows from
 * [searchEntries] and binds the resource keys there.
 *
 * Not declared here, deliberately:
 *  - `show_advanced_settings` — the field lives on `AppearanceSlice`;
 *    this store is only the EXPERIMENTAL-category reset owner for it.
 *  - `dismissed_update_version` / `dismissed_update_at_ms` — one-time
 *    update-dismissal state, not a user preference (never reset, no knob).
 */
object ExperimentalPreferenceSpecs {

    /** Route kind: rows that deep-link into the Experimental screen. */
    const val ROUTE_EXPERIMENTAL_SETTINGS = "experimental_settings"

    /** Route kind: the row that deep-links into the *arr settings screen. */
    const val ROUTE_ARR_SETTINGS = "arr_settings"

    // ------------------------------------------------------------------
    // Opt-in feature toggles — three user-facing knobs over ONE persisted
    // JSON set key (`enabled_experimental_features`); the per-feature read
    // (membership) and write (set ± feature) are the store's codec.
    // ------------------------------------------------------------------

    private val searchExperimentalScreen = PreferenceSearchSpec(
        id = "experimental",
        titleKey = "ss_experimental_title",
        subtitleKey = "ss_experimental_subtitle",
        categoryKey = "ss_cat_experimental",
        keywords = listOf("experimental", "beta", "labs", "preview", "early access", "developer"),
        routeKind = ROUTE_EXPERIMENTAL_SETTINGS,
    )

    private val searchHomeCardClipping = PreferenceSearchSpec(
        id = "HOME_CARD_CLIPPING",
        titleKey = "ss_HOME_CARD_CLIPPING_title",
        subtitleKey = "ss_HOME_CARD_CLIPPING_subtitle",
        categoryKey = "ss_cat_experimental",
        keywords = listOf("home", "card", "clipping", "render", "experimental"),
        routeKind = ROUTE_EXPERIMENTAL_SETTINGS,
        isAdvanced = true,
    )

    private val searchMediaCardPeek = PreferenceSearchSpec(
        id = "MEDIA_CARD_PEEK",
        titleKey = "ss_MEDIA_CARD_PEEK_title",
        subtitleKey = "ss_MEDIA_CARD_PEEK_subtitle",
        categoryKey = "ss_cat_experimental",
        keywords = listOf("press", "hold", "peek", "preview", "media card", "long press", "experimental"),
        routeKind = ROUTE_EXPERIMENTAL_SETTINGS,
        isAdvanced = true,
    )

    private val searchDirectArrIntegration = PreferenceSearchSpec(
        id = "DIRECT_ARR_INTEGRATION",
        titleKey = "ss_DIRECT_ARR_INTEGRATION_title",
        subtitleKey = "ss_DIRECT_ARR_INTEGRATION_subtitle",
        categoryKey = "ss_cat_experimental",
        keywords = listOf("radarr", "sonarr", "arr", "download", "queue", "calendar", "coming soon", "grabbed", "imported"),
        routeKind = ROUTE_EXPERIMENTAL_SETTINGS,
    )

    /** The *arr settings hub entry — navigation into the arr screen, no knob. */
    private val searchArrSettings = PreferenceSearchSpec(
        id = "arr_settings",
        titleKey = "ss_arr_settings_title",
        subtitleKey = "ss_arr_settings_subtitle",
        categoryKey = "ss_cat_integrations",
        keywords = listOf("radarr", "sonarr", "arr", "servers", "api key", "integration"),
        routeKind = ROUTE_ARR_SETTINGS,
    )

    val HOME_CARD_CLIPPING: PreferenceSpec<Boolean> = PreferenceSpec.custom(
        keyName = "enabled_experimental_features",
        default = false,
        resetCategory = PreferenceResetCategory.EXPERIMENTAL,
        search = searchHomeCardClipping,
    )

    val MEDIA_CARD_PEEK: PreferenceSpec<Boolean> = PreferenceSpec.custom(
        keyName = "enabled_experimental_features",
        default = false,
        resetCategory = PreferenceResetCategory.EXPERIMENTAL,
        search = searchMediaCardPeek,
    )

    val DIRECT_ARR_INTEGRATION: PreferenceSpec<Boolean> = PreferenceSpec.custom(
        keyName = "enabled_experimental_features",
        default = false,
        resetCategory = PreferenceResetCategory.EXPERIMENTAL,
        search = searchDirectArrIntegration,
    )

    /**
     * The per-feature toggle specs, keyed by the persisted enum — the single
     * registry a new `ExperimentalFeature` joins (its spec is where the
     * feature's search row is declared; the settings screen's toggle registry
     * and the catalog binding table follow it).
     */
    val featureSpecs: Map<ExperimentalFeature, PreferenceSpec<Boolean>> = mapOf(
        ExperimentalFeature.HOME_CARD_CLIPPING to HOME_CARD_CLIPPING,
        ExperimentalFeature.MEDIA_CARD_PEEK to MEDIA_CARD_PEEK,
        ExperimentalFeature.DIRECT_ARR_INTEGRATION to DIRECT_ARR_INTEGRATION,
    )

    fun featureSpec(feature: ExperimentalFeature): PreferenceSpec<Boolean> =
        requireNotNull(featureSpecs[feature]) { "no preference spec declared for experimental feature ${feature.name}" }

    // ------------------------------------------------------------------
    // Misc-app knobs (reset under MISC_APP; this store's reset list carries
    // the self-update/share/history five — see ExperimentalStore.resetKeysFor)
    // ------------------------------------------------------------------

    val SELF_UPDATE_CHECK_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.boolean(
        keyName = "self_update_check_enabled",
        default = true,
        resetCategory = PreferenceResetCategory.MISC_APP,
    )

    val SELF_UPDATE_DOWNLOAD_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.boolean(
        keyName = "self_update_download_enabled",
        default = false,
        resetCategory = PreferenceResetCategory.MISC_APP,
    )

    /**
     * Nullable app-language override (absent = follow system). Declares
     * `MISC_APP` as its logical reset category; the MISC_APP reset LIST
     * entry is owned by `SubtitleLanguageStore` (see the store docs) — this
     * store must not add it to its own list.
     */
    val APP_LANGUAGE: PreferenceSpec<String?> = PreferenceSpec.string(
        keyName = "app_language",
        default = null,
        resetCategory = PreferenceResetCategory.MISC_APP,
    )

    val SHOW_SHARE_MEDIA_OPTION: PreferenceSpec<Boolean> = PreferenceSpec.boolean(
        keyName = "show_share_media_option",
        default = true,
        resetCategory = PreferenceResetCategory.MISC_APP,
    )

    val HIDE_SEARCH_HISTORY: PreferenceSpec<Boolean> = PreferenceSpec.boolean(
        keyName = "hide_search_history",
        default = false,
        resetCategory = PreferenceResetCategory.MISC_APP,
    )

    /** Same MISC_APP reset-ownership note as [APP_LANGUAGE]: list entry owned by `SubtitleLanguageStore`. */
    val PREFER_AUDIO_DESCRIPTION: PreferenceSpec<Boolean> = PreferenceSpec.boolean(
        keyName = "prefer_audio_description",
        default = false,
        resetCategory = PreferenceResetCategory.MISC_APP,
    )

    /** Dismissed-update suppression window, persisted as the enum name. */
    val UPDATE_DISMISS_PERIOD: PreferenceSpec<UpdateDismissPeriod> = PreferenceSpec.custom(
        keyName = "update_dismiss_period",
        default = UpdateDismissPeriod.DEFAULT,
        resetCategory = PreferenceResetCategory.MISC_APP,
    )

    /**
     * The domain's settings-search declaration, in catalog order — the
     * single source the settings feature derives its
     * `ExperimentalSettingsSearchItems` from (ids identical to the retired
     * hand-written list: the screen entry, the three feature toggles, the
     * *arr settings hub).
     */
    val searchEntries: List<PreferenceSearchSpec> = listOf(
        searchExperimentalScreen,
        searchHomeCardClipping,
        searchMediaCardPeek,
        searchDirectArrIntegration,
        searchArrSettings,
    )

    /** Every knob spec declared here, in store-declaration order. */
    val all: List<PreferenceSpec<*>> = listOf(
        HOME_CARD_CLIPPING,
        MEDIA_CARD_PEEK,
        DIRECT_ARR_INTEGRATION,
        SELF_UPDATE_CHECK_ENABLED,
        SELF_UPDATE_DOWNLOAD_ENABLED,
        APP_LANGUAGE,
        SHOW_SHARE_MEDIA_OPTION,
        HIDE_SEARCH_HISTORY,
        PREFER_AUDIO_DESCRIPTION,
        UPDATE_DISMISS_PERIOD,
    )
}
