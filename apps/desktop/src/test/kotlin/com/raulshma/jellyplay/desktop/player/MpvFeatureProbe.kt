package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.desktop.player.mpv.MpvLib
import com.sun.jna.Pointer
import java.util.concurrent.ConcurrentHashMap

/**
 * Live-libmpv capability probes for the real-engine suites: the engine
 * targets the 0.41-era API the bindings were written against, and two of its
 * features need more than a loadable libmpv — `pitch` landed in mpv 0.39 and
 * loadfile's options slot moved behind `index` in 0.38, so a distro libmpv
 * 0.37 (Ubuntu 24.04) loads fine yet can't exercise either. CI never sees
 * this (no libmpv → the live suites skip at [isAvailable]); a dev box with
 * a stale libmpv must skip the SAME cases for the SAME reason — capability,
 * not version tables: each probe asks the loaded library itself.
 */
object MpvFeatureProbe {

    private val cache = ConcurrentHashMap<String, Boolean>()

    /** libmpv loads at all (the classic gate every live-mpv suite starts with). */
    fun isAvailable(): Boolean = probed("load") { true }

    /**
     * `loadfile <url> <flags> <index> <options>` — the shape [MpvDesktopEngine]
     * uses to carry a per-file `start`. < 0.38 rejects the shape outright
     * (`invalid parameter`, no load attempted), so command ACCEPTANCE is the
     * probe; a nonexistent path keeps it free of any real file I/O (the async
     * open failure lands on a core that is destroyed immediately after).
     */
    fun hasLoadfileOptionsSlot(): Boolean = probed("loadfileOptions") { core ->
        MpvLib.mpv.mpv_set_option_string(core, "vo", "null")
        MpvLib.mpv.mpv_set_option_string(core, "ao", "null")
        // Commands only run on a live core: an uninitialized one answers
        // every command_string with MPV_ERROR_UNINITIALIZED (measured on
        // libmpv 0.37 — a valid-shape loadfile returns -3 before
        // mpv_initialize, 0 after), so without the initialize this probe
        // would read "no options slot" on every libmpv and silently skip
        // the start-position contract everywhere. The pitch probe stays
        // pre-init on purpose: options exist to be set before initialize.
        MpvLib.mpv.mpv_initialize(core) >= 0 &&
            MpvLib.mpv.mpv_command_string(
                core,
                "loadfile /nonexistent/jellyplay-capability-probe replace 0 start=+1",
            ) >= 0
    }

    /** The `pitch` option (semitone-shift playback without touching speed) — mpv ≥ 0.39. */
    fun hasPitch(): Boolean = probed("pitch") { core ->
        MpvLib.mpv.mpv_set_option_string(core, "pitch", "1.0") >= 0
    }

    private inline fun probed(key: String, probe: (Pointer) -> Boolean): Boolean =
        cache.getOrPut(key) {
            val core = MpvLib.create() ?: return@getOrPut false
            try {
                MpvLib.mpv.mpv_set_option_string(core, "config", "no")
                MpvLib.mpv.mpv_set_option_string(core, "idle", "yes")
                probe(core)
            } finally {
                MpvLib.mpv.mpv_terminate_destroy(core)
            }
        }
}
