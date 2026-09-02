package com.raulshma.jellyplay.desktop

import java.awt.Rectangle
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Persistence + sanitize suite for [DesktopWindowStateStore]. Round-trips
 * run against a real tmpdir like DesktopCrashHandlerTest; sanitize is pure
 * and fed synthetic screen lists (a real GraphicsEnvironment needs a
 * display, which the headless test JVM doesn't have — which is also why
 * availableScreens() returning empty there is pinned).
 */
class DesktopWindowStateStoreTest {

    private val tempDirs = mutableListOf<Path>()

    private fun newStore(fileName: String = "window-state.properties"): DesktopWindowStateStore =
        DesktopWindowStateStore(
            Files.createTempDirectory("jellyplay-window-state-test").also { tempDirs.add(it) }.resolve(fileName),
        )

    @AfterTest
    fun cleanup() {
        tempDirs.forEach { dir -> dir.toFile().deleteRecursively() }
    }

    // --- load/save round-trip ---

    @Test
    fun `save then load round-trips floating geometry`() {
        val store = newStore()
        val geometry = DesktopWindowGeometry(x = 120, y = -40, width = 1280, height = 800)
        store.save(geometry)
        assertEquals(geometry, store.load())
    }

    @Test
    fun `save then load round-trips maximized flag with restore bounds`() {
        val store = newStore()
        // What Main.kt persists when the session ended maximized: the
        // pre-maximize floating bounds + maximized=true.
        val geometry = DesktopWindowGeometry(x = 10, y = 20, width = 900, height = 600, maximized = true)
        store.save(geometry)
        assertEquals(geometry, store.load())
    }

    @Test
    fun `load with no file returns null`() {
        assertNull(newStore().load())
    }

    @Test
    fun `load with corrupt file returns null instead of throwing`() {
        val store = newStore()
        Files.writeString(
            // Truncated/garbled write or hand-edited file — every parse
            // failure mode funnels to null so Main.kt falls back centered.
            (tempDirs.last()).resolve("window-state.properties"),
            "window.x=not-a-number\nwindow.y=\n",
        )
        assertNull(store.load())
    }

    @Test
    fun `load rejects non-positive size`() {
        val store = newStore()
        store.save(DesktopWindowGeometry(x = 0, y = 0, width = -5, height = 800))
        assertNull(store.load())
    }

    @Test
    fun `save creates parent directories and leaves no temp files behind`() {
        val root = Files.createTempDirectory("jellyplay-window-state-test").also { tempDirs.add(it) }
        val store = DesktopWindowStateStore(root.resolve("nested/dir/window-state.properties"))
        store.save(DesktopWindowGeometry(x = 0, y = 0, width = 800, height = 600))
        assertTrue(store.load() != null, "file must be readable after the nested-dir save")
        val dirEntries = Files.list(root.resolve("nested/dir")).use { it.count() }
        assertEquals(1L, dirEntries, "atomic write must not leave .tmp siblings behind")
    }

    // --- sanitize (pure, synthetic screens) ---

    private val primaryScreen = Rectangle(0, 0, 1920, 1080)
    private val leftScreen = Rectangle(-1920, 0, 1920, 1080)

    @Test
    fun `sanitize keeps on-screen geometry untouched`() {
        val geometry = DesktopWindowGeometry(x = 100, y = 100, width = 1280, height = 800)
        assertEquals(
            geometry,
            DesktopWindowStateStore.sanitize(geometry, listOf(primaryScreen)),
        )
    }

    @Test
    fun `sanitize keeps geometry on a non-primary monitor`() {
        val geometry = DesktopWindowGeometry(x = -1800, y = 50, width = 1280, height = 800)
        assertEquals(
            geometry,
            DesktopWindowStateStore.sanitize(geometry, listOf(primaryScreen, leftScreen)),
        )
    }

