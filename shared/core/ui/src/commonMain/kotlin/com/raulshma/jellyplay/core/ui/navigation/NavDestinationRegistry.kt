package com.raulshma.jellyplay.core.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bolt
import com.composables.icons.tabler.outline.Calendar
import com.composables.icons.tabler.outline.DeviceTv
import com.composables.icons.tabler.outline.Disc
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Flame
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.Library
import com.composables.icons.tabler.outline.Mail
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Settings
import com.composables.icons.tabler.outline.Shield
import com.composables.icons.tabler.outline.Stack
import com.composables.icons.tabler.outline.Users

/**
 * Top-level destination facts — the [NAV_DESTINATIONS] registry (key / icon /
 * rail label / group / per-surface membership), the lookup and icon
 * extensions built on it, the recorded label overrides, and the per-mode
 * membership tables derived from the registry rows. Split out of NavKey.kt,
 * whose class KDoc documents the restore contract governing every [Route]
 * declared there; the package (and therefore every persisted binary name and
 * the R8 keep rule) is unchanged.
 */
/**
 * One top-level destination's render facts: the persisted customization
 * [key], the [icon] every shell renders for it, the desktop-rail label +
 * [railGroup], and the per-surface [topLevelGroups] membership. This registry
 * is the single home for destination facts — the former per-shell parallel
 * tables (the Android `routeToIcon` when-with its silent Home fallback, the
 * desktop `DESKTOP_RAIL_ITEMS` label/icon/group list, and the hand-listed
 * video/music top-level tables) all derive from it, so a new top-level route
 * registers its facts exactly once.
 *
 * Icon note: the two shells previously disagreed on two routes (Library:
 * `Stack2` on phone vs `Library` on desktop; Shortcuts: `Apps` vs `Bolt`).
 * The registry unifies on the desktop set — a visual-only change on the
 * phone bar/drawer.
 *
 * Per-shell DISPLAY policy (which hidden/customization-filtered subset a
 * shell actually renders, and the desktop rail / TV drawer / overflow
 * memberships — deliberately per-shell decisions) stays out of the registry;
 * what moved IN is the per-surface membership + order every shell agreed on,
 * recorded as [NavDestination.topLevelGroups] and derived into the
 * video/music tables below. Their labels resolve through
 * [NAV_DESTINATION_BY_ROUTE], and the one deliberate wording divergence
 * ("Browse" on the music bar, "Music" on the rail) is recorded in
 * [TOP_LEVEL_LABEL_OVERRIDES] instead of being hand-copied per table.
 */
data class NavDestination(
    val route: Route,
    val key: String,
    val icon: ImageVector,
    val railLabel: String,
    val railGroup: NavDestinationGroup,
    /**
     * The top-level surfaces this destination renders on, keyed by surface
     * with that surface's 0-based bar/drawer position as the value — the
     * sort key the video/music tables below derive their order from (the
     * registry row order is the customization-UI display order, which is NOT
     * the same policy: MusicBrowse sits after Search there but before it on
     * the music bar). Empty for destinations that are never top-level tabs
     * (their rows exist for the rail/customization facts).
     */
    val topLevelGroups: Map<TopLevelGroup, Int> = emptyMap(),
)

/** The rail's visual clusters — a spacer renders between consecutive groups. */
enum class NavDestinationGroup { Browsing, Tools, System }

/**
 * The per-mode top-level surfaces a destination can render as a tab on:
 * the video-mode bar/drawer and the music-mode bar/drawer. Membership +
 * order live on the [NavDestination] rows ([NavDestination.topLevelGroups]);
 * the published tables below are derived from them.
 */
enum class TopLevelGroup { Video, Music }

/**
 * Every top-level nav destination, in customization-UI display order (the
 * same order the persisted-vocabulary table below locks in — that table is
 * derived from this registry, so the two cannot drift).
 */
val NAV_DESTINATIONS: List<NavDestination> = listOf(
    NavDestination(
        Route.Home, "Home", Tabler.Outline.Home, "Home", NavDestinationGroup.Browsing,
        topLevelGroups = mapOf(TopLevelGroup.Video to 0, TopLevelGroup.Music to 0),
    ),
    NavDestination(
        Route.Library, "Library", Tabler.Outline.Library, "Library", NavDestinationGroup.Browsing,
        topLevelGroups = mapOf(TopLevelGroup.Video to 1),
    ),
    NavDestination(
        Route.Search, "Search", Tabler.Outline.Search, "Search", NavDestinationGroup.Browsing,
        topLevelGroups = mapOf(TopLevelGroup.Video to 2, TopLevelGroup.Music to 2),
    ),
    NavDestination(
        Route.LiveTv, "LiveTv", Tabler.Outline.DeviceTv, "Live TV", NavDestinationGroup.Browsing,
        topLevelGroups = mapOf(TopLevelGroup.Video to 3),
    ),
    NavDestination(
        Route.MusicBrowse, "MusicBrowse", Tabler.Outline.Disc, "Music", NavDestinationGroup.Browsing,
        topLevelGroups = mapOf(TopLevelGroup.Music to 1),
    ),
    NavDestination(Route.Shortcuts, "Shortcuts", Tabler.Outline.Bolt, "Shortcuts", NavDestinationGroup.Tools),
    NavDestination(Route.Downloads, "Downloads", Tabler.Outline.Download, "Downloads", NavDestinationGroup.Browsing),
    NavDestination(Route.Newsletter, "Newsletter", Tabler.Outline.Mail, "Newsletter", NavDestinationGroup.Browsing),
    NavDestination(Route.WatchProgressHeatmap, "WatchProgressHeatmap", Tabler.Outline.Flame, "Insights", NavDestinationGroup.Browsing),
    NavDestination(Route.SyncPlay, "SyncPlay", Tabler.Outline.Users, "SyncPlay", NavDestinationGroup.Browsing),
    NavDestination(Route.Requests, "Requests", Tabler.Outline.Movie, "Requests", NavDestinationGroup.Tools),
    NavDestination(Route.UpcomingCalendar, "UpcomingCalendar", Tabler.Outline.Calendar, "Calendar", NavDestinationGroup.Tools),
    NavDestination(Route.ArrQueue, "ArrQueue", Tabler.Outline.Stack, "Arr Queue", NavDestinationGroup.Tools),
    NavDestination(Route.Settings, "Settings", Tabler.Outline.Settings, "Settings", NavDestinationGroup.System),
    NavDestination(Route.AdminDashboard, "AdminDashboard", Tabler.Outline.Shield, "Admin", NavDestinationGroup.System),
)

