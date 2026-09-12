package com.raulshma.jellyplay.core.data.repository

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.raulshma.jellyplay.core.model.MediaType
import java.io.File

// V3 downloads conveyor: the sanitize/extension/free-space-floor CONTRACT
// moved to :shared:core:data jvmShared (DownloadStorageLayoutContract, same
// package) with the portable DownloadRepositoryImpl; this Android actual keeps
// the Context/Environment/StatFs logic verbatim and now implements the shared
// interface. The @Inject/@Singleton annotations were stripped — Koin owns
// construction (the app composition root's androidDownloadSeamsModule), and
// DownloadRecoveryInitializer (Koin single, app androidAppModule) consumes this
// (StorageSettingsViewModel) via koin().get().

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
 * as pure functions (the two first now delegate to the shared contract
 * companion so Android and desktop produce identical filenames).
 *
 * **Output.** A [ResolvedDownloadPath] carrying the base directory (created if
 * missing), the sanitized filename, and the absolute file path the caller
 * writes into the `DownloadEntity.downloadPath` column.
 */
class DownloadStorageLayout(
    private val context: Context,
) : DownloadStorageLayoutContract {

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
    override fun resolve(
        mediaType: String,
        storageLocationPref: String,
        name: String,
        idHint: String,
        container: String?,
    ): ResolvedDownloadPath {
        val isAudioType = mediaType == MediaType.AUDIO.name || mediaType == MediaType.MUSIC.name
        val dirType = if (isAudioType) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        // "INTERNAL" → filesDir (app-private, never media-scanned).
        // "EXTERNAL" / "EXTERNAL_2" / "EXTERNAL_3" / ... → the Nth app-private
        // external root from Context.getExternalFilesDirs(dirType) (plural).
        //   - "EXTERNAL"    == primary external (backward-compat, index 0)
        //   - "EXTERNAL_N"  == the Nth mount (1-based: EXTERNAL_2 = 2nd root).
        // `getExternalFilesDirs` returns every app-private external root the
        // OS will grant — primary emulated storage PLUS any inserted SD/USB
        // mount, already scoped to this app (no SAF needed). Real removable
        // mounts surface to the UI without reworking the download stack onto
        // content:// URIs.
        val pref = StorageLocationPref(storageLocationPref)
        val baseDir = when {
            pref.isInternal && !isAudioType -> File(context.filesDir, "downloads")
            pref.isInternal && isAudioType -> File(context.filesDir, "downloads/music")
            else -> externalRootForPref(pref, dirType)
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
     * Resolves the app-private external root selected by [pref]
     * under [dirType]. Returns null when the requested index is out of range
     * (e.g. the SD card was unmounted) — callers fall back to filesDir.
     *
     * `Context.getExternalFilesDirs` is the cleanest API for
     * surfacing real removable mounts under app-private scoping — no SAF, no
     * `content://`, no MediaStore. Each returned entry is a real directory the
     * OS will let this app write into for the lifetime of the mount.
     */
    private fun externalRootForPref(pref: StorageLocationPref, dirType: String): File? {
        val roots = context.getExternalFilesDirs(dirType)
            ?.filterNotNull()
            ?.filter { it.absolutePath.isNotBlank() }
            ?: return null
        if (roots.isEmpty()) return null
        val index = pref.externalIndex.coerceIn(0, roots.lastIndex)
        return roots[index]
    }

    /**
     * Enumerates the storage mounts the user can pick for downloads, in a
     * stable order: INTERNAL first, then each app-private external root from
     * [Context.getExternalFilesDirs] (primary emulated storage, then any
     * inserted SD / USB mount). Each entry carries the [prefValue] to persist
     * and a [kind] the UI maps to a localized label.
     *
     * This is what makes "Storage location" show the *real*
     * available mounts instead of a hardcoded INTERNAL/EXTERNAL pair — when an
     * SD card or USB stick is inserted, it appears here as an extra
     * [StorageMountKind.REMOVABLE] option. Stays within app-private scoped
     * storage (no SAF rework).
     *
     * Android-only surface (the shared [DownloadStorageLayoutContract] has no
     * counterpart — desktop has a single volume), consumed by the settings UI.
     */
    fun availableMounts(): List<StorageMount> {
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
            ?: return options
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
        return options
    }

    private fun freeBytes(dir: File): Long = try {
        val statFs = StatFs(dir.absolutePath)
        statFs.availableBlocksLong * statFs.blockSizeLong
    } catch (_: Throwable) { 0L }

    /**
     * The download display name with characters that are unsafe on the local
     * filesystem (or in a URI) replaced with `_`. Delegates to the shared
     * contract companion so Android and desktop produce identical filenames.
     * Exposed for testing.
     */
    internal fun sanitizeName(name: String): String =
        DownloadStorageLayoutContract.sanitizeName(name)

    /**
     * File extension for the download. Delegates to the shared contract
     * companion (single rule across platforms). Falls back to the legacy
     * hardcoded extension for audio/video when the container is missing or
     * unsafe (path-traversal / weird chars).
     */
    internal fun deriveExtension(container: String?, isAudioType: Boolean): String =
        DownloadStorageLayoutContract.deriveExtension(container, isAudioType)

    /**
     * True iff [dir] has at least [DownloadStorageLayoutContract.MIN_FREE_BYTES]
     * available. The floor guards against starting a download onto a nearly-full
     * volume. Exposed for testing (callers pass a real dir from the test
     * filesystem).
     */
    internal fun hasMinimumFreeSpace(dir: File): Boolean {
        val statFs = StatFs(dir.absolutePath)
        val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
        return DownloadStorageLayoutContract.hasMinimumFreeSpace(availableBytes)
    }
}

/**
 * One selectable download destination surfaced by
 * [DownloadStorageLayout.availableMounts].
 *
 * @property prefValue the value to persist as `downloadStorageLocation`
 *   ("INTERNAL" / "EXTERNAL" / "EXTERNAL_N").
 * @property kind coarse label class the UI maps to a localized string.
 * @property availableBytes free bytes on the mount, or 0 if unavailable.
 * @property rootPath absolute path of the app-private root (for display).
 */
data class StorageMount(
    val prefValue: String,
    val kind: StorageMountKind,
    val availableBytes: Long,
    val rootPath: String,
)

/**
 * Coarse classification of a [StorageMount] for UI labeling. The settings
 * screen maps each to a localized string (`storage_internal`, etc.).
 */
enum class StorageMountKind {
    /** App-private `filesDir` (built-in flash, never media-scanned). */
    INTERNAL,

    /** Primary emulated external storage (built-in flash, app-private). */
    PRIMARY_EXTERNAL,

    /** A real removable mount (SD card / USB) reported by the OS. */
    REMOVABLE,

    /** Secondary non-removable external mount (e.g. adopted storage). */
    EXTERNAL,
}

/**
 * Value class around the raw `download_storage_location` preference string,
 * so the "is this INTERNAL?" / "which external index?" parsing lives in one
 * place instead of being re-derived at every call site.
 *
 * **Persistence stays `String`.** The slice is `@Serializable` and crosses the
 * backup/restore boundary as a plain string, so this class is a parse/label
 * helper — construct it from the stored value at the point of use.
 *
 * Accepted forms (case-insensitive):
 *  - `"INTERNAL"` → app-private filesDir.
 *  - `"EXTERNAL"` → primary external root (index 0, backward-compat).
 *  - `"EXTERNAL_N"` → the Nth external mount (1-based: `EXTERNAL_2` = index 1).
 *  - anything else → treated as primary external (legacy fallback).
 *
 * @param raw the stored preference value.
 */
@JvmInline
value class StorageLocationPref(val raw: String) {

    /** True iff this selects app-private internal storage (`filesDir`). */
    val isInternal: Boolean
        get() = raw.trim().equals("INTERNAL", ignoreCase = true) &&
            !raw.trim().startsWith("EXTERNAL", ignoreCase = true)

    /**
     * 0-based index into `Context.getExternalFilesDirs`: `"EXTERNAL"` → 0,
     * `"EXTERNAL_N"` → N-1. Anything unparseable → 0 (backward-compat with
     * the legacy binary INTERNAL/EXTERNAL toggle).
     */
    val externalIndex: Int
        get() {
            val upper = raw.trim()
            if (upper.equals("EXTERNAL", ignoreCase = true)) return 0
            val match = Regex("^EXTERNAL_(\\d+)$", RegexOption.IGNORE_CASE).matchEntire(upper)
                ?: return 0
            return ((match.groupValues[1].toIntOrNull() ?: 1) - 1).coerceAtLeast(0)
        }
}
