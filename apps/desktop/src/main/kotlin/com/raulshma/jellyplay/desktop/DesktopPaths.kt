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
     * http-cache subtree under it, wave 21B) — same conversion as
     * [dataDirNio].
     */
    val configDirNio: NioPath get() = NioPathOf(configDir.toString())

    /**
     * Crash-log directory (wave 10A release engineering): `<data>/logs`.
     * Created lazily by [DesktopCrashHandler] on first write — no eager
     * directory for users who never crash.
     */
    val logsDirNio: NioPath get() = NioPathOf("$dataDir/logs")

    companion object {
        fun resolve(): DesktopPaths {
            // Wave 12A measurement hook: the perf harness overrides the whole
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