    @Test
    fun `sanitize rejects fully off-screen geometry`() {
        // Monitor unplugged between sessions: saved x/y now point into the
        // void — null makes Main.kt launch centered instead.
        val geometry = DesktopWindowGeometry(x = 5000, y = 5000, width = 1280, height = 800)
        assertNull(DesktopWindowStateStore.sanitize(geometry, listOf(primaryScreen)))
    }

    @Test
    fun `sanitize rejects geometry with only a sliver on screen`() {
        // 40px of the window still overlaps — less than the title-bar grab
        // region, so treat as unreachable rather than "restore it".
        val geometry = DesktopWindowGeometry(x = 1880, y = 100, width = 1280, height = 800)
        assertNull(DesktopWindowStateStore.sanitize(geometry, listOf(primaryScreen)))
    }

    @Test
    fun `sanitize keeps partially visible geometry`() {
        // Half-offscreen by choice is a normal way to leave a window.
        val geometry = DesktopWindowGeometry(x = 1500, y = 100, width = 1280, height = 800)
        assertEquals(
            geometry,
            DesktopWindowStateStore.sanitize(geometry, listOf(primaryScreen)),
        )
    }

    @Test
    fun `sanitize clamps size up to the minimum`() {
        val geometry = DesktopWindowGeometry(x = 100, y = 100, width = 50, height = 50)
        val sanitized = DesktopWindowStateStore.sanitize(geometry, listOf(primaryScreen))
        assertTrue(sanitized != null, "sliver size must be clamped, not dropped")
        assertEquals(DesktopWindowStateStore.MIN_WIDTH_PX, sanitized!!.width)
        assertEquals(DesktopWindowStateStore.MIN_HEIGHT_PX, sanitized.height)
    }

    @Test
    fun `sanitize clamps window larger than its host monitor`() {
        // 2560px window saved on a big monitor, restored session has only
        // the 1920px laptop panel — keep position, fit size.
        val geometry = DesktopWindowGeometry(x = 0, y = 0, width = 2560, height = 1400)
        assertEquals(
            DesktopWindowGeometry(x = 0, y = 0, width = 1920, height = 1080),
            DesktopWindowStateStore.sanitize(geometry, listOf(primaryScreen)),
        )
    }

    @Test
    fun `sanitize preserves the maximized flag through clamping`() {
        val geometry = DesktopWindowGeometry(x = 0, y = 0, width = 2560, height = 1400, maximized = true)
        val sanitized = DesktopWindowStateStore.sanitize(geometry, listOf(primaryScreen))
        assertTrue(sanitized != null)
        assertTrue(sanitized!!.maximized, "maximize replay must survive the size clamp")
    }

    @Test
    fun `sanitize with no screens returns null`() {
        // Headless (tests, perf harness): nothing is trustworthy — fall
        // back to the centered default rather than restoring blind.
        val geometry = DesktopWindowGeometry(x = 100, y = 100, width = 1280, height = 800)
        assertNull(DesktopWindowStateStore.sanitize(geometry, emptyList()))
    }

    @Test
    fun `availableScreens never throws and matches the headless mode`() {
        // Environment-agnostic pin (dev JVMs here have a display, CI ones
        // may not): a display-equipped JVM must report its real screens —
        // Main.kt's restore branch depends on them — while a headless JVM
        // (perf harness) must get an empty list, not an exception, so the
        // centered fallback takes over.
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            assertTrue(DesktopWindowStateStore.availableScreens().isEmpty())
        } else {
            assertTrue(DesktopWindowStateStore.availableScreens().isNotEmpty())
        }
    }

    // --- fromRectangle ---

    @Test
    fun `fromRectangle maps AWT bounds and the maximize flag`() {
        val rect = Rectangle(11, 22, 640, 480)
        assertEquals(
            DesktopWindowGeometry(x = 11, y = 22, width = 640, height = 480, maximized = true),
            DesktopWindowGeometry.fromRectangle(rect, maximized = true),
        )
        assertFalse(DesktopWindowGeometry.fromRectangle(rect, maximized = false).maximized)
    }
}
