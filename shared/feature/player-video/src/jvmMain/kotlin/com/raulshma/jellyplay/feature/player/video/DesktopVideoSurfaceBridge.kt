package com.raulshma.jellyplay.feature.player.video

/**
 * Desktop HWND bridge (wave 9A): the SwingPanel video surface (jvmMain actual
 * of [EngineVideoSurface]) publishes the active child-window handle provider
 * here, and the desktop [PlayerEngineFactory][com.raulshma.jellyplay.feature
 * .player.video.engine.PlayerEngineFactory] implementation reads it when the
 * session creates its `MpvDesktopEngine` — mpv's `wid` option is ctor-time,
 * so the child window must exist before the engine.
 *
 * One active provider at a time: the desktop player route is single-instance
 * (Route.VideoPlayer), and [clear] is identity-guarded so a disposing surface
 * cannot unregister a newer session's provider.
 */
object DesktopVideoSurfaceBridge {

    @Volatile
    private var handleProvider: (() -> Long?)? = null

    /** Whether this JVM can host an embedded native video surface. */
    val isWindowsVideoSurfaceSupported: Boolean =
        System.getProperty("os.name", "").startsWith("Windows")

    @Volatile
    private var softwareSurfaceProbe: (() -> Boolean)? = null

    /**
     * Whether this JVM can drive the mpv render-API SOFTWARE surface
     * (wave 12B): libmpv loads AND an offscreen "sw" render context smoke-pass.
     * The app layer registers the prober (apps/desktop's
     * MpvSoftwareSurfaceSupport — this module cannot see MpvLib); reads before
     * registration (and probe failures) degrade to false, restoring the
     * pre-12B dead-end behavior. Never throws: a throwing/crashing prober is
     * treated as unsupported, so boot cannot be crashed through this path.
     * Caching is the PROBER'S job (one native smoke test per process).
     */
    val isSoftwareVideoSurfaceSupported: Boolean
        get() = try {
            softwareSurfaceProbe?.invoke() ?: false
        } catch (_: Throwable) {
            false
        }

    /**
     * Install/remove the software-surface prober. Idempotent overwrites are
     * fine; call once during desktop app bootstrap ([DesktopAppRoot][com
     * .raulshma.jellyplay.desktop.DesktopAppRoot] composition, wave 12B).
     */
    fun registerSoftwareSurfaceProbe(probe: (() -> Boolean)?) {
        softwareSurfaceProbe = probe
    }

    /** Register the provider for the composing video surface (composition factory). */
    fun register(provider: () -> Long?) {
        handleProvider = provider
    }

    /** Unregister [provider] if it is still the active one (surface disposal). */
    fun clear(provider: () -> Long?) {
        if (handleProvider === provider) {
            handleProvider = null
        }
    }

    /** The active child-window handle, or null when no surface is ready. */
    fun activeHandle(): Long? = handleProvider?.invoke()
}
