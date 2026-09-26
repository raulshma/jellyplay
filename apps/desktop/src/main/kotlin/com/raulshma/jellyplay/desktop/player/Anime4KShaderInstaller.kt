package com.raulshma.jellyplay.desktop.player

import java.io.File
import java.io.InputStream

/**
 * Extracts the bundled Anime4K v4.0.1 GLSL chains from the classpath
 * into the user's shader directory (`%APPDATA%/JellyPlay/shaders/`), because
 * mpv's `glsl-shaders` option needs REAL FILE PATHS — classpath resources
 * cannot be fed to mpv directly.
 *
 * Idempotent + versioned: a `manifest.json` in the target directory records
 * the bundled shader version; installation is skipped when the manifest
 * matches the current version AND every chain file already exists, and the
 * whole set is re-extracted when the version bumps (or the manifest is
 * missing/corrupt, or files were deleted by the user). Extraction always
 * overwrites in place — user-dropped `*.glsl` files never collide because
 * they never carry the bundled `Anime4K_*` names (and a collision would be
 * overwritten only by a deliberate version bump).
 *
 * The heavy lifting is isolated behind two seams so the logic is testable
 * without the real classpath or a fixed filesystem:
 *  - [resourceOpener]: resolves a bundled shader's bytes (default: the
 *    application classpath under [Companion.RESOURCE_ROOT]).
 *  - the target [File] (tests pass a temp directory).
 *
 * The manifest codec is hand-rolled (write + a lenient version read): the
 * desktop app module does not apply the kotlinx-serialization compiler
 * plugin, and the manifest's shape is fully owned here.
 */
class Anime4KShaderInstaller(
    private val targetDir: File,
    private val bundledFiles: List<String> = DEFAULT_BUNDLED_FILES,
    private val resourceOpener: (String) -> InputStream? = ::openClasspathResource,
) {

    /** The installed-shaders manifest (version → re-extract on bump). */
    data class Manifest(
        val version: String,
        val files: List<String> = emptyList(),
    )

    /** The outcome of [ensureInstalled] — surfaced in startup logs only. */
    enum class Result { INSTALLED, CURRENT, FAILED }

    /**
     * Brings the target directory up to the bundled version. Never throws:
     * a failed extraction degrades to [Result.FAILED] and the engine omits
     * the `glsl-shaders` pair for missing files' packs (an empty/partial pack
     * list beats a crashed startup — the app boots, playback just has no
     * shader pack until the next launch).
     */
    fun ensureInstalled(): Result = try {
        val upToDate = !needsInstall(
            manifest = readManifest(),
            bundledVersion = VERSION,
            allFilesPresent = bundledFiles.all { File(targetDir, it).isFile },
        )
        if (upToDate) Result.CURRENT else {
            extractAll()
            Result.INSTALLED
        }
    } catch (_: Exception) {
        Result.FAILED
    }

    /**
     * The user shaders (FSRCNNX / ArtCNN are intentionally NOT
     * bundled — any `*.glsl` the user drops into the shaders directory is
     * listable as a CUSTOM-pack candidate). Returns every non-bundled
     * `*.glsl` file, sorted by name; empty when the directory doesn't exist.
     */
    fun listUserShaderFiles(): List<File> {
        if (!targetDir.isDirectory) return emptyList()
        val bundled = bundledFiles.toSet()
        return targetDir.listFiles { file -> file.isFile && file.name.endsWith(".glsl") }
            ?.filter { it.name !in bundled }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    private fun readManifest(): Manifest? {
        val file = File(targetDir, MANIFEST_NAME)
        if (!file.isFile) return null
        return try {
            parseManifest(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    private fun extractAll() {
        targetDir.mkdirs()
        for (name in bundledFiles) {
            val bytes = resourceOpener(name)?.use { it.readBytes() }
                ?: error("bundled shader resource not found: $name")
            File(targetDir, name).writeBytes(bytes)
        }
        File(targetDir, MANIFEST_NAME).writeText(manifestJson(Manifest(version = VERSION, files = bundledFiles)))
    }

    internal companion object {
        /**
         * The bundled Anime4K release version — BUMP when refreshing the
         * resource files; the manifest comparison re-extracts automatically.
         * (The upstream v4.0.1 release tag ships its zip asset under the
         * `Anime4K_v4.0.zip` name; the shader content IS the 4.0.1 set.)
         */
        internal const val VERSION = "4.0.1"

        internal const val MANIFEST_NAME = "manifest.json"

        /** Classpath root the chain files are bundled under. */
        internal const val RESOURCE_ROOT = "shaders/anime4k"

        /**
         * The union of the three official mpv chains (v4.0.1
         * `GLSL_Instructions.md`, higher-end-GPU recipes): Mode A
         * (Restore_CNN_VL), Mode B (Restore_CNN_Soft_VL) and Mode C
         * (Upscale_Denoise_CNN_x2_VL). Order within a chain is owned by
         * `MpvConfigMapping.anime4kChain`; this list is only the extraction
         * set.
         */
        internal val DEFAULT_BUNDLED_FILES = listOf(
            "Anime4K_Clamp_Highlights.glsl",
            "Anime4K_Restore_CNN_VL.glsl",
            "Anime4K_Restore_CNN_Soft_VL.glsl",
            "Anime4K_Upscale_Denoise_CNN_x2_VL.glsl",
            "Anime4K_Upscale_CNN_x2_VL.glsl",
            "Anime4K_Upscale_CNN_x2_M.glsl",
            "Anime4K_AutoDownscalePre_x2.glsl",
            "Anime4K_AutoDownscalePre_x4.glsl",
        )

        /** The real classpath opener (dev runs and the packaged jars share it). */
        fun openClasspathResource(name: String): InputStream? =
            Anime4KShaderInstaller::class.java.classLoader
                ?.getResourceAsStream("$RESOURCE_ROOT/$name")

        /**
         * The pure version-bump decision, split out for the extraction
         * policy tests: install when the manifest is missing/corrupt, its
         * version differs from the bundled one, or any bundled file went
         * missing on disk.
         */
        fun needsInstall(manifest: Manifest?, bundledVersion: String, allFilesPresent: Boolean): Boolean =
            manifest == null || manifest.version != bundledVersion || !allFilesPresent

        /** The manifest document [extractAll] writes. */
        internal fun manifestJson(manifest: Manifest): String = buildString {
            append("{\n  \"version\": \"")
            append(manifest.version)
            append("\",\n  \"files\": [")
            manifest.files.forEachIndexed { index, name ->
                if (index > 0) append(',')
                append("\n    \"$name\"")
            }
            if (manifest.files.isNotEmpty()) append("\n  ")
            append("]\n}\n")
        }

        /** Lenient [Manifest] read of a document [manifestJson] wrote (tests). */
        internal fun parseManifest(text: String): Manifest? {
            val version = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
                ?: return null
            return Manifest(
                version = version,
                files = Regex("\"([A-Za-z0-9_.]+\\.glsl)\"").findAll(text)
                    .map { it.groupValues[1] }
                    .toList(),
            )
        }
    }
}
