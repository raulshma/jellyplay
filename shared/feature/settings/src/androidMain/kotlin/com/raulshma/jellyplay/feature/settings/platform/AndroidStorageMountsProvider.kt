package com.raulshma.jellyplay.feature.settings.platform

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.raulshma.jellyplay.feature.settings.StorageMount
import com.raulshma.jellyplay.feature.settings.StorageMountKind
import com.raulshma.jellyplay.feature.settings.StorageMountsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android actual of the [StorageMountsProvider] seam. The legacy
 * `DownloadStorageLayout.availableMounts()` body is pure framework
 * (filesDir + `Context.getExternalFilesDirs` + `Environment.isExternalStorageRemovable`
 * + `StatFs`), so it is inlined here verbatim against this module's
 * module-local [StorageMount]/[StorageMountKind] — no legacy core:data types
 * (they are not visible from this module), no app-side Koin override needed.
 *
 * Enumerates the storage mounts the user can pick for downloads, in a stable
 * order: INTERNAL first, then each app-private external root (primary emulated
 * storage, then any inserted SD / USB mount). When an SD card or USB stick is
 * inserted, it appears here as an extra [StorageMountKind.REMOVABLE] option.
 * Stays within app-private scoped storage (no SAF rework).
 */
internal class AndroidStorageMountsProvider(
    private val context: Context,
) : StorageMountsProvider {

    override suspend fun availableMounts(): List<StorageMount> = withContext(Dispatchers.IO) {
        val options = mutableListOf<StorageMount>()
        options.add(
            StorageMount(
                prefValue = "INTERNAL",
                kind = StorageMountKind.INTERNAL,
                availableBytes = freeBytes(context.filesDir),
                rootPath = context.filesDir.absolutePath,
            )
        )
        val externalRoots = context.getExternalFilesDirs(null)
            ?.filterNotNull()
            ?.filter { it.absolutePath.isNotBlank() }
            ?: return@withContext options
        externalRoots.forEachIndexed { index, root ->
            val prefValue = if (index == 0) "EXTERNAL" else "EXTERNAL_${index + 1}"
            val removable = try { Environment.isExternalStorageRemovable(root) } catch (_: Throwable) { false }
            val kind = if (index == 0) {
                // Primary external is emulated (built-in flash); only secondary
                // entries are real removable mounts.
                StorageMountKind.PRIMARY_EXTERNAL
            } else if (removable) {
                StorageMountKind.REMOVABLE
            } else {
                StorageMountKind.EXTERNAL
            }
            options.add(
                StorageMount(
                    prefValue = prefValue,
                    kind = kind,
                    availableBytes = freeBytes(root),
                    rootPath = root.absolutePath,
                )
            )
        }
        options
    }

    private fun freeBytes(dir: java.io.File): Long = try {
        val statFs = StatFs(dir.absolutePath)
        statFs.availableBlocksLong * statFs.blockSizeLong
    } catch (_: Throwable) { 0L }
}
