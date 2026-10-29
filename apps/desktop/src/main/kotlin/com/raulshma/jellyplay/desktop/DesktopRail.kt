package com.raulshma.jellyplay.desktop

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import com.raulshma.jellyplay.core.ui.navigation.NAV_DESTINATION_BY_ROUTE
import com.raulshma.jellyplay.core.ui.navigation.NAV_DESTINATIONS
import com.raulshma.jellyplay.core.ui.navigation.NavDestination
import com.raulshma.jellyplay.core.ui.navigation.NavPrimarySurface
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer

/**
 * The rail renders the shared [NavDestination] registry — label, icon, group
 * AND the display order come from the one destination-facts table in core/ui.
 * Two things stay shell-side, both derivations over the registry rows rather
 * than hand lists: the rail's OWN display order ([DESKTOP_RAIL_ROUTES] — the
 * rows' [NavDestination.railOrder] positions, the recorded desktop order
 * policy that deliberately differs from the registry's customization-UI
 * order), and nothing else — PRIMARY ELIGIBILITY is the registry's placement
 * descriptor ([NavDestination.primarySurfaces], filtered into
 * [DESKTOP_RAIL_ITEMS]).
 *
 * A destination joins (or moves on) the rail by editing its registry row's
 * `railOrder` — a route listed without a row is impossible by construction
 * (the order IS the row), but the former failure modes still fail loudly:
 * NavDestinationRegistryTest pins railOrder presence ⇔ DesktopRail placement
 * and dense 0..n-1 positions, and DesktopRailRegistryContractTest pins the
 * exact sequence against the registry lookup. A silent drop can therefore
 * only be a deliberate registry edit that removed both the placement and the
 * order — never a forgotten row.
 */
internal val DESKTOP_RAIL_ROUTES: List<Route> = NAV_DESTINATIONS
    .mapNotNull { row -> row.railOrder?.let { order -> row to order } }
    .sortedBy { (_, order) -> order }
    .map { (row, _) -> row.route }

/**
 * The rail's items: the listed routes resolved to their registry rows, kept
 * only when the registry places them on this surface
 * ([NavPrimarySurface.DesktopRail]) — the placement eligibility vote is the
 * registry's; the list above contributes the order. DesktopRailRegistryContractTest
 * pins the filtered membership equal to the descriptor's DesktopRail set both
 * ways, so a listed-but-unplaced route drops here (and fails the contract
 * test) instead of rendering on a surface the registry didn't place it on.
 */
internal val DESKTOP_RAIL_ITEMS: List<NavDestination> = DESKTOP_RAIL_ROUTES.map { route ->
    checkNotNull(NAV_DESTINATION_BY_ROUTE[route]) {
        "DESKTOP_RAIL_ROUTES lists ${route::class.simpleName} but the NAV_DESTINATIONS registry " +
            "has no row for it — register the destination or drop it from the rail list."
    }
}.filter { NavPrimarySurface.DesktopRail in it.primarySurfaces }

/**
 * Rail + tab-switch destinations; Home is the start tab (same as the Android
 * shell). Internal (was file-private in DesktopAppRoot) because
 * [DesktopNavScaffold] consumes it from its own file.
 */
internal val DESKTOP_TOP_LEVEL_ROUTES: Set<Route> = DESKTOP_RAIL_ITEMS.map { it.route }.toSet()

/**
 * The saved-state configuration every desktop NavDisplay shares: the sealed
 * Route serializer is registered as the polymorphic NavKey default so
 * saved-state lookups resolve any Route subclass without enumerating ~100
 * leaves per lookup. Two consumers today — [DesktopNavScaffold] and the
 * signed-out shell's shared SignedOutAuthHost (via its content slot).
 */
internal fun desktopNavSavedStateConfiguration(): SavedStateConfiguration =
    SavedStateConfiguration {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) {
                // The sealed Route hierarchy enumerates its leaves via
                // kotlin-reflect; new Route subclasses register themselves.
                for (leaf in Route::class.sealedSubclasses) {
                    @Suppress("UNCHECKED_CAST")
                    val leafClass = leaf as KClass<NavKey>
                    @Suppress("UNCHECKED_CAST")
                    val leafSerializer =
                        serializer(leaf.java as Class<NavKey>) as KSerializer<NavKey>
                    subclass(leafClass, leafSerializer)
                }
            }
        }
    }
