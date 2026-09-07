package com.raulshma.jellyplay.navigation

import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.navKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [visibleTopLevelRoutes] — the active-top-level-routes fold that used to
 * live inline in JellyPlayApp's `activeTopLevelRoutes` remember block: the
 * home-mode route set, the offline hide-set (Live TV — no offline fallback),
 * and the user's nav customization (hidden items + stored order) composed in
 * that order.
 */
class VisibleTopLevelRoutesTest {

    private val nothingHidden: Set<String> = emptySet()
    private val noOrder: List<String> = emptyList()

    @Test
    fun `online video mode shows the full video route set in base order`() {
        assertEquals(
            linkedMapOf(
                Route.Home to "Home",
                Route.Library to "Library",
                Route.Search to "Search",
                Route.LiveTv to "Live TV",
            ),
            visibleTopLevelRoutes(HomeMode.VIDEO, nothingHidden, noOrder, isOffline = false),
        )
    }

    @Test
    fun `offline hides only Live TV — the server-bound destination without an offline fallback`() {
        val routes = visibleTopLevelRoutes(HomeMode.VIDEO, nothingHidden, noOrder, isOffline = true)
        assertEquals(
            listOf(Route.Home, Route.Library, Route.Search),
            routes.keys.toList(),
        )
        // Library deliberately stays: its grid auto-switches to the offline
        // store (#147), and Home/Search keep offline paths of their own.
    }

    @Test
    fun `music mode swaps the base set — and offline leaves it untouched`() {
        val online = visibleTopLevelRoutes(HomeMode.MUSIC, nothingHidden, noOrder, isOffline = false)
        assertEquals(
            listOf(Route.Home, Route.MusicBrowse, Route.Search),
            online.keys.toList(),
        )
        // LiveTv is not in the music set, so the offline hide-set is a no-op.
        assertEquals(
            online.keys.toList(),
            visibleTopLevelRoutes(HomeMode.MUSIC, nothingHidden, noOrder, isOffline = true).keys.toList(),
        )
    }

    @Test
    fun `user-hidden items are filtered by navKey`() {
        val routes = visibleTopLevelRoutes(
            HomeMode.VIDEO,
            hiddenNavItems = setOf(Route.Library.navKey),
            navItemOrder = noOrder,
            isOffline = false,
        )
        assertEquals(
            listOf(Route.Home, Route.Search, Route.LiveTv),
            routes.keys.toList(),
        )
    }

    @Test
    fun `stored nav order reorders while keeping unlisted routes in input order`() {
        val routes = visibleTopLevelRoutes(
            HomeMode.VIDEO,
            hiddenNavItems = nothingHidden,
            navItemOrder = listOf("Search", "Home"),
            isOffline = false,
        )
        // Search and Home follow the stored rank; Library/LiveTv were never
        // listed, so they keep their relative input order after them.
        assertEquals(
            listOf(Route.Search, Route.Home, Route.Library, Route.LiveTv),
            routes.keys.toList(),
        )
    }

    @Test
    fun `offline + user-hidden + custom order compose`() {
        val routes = visibleTopLevelRoutes(
            homeMode = HomeMode.VIDEO,
            hiddenNavItems = setOf(Route.Search.navKey),
            navItemOrder = listOf("LiveTv", "Home"),
            isOffline = true,
        )
        // LiveTv is hidden offline despite ranking first in the stored order;
        // Library (unlisted, unhidden) keeps its input-order slot.
        assertEquals(
            listOf(Route.Home, Route.Library),
            routes.keys.toList(),
        )
        // The full offline+hidden+order combination in one map pin.
        assertEquals(
            linkedMapOf(
                Route.Home to "Home",
                Route.Library to "Library",
            ),
            routes,
        )
    }
}
