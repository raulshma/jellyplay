package com.raulshma.jellyplay.core.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.raulshma.jellyplay.core.model.ShortcutDefinition
import com.raulshma.jellyplay.core.model.ShortcutLimits
import com.raulshma.jellyplay.core.model.ShortcutSurface
import com.raulshma.jellyplay.core.model.ShortcutTargetKind
import io.mockk.mockk
import org.jetbrains.compose.resources.StringResource
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the user-shortcuts resolution policy: shipped defaults for an
 * unconfigured user, order/dedupe/unknown-target drops, admin + offline
 * gating, surface clamps, and the Settings overflow guard rail.
 */
class ShortcutsCustomizationTest {

    private val labelRes = mockk<StringResource>()
    private val icon = mockk<ImageVector>()
    private val otherIcon = mockk<ImageVector>()

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
    private val catalog = listOf(
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

    private fun def(id: String, kind: ShortcutTargetKind, targetId: String, iconId: String = "", labelOverride: String? = null) =
        ShortcutDefinition(id, kind, targetId, iconId, labelOverride)

    // ------------------------------------------------------------------
    // Defaults
    // ------------------------------------------------------------------

    @Test
    fun `unconfigured user gets the shipped overflow layout`() {
        val resolved = resolveShortcuts(
            definitions = emptyList(),
            headerOrder = emptyList(),
            overflowOrder = emptyList(),
            catalog = catalog,
            surface = ShortcutSurface.OVERFLOW,
        )
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
            resolved.map { it.id },
        )
    }

    @Test
    fun `unconfigured header is empty`() {
        val resolved = resolveShortcuts(
            definitions = emptyList(),
            headerOrder = emptyList(),
            overflowOrder = emptyList(),
            catalog = catalog,
            surface = ShortcutSurface.HEADER,
        )
        assertTrue(resolved.isEmpty())
    }

    @Test
    fun `defaultShortcutsFor matches the unconfigured resolve output`() {
        ShortcutSurface.entries.forEach { surface ->
            val definitions = defaultShortcutsFor(surface)
            val resolved = resolveShortcuts(
                definitions = emptyList(),
                headerOrder = emptyList(),
                overflowOrder = emptyList(),
                catalog = catalog,
                surface = surface,
            )
            assertEquals(resolved.map { it.id }, definitions.map { it.id })
            assertEquals(resolved.map { it.targetIdKey() }, definitions.map { it.targetId })
        }
    }

    @Test
    fun `a partially-empty config is NOT treated as unconfigured`() {
        // Definitions exist but this surface's order is empty → nothing to
        // show (the user unpinned everything), not the shipped defaults.
        val resolved = resolveShortcuts(
            definitions = listOf(def("a", ShortcutTargetKind.SCREEN, "Favorites")),
            headerOrder = listOf("a"),
            overflowOrder = emptyList(),
            catalog = catalog,
            surface = ShortcutSurface.OVERFLOW,
        )
        assertTrue(resolved.isEmpty())
    }

    // ------------------------------------------------------------------
    // Order + drops
    // ------------------------------------------------------------------

    @Test
    fun `stored order drives display order`() {
        val resolved = resolveShortcuts(
            definitions = listOf(
                def("a", ShortcutTargetKind.SCREEN, "Favorites"),
                def("b", ShortcutTargetKind.SCREEN, "SyncPlay"),
            ),
            headerOrder = emptyList(),
            overflowOrder = listOf("b", "a"),
            catalog = catalog,
            surface = ShortcutSurface.OVERFLOW,
        )
        assertEquals(listOf("SyncPlay", "Favorites"), resolved.map { it.targetIdKey() })
    }

    @Test
    fun `order ids without a definition and unknown targets drop`() {
        val resolved = resolveShortcuts(
            definitions = listOf(def("a", ShortcutTargetKind.SCREEN, "Favorites")),
            headerOrder = emptyList(),
            overflowOrder = listOf("ghost", "a", "removed", "a"),
            catalog = catalog.filterNot { it.targetId == "Favorites" },
            surface = ShortcutSurface.OVERFLOW,
        )
        // "a" survives the order list but its target left the catalog → dropped.
        assertTrue(resolved.isEmpty())
    }

    @Test
    fun `duplicate order ids dedupe`() {
        val resolved = resolveShortcuts(
            definitions = listOf(def("a", ShortcutTargetKind.SCREEN, "Favorites")),
            headerOrder = emptyList(),
            overflowOrder = listOf("a", "a"),
            catalog = catalog,
            surface = ShortcutSurface.OVERFLOW,
        )
        assertEquals(1, resolved.size)
    }

    // ------------------------------------------------------------------
    // Gating
    // ------------------------------------------------------------------

    @Test
    fun `admin-gated entries drop for non-admins and stay for admins`() {
        val definitions = listOf(def("admin", ShortcutTargetKind.SCREEN, "AdminDashboard"))
        val nonAdmin = resolveShortcuts(definitions, emptyList(), listOf("admin"), catalog, ShortcutSurface.OVERFLOW)
        assertTrue(nonAdmin.isEmpty())
        val admin = resolveShortcuts(
            definitions, emptyList(), listOf("admin"), catalog,
            ShortcutSurface.OVERFLOW, isAdmin = true,
        )
        assertEquals(1, admin.size)
    }

