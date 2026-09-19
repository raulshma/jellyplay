package com.raulshma.jellyplay.desktop

import com.raulshma.jellyplay.core.ui.navigation.NAV_DESTINATION_BY_ROUTE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The apps/desktop half of the nav registry contract — core:ui's
 * NavDestinationRegistryTest holds the phone/TV half and cannot see this
 * module's rail list (the module graph runs desktop → core:ui, never the
 * reverse), so the desktop assertion lands here:
 *
 * every route [DESKTOP_RAIL_ROUTES] lists must resolve a [NAV_DESTINATIONS]
 * registry row (label, icon, group — the rail renders nothing else).
 *
 * DESKTOP_RAIL_ITEMS already fails at class-init when a listed route has no
 * row (its mapping uses checkNotNull, not the former silent `mapNotNull`
 * drop that let a route vanish off the rail unnoticed), so merely touching
 * the val below IS the assertion — the per-route checks keep the failure
 * message pointed at the offending route.
 */
class DesktopRailRegistryContractTest {

    @Test
    fun everyRailRouteHasARegistryRow() {
        DESKTOP_RAIL_ROUTES.forEach { route ->
            assertTrue(
                route in NAV_DESTINATION_BY_ROUTE,
                "${route::class.simpleName} is listed on the desktop rail but has no NAV_DESTINATIONS row",
            )
        }
    }

    @Test
    fun railItemsResolveExactlyTheListedRoutes() {
        // The resolved items keep the hand-listed membership/order one-to-one
        // — same size, same sequence, each the registry row of its route.
        assertEquals(DESKTOP_RAIL_ROUTES.size, DESKTOP_RAIL_ITEMS.size)
        assertEquals(DESKTOP_RAIL_ROUTES, DESKTOP_RAIL_ITEMS.map { it.route })
        DESKTOP_RAIL_ITEMS.forEach { item ->
            assertEquals(item, NAV_DESTINATION_BY_ROUTE[item.route])
        }
    }
}
