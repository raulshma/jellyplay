package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.PlatformKind
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the spec-derived Experimental catalog (Stage A pilot) against the
 * retired hand-written `ExperimentalSettingsSearchItems` list, field for
 * field: the derivation ([toSettingsSearchItems] over
 * `ExperimentalPreferenceSpecs.searchEntries`) must be byte-equivalent in
 * behavior — same ids in the same order, same bound resources, same
 * keywords, routes, icons, isAdvanced flags and platform availability — so
 * the switch to spec-derived entries changes nothing downstream (deep-link
 * highlighting, group membership, search tie-break order).
 */
class ExperimentalSettingsSearchItemsTest {

    @Test
    fun `derived entries stay byte-equivalent to the retired hand-written list`() {
        val items = ExperimentalSettingsSearchItems
        assertEquals(5, items.size)

        val experimental = items[0]
        assertEquals("experimental", experimental.id)
        assertEquals("ss_experimental_title", experimental.titleRes.key)
        assertEquals("ss_experimental_subtitle", experimental.subtitleRes.key)
        assertEquals("ss_cat_experimental", experimental.categoryRes.key)
        assertEquals(listOf("experimental", "beta", "labs", "preview", "early access", "developer"), experimental.keywords)
        assertEquals(Route.ExperimentalSettings(), experimental.route)
        assertSame(Tabler.Outline.Flask, experimental.icon)
        assertFalse(experimental.isAdvanced)
        assertEquals(PlatformKind.entries.toSet(), experimental.platforms)

        val clipping = items[1]
        assertEquals("HOME_CARD_CLIPPING", clipping.id)
        assertEquals("ss_HOME_CARD_CLIPPING_title", clipping.titleRes.key)
        assertEquals("ss_HOME_CARD_CLIPPING_subtitle", clipping.subtitleRes.key)
        assertEquals("ss_cat_experimental", clipping.categoryRes.key)
        assertEquals(listOf("home", "card", "clipping", "render", "experimental"), clipping.keywords)
        assertEquals(Route.ExperimentalSettings(), clipping.route)
        assertSame(Tabler.Outline.Photo, clipping.icon)
        assertTrue(clipping.isAdvanced)
        assertEquals(PlatformKind.entries.toSet(), clipping.platforms)

        val peek = items[2]
        assertEquals("MEDIA_CARD_PEEK", peek.id)
        assertEquals("ss_MEDIA_CARD_PEEK_title", peek.titleRes.key)
        assertEquals("ss_MEDIA_CARD_PEEK_subtitle", peek.subtitleRes.key)
        assertEquals("ss_cat_experimental", peek.categoryRes.key)
        assertEquals(listOf("press", "hold", "peek", "preview", "media card", "long press", "experimental"), peek.keywords)
        assertEquals(Route.ExperimentalSettings(), peek.route)
        assertSame(Tabler.Outline.HandFinger, peek.icon)
        assertTrue(peek.isAdvanced)
        assertEquals(PlatformKind.entries.toSet(), peek.platforms)

        val directArr = items[3]
        assertEquals("DIRECT_ARR_INTEGRATION", directArr.id)
        assertEquals("ss_DIRECT_ARR_INTEGRATION_title", directArr.titleRes.key)
        assertEquals("ss_DIRECT_ARR_INTEGRATION_subtitle", directArr.subtitleRes.key)
        assertEquals("ss_cat_experimental", directArr.categoryRes.key)
        assertEquals(
            listOf("radarr", "sonarr", "arr", "download", "queue", "calendar", "coming soon", "grabbed", "imported"),
            directArr.keywords,
        )
        assertEquals(Route.ExperimentalSettings(), directArr.route)
        assertSame(Tabler.Outline.Download, directArr.icon)
        assertFalse(directArr.isAdvanced)
        assertEquals(PlatformKind.entries.toSet(), directArr.platforms)

        val arrSettings = items[4]
        assertEquals("arr_settings", arrSettings.id)
        assertEquals("ss_arr_settings_title", arrSettings.titleRes.key)
        assertEquals("ss_arr_settings_subtitle", arrSettings.subtitleRes.key)
        assertEquals("ss_cat_integrations", arrSettings.categoryRes.key)
        assertEquals(listOf("radarr", "sonarr", "arr", "servers", "api key", "integration"), arrSettings.keywords)
        assertEquals(Route.ArrSettings(), arrSettings.route)
        assertSame(Tabler.Outline.Download, arrSettings.icon)
        assertFalse(arrSettings.isAdvanced)
        assertEquals(PlatformKind.entries.toSet(), arrSettings.platforms)
    }

    @Test
    fun `derived ids match the experimental screen group declaration`() {
        assertEquals(
            SettingsScreenGroups.experimental.itemIds,
            ExperimentalSettingsSearchItems.map { it.id },
            "the derived list must keep feeding the experimental screen group unchanged",
        )
    }
}