/** Route → registry row; shells resolve their display order through this lookup. */
val NAV_DESTINATION_BY_ROUTE: Map<Route, NavDestination> =
    NAV_DESTINATIONS.associateBy { it.route }

/** The destination's registry icon; non-registered (detail) routes fall back to Home. */
val Route.navIcon: ImageVector
    get() = NAV_DESTINATION_BY_ROUTE[this]?.icon ?: Tabler.Outline.Home

/**
 * Recorded per-surface label divergences from the [NAV_DESTINATIONS] registry:
 * the ONLY place a top-level route may render a different label than its
 * registry [NavDestination.railLabel].
 *
 * This is a recorded product divergence, not a parallel label vocabulary —
 * `MusicBrowse` reads "Browse" wherever labels resolve through
 * [topLevelLabel] (the music bottom bar / TV drawer) while the desktop rail
 * reads the registry row directly and keeps showing "Music". Unifying the two
 * wordings is a flagged product decision; until it is made, the drift lives
 * HERE, explicitly named and contract-tested (NavDestinationRegistryTest),
 * instead of hiding in a hand-copied route→label map.
 */
val TOP_LEVEL_LABEL_OVERRIDES: Map<Route, String> = linkedMapOf(
    Route.MusicBrowse to "Browse",
)

/**
 * Resolves the label a top-level surface renders for [route]: the
 * [NAV_DESTINATIONS] registry row's [NavDestination.railLabel], replaced by
 * any recorded [TOP_LEVEL_LABEL_OVERRIDES] entry. A route without a registry
 * row cannot be labeled at all — this fails loudly rather than inventing a
 * fallback, so an unregistered route breaks at init (and in
 * NavDestinationRegistryTest), never as a silently blank tab.
 */
fun topLevelLabel(route: Route): String {
    val registryLabel = NAV_DESTINATION_BY_ROUTE[route]?.railLabel
        ?: error(
            "No NAV_DESTINATIONS row for ${route::class.simpleName}: a top-level route must " +
                "register its facts exactly once (NavKey.kt NAV_DESTINATIONS).",
        )
    return TOP_LEVEL_LABEL_OVERRIDES[route] ?: registryLabel
}

/**
 * Turns one surface's registry membership ([NavDestination.topLevelGroups])
 * into the route→label map the browse shell renders for it: rows carrying
 * the [group] flag, ordered by their recorded per-surface position, labels
 * resolved through [topLevelLabel]. Labels are never hand-written — they
 * resolve through [topLevelLabel] (registry row first, then the recorded
 * overrides), so the only label literals in these tables are the ones in
 * [TOP_LEVEL_LABEL_OVERRIDES].
 */
private fun deriveTopLevelRoutes(group: TopLevelGroup): LinkedHashMap<Route, String> {
    val orderedRoutes = NAV_DESTINATIONS
        .mapNotNull { row -> row.topLevelGroups[group]?.let { position -> row.route to position } }
        .sortedBy { (_, position) -> position }
        .map { (route, _) -> route }
    val routes = LinkedHashMap<Route, String>()
    for (route in orderedRoutes) {
        routes[route] = topLevelLabel(route)
    }
    return routes
}

/**
 * Video-mode top-level destinations, in bar/drawer order. Derived from the
 * [NAV_DESTINATIONS] rows' [TopLevelGroup.Video] membership + position — a
 * row is added to (or ordered on) this table by editing its
 * [NavDestination.topLevelGroups] flag, never by editing a second list.
 */
val VIDEO_TOP_LEVEL_ROUTES: Map<Route, String> = deriveTopLevelRoutes(TopLevelGroup.Video)

/**
 * Music-mode top-level destinations, in bar/drawer order. Same derivation as
 * [VIDEO_TOP_LEVEL_ROUTES] over [TopLevelGroup.Music]; the music tab's
 * "Browse" wording is the one recorded [TOP_LEVEL_LABEL_OVERRIDES] entry.
 */
val MUSIC_TOP_LEVEL_ROUTES: Map<Route, String> = deriveTopLevelRoutes(TopLevelGroup.Music)

val TOP_LEVEL_ROUTES = VIDEO_TOP_LEVEL_ROUTES

// The two maps above keep explicit Map<Route, String> annotations: the
// previous hand-written form put Pair varargs inline, and without the
// annotations type inference unified the vararg keys up to `out Any`
// and every downstream Set<Route> use broke (JVM/android inference was
// unaffected, class E). The derivation helper removes the Pair shape
// entirely, but the annotations stay as a guard against reintroducing one.
val ALL_TOP_LEVEL_ROUTE_KEYS: Set<Route> =
    VIDEO_TOP_LEVEL_ROUTES.keys.union(MUSIC_TOP_LEVEL_ROUTES.keys)
