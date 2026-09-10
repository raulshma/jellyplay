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
 * The five placement rules of [DesktopWindowPlacementController] — the
 * choreography that used to live as composable locals in Main.kt — pinned
 * against a fake [DesktopWindowPlacementHost] and the real
 * [DesktopWindowStateStore] on a tmpdir (the DesktopWindowStateStoreTest
 * pattern): a headless JVM has no AWT windows to drive, and the seam exists
 * exactly so the rules don't need one. Toggle/replay state transitions run
 * as call sequences (the PipLifecyclePolicyTest style); persist assertions
 * read back through the store.
 */
class DesktopWindowPlacementControllerTest {

    private val floating = Rectangle(100, 60, 1280, 800)
    private val workArea = Rectangle(0, 0, 1920, 1040)
    private val floatingGeometry =
        DesktopWindowGeometry(x = 100, y = 60, width = 1280, height = 800, maximized = false)

    private val tempDirs = mutableListOf<Path>()

    @AfterTest
    fun cleanup() {
        tempDirs.forEach { dir -> dir.toFile().deleteRecursively() }
    }

    private fun newStore(): DesktopWindowStateStore =
        DesktopWindowStateStore(
            Files.createTempDirectory("jellyplay-placement-test").also { tempDirs.add(it) }
                .resolve("window-state.properties"),
        )

    /**
     * Seam fake: programmable work area, every controller-driven bounds
     * write recorded (Rectangle is mutable — copies, so history cannot be
     * rewritten under the assertions).
     */
    private class FakeHost(
        initialBounds: Rectangle,
        private val workArea: Rectangle?,
    ) : DesktopWindowPlacementHost {
        private var currentBounds = Rectangle(initialBounds)
        val writes = mutableListOf<Rectangle>()
        override var bounds: Rectangle
            get() = currentBounds
            set(value) {
                currentBounds = Rectangle(value)
                writes += Rectangle(value)
            }
        override fun workAreaOrNull(): Rectangle? = workArea
    }

    private fun newController(
        host: FakeHost,
        store: DesktopWindowStateStore = newStore(),
        savedMaximized: Boolean = false,
    ): DesktopWindowPlacementController =
        DesktopWindowPlacementController(
            host = host,
            stateStore = store,
            savedMaximized = savedMaximized,
        )

    // ── Rules 1+2: the manual maximize toggle ───────────────────────────────

    @Test
    fun `maximize fills the work area and reports maximized`() {
        val host = FakeHost(floating, workArea)
        val controller = newController(host)
        controller.toggleMaximize()
        assertEquals(workArea, host.bounds)
        assertTrue(controller.isMaximized, "title bar must show the maximize as active")
    }

    @Test
    fun `restore returns the pre-maximize bounds and re-arms the toggle`() {
        val host = FakeHost(floating, workArea)
        val controller = newController(host)
        controller.toggleMaximize()
        controller.toggleMaximize()
        assertEquals(floating, host.bounds)
        assertFalse(controller.isMaximized)
        // A third toggle is a FRESH maximize cycle: re-snapshots the current
        // (restored) bounds and fills the work area again.
        controller.toggleMaximize()
        assertEquals(workArea, host.bounds)
        assertTrue(controller.isMaximized)
    }

    @Test
    fun `maximize without a work area still arms the swap`() {
        // graphicsConfiguration unavailable (workAreaOrNull's null branch):
        // bounds stay put but the toggle state advances — the title-bar
        // icon must not desync from a half-applied maximize.
        val host = FakeHost(floating, null)
        val controller = newController(host)
        controller.toggleMaximize()
        assertEquals(floating, host.bounds)
        assertTrue(controller.isMaximized)
        controller.toggleMaximize()
        assertEquals(floating, host.bounds)
        assertFalse(controller.isMaximized)
    }

    // ── Rule 3: the windowOpened replay ─────────────────────────────────────

