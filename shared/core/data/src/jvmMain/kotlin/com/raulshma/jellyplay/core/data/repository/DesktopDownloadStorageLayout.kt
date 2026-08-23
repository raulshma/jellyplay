package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaType
import java.io.File
import java.nio.file.Path

/**
 * Desktop actual of the [DownloadStorageLayoutContract] (V3 downloads
 * conveyor): resolves downloads under the app's appdata dir
 * (`<dataDir>/downloads`, `<dataDir>/downloads/music` for audio — the same
 * subtree names as the Android filesDir layout) via `java.nio.Path`, matching
 * the desktopDataModule's java.nio dataDir parameter.
 *
 * Differences from the Android actual, by design:
 *  - desktop has a single volume, so [storageLocationPref] is ignored (the
 *    settings screen's storage-location picker is an Android-only surface);
 *  - free space comes from [File.getUsableSpace] instead of `StatFs` against
 *    the same [DownloadStorageLayoutContract.MIN_FREE_BYTES] floor;
 *  - the filename rules (sanitize + container extension) come from the shared
 *    contract companion, so the same inputs produce IDENTICAL filenames on
 *    both platforms.
 */
class DesktopDownloadStorageLayout(
    private val dataDir: Path,
) : DownloadStorageLayoutContract {

    override fun resolve(
        mediaType: String,
        storageLocationPref: String,
        name: String,
        idHint: String,
        container: String?,
    ): ResolvedDownloadPath {
        val isAudioType = mediaType == MediaType.AUDIO.name || mediaType == MediaType.MUSIC.name
        val baseDir = File(dataDir.toFile(), if (isAudioType) "downloads/music" else "downloads")
        if (!baseDir.exists()) baseDir.mkdirs()

        check(DownloadStorageLayoutContract.hasMinimumFreeSpace(baseDir.usableSpace)) {
            "Insufficient storage space. Less than 100 MB available on device."
        }

        val safeName = DownloadStorageLayoutContract.sanitizeName(name)
        val extension = DownloadStorageLayoutContract.deriveExtension(container, isAudioType)
        val file = File(baseDir, "${safeName}_${idHint}.$extension")
        return ResolvedDownloadPath(
            baseDir = baseDir,
            fileName = file.name,
            filePath = file.absolutePath,
        )
    }
}
