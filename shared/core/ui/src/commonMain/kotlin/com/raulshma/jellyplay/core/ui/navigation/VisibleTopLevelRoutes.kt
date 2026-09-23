package com.raulshma.jellyplay.core.ui.navigation

/**
 * The top-level-route composition policy every browse shell renders through —
 * the former Android-shell-only `visibleTopLevelRoutes` fold, now shared:
 *
 *  1. the caller picks the BASE SET — the shell's own display-order policy
 *     (Android: the homeMode-selected [VIDEO_TOP_LEVEL_ROUTES] /
 *     [MUSIC_TOP_LEVEL_ROUTES] bar set; desktop: the full
 *     `DESKTOP_RAIL_ROUTES` rail). Membership+order tables stay per-shell;
 *  2. while offline, the server-bound destination with no offline fallback is
 *     hidden ([Route.LiveTv] — live streams always degrade to a dead-end
 *     ErrorScreen; Library is NOT hidden here, its grid auto-switches to the
 *     offline store, and Home/Search/Shortcuts/MusicBrowse all keep offline
 *     paths);
 *  3. the user's nav customization filters (hidden items, which may include
 *     the offline hide-set) and reorders (stored nav-item order) the
 *     remainder.
 *
 * Keys are [Route.navKey] literals (never `simpleName`, which R8 obfuscates).
 * Two shapes, mirroring [applyNavCustomization]'s dual form: the map form for
 * shells whose items are route→label (Android's bottom bar/drawer), the list
 * form for shells whose items carry more than a label (the desktop rail's
 * [NavDestination] descriptors).
 */
fun visibleTopLevelRoutes(
    baseRoutes: Map<Route, String>,
    hiddenNavItems: Set<String>,
    navItemOrder: List<String>,
    isOffline: Boolean,
): LinkedHashMap<Route, String> {
    val offlineHidden = if (isOffline) setOf(Route.LiveTv.navKey) else emptySet()
    return applyNavCustomization(baseRoutes, hiddenNavItems + offlineHidden, navItemOrder)
}

/** List form of the map-based fold — see the map form's KDoc. */
fun <T> visibleTopLevelRoutes(
    baseRoutes: List<T>,
    keyOf: (T) -> String,
    hiddenNavItems: Set<String>,
    navItemOrder: List<String>,
    isOffline: Boolean,
): List<T> {
    val offlineHidden = if (isOffline) setOf(Route.LiveTv.navKey) else emptySet()
    return applyNavCustomization(baseRoutes, keyOf, hiddenNavItems + offlineHidden, navItemOrder)
}
