package com.raulshma.jellyplay.desktop

import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.nio.file.Files
import java.nio.file.Path as NioPath
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Floating (restore) window geometry as persisted between sessions. All
 * values are AWT pixels in the same coordinate space the screens list
 * uses — NOT dp; [Main.kt] converts at the rememberWindowState call site
 * through the toolkit's screen resolution (the same density Compose
 * Desktop itself derives, so restored px round-trip exactly).
 *
 * [maximized] records that the last session ended with the manual
 * maximize active (see maximizedRestoreBounds in Main.kt — an
 * undecorated frame can't use AWT's MAXIMIZED_BOTH, so "maximized" is
 * app-managed bounds-swapping). When true, x/y/width/height are the
 * PRE-maximize floating bounds, never the work-area fill.
 */
internal data class DesktopWindowGeometry(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val maximized: Boolean = false,
) {
    fun toRectangle(): Rectangle = Rectangle(x, y, width, height)

    companion object {
        fun fromRectangle(rectangle: Rectangle, maximized: Boolean): DesktopWindowGeometry =
            DesktopWindowGeometry(
                x = rectangle.x,
                y = rectangle.y,
                width = rectangle.width,
                height = rectangle.height,
                maximized = maximized,
            )
    }
}

/**
 * Remembers the main window's floating bounds across sessions as a
 * properties file under the config dir (`window-state.properties`).
 *
 * Sanitizing happens at LOAD, not save: the classic failure this guards
 * is "session ended on a monitor that no longer exists" (docked laptop,
 * RDP fallback) — saved bounds are always real on-screen values from the
 * session that wrote them, but the NEXT session's screen list may not
 * contain them. [sanitize] runs against the live screen list before the
 * geometry is trusted; when it rejects, Main.kt falls back to a centered
 * default window.
 *
 * Writes are atomic (temp file + move) so a crash mid-write can never
 * leave a truncated properties file behind — load() would just see the
 * previous session's state instead of nothing.
 */
internal class DesktopWindowStateStore(private val file: NioPath) {

    fun load(): DesktopWindowGeometry? {
        if (!Files.isRegularFile(file)) return null
        val properties = Properties()
        runCatching {
            Files.newInputStream(file).use { properties.load(it) }
        }.getOrElse { return null }
        val x = properties.getProperty(KEY_X)?.toIntOrNull() ?: return null
        val y = properties.getProperty(KEY_Y)?.toIntOrNull() ?: return null
        val width = properties.getProperty(KEY_WIDTH)?.toIntOrNull() ?: return null
        val height = properties.getProperty(KEY_HEIGHT)?.toIntOrNull() ?: return null
        if (width <= 0 || height <= 0) return null
        return DesktopWindowGeometry(
            x = x,
            y = y,
            width = width,
            height = height,
            maximized = properties.getProperty(KEY_MAXIMIZED)?.toBooleanStrictOrNull() ?: false,
        )
    }

    fun save(geometry: DesktopWindowGeometry) {
        val properties = Properties().apply {
            setProperty(KEY_X, geometry.x.toString())
            setProperty(KEY_Y, geometry.y.toString())
            setProperty(KEY_WIDTH, geometry.width.toString())
            setProperty(KEY_HEIGHT, geometry.height.toString())
            setProperty(KEY_MAXIMIZED, geometry.maximized.toString())
        }
        runCatching {
            Files.createDirectories(file.parent)
            val tempFile = Files.createTempFile(file.parent, file.fileName.toString(), ".tmp")
            Files.newOutputStream(tempFile).use { properties.store(it, null) }
            try {
                Files.move(
                    tempFile,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                // Same-volume temp file: plain replace is still crash-safe
                // (worst case a stale previous session's file survives).
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    companion object {
        private const val KEY_X = "window.x"
        private const val KEY_Y = "window.y"
        private const val KEY_WIDTH = "window.width"
        private const val KEY_HEIGHT = "window.height"
        private const val KEY_MAXIMIZED = "window.maximized"

        /**
         * Below this there is no sane UI to render into (the window would be
         * a sliver after a botched drag to a screen edge).
         */
        internal const val MIN_WIDTH_PX = 400
        internal const val MIN_HEIGHT_PX = 300

        /**
         * How much of the window must stay on a screen for the saved
         * position to count as "findable" — enough room to grab the custom
         * title bar and drag it back. Fully-offscreen and nearly-offscreen
         * geometries are rejected rather than clamped, because clamping a
         * monitor-sized window onto a smaller monitor would silently change
         * what the user remembered.
         */
        internal const val MIN_VISIBLE_PX = 120

        /**
         * Bounds of every attached screen. Empty on headless runs (tests,
         * perf harness) — callers treat that as "no saved geometry is
         * trustworthy" and fall back to the centered default.
         */
        fun availableScreens(): List<Rectangle> =
            runCatching {
                GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
                    .map { it.defaultConfiguration.bounds }
            }.getOrDefault(emptyList())

        /**
         * Validates [raw] against [screens] and returns geometry safe to
         * restore, or null when the fallback (centered default) should be
         * used instead. Pure — unit-tested without a display:
         *  - size clamped up to [MIN_WIDTH_PX]/[MIN_HEIGHT_PX]
         *  - the screen with the largest overlap is the "host"; a window
         *    larger than its host is clamped to the host's size
         *  - at least [MIN_VISIBLE_PX] of width and height must overlap the
         *    host, else null (position unreachable)
         */
        fun sanitize(raw: DesktopWindowGeometry, screens: List<Rectangle>): DesktopWindowGeometry? {
            if (screens.isEmpty()) return null
            val width = raw.width.coerceAtLeast(MIN_WIDTH_PX)
            val height = raw.height.coerceAtLeast(MIN_HEIGHT_PX)
            val candidate = Rectangle(raw.x, raw.y, width, height)
            val host = screens.maxByOrNull { screen ->
                candidate.intersection(screen).let { it.width.coerceAtLeast(0) * it.height.coerceAtLeast(0) }
            } ?: return null
            val overlap = candidate.intersection(host)
            if (overlap.width < MIN_VISIBLE_PX || overlap.height < MIN_VISIBLE_PX) return null
            return DesktopWindowGeometry(
                x = raw.x,
                y = raw.y,
                width = width.coerceAtMost(host.width),
                height = height.coerceAtMost(host.height),
                maximized = raw.maximized,
            )
        }
    }
}
