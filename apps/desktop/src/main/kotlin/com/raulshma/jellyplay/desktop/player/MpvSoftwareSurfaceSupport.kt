package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.desktop.player.mpv.MpvLib
import com.raulshma.jellyplay.desktop.player.mpv.MpvLibRender
import com.sun.jna.ptr.PointerByReference

/**
 * One-shot, lazy capability probe for the mpv software-render surface path
 * (wave 12B): "does libmpv load AND can an offscreen `sw` render context be
 * created on this machine's dll?"
 *
 * Why a real smoke test instead of an os.name check: software rendering is
 * platform-independent in principle, but a stripped/old libmpv build may not
 * contain the sw backend (create fails with MPV_ERROR_NOT_IMPLEMENTED), and
 * the Route.VideoPlayer guard must not offer video on machines where it would
 * fail at playback. The probe runs the exact operation the real session runs:
 * create core -> initialize (with vo=libmpv) -> create sw context -> free.
 *
 * Cost model: first access pays one throwaway mpv core create/init/free plus a
 * dll map if libmpv wasn't loaded yet — tens to low hundreds of ms, once per
 * process. DesktopAppRoot touches this exactly twice (nav entry registration +
 * dead-end predicate), both during composition; the result is cached here so
 * subsequent reads are free. Never throws: any failure degrades to `false`,
 * which restores the pre-12B dead-end behavior instead of crashing boot.
 */
object MpvSoftwareSurfaceSupport {

    val isSupported: Boolean by lazy {
        try {
            probe()
        } catch (_: Throwable) {
            false   // no libmpv / load-time JNI failure / anything else: no sw surface
        }
    }

    private fun probe(): Boolean {
        val api = MpvLib.mpv
        val core = api.mpv_create() ?: return false
        try {
            // Same base options the real engine sets before initialize — the
            // probe must exercise the configuration playback will actually use.
            MpvLib.mpv.mpv_set_option_string(core, "config", "no")
            MpvLib.mpv.mpv_set_option_string(core, "idle", "yes")
            MpvLib.mpv.mpv_set_option_string(core, "vo", "libmpv")
            MpvLib.mpv.mpv_set_option_string(core, "ao", "null")
            if (api.mpv_initialize(core) < 0) return false

            val ctxOut = PointerByReference()
            val rc = MpvLibRender.render.mpv_render_context_create(
                ctxOut,
                core,
                MpvLibRender.apiTypeCreateParams(),
            )
            val created = rc >= 0 && ctxOut.value != null
            if (created) MpvLibRender.render.mpv_render_context_free(ctxOut.value)
            return created
        } finally {
            api.mpv_terminate_destroy(core)
        }
    }
}
