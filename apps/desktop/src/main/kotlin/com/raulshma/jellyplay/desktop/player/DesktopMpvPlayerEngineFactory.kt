package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.feature.player.video.DesktopVideoSurfaceBridge
import com.raulshma.jellyplay.feature.player.video.NoOpPlayerEngineFactory
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
import com.raulshma.jellyplay.desktop.player.EngineActivitySnapshot.Companion.SURFACE_HWND
import com.raulshma.jellyplay.desktop.player.EngineActivitySnapshot.Companion.SURFACE_NO_OP
import com.raulshma.jellyplay.desktop.player.EngineActivitySnapshot.Companion.SURFACE_SOFTWARE
import com.raulshma.jellyplay.desktop.player.EngineActivitySnapshot.Companion.SURFACE_WID_NULL
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
 * an audio-only session. The player route is single-instance, so there
 * is exactly one publishing surface at any time; engine teardown is owned by
 * PlayerSessionManager's release paths, not this factory.
 *
 * Surface selection (wave 12B): a realized HWND wins (Windows-primary path,
 * MpvDesktopEngine with `wid`). When no handle appears within the existing
 * budget — ALWAYS on non-Windows, exceptionally on Windows when AWT never
 * realizes the child window — and the software-surface prober smoke-passed,
 * the session gets [MpvSoftwareRenderEngine] instead: mpv renders CPU frames
 * the commonMain screen hosts through DesktopSoftwareVideoPane, no child
 * window needed. Only when neither surface story applies does the pre-12B
 * degrade survive (no-wid engine → empty surface/audio-only).
 *
 * Engine selection: mpv is the only real desktop backend, so media3/libVLC
 * picks ride it too (the decoder picker still functions as an engine-reload).
 * EXTERNAL keeps its Android semantics — playback happens out-of-band, which
 * the shared NoOpPlayerEngineFactory expresses — rather than pretending mpv
 * is someone else's window.
 *
 * Wave 13B: every created engine (and which branch created it) is reported to
 * the [EngineActivityRecorder] Koin single — pure observation feeding the
 * DesktopSessionHarness evidence; a null recorder (tests constructing the
 * factory bare) records nothing.
 *
 * @param recorder engine-activity recorder, or null to skip recording.
 */
class DesktopMpvPlayerEngineFactory(
    private val recorder: EngineActivityRecorder? = null,
) : PlayerEngineFactory {

    override suspend fun create(playerType: PlayerType): MediaEngine {
        val engine: MediaEngine
        val surface: String
        when (playerType) {
            PlayerType.EXTERNAL -> {
                engine = NoOpPlayerEngineFactory.create(playerType)
                surface = SURFACE_NO_OP
            }
            PlayerType.MPV,
            // mpv is the desktop stand-in until a media3/libVLC backend exists.
            PlayerType.EXO_PLAYER,
            PlayerType.LIBVLC,
            -> {
                val windowed = if (DesktopVideoSurfaceBridge.isWindowsVideoSurfaceSupported) {
                    awaitSurfaceHandle()
                } else {
                    null
                }
                when {
                    windowed != null -> {
                        engine = MpvDesktopEngine(extraOptions = emptyMap(), windowHandle = windowed)
                        surface = SURFACE_HWND
                    }

                    DesktopVideoSurfaceBridge.isSoftwareVideoSurfaceSupported -> {
                        // Wave 12B: no (or late) embedded surface — fall back to the
                        // mpv render-API software renderer. On non-Windows this is
                        // the PRIMARY path; on Windows it fires only when the HWND
                        // never materialized within HANDLE_WAIT_TIMEOUT_MS, which is
                        // worth a line of stderr because it means AWT did not
                        // realize the child window in time.
                        System.err.println(
                            "[JellyPlay] video session: no embedded HWND available — " +
                                "using mpv software-render surface",
                        )
                        engine = MpvSoftwareRenderEngine()
                        surface = SURFACE_SOFTWARE
                    }

                    else -> {
                        // Legacy degrade chain: engine without wid → empty-surface
                        // audio-only playback, exactly the pre-12B behavior.
                        engine = MpvDesktopEngine(extraOptions = emptyMap(), windowHandle = null)
                        surface = SURFACE_WID_NULL
                    }
                }
            }
        }
        recorder?.recordCreated(engine, surface)
        return engine
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
