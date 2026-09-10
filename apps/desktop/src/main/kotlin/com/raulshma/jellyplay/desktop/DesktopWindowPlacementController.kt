package com.raulshma.jellyplay.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window

/**
 * The undecorated window's placement choreography — the five rules that
 * used to live as scattered composable locals in Main.kt, extracted so the
 * dance has a test surface beside the [DesktopWindowStateStore] persistence
 * half it feeds (the store keeps load/sanitize/save; this controller owns
 * the WHEN and the WHAT of writes, plus the manual-maximize machine):
 *
 *  1. **Maximize fills the work area.** AWT's `MAXIMIZED_BOTH` is unusable
 *     on the undecorated frame (a WS_POPUP window is never zoomed natively
 *     — size applied, position ignored, restore broken), so "maximized" is
 *     app-managed bounds-swapping: [toggleMaximize] snapshots the current
 *     bounds and applies the monitor's work area.
 *  2. **Restore returns the snapshotted bounds.** The second
 *     [toggleMaximize] puts the pre-maximize floating bounds back.
 *  3. **windowOpened replays the saved maximize exactly once.** When the
 *     previous session ended maximized, `rememberWindowState` restores only
 *     the FLOATING bounds — [onWindowOpened] redoes the bounds swap once
 *     the AWT window exists (applying bounds mid-composition would fight
 *     the initial `pack()`); the replay arm discharges on the first call,
 *     and a manual maximize that already ran is likewise inert.
 *  4. **Persist skips fullscreen.** [persistOnDispose] writes nothing when
 *     the window tears down out of fullscreen — the AWT bounds then are the
 *     screen fill, not anything the user positioned, so the previous
 *     session's real geometry wins. (The shell reads its Compose
 *     WindowState placement and passes the flag; a degenerate empty bounds
 *     read is likewise not persisted.)
 *  5. **Restore bounds are preferred over fill bounds.** A dispose while
 *     maximized persists the snapshotted PRE-maximize bounds with
 *     `maximized = true` — never the work-area fill — so the next session
 *     replays rule 3 instead of restoring a "floating" window the size of
 *     the screen.
 *
 * The window reaches the controller only through
 * [DesktopWindowPlacementHost]: the Compose window satisfies it via
 * [AwtDesktopWindowPlacementHost], the fake satisfies it in tests (two
 * adapters justify the seam). Fullscreen arrives as a PARAMETER, not a
 * seam member — it lives in Compose's WindowState, which is shell wiring
 * rather than window geometry.
 *
 * [isMaximized] is snapshot state so DesktopTitleBar's toggle icon
 * re-composes exactly like the old inline `maximizedRestoreBounds` local
 * did. No declared deltas: every branch below is the inline original's,
 * verbatim (the shell now attaches its windowOpened listener
 * unconditionally where it used to skip registration for non-maximized
 * sessions — observable behavior identical, the controller's armed guard
 * makes those calls inert).
 */
internal class DesktopWindowPlacementController(
    private val host: DesktopWindowPlacementHost,
    private val stateStore: DesktopWindowStateStore,
    savedMaximized: Boolean,
) {

    /**
     * Snapshot of the floating bounds to return on restore; null = the
     * window is floating. Snapshot state so the title bar tracks it.
     */
    private var maximizedRestoreBounds by mutableStateOf<Rectangle?>(null)

    /** Whether the manual maximize (rule 1) is currently active. */
    val isMaximized: Boolean
        get() = maximizedRestoreBounds != null

    /** Rule 3's "exactly once": discharges on the first [onWindowOpened]. */
    private var replayArmed = savedMaximized

    /**
     * The title-bar maximize toggle: work area on (rule 1), snapshotted
     * bounds back off (rule 2). A missing work area still arms the swap
     * (bounds stay put) so the toggle state cannot desync from the button.
     */
    fun toggleMaximize() {
        val restoreBounds = maximizedRestoreBounds
        if (restoreBounds != null) {
            host.bounds = restoreBounds
            maximizedRestoreBounds = null
        } else {
            maximizedRestoreBounds = host.bounds
            host.workAreaOrNull()?.let { host.bounds = it }
        }
    }

    /**
     * The windowOpened choreography (rule 3). The shell may attach its
     * listener unconditionally — with no saved maximize (or after the
     * replay already ran) this is inert.
     */
    fun onWindowOpened() {
        if (!replayArmed) return
        replayArmed = false
        if (maximizedRestoreBounds == null) {
            maximizedRestoreBounds = host.bounds
            host.workAreaOrNull()?.let { host.bounds = it }
        }
    }

    /**
     * The persist-on-dispose decision (rules 4+5), into [stateStore]. This
     * covers every exit path (title-bar close, Ctrl+Q, tray Quit — all
     * funnel into exitApplication, which disposes the composition). A
     * crash mid-session loses the last position — accepted; a move/resize
     * listener would fire continuously during drags.
     */
    fun persistOnDispose(isFullscreen: Boolean) {
        if (isFullscreen) return
        val currentBounds = host.bounds
        if (currentBounds.isEmpty) return
        val restoreBounds = maximizedRestoreBounds
        stateStore.save(
            DesktopWindowGeometry.fromRectangle(
                rectangle = restoreBounds ?: currentBounds,
                maximized = restoreBounds != null,
            ),
        )
    }

    companion object {
        /**
         * AWT-pixels-per-dp for converting saved window geometry back into
         * the dp WindowState speaks. Compose Desktop derives its density
         * from the toolkit's screen resolution (96 = 100%); reading the same
         * value here makes restored pixel bounds round-trip exactly instead
         * of drifting per display scale. Headless JVMs report nonsense —
         * guard to identity.
         */
        fun pxToDpFactor(): Float =
            runCatching {
                Toolkit.getDefaultToolkit().screenResolution / 96f
            }.getOrDefault(1f)
                .takeIf { it > 0f && !it.isNaN() }
                ?: 1f
    }
}

/**
 * Minimal window-geometry seam under [DesktopWindowPlacementController] —
 * exactly the operations the placement rules need, so the choreography is
 * testable headless. All values are AWT pixels in the window's own
 * coordinate space.
 */
internal interface DesktopWindowPlacementHost {

    /** The window's current bounds. */
    var bounds: Rectangle

    /**
     * The current monitor's work area (screen minus taskbar), or null when
     * the graphics configuration is unavailable. Used for the manual
     * maximize of the undecorated window.
     */
    fun workAreaOrNull(): Rectangle?
}

/**
 * Production [DesktopWindowPlacementHost] over the Compose window (a plain
 * [java.awt.Window] at this layer) — the platform half of the seam.
 */
internal class AwtDesktopWindowPlacementHost(
    private val window: Window,
) : DesktopWindowPlacementHost {

    override var bounds: Rectangle
        get() = window.bounds
        set(value) {
            window.bounds = value
        }

    override fun workAreaOrNull(): Rectangle? {
        val gc = window.graphicsConfiguration ?: return null
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
        return Rectangle(
            gc.bounds.x + insets.left,
            gc.bounds.y + insets.top,
            gc.bounds.width - insets.left - insets.right,
            gc.bounds.height - insets.top - insets.bottom,
        )
    }
}
