package com.raulshma.jellyplay.feature.shortcuts

import com.raulshma.jellyplay.core.model.ShortcutTargetKind
import com.raulshma.jellyplay.core.ui.navigation.NAV_DESTINATION_BY_ROUTE
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.ShortcutAction
import com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalogTarget
import com.raulshma.jellyplay.core.ui.navigation.navKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins the catalog contract the resolve policy's shipped defaults depend on:
 * every [ShortcutAction] registered, and every default overflow screen
 * (SyncPlay, Downloads, Shortcuts, Settings) present with admin-free gating.
 * A registration gap here silently loses the row from the unconfigured
 * default menu — this is the guard.
 */
class DefaultShortcutCatalogContractTest {

    private val catalog = DefaultShortcutCatalog()

    @Test
    fun `every target id is unique across kinds`() {
        val keys = catalog.entries.map { it.targetId }
        assertEquals(keys.size, keys.distinct().size, "duplicate target ids in the catalog")
    }

    @Test
    fun `every shipped action is registered`() {
        ShortcutAction.entries.forEach { action ->
            val entry = catalog.entryFor(ShortcutTargetKind.ACTION, action.id)
            assertNotNull(entry, "action ${action.id} missing from the catalog — the default menu loses it")
        }
    }

    @Test
    fun `default overflow screens are registered and not admin-gated`() {
        listOf(Route.SyncPlay, Route.Downloads, Route.Shortcuts, Route.Settings).forEach { route ->
            val entry = catalog.entries.firstOrNull {
                it.target is ShortcutCatalogTarget.Screen && it.targetId == route.navKey
            }
            assertNotNull(entry, "screen ${route.navKey} missing from the catalog — the default menu loses it")
            assertTrue(!entry.requiresAdmin, "default menu entry ${route.navKey} must not be admin-gated")
        }
    }

    @Test
    fun `screen keys are stable explicit literals`() {
        // No parameterized route may leak a class-name fallback as its key.
        catalog.entries.forEach { entry ->
            val screen = entry.target as? ShortcutCatalogTarget.Screen ?: return@forEach
            assertTrue(screen.key.isNotBlank() && !screen.key.contains(':'), "malformed screen key '${screen.key}'")
        }
    }

    @Test
    fun `registered routes' catalog rows agree with their registry rows`() {
        // The destination facts (icon, offline gating) are the registry row's —
        // the catalog derives them (registeredScreenItem) instead of copying, so
        // a hand-written icon or a diverging hiddenOffline flag on a registered
        // route is the drift this contract catches.
        catalog.items.forEach { item ->
            val screen = item.entry.target as? ShortcutCatalogTarget.Screen ?: return@forEach
            val destination = NAV_DESTINATION_BY_ROUTE[screen.route] ?: return@forEach
            assertEquals(
                destination.icon,
                item.entry.defaultIcon,
                "${screen.route::class.simpleName}'s catalog icon must be its registry row's icon",
            )
            assertEquals(
                destination.hiddenOffline,
                item.entry.hiddenOffline,
                "${screen.route::class.simpleName}'s offline gating must be its registry row's fact",
            )
        }
    }

    @Test
    fun `every catalog item carries a label and default icon`() {
        catalog.items.forEach { item ->
            assertTrue(item.entry.targetId.isNotBlank())
            assertTrue(item.entry.defaultIcon.name.isNotBlank())
        }
    }
}
