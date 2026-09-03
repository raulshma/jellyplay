package com.raulshma.jellyplay.desktop

import java.nio.file.Files
import java.nio.file.Path
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pins [DesktopPaths.resolve]'s precedence table (wave 12A):
 *
 *  1. `jellyplay.perf.dataDir` (system property, the perf harness's surgical
 *     override) wins over EVERYTHING and reroutes the whole tree under
 *     `<dir>/{data,config}` with `<dir>/data/jellyplay.db` — baseline runs
 *     must never touch real appdata;
 *  2. otherwise the platform appdata convention: Windows `%APPDATA%\JellyPlay`
 *     (falling back to `<user.home>/.AppData` when the env var is unset), macOS
 *     `~/Library/Application Support/JellyPlay`, XDG
 *     `$XDG_DATA_HOME/jellyplay` else `~/.local/share/jellyplay`;
 *  3. every branch produces the SAME inner layout: `<root>/data`,
 *     `<root>/config`, `<root>/data/jellyplay.db` (+ `<data>/logs` derived).
 *
 * The os/user.home branches are driven through injectable system properties
 * (saved + restored per test); the two ENV reads (`APPDATA`,
 * `XDG_DATA_HOME`) are not settable from a JVM, so those branches mirror-read
 * the real environment to compute the expectation.
 */
class DesktopPathsTest {

    private val tempDirs = mutableListOf<Path>()
    private val savedProps = mutableMapOf<String, String?>()

    private fun withProp(key: String, value: String) {
        savedProps.putIfAbsent(key, System.getProperty(key))
        System.setProperty(key, value)
    }

    @BeforeTest
    fun snapshotProps() {
        listOf("os.name", "user.home", DesktopStartupPerf.PROP_DATA_DIR).forEach {
            savedProps.putIfAbsent(it, System.getProperty(it))
        }
    }

    @AfterTest
    fun restoreProps() {
        savedProps.forEach { (key, value) ->
            if (value == null) System.clearProperty(key) else System.setProperty(key, value)
        }
        savedProps.clear()
        tempDirs.forEach { dir -> dir.toFile().deleteRecursively() }
    }

    private fun newTempDir(): String =
        Files.createTempDirectory("jellyplay-paths-test").also { tempDirs.add(it) }.toString()

    // ── layout invariants (shared by every branch) ────────────────────────

    @Test
    fun perfOverrideReroutesTheWholeTreeAndWinsOverPlatformConventions() {
        val dir = newTempDir()
        withProp(DesktopStartupPerf.PROP_DATA_DIR, dir)
        // Exotic platform hints that would otherwise pick a different branch:
        // the perf property must beat them all.
        withProp("os.name", "Mac OS X")
        withProp("user.home", newTempDir())

        val paths = DesktopPaths.resolve()

        assertEquals("$dir/data".toPath(), paths.dataDir)
        assertEquals("$dir/config".toPath(), paths.configDir)
        assertEquals("$dir/data/jellyplay.db".toPath(), paths.databaseFile)
    }

    @Test
    fun blankPerfOverrideFallsThroughToPlatformResolution() {
        val home = newTempDir()
        withProp(DesktopStartupPerf.PROP_DATA_DIR, "")
        withProp("os.name", "Mac OS X")
        withProp("user.home", home)

        val paths = DesktopPaths.resolve()

        // Blank is not an override — the mac convention applies instead.
        assertEquals("$home/Library/Application Support/JellyPlay/data".toPath(), paths.dataDir)
    }

    @Test
    fun innerLayoutIsIdenticalAcrossBranches() {
        val dir = newTempDir()
        withProp(DesktopStartupPerf.PROP_DATA_DIR, dir)
        val perfPaths = DesktopPaths.resolve()

        val home = newTempDir()
        System.clearProperty(DesktopStartupPerf.PROP_DATA_DIR)
        withProp("os.name", "Mac OS X")
        withProp("user.home", home)
        val macPaths = DesktopPaths.resolve()

        for (paths in listOf(perfPaths, macPaths)) {
            assertEquals(
                paths.databaseFile.toString(),
                paths.dataDirNio.resolve("jellyplay.db").toString(),
            )
            assertEquals(paths.dataDirNio.resolve("logs"), paths.logsDirNio)
            assertEquals("config", paths.configDirNio.fileName.toString())
            assertEquals("data", paths.dataDirNio.fileName.toString())
        }
    }

    // ── platform branches ─────────────────────────────────────────────────

    @Test
    fun windowsBranchUsesAppDataWithHomeFallback() {
        val home = newTempDir()
        withProp(DesktopStartupPerf.PROP_DATA_DIR, "")
        withProp("os.name", "Windows 11")
        withProp("user.home", home)

        val paths = DesktopPaths.resolve()
        val appData = System.getenv("APPDATA")

        if (appData != null) {
            assertEquals("$appData/JellyPlay/data".toPath(), paths.dataDir)
            assertEquals("$appData/JellyPlay/config".toPath(), paths.configDir)
        } else {
            // Env unset → the documented `<home>/.AppData` fallback.
            assertEquals("$home/.AppData/JellyPlay/data".toPath(), paths.dataDir)
        }
        // The Windows root is always the JellyPlay dir under the appdata base.
        assertEquals("data", paths.dataDirNio.fileName.toString())
        assertEquals("JellyPlay", paths.dataDirNio.parent.fileName.toString())
    }

    @Test
    fun macBranchUsesApplicationSupportUnderTheInjectedHome() {
        val home = newTempDir()
        withProp(DesktopStartupPerf.PROP_DATA_DIR, "")
        withProp("os.name", "Mac OS X")
        withProp("user.home", home)

        val paths = DesktopPaths.resolve()

        assertEquals("$home/Library/Application Support/JellyPlay/data".toPath(), paths.dataDir)
        assertEquals("$home/Library/Application Support/JellyPlay/config".toPath(), paths.configDir)
        assertEquals(
            "$home/Library/Application Support/JellyPlay/data/jellyplay.db".toPath(),
            paths.databaseFile,
        )
    }

    @Test
    fun linuxBranchPrefersXdgDataHomeThenFallsBackToLocalShare() {
        val home = newTempDir()
        withProp(DesktopStartupPerf.PROP_DATA_DIR, "")
        withProp("os.name", "Linux")
        withProp("user.home", home)

        val paths = DesktopPaths.resolve()
        val xdg = System.getenv("XDG_DATA_HOME")

        if (xdg != null) {
            assertEquals("$xdg/jellyplay/data".toPath(), paths.dataDir)
        } else {
            assertEquals("$home/.local/share/jellyplay/data".toPath(), paths.dataDir)
        }
    }

    // ── nio conversions + derived dirs ────────────────────────────────────

    @Test
    fun nioViewsAndLogsDirDeriveFromTheOkioTree() {
        val dir = newTempDir()
        withProp(DesktopStartupPerf.PROP_DATA_DIR, dir)

        val paths = DesktopPaths.resolve()

        assertEquals("$dir/data/jellyplay.db".toPath().toString(), paths.dataDirNio.resolve("jellyplay.db").toString())
        assertEquals("$dir/config".toPath().toString(), paths.configDirNio.toString())
        assertTrue(
            paths.logsDirNio.toString().endsWith("logs"),
            "crash logs live at <data>/logs: ${paths.logsDirNio}",
        )
        assertEquals(
            paths.dataDirNio.resolve("logs").toString(),
            paths.logsDirNio.toString(),
        )
        assertNotEquals(paths.dataDirNio.toString(), paths.configDirNio.toString())
    }
}
