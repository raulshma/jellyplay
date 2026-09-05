package com.raulshma.jellyplay.feature.shell.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the [ShellSectionRegistry] ledger mechanics over the same wiring
 * [shellEntryProvider] installs: real entries carrying nav3's derived
 * contentKey, unknown keys falling back to the blank [NavEntry] stamped with
 * the [UnregisteredEntryContentKey] sentinel. The graphs here are hand-wired
 * miniatures (the real graph needs ~20 feature sections plus Navigator /
 * ShellHostHooks collaborators), but the fallback block is copied verbatim
 * from [shellEntryProvider] so the sentinel-identity contract is pinned at
 * the seam the registry actually consumes.
 */
class ShellSectionRegistryTest {

    /** Sentinel-fallback resolver registering [Route.Home] + [Route.Search]. */
    private fun homeAndSearchGraph(): (NavKey) -> NavEntry<NavKey> =
        entryProvider(
            fallback = { key ->
                NavEntry(key, UnregisteredEntryContentKey, emptyMap<String, Any>(), {})
            },
        ) {
            entry<Route.Home> { }
            entry<Route.Search> { }
        }

    /** Sentinel-fallback resolver registering only [Route.Search]. */
    private fun searchOnlyGraph(): (NavKey) -> NavEntry<NavKey> =
        entryProvider(
            fallback = { key ->
                NavEntry(key, UnregisteredEntryContentKey, emptyMap<String, Any>(), {})
            },
        ) {
            entry<Route.Search> { }
        }

    // ── Before attach ────────────────────────────────────────────────────

    @Test
    fun `isRegistered is false for every route before the first attach`() {
        // The one-composition window the KDoc describes: the guard degrades
        // every key to the unregistered (snackbar) answer, never a crash.
        val registry = ShellSectionRegistry()

        assertFalse(registry.isRegistered(Route.Home))
        assertFalse(registry.isRegistered(Route.Licenses))
    }

    // ── Registered vs unregistered resolution ────────────────────────────

    @Test
    fun `a registered route resolves to its real entry`() {
        val registry = ShellSectionRegistry()
        val graph = homeAndSearchGraph()
        registry.attach(graph)

        assertTrue(registry.isRegistered(Route.Home))
        val entry = assertNotNull(graph(Route.Home), "registered keys resolve to a real entry")
        assertNotSame(UnregisteredEntryContentKey, entry.contentKey)
    }

    @Test
    fun `an unregistered route falls back to the sentinel entry`() {
        val registry = ShellSectionRegistry()
        val graph = homeAndSearchGraph()
        registry.attach(graph)

        assertFalse(registry.isRegistered(Route.Licenses))
        // The KDoc's exact registered/unregistered test: reference identity
        // against the sentinel, so only the fallback entry ever carries it.
        val entry = assertNotNull(graph(Route.Licenses), "unknown keys still get an entry — blank render, never a throw")
        assertSame(UnregisteredEntryContentKey, entry.contentKey)
    }

    // ── Re-attach replaces, never accumulates ────────────────────────────

    @Test
    fun `re-attaching replaces the graph a dropped route stops being registered`() {
        val registry = ShellSectionRegistry()
        registry.attach(homeAndSearchGraph())
        assertTrue(registry.isRegistered(Route.Home))

        registry.attach(searchOnlyGraph())

        assertFalse(registry.isRegistered(Route.Home), "the ledger mirrors the new graph, not the union")
        assertTrue(registry.isRegistered(Route.Search))
    }

    @Test
    fun `re-attaching the same graph is idempotent`() {
        // Shells rebuild the provider every recomposition against the one
        // remembered registry — repeat attach must be a no-op.
        val registry = ShellSectionRegistry()
        registry.attach(homeAndSearchGraph())
        assertTrue(registry.isRegistered(Route.Home))

        registry.attach(homeAndSearchGraph())

        assertTrue(registry.isRegistered(Route.Home))
        assertFalse(registry.isRegistered(Route.Licenses))
    }
}
