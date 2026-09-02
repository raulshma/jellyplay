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
 *
 * @param engine the live session engine, or null while the session is still
 *   creating one. The null case is the wave-14B pre-engine surface mount: mpv
 *   captures the embed target HWND at engine-construction time, so the desktop
 *   SwingPanel must be realized BEFORE the engine factory's bounded wait for
 *   the handle — with the old screen-side `engine != null` guard the two
 *   waited on each other (the surface only composed once an engine existed,
 *   and the factory only created an engine once a surface published) and
 *   every session fell through to the software-render surface. The Android
 *   actual renders nothing while null — exactly what the former guard did.
 *   Desktop keeps the same remembered Canvas across the null → engine
 *   transition (the seam call site is unconditional, so positional
 *   memoization holds the instance), which is load-bearing: mpv embeds into
 *   THAT canvas's HWND, and swapping the canvas after engine creation would
 *   destroy the embed target under a playing session.
 */
@Composable
internal expect fun EngineVideoSurface(
    engine: MediaEngine?,
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
 * PixelCopy path via ScreenshotSaver; desktop captures through the ENGINE
 * (mpv's `screenshot-to-file` — the embedded child window has no read-back),
 * so its actual downcasts [engine] to its own capture interface and ignores
 * [surfaceView] entirely, which also covers the software-render surface that
 * publishes no platform surface object (wave 17B).
 *
 * @param surfaceView the platform surface the engine renders into (Android
 *   PixelCopy path; unused on desktop).
 * @param engine the live session engine, or null while none exists. Android
 *   ignores it; the desktop actual requires a capture-capable engine.
 * @param onMessage the user-facing outcome message (saved path or failure).
 */
internal expect fun requestVideoFrameCapture(
    surfaceView: Any?,
    engine: MediaEngine?,
    titleHint: String,
    onMessage: (message: String) -> Unit,
)