    @Test
    fun `offline-hidden entries drop while offline`() {
        val definitions = listOf(def("tv", ShortcutTargetKind.SCREEN, "LiveTv"))
        val online = resolveShortcuts(definitions, emptyList(), listOf("tv"), catalog, ShortcutSurface.OVERFLOW)
        assertEquals(1, online.size)
        val offline = resolveShortcuts(
            definitions, emptyList(), listOf("tv"), catalog,
            ShortcutSurface.OVERFLOW, isOffline = true,
        )
        assertTrue(offline.isEmpty())
    }

    // ------------------------------------------------------------------
    // Icon + label resolution
    // ------------------------------------------------------------------

    @Test
    fun `definition icon wins over the catalog default and unknown icons fall back`() {
        val definitions = listOf(
            def("a", ShortcutTargetKind.SCREEN, "Favorites", iconId = "wand"),
            def("b", ShortcutTargetKind.SCREEN, "SyncPlay", iconId = "not-a-real-icon"),
        )
        val resolved = resolveShortcuts(definitions, emptyList(), listOf("a", "b"), catalog, ShortcutSurface.OVERFLOW)
        assertEquals("wand", iconIdOf(resolved[0]))
        assertEquals(icon, resolved[1].icon)
    }

    @Test
    fun `blank label overrides are dropped`() {
        val definitions = listOf(def("a", ShortcutTargetKind.SCREEN, "Favorites", labelOverride = "   "))
        val resolved = resolveShortcuts(definitions, emptyList(), listOf("a"), catalog, ShortcutSurface.OVERFLOW)
        assertNull(resolved.single().labelOverride)
    }

    // ------------------------------------------------------------------
    // Clamp + guard rail
    // ------------------------------------------------------------------

    @Test
    fun `surface clamps to the header capacity`() {
        val definitions = (1..10).map { def("h$it", ShortcutTargetKind.SCREEN, "Favorites") }
        val resolved = resolveShortcuts(
            definitions, (1..10).map { "h$it" }, emptyList(), catalog,
            ShortcutSurface.HEADER, maxSlots = ShortcutLimits.HEADER_MAX,
        )
        assertEquals(ShortcutLimits.HEADER_MAX, resolved.size)
    }

    @Test
    fun `guard rail appends Settings when unreachable and absent from the menu`() {
        val definitions = listOf(def("a", ShortcutTargetKind.SCREEN, "Favorites"))
        val resolved = resolveShortcuts(
            definitions, emptyList(), listOf("a"), catalog,
            ShortcutSurface.OVERFLOW, settingsReachable = false,
        )
        assertEquals(GUARD_RAIL_SETTINGS_ID, resolved.last().id)
        assertTrue(resolved.last().targetsSettings)
    }

    @Test
    fun `guard rail respects the clamp — a full menu yields its last slot`() {
        val definitions = (1..5).map { def("o$it", ShortcutTargetKind.SCREEN, "Favorites") }
        val order = definitions.map { it.id }
        val resolved = resolveShortcuts(
            definitions, emptyList(), order, catalog,
            ShortcutSurface.OVERFLOW, settingsReachable = false, maxSlots = 5,
        )
        // Still 5 (not 6): the last user entry made room for the rail.
        assertEquals(5, resolved.size)
        assertEquals(GUARD_RAIL_SETTINGS_ID, resolved.last().id)
        assertTrue(resolved.dropLast(1).none { it.targetsSettings })
    }

    @Test
    fun `guard rail stays quiet when Settings is reachable or already in the menu`() {
        val definitions = listOf(
            def("a", ShortcutTargetKind.SCREEN, "Favorites"),
            def("s", ShortcutTargetKind.SCREEN, "Settings"),
        )
        // In the menu already → no append.
        val inMenu = resolveShortcuts(
            definitions, emptyList(), listOf("s", "a"), catalog,
            ShortcutSurface.OVERFLOW, settingsReachable = false,
        )
        assertTrue(inMenu.none { it.id == GUARD_RAIL_SETTINGS_ID })
        // Reachable via the nav bar → no append.
        val reachable = resolveShortcuts(
            definitions, emptyList(), listOf("a"), catalog,
            ShortcutSurface.OVERFLOW, settingsReachable = true,
        )
        assertTrue(reachable.none { it.id == GUARD_RAIL_SETTINGS_ID })
        // Guard rail never fires on the header surface.
        val header = resolveShortcuts(
            definitions, listOf("a"), emptyList(), catalog,
            ShortcutSurface.HEADER, settingsReachable = false,
        )
        assertTrue(header.none { it.id == GUARD_RAIL_SETTINGS_ID })
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

    private fun ResolvedShortcut.targetIdKey(): String = when (val t = target) {
        is ShortcutCatalogTarget.Screen -> t.key
        is ShortcutCatalogTarget.Action -> t.action.id
    }

    /** The registry id of a resolved icon — the wand icon is the only named one the tests use. */
    private fun iconIdOf(shortcut: ResolvedShortcut): String {
        com.raulshma.jellyplay.core.ui.icons.ShortcutIconRegistry.ALL.forEach {
            if (it.icon == shortcut.icon) return it.id
        }
        error("icon not in the registry")
    }
}
