package com.raulshma.jellyplay.core.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [visibleTopLevelRoutes] — the top-level-route composition fold both
 * shells render through (the former Android-shell-only test, retargeted):
 * the offline hide-set (Live TV — no offline fallback), the user's nav
 * customization (hidden items + stored order), and the three composed in that
 * order. The base set is the caller's display-order policy — the tests pass
 * the two published tables the way the Android shell does; the desktop rail
 * passes its own.
 */
class VisibleTopLevelRoutesTest {

    private val nothingHidden: Set<String> = emptySet()
    private val noOrder: List<String> = emptyList()

    private fun videoRoutes(
        hiddenNavItems: Set<String> = nothingHidden,
        navItemOrder: List<String> = noOrder,
        isOffline: Boolean = false,
    ) = visibleTopLevelRoutes(
        VIDEO_TOP_LEVEL_ROUTES,
        hiddenNavItems,
        navItemOrder,
        isOffline,
    )

    @Test
    fun `online video mode shows the full video route set in base order`() {
        assertEquals(
            linkedMapOf(
                Route.Home to "Home",
                Route.Library to "Library",
                Route.Search to "Search",
                Route.LiveTv to "Live TV",
            ),
            videoRoutes(isOffline = false),
        )
    }

    @Test
    fun `offline hides only Live TV — the server-bound destination without an offline fallback`() {
        val routes = videoRoutes(isOffline = true)
        assertEquals(
            listOf(Route.Home, Route.Library, Route.Search),
            routes.keys.toList(),
        )
        // Library deliberately stays: its grid auto-switches to the offline
        // store (#147), and Home/Search keep offline paths of their own.
    }

    @Test
    fun `the music base set swaps the input — and offline leaves it untouched`() {
        val online = visibleTopLevelRoutes(MUSIC_TOP_LEVEL_ROUTES, nothingHidden, noOrder, isOffline = false)
        assertEquals(
            listOf(Route.Home, Route.MusicBrowse, Route.Search),
            online.keys.toList(),
        )
        // LiveTv is not in the music set, so the offline hide-set is a no-op.
        assertEquals(
            online.keys.toList(),
            visibleTopLevelRoutes(MUSIC_TOP_LEVEL_ROUTES, nothingHidden, noOrder, isOffline = true).keys.toList(),
        )
    }

    @Test
    fun `user-hidden items are filtered by navKey`() {
        val routes = videoRoutes(
            hiddenNavItems = setOf(Route.Library.navKey),
        )
        assertEquals(
            listOf(Route.Home, Route.Search, Route.LiveTv),
            routes.keys.toList(),
        )
    }

    @Test
    fun `stored nav order reorders while keeping unlisted routes in input order`() {
        val routes = videoRoutes(
            navItemOrder = listOf("Search", "Home"),
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
        val routes = videoRoutes(
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

    @Test
    fun `the list form applies the same offline-hide policy to descriptor items`() {
        // The desktop rail's shape: items carry more than a label, so the
        // list form filters/orders the items themselves. Offline drops ONLY
        // the LiveTv descriptor; the rest keep their input order.
        val rail = VIDEO_TOP_LEVEL_ROUTES.keys.map { route ->
            NAV_DESTINATION_BY_ROUTE.getValue(route)
        }
        val descriptors = visibleTopLevelRoutes(
            rail,
            { it.route.navKey },
            hiddenNavItems = nothingHidden,
            navItemOrder = noOrder,
            isOffline = true,
        )

        assertEquals(
            listOf(Route.Home, Route.Library, Route.Search),
            descriptors.map { it.route },
        )
    }
}
