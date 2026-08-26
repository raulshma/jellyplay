package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine

/**
 * Engine video-surface seam for the commonMain [VideoPlayerScreen] (wave 9A).
 *
 * Android actual: the screen's original `AndroidView` hosts, verbatim — the
 * engine's `AndroidSurfaceProvider.createSurfaceView` view (empty fallback
 * view for non-View-surface engines, the V2a degrade path) and the
 * NATIVE_PINNED subtitle `FrameLayout` the engine reparents its native
 * subtitle view into.
 *
 * Desktop actual: a SwingPanel-hosted heavyweight `java.awt.Canvas` whose
 * HWND is published to [com.raulshma.jellyplay.feature.player
 * .DesktopVideoSurfaceBridge] for the per-session `MpvDesktopEngine`
 * (`wid` is ctor-time, so the child window must exist before the engine).
 * Engines that cannot embed degrade to an empty surface, audio keeps playing
 * — the same degrade the Android fallback view expresses.
 */
@Composable
internal expect fun EngineVideoSurface(
    engine: MediaEngine,
    effectiveZoom: Float,
    /** Invoked with the platform surface (Android View / AWT component) once created. */
    onSurfaceCreated: (surface: Any?) -> Unit,
    /** Invoked on surface re-attach (Android update pass — subtitle style diff lives screen-side). */
    onSurfaceUpdate: () -> Unit,
    /** Window-space bounds of the surface (PiP source-rect hint). */
    onBoundsChanged: (left: Int, top: Int, right: Int, bottom: Int) -> Unit,
)

/**
 * Sibling host the engine reparents its native subtitle view into for
 * [com.raulshma.jellyplay.feature.player.video.engine.ZoomSafeSubtitleStrategy
 * .NATIVE_PINNED] engines. Android renders the FrameLayout host; desktop has
 * no NATIVE_PINNED engine (mpv is COMPOSE_CUE) and renders nothing.
 */
@Composable
internal expect fun NativePinnedSubtitleHost(engine: MediaEngine)

/**
 * Zoom-safe COMPOSE_CUE subtitle overlay: renders the live cue line while the
 * video is zoomed. Android delegates to the mpv overlay (AndroidFontProvider
 * typefaces); desktop renders nothing — pointer pinch-zoom never engages on
 * desktop, so the overlay is unreachable and mpv's native libass path keeps
 * full fidelity.
 */
@Composable
internal expect fun ZoomedSubtitleOverlayHost(
    cue: CharSequence?,
    style: SubtitleStyle,
    viewModel: VideoPlayerViewModel,
)

/**
 * Capture the current video frame (screenshot action). Android keeps the
 * PixelCopy path via ScreenshotSaver; desktop reports an honest failure —
 * there is no read-back of the mpv-embedded child window (capture would go
 * through mpv's own screenshot command, queued work), so this path stays
 * defensive even though the desktop engine mirrors the matrix's
 * `supportsScreenshot = true` and the controls show the action.
 *
 * @param surfaceView the platform surface the engine renders into.
 * @param onMessage the user-facing outcome message (saved path or failure).
 */
internal expect fun requestVideoFrameCapture(
    surfaceView: Any?,
    titleHint: String,
    onMessage: (message: String) -> Unit,
)
