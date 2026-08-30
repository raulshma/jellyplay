package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.model.OfflineSubtitleEntry
import com.raulshma.jellyplay.core.model.OfflineSubtitleManifest
import kotlinx.serialization.json.Json
import java.io.File

private const val TAG = "SubtitleBundleWriter"

// Disk-side half of the offline subtitle bundle: what lands next to a
// downloaded video once the per-stream fetches have run. Extracted from
// [DownloadRepositoryImpl.downloadExternalSubtitles] so the manifest/orphan
// semantics are unit-testable without Room, OkHttp, or a server.

/**
 * Derives the VobSub pair URLs from the server's deliveryUrl for the stream.
 * The server advertises whichever half the MediaStream points at; the sibling
 * is the same path with the extension swapped (query string preserved — it
 * carries the api_key). Returns `palette to bitmap`. The third arm handles an
 * endpoint shape with no file extension by assuming the bitmap rides on
 * [subUrl] and probing the conventional `.idx` sibling beside it.
 */
internal fun vobsubPairUrls(subUrl: String): Pair<String, String> {
    val path = subUrl.substringBefore('?')
    val query = subUrl.removePrefix(path)
    return when {
        path.endsWith(".idx", ignoreCase = true) -> subUrl to (path.dropLast(4) + ".sub" + query)
        path.endsWith(".sub", ignoreCase = true) -> (path.dropLast(4) + ".idx" + query) to subUrl
        else -> "$path.idx$query" to subUrl
    }
}

/**
 * Persists [entries] as the authoritative `manifest.json` of [subtitlesDir].
 */
internal fun writeSubtitleManifest(
    subtitlesDir: File,
    entries: List<OfflineSubtitleEntry>,
    json: Json,
) {
    File(subtitlesDir, DownloadArtifacts.SUBTITLE_MANIFEST_FILE)
        .writeText(json.encodeToString(OfflineSubtitleManifest(entries)))
}

/**
 * Deletes files in [subtitlesDir] that neither the current bundle nor the
 * manifest reference — leftovers from a previous bundle whose streams no
 * longer exist server-side. Callers must only invoke this after a **fully**
 * successful pass (every deliverable stream fetched): on a partial pass the
 * rewritten manifest legitimately omits a transiently-failed stream, and
 * deleting its file would destroy a still-working sidecar instead of leaving
 * it stale for the resync to replace.
 */
internal fun pruneOrphanSidecarFiles(subtitlesDir: File, liveFileNames: Set<String>) {
    subtitlesDir.listFiles()
        ?.filter { it.isFile && it.name != DownloadArtifacts.SUBTITLE_MANIFEST_FILE && it.name !in liveFileNames }
        ?.forEach { file ->
            if (!file.delete()) {
                // Not fatal — the next fully successful pass retries the prune.
                Log.d(TAG, "Failed to prune orphan sidecar ${file.name} in ${subtitlesDir.name}")
            }
        }
}
