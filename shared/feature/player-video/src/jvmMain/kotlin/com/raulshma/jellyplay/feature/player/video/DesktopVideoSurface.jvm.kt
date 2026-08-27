package com.raulshma.jellyplay.feature.player.video

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.graphicsLayer
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.sun.jna.Native
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Color
import java.awt.event.HierarchyEvent
import javax.swing.JPanel

/**
 * Desktop actual of the engine video-surface seam (wave 9A): a SwingPanel
 * hosting a heavyweight [Canvas] whose HWND mpv embeds into via `wid`.
 *
 * Wave 12B dispatch: engines implementing [SoftwareFrameVideoSurface] (mpv
 * render-API software renderer) render through DesktopSoftwareVideoPane — a
 * plain Compose Canvas — when the machine's sw support smoke-passed; see that
 * pane for the pixel pipeline. The SwingPanel path below is unchanged and
 * remains the primary host on Windows.
 *
 * Sizing: SwingPanel lays its child out to the composable's bounds and the
 * Canvas fills the wrapping JPanel (BorderLayout/CENTER), so window resizes
 * reach the embedded child window through AWT layout.
 *
 * Known desktop v1 limitation (heavyweight z-order): the native child window
 * composites ABOVE all Compose content, so the overlay controls render under
 * the video while it plays. Keyboard paths (the non-TV shortcut layer, Esc
 * back via the shell) are unaffected; dialogs are separate OS windows.
 *
 * [engine] is unused here by design: on desktop the wiring is inverted — the
 * surface publishes its HWND through [DesktopVideoSurfaceBridge] and the
 * engine factory (see apps/desktop) builds the per-session MpvDesktopEngine
 * with that handle. [onBoundsChanged] has no desktop consumer (no PiP), so
 * the PiP source-rect hint is never requested.
 *
 * Lifecycle (compose.desktop 1.11's SwingPanel has no onRelease hook): the
 * canvas + handle provider are remembered per composition, [register]/
 * [clear] bracket it in a [DisposableEffect], and a HierarchyListener clears
 * the provider too when AWT detaches the canvas without composition disposal
 * (window close). The bridge's identity guard makes both paths idempotent.
 *
 * Non-Windows JVMs register no handle — the desktop engine factory then
 * builds its engine without `wid` and the surface degrades to an empty
 * panel (audio-only path), mirroring the Android non-View-surface degrade.
 */
@Composable
internal actual fun EngineVideoSurface(
    engine: MediaEngine,
    effectiveZoom: Float,
    onSurfaceCreated: (Any?) -> Unit,
    onSurfaceUpdate: () -> Unit,
    onBoundsChanged: (Int, Int, Int, Int) -> Unit,
) {
    // Wave 12B: engines whose video output is CPU frame buffers (mpv render-API
    // software backend) host through the Compose Canvas pane instead — no child
    // window. Selected ONLY when the sw surface actually smoke-passed on this
    // machine's libmpv; every other engine keeps the exact SwingPanel/HWND
    // path below, byte-for-byte.
    val softwareSurface = engine as? SoftwareFrameVideoSurface
    if (softwareSurface != null && DesktopVideoSurfaceBridge.isSoftwareVideoSurfaceSupported) {
        DesktopSoftwareVideoPane(
            surface = softwareSurface,
            playingFlow = engine.isPlaying,
            effectiveZoom = effectiveZoom,
            onSurfaceCreated = onSurfaceCreated,
            onSurfaceUpdate = onSurfaceUpdate,
        )
        return
    }
    val surface = remember {
        val canvas = Canvas()
        val handleProvider: () -> Long? = {
            // The Canvas must be displayable (added to the realized window)
            // before it has a native peer; until then there is no HWND.
            if (DesktopVideoSurfaceBridge.isWindowsVideoSurfaceSupported &&
                canvas.isDisplayable
            ) {
                try {
                    Native.getComponentPointer(canvas)
                        ?.let { ptr -> com.sun.jna.Pointer.nativeValue(ptr) }
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            }
        }
        canvas.addHierarchyListener { event ->
            if ((event.changeFlags and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong()) != 0L &&
                !canvas.isDisplayable
            ) {
                DesktopVideoSurfaceBridge.clear(handleProvider)
            }
        }
        VideoSurface(canvas, handleProvider)
    }

    DisposableEffect(surface) {
        DesktopVideoSurfaceBridge.register(surface.handleProvider)
        onDispose {
            DesktopVideoSurfaceBridge.clear(surface.handleProvider)
            onSurfaceCreated(null)
        }
    }

    SwingPanel(
        background = androidx.compose.ui.graphics.Color.Black,
        factory = {
            onSurfaceCreated(surface.canvas)
            JPanel().apply {
                layout = BorderLayout()
                background = Color.BLACK
                add(surface.canvas, BorderLayout.CENTER)
            }
        },
        update = { _ ->
            onSurfaceUpdate()
        },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = effectiveZoom
                scaleY = effectiveZoom
            },
    )
}

/** The composed surface pair: heavyweight canvas + its HWND read-back lambda. */
private class VideoSurface(
    val canvas: Canvas,
    val handleProvider: () -> Long?,
)
