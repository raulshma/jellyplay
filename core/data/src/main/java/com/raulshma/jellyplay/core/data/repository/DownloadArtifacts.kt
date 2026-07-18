package com.raulshma.jellyplay.core.data.repository

import java.io.File

/**
 * Convention-based storage for extra assets bundled alongside a downloaded
 * video file (sibling directories/files of the media file). Kept in one place
 * so every download/delete path stays consistent — adding a new artifact type
 * only requires extending [cleanup] and the relevant writer.
 *
 *  - `<dir>/trickplay/`                       trickplay sprite sheets + `meta.json`
 *  - `<dir>/subtitles/`                       offline external subtitles + `manifest.json`
 *  - `<dir>/segments.json`                    serialized intro/outro/recap media segments
 *  - `<dir>/<itemId>_poster.jpg`              offline poster (primary) image, unique per item
 *  - `<dir>/<itemId>_backdrop.jpg`            offline backdrop image, unique per item
 *
 * The poster/backdrop are downloaded to local files at download time so the
 * offline screens can render them without a network connection (previously the
 * remote URLs were only stored, leaving blurHash as the sole offline visual).
 *
 * The filenames are keyed by Jellyfin `itemId` (a stable UUID) because all
 * downloads share a single flat directory (`getExternalFilesDir(MOVIES)` or
 * `filesDir/downloads`) — fixed names like `poster.jpg` would collide and
 * silently overwrite each other across episodes/movies.
 */
internal object DownloadArtifacts {
    const val TRICKPLAY_DIR = "trickplay"
    const val SUBTITLES_DIR = "subtitles"
    const val SUBTITLE_MANIFEST_FILE = "manifest.json"
    const val SEGMENTS_FILE = "segments.json"

    /** Per-item poster filename, unique within the shared downloads dir. */
    fun posterFile(itemId: String): String = "${itemId}_poster.jpg"

    /** Per-item backdrop filename, unique within the shared downloads dir. */
    fun backdropFile(itemId: String): String = "${itemId}_backdrop.jpg"

    /**
     * Recursively removes all bundled extra artifacts under [parentDir].
     *
     * Pass [itemId] so only that item's poster/backdrop are deleted; omitting
     * it preserves back-compat with the (item-agnostic) trickplay/subtitles/
     * segments cleanup but skips image deletion to avoid clobbering artwork
     * belonging to other downloads in the same shared directory.
     */
    fun cleanup(parentDir: File?, itemId: String? = null) {
        if (parentDir == null) return
        File(parentDir, TRICKPLAY_DIR).takeIf { it.exists() }?.deleteRecursively()
        File(parentDir, SUBTITLES_DIR).takeIf { it.exists() }?.deleteRecursively()
        File(parentDir, SEGMENTS_FILE).takeIf { it.exists() }?.delete()
        if (itemId != null) {
            File(parentDir, posterFile(itemId)).takeIf { it.exists() }?.delete()
            File(parentDir, backdropFile(itemId)).takeIf { it.exists() }?.delete()
        }
    }
}
