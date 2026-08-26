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
