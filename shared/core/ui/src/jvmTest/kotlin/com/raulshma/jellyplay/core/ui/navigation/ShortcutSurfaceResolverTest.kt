package com.raulshma.jellyplay.core.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.raulshma.jellyplay.core.model.ShortcutDefinition
import com.raulshma.jellyplay.core.model.ShortcutSurface
import com.raulshma.jellyplay.core.model.ShortcutTargetKind
import io.mockk.mockk
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [ShortcutSurfaceResolver] — the shared assembly over
 * [resolveShortcuts] (the one owner of "resolved shortcuts for a surface"):
 * per-surface lists from one call, the Settings-reachability derivation from
 * the nav-bar composition, and the gating facts threading to every surface.
 */
class ShortcutSurfaceResolverTest {

    private val labelRes = mockk<StringResource>()
    private val icon = mockk<ImageVector>()

    private fun screenEntry(key: String, requiresAdmin: Boolean = false, hiddenOffline: Boolean = false) =
        ShortcutCatalogEntry(
            target = ShortcutCatalogTarget.Screen(routeForKey(key), key),
            labelRes = labelRes,
            defaultIcon = icon,
            requiresAdmin = requiresAdmin,
            hiddenOffline = hiddenOffline,
        )

    private fun actionEntry(action: ShortcutAction) =
        ShortcutCatalogEntry(
            target = ShortcutCatalogTarget.Action(action),
            labelRes = labelRes,
            defaultIcon = icon,
        )

    /** The full default catalog (every shipped default target registered) + extras. */
    private val catalog = object : ShortcutCatalog {
        override val entries = listOf(
            actionEntry(ShortcutAction.SURPRISE_ME),
            actionEntry(ShortcutAction.PLAY_ON),
            actionEntry(ShortcutAction.TOGGLE_OFFLINE),
            screenEntry("SyncPlay"),
            screenEntry("Downloads"),
            screenEntry("Shortcuts"),
            screenEntry("Settings"),
            screenEntry("AdminDashboard", requiresAdmin = true),
            screenEntry("LiveTv", hiddenOffline = true),
            screenEntry("Favorites"),
        )
    }

    private fun def(id: String, targetId: String) =
        ShortcutDefinition(id, ShortcutTargetKind.SCREEN, targetId)

    private fun resolver(
        isAdmin: Boolean = false,
        isOffline: Boolean = false,
        activeTopLevelRoutes: Set<Route>? = null,
    ) = ShortcutSurfaceResolver(
        catalog = catalog,
        isAdmin = isAdmin,
        isOffline = isOffline,
        activeTopLevelRoutes = activeTopLevelRoutes ?: NAV_DESTINATIONS.map { it.route }.toSet(),
    )

    @Test
    fun `one call resolves every surface — defaults on the overflow, empty header`() {
        val resolved = resolver().resolve(emptyList(), emptyList(), emptyList())
        assertEquals(
            listOf(
                "default:surprise_me",
                "default:SyncPlay",
                "default:Downloads",
                "default:toggle_offline",
                "default:play_on",
                "default:Shortcuts",
                "default:Settings",
            ),
            resolved.overflow.map { it.id },
        )
        assertTrue(resolved.header.isEmpty())
        // The surface index reads the same lists the named properties carry.
        assertEquals(resolved.header, resolved[ShortcutSurface.HEADER])
        assertEquals(resolved.overflow, resolved[ShortcutSurface.OVERFLOW])
    }

    @Test
    fun `stored slice rows order each surface independently`() {
        val resolved = resolver().resolve(
            definitions = listOf(def("a", "Favorites"), def("b", "SyncPlay")),
            headerOrder = listOf("a"),
            overflowOrder = listOf("b"),
        )
        assertEquals(listOf("Favorites"), resolved.header.map { targetKey(it) })
        assertEquals(listOf("SyncPlay"), resolved.overflow.map { targetKey(it) })
    }

    @Test
    fun `settings reachability derives from the nav-bar composition`() {
        val definitions = listOf(def("a", "Favorites"))
        // Settings reachable among the active routes → the guard rail stays quiet.
        val reachable = resolver(activeTopLevelRoutes = setOf(Route.Home, Route.Settings))
            .resolve(definitions, emptyList(), listOf("a"))
        assertTrue(reachable.overflow.none { it.targetsSettings && it.id == GUARD_RAIL_SETTINGS_ID })
        // Settings hidden from the nav bar and absent from the menu → appended.
        val unreachable = resolver(activeTopLevelRoutes = setOf(Route.Home))
            .resolve(definitions, emptyList(), listOf("a"))
        assertEquals(GUARD_RAIL_SETTINGS_ID, unreachable.overflow.last().id)
    }

    @Test
    fun `the default routes set models everything reachable — no guard rail`() {
        val resolved = resolver().resolve(listOf(def("a", "Favorites")), emptyList(), listOf("a"))
        assertTrue(resolved.overflow.none { it.id == GUARD_RAIL_SETTINGS_ID })
    }

    @Test
    fun `admin and offline gating thread to every surface`() {
        val definitions = listOf(def("admin", "AdminDashboard"), def("tv", "LiveTv"))
        val gated = resolver(isOffline = true).resolve(definitions, listOf("admin"), listOf("tv"))
        // Non-admin + offline: both entries drop from both surfaces.
        assertTrue(gated.header.isEmpty())
        assertTrue(gated.overflow.isEmpty())
        val allowed = resolver(isAdmin = true, isOffline = false)
            .resolve(definitions, listOf("admin"), listOf("tv"))
        assertEquals(listOf("AdminDashboard"), allowed.header.map { targetKey(it) })
        assertEquals(listOf("LiveTv"), allowed.overflow.map { targetKey(it) })
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Registers only the keys the tests reference — a minimal route registry stand-in. */
    private fun routeForKey(key: String): Route = when (key) {
        "SyncPlay" -> Route.SyncPlay
        "Downloads" -> Route.Downloads
        "Shortcuts" -> Route.Shortcuts
        "Settings" -> Route.Settings
        "AdminDashboard" -> Route.AdminDashboard
        "LiveTv" -> Route.LiveTv
        else -> Route.Favorites
    }

    private fun targetKey(shortcut: ResolvedShortcut): String = when (val t = shortcut.target) {
        is ShortcutCatalogTarget.Screen -> t.key
        is ShortcutCatalogTarget.Action -> t.action.id
    }
}
