package com.raulshma.jellyplay.core.data.repository

import java.io.File

/**
 * Convention-based storage for extra assets bundled alongside a downloaded
 * video file (sibling directories/files of the media file). Kept in one place
 * so every download/delete path stays consistent — adding a new artifact type
 * only requires extending [cleanup] and the relevant writer.
 *
 *  - `<dir>/trickplay/`           trickplay sprite sheets + `meta.json`
 *  - `<dir>/subtitles/`           offline external subtitles + `manifest.json`
 *  - `<dir>/segments.json`        serialized intro/outro/recap media segments
 */
internal object DownloadArtifacts {
    const val TRICKPLAY_DIR = "trickplay"
    const val SUBTITLES_DIR = "subtitles"
    const val SUBTITLE_MANIFEST_FILE = "manifest.json"
    const val SEGMENTS_FILE = "segments.json"

    /** Recursively removes all bundled extra artifacts under [parentDir]. */
    fun cleanup(parentDir: File?) {
        if (parentDir == null) return
        File(parentDir, TRICKPLAY_DIR).takeIf { it.exists() }?.deleteRecursively()
        File(parentDir, SUBTITLES_DIR).takeIf { it.exists() }?.deleteRecursively()
        File(parentDir, SEGMENTS_FILE).takeIf { it.exists() }?.delete()
    }
}
