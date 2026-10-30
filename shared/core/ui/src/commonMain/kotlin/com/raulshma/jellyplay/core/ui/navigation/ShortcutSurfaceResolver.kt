package com.raulshma.jellyplay.core.ui.navigation

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.ShortcutDefinition
import com.raulshma.jellyplay.core.model.ShortcutLimits
import com.raulshma.jellyplay.core.model.ShortcutSurface

/**
 * One owner for "the resolved shortcuts for a surface" — the shared assembly
 * over the pure [resolveShortcuts] fold. The fold's input assembly used to be
 * hand-written at every consuming host (the shell's overflow menu, home's
 * header dock): each caller re-bound the catalog, re-picked its surface and
 * clamp, and re-derived the Settings-reachability fact from whatever carrier
 * it happened to hold. This class binds those ONCE: construct it with the
 * capability/gating facts plus the catalog, hand it the stored slice's three
 * rows (definitions + the two order lists — plain data here so this module
 * keeps no dependency on the store that persists them), and read the
 * per-surface lists off the result. A future surface joins the result object,
 * and every existing host gets its list for free.
 *
 * The [activeTopLevelRoutes] set is the nav-bar composition the caller
 * already computes ([visibleTopLevelRoutes]); Settings' reachability — the
 * overflow guard rail's trigger — derives from it here
 * (`Route.Settings in activeTopLevelRoutes`), the derivation's only home.
 * The default (every registered destination reachable) models a caller with
 * no nav-bar constraint — the header surface never consults the fact.
 *
 * Pure over plain data, no DI inside: callers keep their own injection
 * patterns (the app shell's composition resolution, the home ViewModel's
 * constructor-injected catalog) and construct this per gating snapshot.
 */
class ShortcutSurfaceResolver(
    private val catalog: ShortcutCatalog,
    private val isAdmin: Boolean,
    private val isOffline: Boolean,
    private val activeTopLevelRoutes: Set<Route> = NAV_DESTINATIONS.map { it.route }.toSet(),
) {

    /** Whether the nav bar can reach Settings — the overflow guard rail's trigger. */
    private val settingsReachable: Boolean
        get() = Route.Settings in activeTopLevelRoutes

    /**
     * Resolves every surface for [definitions] ordered by its stored list —
     * defaults, gating and clamps applied by the shared fold, the Settings
     * guard rail appended on the overflow surface when unreachable and absent.
     */
    fun resolve(
        definitions: List<ShortcutDefinition>,
        headerOrder: List<String>,
        overflowOrder: List<String>,
    ): ResolvedShortcutSurfaces = ResolvedShortcutSurfaces(
        header = resolveShortcuts(
            definitions = definitions,
            headerOrder = headerOrder,
            overflowOrder = overflowOrder,
            catalog = catalog.entries,
            surface = ShortcutSurface.HEADER,
            isAdmin = isAdmin,
            isOffline = isOffline,
            maxSlots = ShortcutLimits.HEADER_MAX,
        ),
        overflow = resolveShortcuts(
            definitions = definitions,
            headerOrder = headerOrder,
            overflowOrder = overflowOrder,
            catalog = catalog.entries,
            surface = ShortcutSurface.OVERFLOW,
            isAdmin = isAdmin,
            isOffline = isOffline,
            settingsReachable = settingsReachable,
            maxSlots = ShortcutLimits.OVERFLOW_MAX,
        ),
    )
}

/**
 * The per-surface resolved lists ([ShortcutSurfaceResolver.resolve]'s result)
 * — every surface's shortcuts to render, in order, gating and clamps applied.
 * Indexed by [get] so a host picks its surface without forking on it.
 */
@Immutable
data class ResolvedShortcutSurfaces(
    val header: List<ResolvedShortcut>,
    val overflow: List<ResolvedShortcut>,
) {
    operator fun get(surface: ShortcutSurface): List<ResolvedShortcut> = when (surface) {
        ShortcutSurface.HEADER -> header
        ShortcutSurface.OVERFLOW -> overflow
    }
}
