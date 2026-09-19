package com.raulshma.jellyplay.core.datastore.experimental

import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.spec.PreferencePlatformRule
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.UpdateDismissPeriod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Pins the Experimental domain's spec declarations (Stage A pilot): the
 * search entries must stay byte-equivalent to the retired hand-written
 * `ExperimentalSettingsSearchItems` list (ids, order, category keys,
 * keywords, isAdvanced, platform rules — the derivation source), and every
 * knob spec must bind the store's canonical key names / defaults / reset
 * categories, so the specs cannot drift the store they decorate.
 */
class ExperimentalPreferenceSpecsTest {

    @Test
    fun `search entries keep the retired hand-written ids in catalog order`() {
        assertEquals(
            listOf("experimental", "HOME_CARD_CLIPPING", "MEDIA_CARD_PEEK", "DIRECT_ARR_INTEGRATION", "arr_settings"),
            ExperimentalPreferenceSpecs.searchEntries.map { it.id },
        )
    }

    @Test
    fun `search entries stay byte-equivalent to the retired hand-written declarations`() {
        val byId = ExperimentalPreferenceSpecs.searchEntries.associateBy { it.id }
        fun entry(id: String) = requireNotNull(byId[id]) { "missing search entry $id" }

        val screen = entry("experimental")
        assertEquals("ss_experimental_title", screen.titleKey)
        assertEquals("ss_experimental_subtitle", screen.subtitleKey)
        assertEquals("ss_cat_experimental", screen.categoryKey)
        assertEquals(listOf("experimental", "beta", "labs", "preview", "early access", "developer"), screen.keywords)
        assertEquals("experimental_settings", screen.routeKind)
        assertFalse(screen.isAdvanced)
        assertEquals(PreferencePlatformRule.ALL, screen.platformRule)

        val clipping = entry("HOME_CARD_CLIPPING")
        assertEquals("ss_HOME_CARD_CLIPPING_title", clipping.titleKey)
        assertEquals("ss_HOME_CARD_CLIPPING_subtitle", clipping.subtitleKey)
        assertEquals("ss_cat_experimental", clipping.categoryKey)
        assertEquals(listOf("home", "card", "clipping", "render", "experimental"), clipping.keywords)
        assertEquals("experimental_settings", clipping.routeKind)
        assertEquals(true, clipping.isAdvanced)

        val peek = entry("MEDIA_CARD_PEEK")
        assertEquals("ss_MEDIA_CARD_PEEK_title", peek.titleKey)
        assertEquals("ss_MEDIA_CARD_PEEK_subtitle", peek.subtitleKey)
        assertEquals("ss_cat_experimental", peek.categoryKey)
        assertEquals(listOf("press", "hold", "peek", "preview", "media card", "long press", "experimental"), peek.keywords)
        assertEquals("experimental_settings", peek.routeKind)
        assertEquals(true, peek.isAdvanced)

        val directArr = entry("DIRECT_ARR_INTEGRATION")
        assertEquals("ss_DIRECT_ARR_INTEGRATION_title", directArr.titleKey)
        assertEquals("ss_DIRECT_ARR_INTEGRATION_subtitle", directArr.subtitleKey)
        assertEquals("ss_cat_experimental", directArr.categoryKey)
        assertEquals(
            listOf("radarr", "sonarr", "arr", "download", "queue", "calendar", "coming soon", "grabbed", "imported"),
            directArr.keywords,
        )
        assertEquals("experimental_settings", directArr.routeKind)
        assertFalse(directArr.isAdvanced)

        val arrSettings = entry("arr_settings")
        assertEquals("ss_arr_settings_title", arrSettings.titleKey)
        assertEquals("ss_arr_settings_subtitle", arrSettings.subtitleKey)
        assertEquals("ss_cat_integrations", arrSettings.categoryKey)
        assertEquals(listOf("radarr", "sonarr", "arr", "servers", "api key", "integration"), arrSettings.keywords)
        assertEquals("arr_settings", arrSettings.routeKind)
        assertFalse(arrSettings.isAdvanced)

        // Every entry rides the default platform rule (the retired list set no platforms).
        ExperimentalPreferenceSpecs.searchEntries.forEach {
            assertEquals(PreferencePlatformRule.ALL, it.platformRule, "entry ${it.id} changed platform availability")
        }
    }

