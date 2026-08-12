package com.raulshma.jellyplay.core.data.repository

import java.io.File

/**
 * Convention-based storage for extra assets bundled alongside a downloaded
 * video file (sibling directories/files of the media file). Kept in one place
 * so every download/delete path stays consistent — adding a new artifact type
 * only requires extending [cleanup] and the relevant writer.
 *
 *  - `<dir>/trickplay_<itemId>/`              trickplay sprite sheets + `meta.json`
 *  - `<dir>/subtitles_<itemId>/`              offline external subtitles + `manifest.json`
 *  - `<dir>/<itemId>_segments.json`           serialized intro/outro/recap media segments
 *  - `<dir>/<itemId>_poster.jpg`              offline poster (primary) image, unique per item
 *  - `<dir>/<itemId>_backdrop.jpg`            offline backdrop image, unique per item
 *  - `<dir>/<personId>_person.jpg`            offline cast/person image, unique per person
 *
 * The poster/backdrop/person images are downloaded to local files at download
 * time so the offline screens can render them without a network connection
 * (previously the remote URLs were only stored, leaving blurHash as the sole
 * offline visual). Person images additionally survive Coil memory-cache
 * eviction, which was the root cause of the offline cast row failing.
 *
 * The filenames are keyed by Jellyfin `itemId`/`personId` (stable UUIDs)
 * because all downloads share a single flat directory
 * (`getExternalFilesDir(MOVIES)` or `filesDir/downloads`) — fixed names like
 * `poster.jpg` would collide and silently overwrite each other across
 * episodes/movies.
 */
internal object DownloadArtifacts {
    const val LEGACY_TRICKPLAY_DIR = "trickplay"
    const val LEGACY_SUBTITLES_DIR = "subtitles"
    const val SUBTITLE_MANIFEST_FILE = "manifest.json"
    const val LEGACY_SEGMENTS_FILE = "segments.json"

    fun trickplayDir(itemId: String): String = "trickplay_$itemId"
    fun subtitlesDir(itemId: String): String = "subtitles_$itemId"
    fun segmentsFile(itemId: String): String = "${itemId}_segments.json"

    /** Per-item poster filename, unique within the shared downloads dir. */
    fun posterFile(itemId: String): String = "${itemId}_poster.jpg"

    /** Per-item backdrop filename, unique within the shared downloads dir. */
    fun backdropFile(itemId: String): String = "${itemId}_backdrop.jpg"

    /** Per-person cast image filename, unique within the shared downloads dir. */
    fun personImageFile(personId: String): String = "${personId}_person.jpg"

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
        File(parentDir, LEGACY_TRICKPLAY_DIR).takeIf { it.exists() }?.deleteRecursively()
        File(parentDir, LEGACY_SUBTITLES_DIR).takeIf { it.exists() }?.deleteRecursively()
        File(parentDir, LEGACY_SEGMENTS_FILE).takeIf { it.exists() }?.delete()
        if (itemId != null) {
            File(parentDir, trickplayDir(itemId)).takeIf { it.exists() }?.deleteRecursively()
            File(parentDir, subtitlesDir(itemId)).takeIf { it.exists() }?.deleteRecursively()
            File(parentDir, segmentsFile(itemId)).takeIf { it.exists() }?.delete()
            File(parentDir, posterFile(itemId)).takeIf { it.exists() }?.delete()
            File(parentDir, backdropFile(itemId)).takeIf { it.exists() }?.delete()
        }
    }

    /**
     * Removes the series-scoped poster/backdrop files under [parentDir].
     *
     * Series artwork is written beside downloaded episodes (keyed by seriesId)
     * when a series is queued or a lone episode seeds its parent row, so it
     * must be pruned when the whole series is deleted — per-item cleanup only
     * removes `${itemId}_*` files and would leave the series images orphaned
     * on disk. Only whole-series deletion removes them; deleting individual
     * episodes keeps the files because sibling episode rows still reference
     * the same series artwork.
     */
    fun cleanupSeriesArtwork(parentDir: File?, seriesId: String) {
        if (parentDir == null) return
        File(parentDir, posterFile(seriesId)).takeIf { it.exists() }?.delete()
        File(parentDir, backdropFile(seriesId)).takeIf { it.exists() }?.delete()
    }

    /**
     * Removes the cast/person image files for [personIds] under [parentDir].
     *
     * Person images are keyed by `personId` (not the media item id), so the
     * per-item and series cleanup paths don't touch them. Called when the
     * owning movie/series download is deleted. A given person may appear across
     * multiple items, but Jellyfin person ids are server-global and the image
     * is identical for the same id, so deletion is safe once the last item
     * referencing it is gone — the offline detail screen falls back to the
     * remote URL online and the blurhash offline, same as a never-preloaded row.
     */
    fun cleanupCastArtwork(parentDir: File?, personIds: Collection<String>) {
        if (parentDir == null) return
        personIds.forEach { id ->
            File(parentDir, personImageFile(id)).takeIf { it.exists() }?.delete()
        }
    }
}
