package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the invariants of [PluginPackage] — the plugin-catalog entry whose two
 * derived properties carry non-obvious semantics:
 *
 *  - [PluginPackage.latestVersion] is simply the FIRST entry of [versions]
 *    (the server orders newest-first; the model does not re-sort), and is
 *    `null` for a versionless package.
 *  - [PluginPackage.isInstalled] is constant `false`: the catalog endpoint
 *    never knows the local install state (install-state detection happens at
 *    the repository layer) — a call site reading this field today is reading a
 *    placeholder, and flipping it silently would be a behaviour change.
 *  - [PluginInfo] defaults to [PluginStatus.ACTIVE] status and uninstallable.
 */
class PluginModelsTest {

    @Test
    fun `latest version is the first entry, not the max`() {
        val first = PluginVersionInfo(version = "2.0.0")
        val second = PluginVersionInfo(version = "1.0.0")
        val pkg = PluginPackage(name = "p", guid = "g", versions = listOf(first, second))
        assertEquals(first, pkg.latestVersion)
        assertFalse(pkg.latestVersion == second)
    }

    @Test
    fun `versionless package has no latest version`() {
        val pkg = PluginPackage(name = "p", guid = "g", versions = emptyList())
        assertNull(pkg.latestVersion)
    }

    @Test
    fun `isInstalled is a constant false placeholder`() {
        val pkg = PluginPackage(name = "p", guid = "g", versions = listOf(PluginVersionInfo(version = "1.0.0")))
        assertFalse(pkg.isInstalled)
    }

    @Test
    fun `plugin info defaults to an active, uninstallable-if-server-says state`() {
        val info = PluginInfo()
        assertEquals(PluginStatus.ACTIVE, info.status)
        assertTrue(info.canUninstall)
        assertFalse(info.hasImage)
    }

    @Test
    fun `plugin status taxonomy is complete`() {
        assertEquals(
            setOf(
                "ACTIVE", "RESTART", "DELETED", "SUPERSEDED",
                "MALFUNCTIONED", "NOT_SUPPORTED", "DISABLED",
            ),
            PluginStatus.entries.map { it.name }.toSet(),
        )
    }
}