    @Test
    fun `windowOpened replays the saved maximize exactly once`() {
        val host = FakeHost(floating, workArea)
        val controller = newController(host, savedMaximized = true)
        controller.onWindowOpened()
        assertEquals(workArea, host.bounds)
        assertTrue(controller.isMaximized)
        // windowOpened fires once per AWT window, but the choreography must
        // stay inert to a second delivery (the shell attaches its listener
        // unconditionally): no further bounds write, no re-snapshot.
        val writesAfterFirst = host.writes.toList()
        controller.onWindowOpened()
        assertEquals(writesAfterFirst, host.writes, "the replay must be once-only")
        assertEquals(workArea, host.bounds)
        // And the restore target is the ORIGINAL floating bounds, not the fill.
        controller.toggleMaximize()
        assertEquals(floating, host.bounds)
    }

    @Test
    fun `windowOpened without a saved maximize does nothing`() {
        val host = FakeHost(floating, workArea)
        val controller = newController(host, savedMaximized = false)
        controller.onWindowOpened()
        assertTrue(host.writes.isEmpty(), "no saved maximize means no replay")
        assertFalse(controller.isMaximized)
    }

    @Test
    fun `windowOpened after a manual maximize does not re-snapshot`() {
        // The manual toggle already ran before the AWT windowOpened
        // delivery: the replay must not overwrite the user's restore bounds
        // with the work-area fill.
        val host = FakeHost(floating, workArea)
        val controller = newController(host, savedMaximized = true)
        controller.toggleMaximize()
        controller.onWindowOpened()
        controller.toggleMaximize()
        assertEquals(floating, host.bounds)
    }

    // ── Rules 4+5: the persist-on-dispose decision ──────────────────────────

    @Test
    fun `dispose of a floating window persists its current bounds`() {
        val host = FakeHost(floating, workArea)
        val store = newStore()
        newController(host, store).persistOnDispose(isFullscreen = false)
        assertEquals(floatingGeometry, store.load())
    }

    @Test
    fun `dispose in fullscreen skips the write`() {
        // The AWT bounds while fullscreen are the screen fill, not anything
        // the user positioned — the previous session's file must win.
        val host = FakeHost(floating, workArea)
        val store = newStore()
        newController(host, store).persistOnDispose(isFullscreen = true)
        assertNull(store.load())
    }

    @Test
    fun `dispose in fullscreen skips the write even while maximized`() {
        val host = FakeHost(floating, workArea)
        val store = newStore()
        val controller = newController(host, store)
        controller.toggleMaximize()
        controller.persistOnDispose(isFullscreen = true)
        assertNull(store.load())
    }

    @Test
    fun `dispose while maximized persists restore bounds, never the fill`() {
        val host = FakeHost(floating, workArea)
        val store = newStore()
        val controller = newController(host, store)
        controller.toggleMaximize()
        assertEquals(workArea, host.bounds) // the fill is the live bounds — and loses
        controller.persistOnDispose(isFullscreen = false)
        assertEquals(
            DesktopWindowGeometry(x = 100, y = 60, width = 1280, height = 800, maximized = true),
            store.load(),
        )
    }

    @Test
    fun `dispose with degenerate empty bounds skips the write`() {
        // A torn-down/headless frame can read 0x0 — that is not geometry
        // anyone positioned; keep the previous session's file.
        val host = FakeHost(Rectangle(0, 0, 0, 0), workArea)
        val store = newStore()
        newController(host, store).persistOnDispose(isFullscreen = false)
        assertNull(store.load())
    }

    // ── The full session dance ──────────────────────────────────────────────

    @Test
    fun `session dance — replay, restore, persist floating again`() {
        // The cross-session loop Main.kt drives: last session ended
        // maximized → replay on windowOpened → user restores → this session
        // ends floating → persisted WITHOUT the maximize flag (so the NEXT
        // session does not replay).
        val host = FakeHost(floating, workArea)
        val store = newStore()
        val controller = newController(host, store, savedMaximized = true)
        controller.onWindowOpened()
        assertTrue(controller.isMaximized)
        controller.toggleMaximize()
        assertFalse(controller.isMaximized)
        controller.persistOnDispose(isFullscreen = false)
        assertEquals(floatingGeometry, store.load())
    }
}
