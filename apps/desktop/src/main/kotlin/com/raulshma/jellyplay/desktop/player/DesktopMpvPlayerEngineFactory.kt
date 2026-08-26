package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.feature.player.video.DesktopVideoSurfaceBridge
import com.raulshma.jellyplay.feature.player.video.NoOpPlayerEngineFactory
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
import kotlinx.coroutines.delay

/**
 * Desktop [PlayerEngineFactory] (wave 9A): builds a per-session
 * [MpvDesktopEngine] bound to the composing video surface's HWND, mirroring
 * how androidMain's AndroidPlayerEngineFactory maps a PlayerType to the
 * Android engine stack.
 *
 * mpv's `wid` is ctor-time: the SwingPanel surface publishes its child-window
 * handle to [DesktopVideoSurfaceBridge] when it is realized, and the session
 * pipeline calls create() shortly after the screen composes — often before
 * AWT has attached the heavyweight Canvas. On Windows we therefore wait
 * (bounded, ~frames in practice) for the handle instead of silently starting
 * an audio-only session; non-Windows JVMs skip the wait and degrade through
 * the no-handle path (the empty-surface/audio-only route, same as the Android
 * non-View-surface fallback). The player route is single-instance, so there
 * is exactly one publishing surface at any time; engine teardown is owned by
 * PlayerSessionManager's release paths, not this factory.
 *
 * Engine selection: mpv is the only real desktop backend, so media3/libVLC
 * picks ride it too (the decoder picker still functions as an engine-reload).
 * EXTERNAL keeps its Android semantics — playback happens out-of-band, which
 * the shared NoOpPlayerEngineFactory expresses — rather than pretending mpv
 * is someone else's window.
 */
class DesktopMpvPlayerEngineFactory : PlayerEngineFactory {

    override suspend fun create(playerType: PlayerType): MediaEngine = when (playerType) {
        PlayerType.EXTERNAL -> NoOpPlayerEngineFactory.create(playerType)
        PlayerType.MPV,
        // mpv is the desktop stand-in until a media3/libVLC backend exists.
        PlayerType.EXO_PLAYER,
        PlayerType.LIBVLC,
        -> MpvDesktopEngine(
            extraOptions = emptyMap(),
            windowHandle = if (DesktopVideoSurfaceBridge.isWindowsVideoSurfaceSupported) {
                awaitSurfaceHandle()
            } else {
                null
            },
        )
    }

    /**
     * Polls the bridge for the realized surface handle. `delay` (not
     * `Thread.sleep`): the session pipeline calls create() from the
     * main-adjacent dispatcher that drives the UI, and a blocking wait there
     * would freeze the very event pump AWT needs to realize the child window;
     * delay also makes a cancelled session unwind instead of holding its
     * dispatcher hostage for the full timeout.
     */
    private suspend fun awaitSurfaceHandle(): Long? {
        val deadline = System.currentTimeMillis() + HANDLE_WAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            DesktopVideoSurfaceBridge.activeHandle()?.let { return it }
            delay(HANDLE_POLL_INTERVAL_MS)
        }
        return null
    }

    private companion object {
        /** Generous ceiling for AWT realization (~a few frames typically). */
        private const val HANDLE_WAIT_TIMEOUT_MS = 4_000L

        /** Poll cadence; one composited frame at 60 Hz ≈ 16 ms. */
        private const val HANDLE_POLL_INTERVAL_MS = 16L
    }
}
