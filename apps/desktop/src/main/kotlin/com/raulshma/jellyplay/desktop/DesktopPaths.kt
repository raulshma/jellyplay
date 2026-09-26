package com.raulshma.jellyplay.desktop

import java.nio.file.Path as NioPath
import kotlin.io.path.Path as NioPathOf
import okio.Path
import okio.Path.Companion.toPath

/**
 * Filesystem layout for the desktop app (proper appdirs arrives with
 * V3-settings). The shared Koin modules take okio.Paths; core:data's desktop
 * module is the one java.nio holdout, exposed as [dataDirNio].
 */
data class DesktopPaths(
    val dataDir: Path,
    val configDir: Path,
    val databaseFile: Path,
) {
    val dataDirNio: NioPath get() = NioPathOf(dataDir.toString())

    /**
     * The config root as java.nio (desktopSettingsPlatformModule walks the
     * http-cache subtree under it) — same conversion as
     * [dataDirNio].
     */
    val configDirNio: NioPath get() = NioPathOf(configDir.toString())

    /**
     * Crash-log directory (release engineering): `<data>/logs`.
     * Created lazily by [DesktopCrashHandler] on first write — no eager
     * directory for users who never crash.
     */
    val logsDirNio: NioPath get() = NioPathOf("$dataDir/logs")

    /**
     * GLSL shader-pack root: the appdata root's `shaders` folder —
     * literally `%APPDATA%/JellyPlay/shaders/` on Windows. The bundled
     * Anime4K chains are extracted here at startup (manifest-versioned,
     * re-extracted on version bump) because mpv's `glsl-shaders` option needs
     * real file paths; user-dropped `*.glsl` files live in the same folder
     * and are listable as custom shaders.
     */
    val shadersDirNio: NioPath
        get() = dataDir.parent
            ?.let { NioPathOf(it.toString()).resolve("shaders") }
            ?: NioPathOf("$dataDir/shaders")

    companion object {
        fun resolve(): DesktopPaths {
            //  measurement hook: the perf harness overrides the whole
            // tree so baseline runs never touch real appdata. Only set by
            // tools/perf/desktop-baseline.sh (see DesktopStartupPerf KDoc).
            System.getProperty(DesktopStartupPerf.PROP_DATA_DIR)
                ?.takeIf { it.isNotBlank() }
                ?.let { dir ->
                    return DesktopPaths(
                        dataDir = "$dir/data".toPath(),
                        configDir = "$dir/config".toPath(),
                        databaseFile = "$dir/data/jellyplay.db".toPath(),
                    )
                }
            val os = System.getProperty("os.name").lowercase()
            val home = System.getProperty("user.home")
            val dir = when {
                os.contains("win") -> "${System.getenv("APPDATA") ?: "$home/.AppData"}/JellyPlay"
                os.contains("mac") -> "$home/Library/Application Support/JellyPlay"
                else ->
                    System.getenv("XDG_DATA_HOME")?.let { "$it/jellyplay" }
                        ?: "$home/.local/share/jellyplay"
            }
            return DesktopPaths(
                dataDir = "$dir/data".toPath(),
                configDir = "$dir/config".toPath(),
                databaseFile = "$dir/data/jellyplay.db".toPath(),
            )
        }
    }
}
