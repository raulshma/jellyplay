package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.model.BookFormat
import okio.Path

/**
 * Where the reader's bytes came from. [OfflineDownload] is the completed
 * JellyPlay download (the user pulled it offline deliberately); [ReaderCache]
 * is the transparent streaming fetch into the app-managed reader cache.
 */
sealed interface ResolvedBook {
    val path: Path

    /** A completed offline download — played straight from its download location. */
    data class OfflineDownload(override val path: Path) : ResolvedBook

    /**
     * The reader cache copy. [downloadedNow] distinguishes "fetched this
     * session" (the loading UI showed progress) from "already cached".
     */
    data class ReaderCache(override val path: Path, val downloadedNow: Boolean) : ResolvedBook
}

/** Byte progress of the streaming fetch — `totalBytes` null when the server sends no length. */
data class BookDownloadProgress(val bytesDownloaded: Long, val totalBytes: Long?) {
    /** 0f..1f when the total is known, else null (indeterminate loading veil). */
    val fraction: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { bytesDownloaded.toFloat() / it }
}

/**
 * Resolves a book item to a local file the opener can read. Resolution order
 * (v1):
 *
 * 1. a completed offline download via
 *    `PlaybackSourceResolver.resolveUsableDownload` — the same on-disk
 *    predicate the video/audio players use —
 * 2. otherwise a one-time streaming GET of [BookContentResolver.resolve]'s
 *    [downloadUrl] into `<cacheDir>/reader-cache/<itemId>/<sanitized-name>`,
 *    reporting byte progress for the loading state.
 *
 * `fileName` may be the item's raw `Path` or a bare name — implementations
 * strip any path structure themselves when shaping the cache file name.
 *
 * No eviction policy in v1: the reader cache lives under the app cache/data
 * dir and is app-managed (OS cache clearing on Android, disk cleanup on
 * desktop); re-download after eviction is transparent.
 */
interface BookContentResolver {
    suspend fun resolve(
        itemId: String,
        fileName: String?,
        format: BookFormat,
        downloadUrl: String,
        onProgress: (BookDownloadProgress) -> Unit,
    ): ResolvedBook
}