    @Test
    fun `every experimental feature has one spec whose search id is its persisted name`() {
        assertEquals(
            ExperimentalFeature.entries.toList(),
            ExperimentalPreferenceSpecs.featureSpecs.keys.toList(),
            "feature specs must cover every enum value exactly once, in declaration order",
        )
        ExperimentalFeature.entries.forEach { feature ->
            val spec = ExperimentalPreferenceSpecs.featureSpec(feature)
            assertEquals(feature.name, spec.search?.id, "spec for $feature must own its search row")
            assertEquals("enabled_experimental_features", spec.keyName, "all feature toggles ride the one JSON set key")
            assertEquals(false, spec.default)
            assertEquals(PreferenceResetCategory.EXPERIMENTAL, spec.resetCategory)
        }
    }

    /**
     * Key equality is name-based, so a spec's rebuilt typed key must EQUAL the
     * store's hand-declared key for the same slot even where their static
     * type arguments differ (the custom-coded specs ride a string key). Both
     * sides widen to [Preferences.Key] so the pin is on runtime equality —
     * what reset lists and `prefs.remove` interop rely on.
     */
    private fun assertBindsKey(expected: Preferences.Key<*>, actual: Preferences.Key<*>) =
        assertEquals(expected, actual)

    @Test
    fun `knob specs bind the store's canonical keys, defaults and reset categories`() {
        assertBindsKey(ExperimentalStore.Keys.ENABLED_EXPERIMENTAL_FEATURES, ExperimentalPreferenceSpecs.HOME_CARD_CLIPPING.typedKey())
        assertBindsKey(ExperimentalStore.Keys.ENABLED_EXPERIMENTAL_FEATURES, ExperimentalPreferenceSpecs.MEDIA_CARD_PEEK.typedKey())
        assertBindsKey(ExperimentalStore.Keys.ENABLED_EXPERIMENTAL_FEATURES, ExperimentalPreferenceSpecs.DIRECT_ARR_INTEGRATION.typedKey())
        assertBindsKey(ExperimentalStore.Keys.SELF_UPDATE_CHECK_ENABLED, ExperimentalPreferenceSpecs.SELF_UPDATE_CHECK_ENABLED.typedKey())
        assertBindsKey(ExperimentalStore.Keys.SELF_UPDATE_DOWNLOAD_ENABLED, ExperimentalPreferenceSpecs.SELF_UPDATE_DOWNLOAD_ENABLED.typedKey())
        assertBindsKey(ExperimentalStore.Keys.APP_LANGUAGE, ExperimentalPreferenceSpecs.APP_LANGUAGE.typedKey())
        assertBindsKey(ExperimentalStore.Keys.SHOW_SHARE_MEDIA_OPTION, ExperimentalPreferenceSpecs.SHOW_SHARE_MEDIA_OPTION.typedKey())
        assertBindsKey(ExperimentalStore.Keys.HIDE_SEARCH_HISTORY, ExperimentalPreferenceSpecs.HIDE_SEARCH_HISTORY.typedKey())
        assertBindsKey(ExperimentalStore.Keys.PREFER_AUDIO_DESCRIPTION, ExperimentalPreferenceSpecs.PREFER_AUDIO_DESCRIPTION.typedKey())
        assertBindsKey(ExperimentalStore.Keys.UPDATE_DISMISS_PERIOD, ExperimentalPreferenceSpecs.UPDATE_DISMISS_PERIOD.typedKey())

        // Defaults mirror the store's read projection (ExperimentalSlice defaults).
        assertEquals(true, ExperimentalPreferenceSpecs.SELF_UPDATE_CHECK_ENABLED.default)
        assertEquals(false, ExperimentalPreferenceSpecs.SELF_UPDATE_DOWNLOAD_ENABLED.default)
        assertNull(ExperimentalPreferenceSpecs.APP_LANGUAGE.default)
        assertEquals(true, ExperimentalPreferenceSpecs.SHOW_SHARE_MEDIA_OPTION.default)
        assertEquals(false, ExperimentalPreferenceSpecs.HIDE_SEARCH_HISTORY.default)
        assertEquals(false, ExperimentalPreferenceSpecs.PREFER_AUDIO_DESCRIPTION.default)
        assertEquals(UpdateDismissPeriod.DEFAULT, ExperimentalPreferenceSpecs.UPDATE_DISMISS_PERIOD.default)

        // Reset categories: EXPERIMENTAL for the feature set, MISC_APP for the misc-app knobs.
        ExperimentalPreferenceSpecs.all.forEach { spec ->
            assertEquals(
                if (spec.keyName == "enabled_experimental_features") PreferenceResetCategory.EXPERIMENTAL
                else PreferenceResetCategory.MISC_APP,
                spec.resetCategory,
                "unexpected reset category for ${spec.keyName}",
            )
        }
    }
}
