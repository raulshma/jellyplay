package com.raulshma.jellyplay.navigation

import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.applyNavCustomization
import com.raulshma.jellyplay.core.ui.navigation.MUSIC_TOP_LEVEL_ROUTES
import com.raulshma.jellyplay.core.ui.navigation.navKey
import com.raulshma.jellyplay.core.ui.navigation.VIDEO_TOP_LEVEL_ROUTES

/**
 * Pure fold producing the active top-level route→label map for the browse
 * shells (extracted verbatim from JellyPlayApp's `activeTopLevelRoutes`
 * remember block so the composition rules are JVM-testable):
 *
 *  1. the home mode picks the base route set — [HomeMode.VIDEO] vs
 *     [HomeMode.MUSIC];
 *  2. while offline, the server-bound destination with no offline fallback is
 *     hidden (Live TV — live streams always degrade to a dead-end ErrorScreen;
 *     Library is NOT hidden here, its grid auto-switches to the offline store,
 *     and Home/Search/Shortcuts/MusicBrowse all keep offline paths);
 *  3. the user's nav customization filters (hidden items + the offline
 *     hide-set) and reorders (stored nav-item order) the remainder.
 *
 * Keys are [Route.navKey] literals (never `simpleName`, which R8 obfuscates).
 */
internal fun visibleTopLevelRoutes(
    homeMode: HomeMode,
    hiddenNavItems: Set<String>,
    navItemOrder: List<String>,
    isOffline: Boolean,
): LinkedHashMap<Route, String> =
    when (homeMode) {
        HomeMode.VIDEO -> VIDEO_TOP_LEVEL_ROUTES
        HomeMode.MUSIC -> MUSIC_TOP_LEVEL_ROUTES
    }.let { routes ->
        val offlineHidden = if (isOffline) setOf(Route.LiveTv.navKey) else emptySet()
        applyNavCustomization(routes, hiddenNavItems + offlineHidden, navItemOrder)
    }
