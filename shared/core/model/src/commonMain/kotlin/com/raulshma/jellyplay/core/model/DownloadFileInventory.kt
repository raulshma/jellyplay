package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable

/**
 * One on-disk file belonging to a downloaded item, enumerated by walking the
 * shared downloads directory. Sidecar artifact sizes (subtitles, trickplay,
 * segments, images) are NOT persisted anywhere — the `downloads` table only
 * tracks the media file's bytes — so the byte count here is the file's actual
 * `File.length()` at enumeration time.
 *
 * @param category the artifact bucket; drives grouping in the download-details UI.
 * @param displayName the file's name on disk (e.g. `trickplay_0.jpg`,
 *   `<itemId>_poster.jpg`). Honest and stable so the row reconstructs with
 *   [path].
 * @param path absolute on-disk path.
 * @param sizeBytes `File.length()` when enumerated (0 if unknown/missing).
 */
@Immutable
data class DownloadFileEntry(
    val category: DownloadedFileCategory,
    val displayName: String,
    val path: String,
    val sizeBytes: Long,
)

/**
 * The artifact bucket a [DownloadFileEntry] belongs to. Ordered (MEDIA first,
 * then the sidecars) so the download-details UI renders a stable, sensible
 * grouping without re-sorting at the call site.
 */
@Immutable
enum class DownloadedFileCategory {
    MEDIA,
    SUBTITLE,
    TRICKPLAY,
    SEGMENT,
    IMAGE,
}

/**
 * The full on-disk file inventory for one downloaded item: every enumerated
 * sidecar alongside the media file. Built by `DownloadRepository.getDownloadFileInventory`
 * from the persisted `downloadPath` + the `DownloadArtifacts` naming convention.
 *
 * `entries` is empty (not the inventory itself being null) when the item has a
 * download row but no files could be resolved on disk; a null inventory at the
 * UI layer means "not loaded yet".
 *
 * @param totalSizeBytes sum of every entry's [DownloadFileEntry.sizeBytes] —
 *   the real disk usage of the item's bundle, which may differ from the
 *   `downloads.totalSizeBytes` media-file figure.
 */
@Immutable
data class DownloadFileInventory(
    val entries: List<DownloadFileEntry>,
    val totalSizeBytes: Long,
) {
    companion object {
        val EMPTY = DownloadFileInventory(entries = emptyList(), totalSizeBytes = 0L)
    }
}
