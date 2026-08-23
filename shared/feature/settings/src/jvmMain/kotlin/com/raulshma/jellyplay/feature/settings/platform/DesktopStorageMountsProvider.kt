package com.raulshma.jellyplay.feature.settings.platform

import com.raulshma.jellyplay.feature.settings.StorageMount
import com.raulshma.jellyplay.feature.settings.StorageMountKind
import com.raulshma.jellyplay.feature.settings.StorageMountsProvider
import java.io.File

/**
 * Desktop actual of the [StorageMountsProvider] seam: desktop has a single
 * volume (the shared DownloadStorageLayoutContract docs say as much), so the
 * only pickable destination is INTERNAL, reported against the user's home
 * directory with its usable space. The `downloadStorageLocation` preference is
 * ignored, matching the desktop download-storage actual.
 */
internal class DesktopStorageMountsProvider : StorageMountsProvider {

    override suspend fun availableMounts(): List<StorageMount> {
        val home = File(System.getProperty("user.home") ?: ".")
        return listOf(
            StorageMount(
                prefValue = "INTERNAL",
                kind = StorageMountKind.INTERNAL,
                availableBytes = home.usableSpace,
                rootPath = home.absolutePath,
            )
        )
    }
}
