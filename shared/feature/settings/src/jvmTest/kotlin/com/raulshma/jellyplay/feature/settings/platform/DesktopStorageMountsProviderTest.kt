package com.raulshma.jellyplay.feature.settings.platform

import com.raulshma.jellyplay.feature.settings.StorageMountKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Pins the desktop actual of the [StorageMountsProvider] seam: desktop owns a
 * single download volume, reported as INTERNAL against the user's home
 * directory with its real usable space — the only pickable destination, and
 * the same list on every call (the `downloadStorageLocation` preference is
 * deliberately ignored, matching the desktop download-storage actual).
 */
class DesktopStorageMountsProviderTest {

    @Test
    fun `desktop reports a single INTERNAL mount rooted at the home directory`() = runTest {
        val provider = DesktopStorageMountsProvider()

        val mounts = provider.availableMounts()

        assertEquals(1, mounts.size, "desktop has exactly one pickable destination")
        val mount = mounts.single()
        assertEquals("INTERNAL", mount.prefValue)
        assertEquals(StorageMountKind.INTERNAL, mount.kind)
        val home = File(System.getProperty("user.home") ?: ".")
        assertEquals(home.absolutePath, mount.rootPath)
        assertEquals(home.usableSpace, mount.availableBytes)
    }

    @Test
    fun `the home root really exists so the reported path is displayable`() = runTest {
        // Sanity for the seam's display contract: rootPath is an absolute,
        // existing directory (a synthetic path would render a dead row).
        val mount = DesktopStorageMountsProvider().availableMounts().single()

        assertTrue(File(mount.rootPath).isDirectory, "rootPath must be an existing directory")
        assertTrue(mount.availableBytes >= 0)
    }
}
