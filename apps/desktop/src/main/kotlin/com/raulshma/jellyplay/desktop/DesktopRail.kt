package com.raulshma.jellyplay.desktop

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import com.raulshma.jellyplay.core.ui.navigation.NAV_DESTINATION_BY_ROUTE
import com.raulshma.jellyplay.core.ui.navigation.NavDestination
import com.raulshma.jellyplay.core.ui.navigation.NavPrimarySurface
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer

/**
 * The rail renders the shared [NavDestination] registry — label, icon and
 * group come from the one destination-facts table in core/ui. Two things stay
 * here; both are per-shell policy, not destination facts: the rail's OWN
 * display order ([DESKTOP_RAIL_ROUTES]) and nothing else — PRIMARY ELIGIBILITY
 * is the registry's placement descriptor ([NavDestination.primarySurfaces],
 * filtered into [DESKTOP_RAIL_ITEMS]).
 *
 * Every listed route MUST have a registry row: resolution below fails loudly
 * at class-init. A silent `mapNotNull` drop would let a route that lost
 * its row vanish off the rail with no failure anywhere.
 * DesktopRailRegistryContractTest pins this (internal visibility exists for
 * that test — nothing else in the module may depend on the rail internals),
 * and pins the filtered membership against the descriptor's DesktopRail set
 * both ways, so the eligibility filter cannot silently drop or grow the rail.
 */
internal val DESKTOP_RAIL_ROUTES: List<Route> = listOf(
    Route.Home,
    Route.Search,
    Route.Library,
    Route.LiveTv,
    Route.MusicBrowse,
    Route.Downloads,
    Route.Newsletter,
    Route.WatchProgressHeatmap,
    Route.SyncPlay,
    Route.Requests,
    Route.UpcomingCalendar,
    Route.ArrQueue,
    Route.Shortcuts,
    Route.Settings,
    Route.AdminDashboard,
)

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
