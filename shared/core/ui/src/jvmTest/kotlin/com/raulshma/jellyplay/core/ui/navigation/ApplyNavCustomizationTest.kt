package com.raulshma.jellyplay.core.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the shared nav-customization application ([applyNavCustomization])
 * and the [navKey] persistence vocabulary — the write-side keys the settings
 * UI stores and the read-side comparisons every shell (Android floating bar,
 * desktop rail, TV drawer) performs must stay in one contract (#152).
 */
class ApplyNavCustomizationTest {

    private val routes = linkedMapOf(
        Route.Home to "Home",
        Route.Library to "Library",
        Route.Search to "Search",
        Route.LiveTv to "Live TV",
    )

    @Test
    fun navKeysAreStableLiteralsMatchingLegacyStoredValues() {
        assertEquals("Home", Route.Home.navKey)
        assertEquals("Library", Route.Library.navKey)
        assertEquals("Search", Route.Search.navKey)
        assertEquals("LiveTv", Route.LiveTv.navKey)
        assertEquals("MusicBrowse", Route.MusicBrowse.navKey)
        assertEquals("Shortcuts", SHORTCUTS_NAV_KEY)
        assertEquals("Shortcuts", Route.Shortcuts.navKey)
    }

    @Test
    fun emptyCustomizationKeepsInputOrder() {
        val applied = applyNavCustomization(routes, hiddenNavItems = emptySet(), navItemOrder = emptyList())
        assertEquals(routes.keys.toList(), applied.keys.toList())
        assertEquals(routes.values.toList(), applied.values.toList())
    }

    @Test
    fun hiddenItemsAreDropped() {
        val applied = applyNavCustomization(routes, hiddenNavItems = setOf("LiveTv"), navItemOrder = emptyList())
        assertEquals(listOf(Route.Home, Route.Library, Route.Search), applied.keys.toList())
    }

    @Test
    fun customOrderReordersSurvivors() {
        val applied = applyNavCustomization(
            routes,
            hiddenNavItems = emptySet(),
            navItemOrder = listOf("Search", "Home", "Library", "LiveTv"),
        )
        assertEquals(listOf(Route.Search, Route.Home, Route.Library, Route.LiveTv), applied.keys.toList())
    }

    @Test
    fun partialOrderAppendsUnorderedSurvivorsInInputOrder() {
        val applied = applyNavCustomization(routes, hiddenNavItems = emptySet(), navItemOrder = listOf("LiveTv"))
        assertEquals(listOf(Route.LiveTv, Route.Home, Route.Library, Route.Search), applied.keys.toList())
    }

    @Test
    fun unknownHiddenAndOrderEntriesAreIgnored() {
        // Stored sets from older/obfuscated builds can carry keys no route
        // matches anymore; they must neither crash nor hide anything.
        val applied = applyNavCustomization(
            routes,
            hiddenNavItems = setOf("Bogus", "a"),
            navItemOrder = listOf("AlsoBogus", "Home"),
        )
        assertEquals(listOf(Route.Home, Route.Library, Route.Search, Route.LiveTv), applied.keys.toList())
    }

    @Test
    fun hidingEveryItemYieldsEmptyMap() {
        val applied = applyNavCustomization(
            routes,
            hiddenNavItems = routes.keys.map { it.navKey }.toSet(),
            navItemOrder = emptyList(),
        )
        assertTrue(applied.isEmpty())
    }

    @Test
    fun duplicateOrderEntriesDoNotDuplicateItems() {
        val applied = applyNavCustomization(routes, hiddenNavItems = emptySet(), navItemOrder = listOf("Home", "Home"))
        assertEquals(listOf(Route.Home, Route.Library, Route.Search, Route.LiveTv), applied.keys.toList())
    }
}
