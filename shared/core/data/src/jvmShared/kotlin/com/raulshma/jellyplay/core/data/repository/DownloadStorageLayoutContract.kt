package com.raulshma.jellyplay.core.data.repository

import java.io.File

/**
 * The on-disk storage-layout contract for downloads — where a file goes, what
 * it's named, and whether the destination has room (V3 downloads conveyor).
 *
 * Extracted from the Android-only `DownloadStorageLayout` so the portable
 * [DownloadRepositoryImpl] depends on the *rule*, not the platform: the Android
 * actual (legacy :core:data shim) keeps its Context/Environment/StatFs logic
 * verbatim (filesDir vs N app-private external roots), the desktop actual
 * resolves under the appdata dir with `File.getUsableSpace()`.
 *
 * The pure filename rules (sanitize + container-extension derivation) and the
 * free-space floor live in the [Companion] so every platform actual produces
 * IDENTICAL filenames for the same inputs.
 */
interface DownloadStorageLayoutContract {

    /**
     * Resolves the download destination for [mediaType] under
     * [storageLocationPref].
     *
     * @param mediaType one of MediaType.name (AUDIO/MUSIC → Music dir + mp3;
     *   anything else → Movies dir + mp4, unless [container] overrides).
     * @param storageLocationPref the `downloadStorageLocation` preference value
     *   ("INTERNAL" / "EXTERNAL" / "EXTERNAL_N" on Android; desktop has a
     *   single volume and ignores it).
     * @param name the raw display name; sanitized via [Companion.sanitizeName].
     * @param idHint a short id (typically the first 8 chars of the download row
     *   UUID) appended to the filename to guarantee uniqueness across downloads
     *   of the same name.
     * @param container the original container reported by the server (e.g. "mkv");
     *   used as the file extension when safe, falling back to mp3/mp4 otherwise.
     * @throws IllegalStateException if the resolved directory has less than
     *   [Companion.MIN_FREE_BYTES] free.
     */
    fun resolve(
        mediaType: String,
        storageLocationPref: String,
        name: String,
        idHint: String,
        container: String?,
    ): ResolvedDownloadPath

    companion object {
        /** Floor guarding against starting a download onto a nearly-full volume. */
        const val MIN_FREE_BYTES: Long = 100L * 1024 * 1024

        private val FILENAME_SANITIZE_REGEX = Regex("[^a-zA-Z0-9.\\-]")
        private val FILENAME_CONTAINER_REGEX = Regex("[A-Za-z0-9]{2,8}")

        /**
         * The download display name with characters that are unsafe on the local
         * filesystem (or in a URI) replaced with `_`. Shared by every platform
         * actual so filenames match across platforms for the same inputs.
         */
        fun sanitizeName(name: String): String =
            name.replace(FILENAME_SANITIZE_REGEX, "_")

        /**
         * File extension for the download. Prefers the original [container]
         * reported by the Jellyfin MediaSource so the on-disk extension reflects
         * the real bytes — ExoPlayer selects its extractor from the URI extension
         * and hangs silently when the extension lies (e.g. an MKV stream saved
         * as `.mp4`). Falls back to the legacy hardcoded extension for
         * audio/video when the container is missing or unsafe (path-traversal /
         * weird chars).
         */
        fun deriveExtension(container: String?, isAudioType: Boolean): String =
            container
                ?.takeIf { it.isNotBlank() && FILENAME_CONTAINER_REGEX.matches(it) }
                ?: if (isAudioType) "mp3" else "mp4"

        /** True iff [availableBytes] clears the [MIN_FREE_BYTES] floor. */
        fun hasMinimumFreeSpace(availableBytes: Long): Boolean =
            availableBytes >= MIN_FREE_BYTES
    }
}

/**
 * The resolved on-disk destination for one download.
 *
 * @property baseDir the directory the file lives in (created if missing).
 * @property fileName the sanitized filename (`<safeName>_<id8>.<ext>`).
 * @property filePath absolute path = `baseDir`/`fileName`. This is what gets
 *   written to `DownloadEntity.downloadPath`.
 */
data class ResolvedDownloadPath(
    val baseDir: File,
    val fileName: String,
    val filePath: String,
)
