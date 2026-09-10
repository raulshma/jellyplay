package com.raulshma.jellyplay.core.ui.navigation

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pins the [NAV_DESTINATIONS] registry — the one home for destination facts.
 * The registry is what both shells render icons/labels/groups from, and the
 * persisted-vocabulary table is derived from it, so a route appearing in the
 * customization UI without a registry row (or with a duplicate key) must fail
 * here, not drift silently in a shell.
 */
class NavDestinationRegistryTest {

    @Test
    fun registryCoversEveryCustomizableRouteExactlyOnce() {
        assertEquals(
            CUSTOMIZABLE_TOP_LEVEL_ROUTES.toSet(),
            NAV_DESTINATIONS.map { it.route }.toSet(),
            "every customizable top-level route needs exactly one registry row",
        )
        assertEquals(
            NAV_DESTINATIONS.size,
            NAV_DESTINATIONS.map { it.route }.distinct().size,
            "registry rows must be route-distinct",
        )
    }

    @Test
    fun registryKeysMatchThePersistedNavKeyVocabulary() {
        // NAV_KEYS_BY_ROUTE is derived from the registry; pin the join so a
        // registry edit can never silently re-key an existing install's
        // stored hiddenNavItems/navItemOrder.
        NAV_DESTINATIONS.forEach { destination ->
            assertEquals(destination.key, destination.route.navKey, "registry key must equal ${destination.route::class.simpleName}.navKey")
        }
    }

    @Test
    fun registryKeysAndRailLabelsAreUnique() {
        assertEquals(
            NAV_DESTINATIONS.size,
            NAV_DESTINATIONS.map { it.key }.distinct().size,
            "persisted keys must be unique",
        )
        assertEquals(
            NAV_DESTINATIONS.size,
            NAV_DESTINATIONS.map { it.railLabel }.distinct().size,
            "rail labels must be unique",
        )
    }

    @Test
    fun lookupResolvesEveryRegistryRow() {
        NAV_DESTINATIONS.forEach { destination ->
            assertEquals(destination, NAV_DESTINATION_BY_ROUTE[destination.route])
        }
    }

    @Test
    fun navIconResolvesForEveryRegistryRoute() {
        // The icon fallback is only for non-registered (detail) routes; a
        // registered route rendering the fallback Home icon would be the
        // silent-mislabel drift this registry killed.
        NAV_DESTINATIONS.forEach { destination ->
            assertEquals(
                destination.icon,
                destination.route.navIcon,
                "${destination.route::class.simpleName} must render its registry icon",
            )
        }
    }
}
