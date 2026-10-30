package com.raulshma.jellyplay.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.datastore.shortcuts.ShortcutsStore
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalog
import com.raulshma.jellyplay.core.ui.navigation.ShortcutSurfaceResolver
import com.raulshma.jellyplay.core.ui.navigation.ResolvedShortcutSurfaces

/**
 * The shell's resolved-shortcuts holder (the [BackExitHost]/
 * [BottomNavScrollState] pattern): the ONE site where this composition
 * resolves the [ShortcutsStore] slice + [ShortcutCatalog] out of Koin, and
 * the ONE construction site for the shared [ShortcutSurfaceResolver] —
 * the module that owns "resolved shortcuts for a surface" so neither the
 * phone layout branch nor any future surface re-assembles the fold's inputs.
 * Collects the store's slice and re-resolves on the gating snapshot
 * (slice, admin, offline, nav-bar composition), keyed exactly like the
 * snapshot the resolver consumes.
 *
 * The gating facts stay caller-supplied: they are composition state
 * (MainShellModel's admin flow, the app-wide offline flag, the
 * [visibleTopLevelRoutes] map) this holder cannot see; only the store and
 * the catalog resolve here.
 */
@Composable
internal fun rememberShortcutSurfaces(
    isAdmin: Boolean,
    isOffline: Boolean,
    activeTopLevelRoutes: Set<Route>,
): ResolvedShortcutSurfaces {
    val shortcutsStore: ShortcutsStore = org.koin.compose.koinInject()
    val shortcutCatalog: ShortcutCatalog = org.koin.compose.koinInject()
    val shortcutsSlice by shortcutsStore.shortcuts.collectAsStateWithLifecycle()
    return remember(
        shortcutsSlice, shortcutCatalog, isAdmin, isOffline, activeTopLevelRoutes,
    ) {
        ShortcutSurfaceResolver(
            catalog = shortcutCatalog,
            isAdmin = isAdmin,
            isOffline = isOffline,
            activeTopLevelRoutes = activeTopLevelRoutes,
        ).resolve(shortcutsSlice.definitions, shortcutsSlice.headerOrder, shortcutsSlice.overflowOrder)
    }
}
