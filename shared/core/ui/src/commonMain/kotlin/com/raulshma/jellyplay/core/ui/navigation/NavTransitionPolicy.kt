package com.raulshma.jellyplay.core.ui.navigation

import com.raulshma.jellyplay.core.ui.animation.NavRouteClass

/**
 * Maps a [Route] to a coarse [NavRouteClass] for transition selection — the
 * shell's adapter from per-route classification metadata to the transition
 * policy's input. A pure projection over the member flags, so no route list
 * lives here. Ambient is checked first (before fullscreen) because
 * [Route.Ambient] is a distinct immersive overlay that should always
 * cross-fade.
 *
 * Split out of NavKey.kt, whose class KDoc documents the restore contract
 * governing every [Route] declared there; the package (and therefore every
 * persisted binary name and the R8 keep rule) is unchanged.
 */
val Route?.toNavRouteClass: NavRouteClass
    get() = when {
        this == null -> NavRouteClass.DEFAULT
        this is Route.Ambient -> NavRouteClass.AMBIENT
        isFullScreen -> NavRouteClass.FULLSCREEN
        isModal -> NavRouteClass.MODAL
        isDetail -> NavRouteClass.DETAIL
        ALL_TOP_LEVEL_ROUTE_KEYS.contains(this) -> NavRouteClass.TOP_LEVEL_TAB
        else -> NavRouteClass.DEFAULT
    }
