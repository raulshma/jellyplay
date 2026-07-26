package com.raulshma.jellyplay.core.data.repository

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.raulshma.jellyplay.core.model.MediaType
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deep module: the on-disk storage-layout policy for downloads — where a file
 * goes, what it's named, and whether the destination has room.
 *
 * **Why this lives here.** Previously this policy was inlined in the middle of
 * [DownloadRepositoryImpl.startDownloadInternal] (~40 LOC of dir resolution +
 * filename sanitization + extension derivation + StatFs free-space check),
 * tangled with the download-row lifecycle and unreachable from any other call
 * site. The [StoragePolicy] neighbour owns the *byte-cap* rule; this module
 * owns the *path-layout* rule. Two distinct concerns, two modules.
 *
 * **Inputs.** [resolve] takes the media type, the user's storage-location
 * preference ("INTERNAL" vs "EXTERNAL"), the raw display name, and the
 * container string reported by the Jellyfin MediaSource. It is otherwise pure
 * — no DAO, no network, no DB. Tests pass a fake `Context` (Robolectric) or
 * exercise [sanitizeName] / [deriveExtension] / [hasMinimumFreeSpace] directly
 * as pure functions.
 *
 * **Output.** A [ResolvedDownloadPath] carrying the base directory (created if
 * missing), the sanitized filename, and the absolute file path the caller
 * writes into the `DownloadEntity.downloadPath` column.
 */
@Singleton
class DownloadStorageLayout @Inject constructor(
    private val context: Context,
) {

    /**
     * Resolves the download destination for [mediaType] under [storageLocationPref].
     *
     * @param mediaType one of [MediaType.name] (AUDIO/MUSIC → Music dir + mp3;
     *   anything else → Movies dir + mp4, unless [container] overrides).
     * @param storageLocationPref the `downloadStorageLocation` preference value
     *   ("INTERNAL" / "EXTERNAL", case-insensitive; anything else treats as EXTERNAL).
     * @param name the raw display name; sanitized via [sanitizeName].
     * @param idHint a short id (typically the first 8 chars of the download row
     *   UUID) appended to the filename to guarantee uniqueness across downloads
     *   of the same name.
     * @param container the original container reported by the server (e.g. "mkv");
     *   used as the file extension when safe, falling back to mp3/mp4 otherwise.
     * @throws IllegalStateException if the resolved directory has < 100 MB free.
     */
    fun resolve(
        mediaType: String,
        storageLocationPref: String,
        name: String,
        idHint: String,
        container: String?,
    ): ResolvedDownloadPath {
        val isAudioType = mediaType == MediaType.AUDIO.name || mediaType == MediaType.MUSIC.name
        val dirType = if (isAudioType) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        // "EXTERNAL" prefers the primary external storage mount (still
        // app-private externalFilesDir, scoped to MEDIA_ROOT). "INTERNAL" falls
        // back to filesDir which is never visible to other apps and survives
        // media scan indexing. Both remain app-private post-uninstall.
        val useInternalStorage = storageLocationPref.equals("INTERNAL", ignoreCase = true) &&
            storageLocationPref != "EXTERNAL"
        val baseDir = when {
            useInternalStorage && !isAudioType -> File(context.filesDir, "downloads")
            useInternalStorage && isAudioType -> File(context.filesDir, "downloads/music")
            else -> context.getExternalFilesDir(dirType)
                ?: File(context.filesDir, if (isAudioType) "downloads/music" else "downloads")
        }
        if (!baseDir.exists()) baseDir.mkdirs()

        check(hasMinimumFreeSpace(baseDir)) {
            "Insufficient storage space. Less than 100 MB available on device."
        }

        val safeName = sanitizeName(name)
        val extension = deriveExtension(container, isAudioType)
        val file = File(baseDir, "${safeName}_${idHint}.$extension")
        return ResolvedDownloadPath(
            baseDir = baseDir,
            fileName = file.name,
            filePath = file.absolutePath,
        )
    }

    /**
     * The download display name with characters that are unsafe on the local
     * filesystem (or in a URI) replaced with `_`. Exposed for testing.
     */
    internal fun sanitizeName(name: String): String =
        name.replace(FILENAME_SANITIZE_REGEX, "_")

    /**
     * File extension for the download. Prefers the original [container] reported
     * by the Jellyfin MediaSource so the on-disk extension reflects the real
     * bytes — ExoPlayer selects its extractor from the URI extension and hangs
     * silently when the extension lies (e.g. an MKV stream saved as `.mp4`).
     * Falls back to the legacy hardcoded extension for audio/video when the
     * container is missing or unsafe (path-traversal / weird chars).
     */
    internal fun deriveExtension(container: String?, isAudioType: Boolean): String =
        container
            ?.takeIf { it.isNotBlank() && FILENAME_CONTAINER_REGEX.matches(it) }
            ?: if (isAudioType) "mp3" else "mp4"

    /**
     * True iff [dir] has at least [MIN_FREE_BYTES] available. The floor guards
     * against starting a download onto a nearly-full volume. Exposed for testing
     * (callers pass a real dir from the test filesystem).
     */
    internal fun hasMinimumFreeSpace(dir: File): Boolean {
        val statFs = StatFs(dir.absolutePath)
        val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
        return availableBytes >= MIN_FREE_BYTES
    }

    private companion object {
        private val FILENAME_SANITIZE_REGEX = Regex("[^a-zA-Z0-9.\\-]")
        private val FILENAME_CONTAINER_REGEX = Regex("[A-Za-z0-9]{2,8}")
        private const val MIN_FREE_BYTES = 100L * 1024 * 1024
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
