package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
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
 * Desktop [PlayerEngineFactory]: builds a per-session
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
 * Surface selection (precedence inverted for the overlay fix): the
 * SOFTWARE renderer wins whenever the prober smoke-passed. The SwingPanel/HWND
 * embed renders into a heavyweight child window that composites ABOVE all
 * Compose content — the overlay controls paint under the video and mouse
 * clicks land on the native window, so the commonMain tap-to-toggle-controls
 * handler never fires. [MpvSoftwareRenderEngine] instead renders CPU frames
 * the commonMain screen hosts through DesktopSoftwareVideoPane INSIDE the
 * compose tree, where controls stack correctly and pointer input reaches the
 * normal gesture layer. Only when the probe fails does the embedded HWND path
 * take over (a realized handle within the existing wait budget → MpvDesktopEngine
 * with `wid`), and when neither surface story applies does the pre-12B degrade
 * survive (no-wid engine → empty surface/audio-only).
 *
 * Engine selection: mpv is the only real desktop backend, so media3/libVLC
 * picks ride it too (the decoder picker still functions as an engine-reload).
 * EXTERNAL keeps its Android semantics — playback happens out-of-band, which
 * the shared NoOpPlayerEngineFactory expresses — rather than pretending mpv
 * is someone else's window.
 *
 * Every created engine (and which branch created it) is reported to
 * the [EngineActivityRecorder] Koin single — pure observation feeding the
 * DesktopSessionHarness evidence; a null recorder (tests constructing the
 * factory bare) records nothing. Every DESKTOP-owned
 * engine additionally wires its release into the recorder, so the per-engine
 * observer jobs die with the engine instead of sampling a released mpv handle
 * 2×/s forever. The EXTERNAL branch's shared no-op engine has no desktop
 * release hook — its record keeps the documented always-observe behavior
 * (pure-state reads, sample-capped memory).
 *
 * @param recorder engine-activity recorder, or null to skip recording.
 */
class DesktopMpvPlayerEngineFactory(
    private val recorder: EngineActivityRecorder? = null,
    /**
     * The user's desktop HDR passthrough setting, read at engine
     * creation time — `vo`/`wid` are ctor-time, so a mid-session toggle
     * applies on next playback (the settings row documents this). `false` on
     * any read failure (the safe, tone-mapped default). Tests inject a stub.
     */
    private val hdrPassthroughProvider: suspend () -> Boolean = { false },
    /**
     * The extracted shader-pack directory, resolved per creation so a
     * first-run extraction that lands between sessions is picked up. `null`
     * before extraction (or on failure) — the mapper then omits the
     * `glsl-shaders` pair (no pack), never garbage paths.
     */
    private val shaderDirProvider: () -> String? = { null },
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
                val shaderDir = shaderDirProvider()
                val hdrPassthrough = runCatchingRethrowingCancellation { hdrPassthroughProvider() }.getOrDefault(false)
                if (hdrPassthrough) {
                    // HDR passthrough REQUIRES the wid/HWND-embed path —
                    // the software renderer composites CPU RGB bitmaps and cannot
                    // pass HDR through. The sw-first precedence is therefore
                    // inverted: prefer the embedded window with `vo=gpu-next` and
                    // `target-colorspace-hint=yes`; degrade to the historical
                    // chain (audio-only) only when no handle materializes.
                    val windowed = if (DesktopVideoSurfaceBridge.isWindowsVideoSurfaceSupported) {
                        awaitSurfaceHandle()
                    } else {
                        null
                    }
                    when {
                        windowed != null -> {
                            engine = MpvDesktopEngine(
                                extraOptions = mapOf("vo" to "gpu-next"),
                                windowHandle = windowed,
                                shaderDir = shaderDir,
                                targetColorspaceHint = true,
                            )
                            surface = SURFACE_HWND
                        }
                        else -> {
                            engine = degradeToWidNull(
                                warn = DesktopVideoSurfaceBridge.isWindowsVideoSurfaceSupported,
                                shaderDir = shaderDir,
                            )
                            surface = SURFACE_WID_NULL
                        }
                    }
                } else if (DesktopVideoSurfaceBridge.isSoftwareVideoSurfaceSupported) {
                    // Overlay fix: sw-first precedence — the software pane lives
                    // inside the compose tree (controls above video, clicks
                    // reach the gesture layer), the HWND embed does not. See
                    // the class KDoc; the probe is cached per process, so this
                    // check is free after the first read.
                    engine = MpvSoftwareRenderEngine(shaderDir = shaderDir)
                    surface = SURFACE_SOFTWARE
                } else if (DesktopVideoSurfaceBridge.isWindowsVideoSurfaceSupported) {
                    val windowed = awaitSurfaceHandle()
                    when {
                        windowed != null -> {
                            engine = MpvDesktopEngine(
                                extraOptions = emptyMap(),
                                windowHandle = windowed,
                                shaderDir = shaderDir,
                            )
                            surface = SURFACE_HWND
                        }

                        else -> {
                            engine = degradeToWidNull(warn = true, shaderDir = shaderDir)
                            surface = SURFACE_WID_NULL
                        }
                    }
                } else {
                    engine = degradeToWidNull(warn = false, shaderDir = shaderDir)
                    surface = SURFACE_WID_NULL
                }
            }
        }
        recorder?.recordCreated(engine, surface)
        // Hook the recorder's cancellation onto the engine's own
        // release() — the session manager (PlayerSessionManager) owns engine
        // teardown and calls release() on every dispose path, so riding the
        // engine is the one release signal that never misses. Assignment is
        // race-free: the engine is unpublished until create() returns, so no
        // releaser can run before the hook lands. Only the mpv engines carry
        // the hook (the shared no-op has none — see class KDoc).
        val rec = recorder
        if (rec != null && engine is MpvDesktopEngine) {
            engine.onReleased = { rec.onEngineReleased(engine) }
        }
        return engine
    }

    /**
     * Legacy degrade chain: engine without wid → empty-surface audio-only
     * playback, exactly the pre-12B behavior. On Windows this also means AWT
     * never realized the child window inside the budget — worth a line of
     * stderr ([warn]) because the session silently loses video otherwise.
     */
    private fun degradeToWidNull(warn: Boolean, shaderDir: String?): MpvDesktopEngine {
        if (warn) {
            System.err.println(
                "[JellyPlay] video session: no embedded HWND available and sw " +
                    "probe failed — degrading to audio-only",
            )
        }
        return MpvDesktopEngine(extraOptions = emptyMap(), windowHandle = null, shaderDir = shaderDir)
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
