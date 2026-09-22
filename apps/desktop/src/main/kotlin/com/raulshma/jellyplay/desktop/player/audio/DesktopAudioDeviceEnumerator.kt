package com.raulshma.jellyplay.desktop.player.audio

import com.raulshma.jellyplay.core.model.MpvAudioDevice
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib
import com.raulshma.jellyplay.feature.settings.AudioDeviceEnumerator
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop [AudioDeviceEnumerator]: answers the settings screen's device
 * picker from mpv's own `audio-device-list` property.
 *
 * The list is read from a THROWAWAY idle mpv context — `idle=yes` with no
 * file loaded spins up the demuxer/AO probing without a video output (and
 * without touching the playback engines' contexts, which are per-session
 * and may not exist while the user is in settings). The context is created,
 * read, and `mpv_terminate_destroy`ed within one call; the whole thing runs
 * off the UI dispatcher (context creation is a native-library load away on
 * cold start).
 *
 * Degrades to an empty list when libmpv is missing (the machine without
 * libmpv can't play anything anyway — the factory's engine construction
 * failing is the louder failure surface) or the property read fails; the
 * picker then shows only the synthesized `auto` entry, which is a valid
 * mpv device name on every build.
 *
 * Pure parsing lives in [parse] so the node-dump shape is unit-testable
 * without a live mpv (the fixture test pattern of the chain builders).
 */
class DesktopAudioDeviceEnumerator(
    /** Dispatchers seam (tests inject an immediate context). */
    private val ioContext: CoroutineContext = Dispatchers.IO,
) : AudioDeviceEnumerator {

    override suspend fun enumerateAudioDevices(): List<MpvAudioDevice> = withContext(ioContext) {
        try {
            val context = MpvLib.mpv.mpv_create() ?: return@withContext emptyList()
            try {
                // `config=no`: never let a user mpv.conf leak into the
                // enumeration; `idle=yes`: the empty playlist keeps the core
                // alive without a file; no `vo`: nothing to render.
                MpvLib.mpv.mpv_set_option_string(context, "config", "no")
                MpvLib.mpv.mpv_set_option_string(context, "idle", "yes")
                if (MpvLib.mpv.mpv_initialize(context) < 0) {
                    return@withContext emptyList()
                }
                parse(MpvLib.readNode(context, AUDIO_DEVICE_LIST) as? List<*>)
            } finally {
                MpvLib.mpv.mpv_terminate_destroy(context)
            }
        } catch (_: Throwable) {
            // libmpv missing/unloadable — same degrade-to-empty contract as
            // the engine factory; the picker falls back to the auto entry.
            emptyList()
        }
    }

    companion object {

        private const val AUDIO_DEVICE_LIST = "audio-device-list"

        /**
         * The synthesized first entry: mpv's `auto` device with a friendly
         * description. Always present, even when the node read fails, so the
         * picker's "no explicit device" row exists unconditionally.
         */
        val AUTO_ENTRY = MpvAudioDevice(
            name = "auto",
            description = "Automatically pick the system default output device",
        )

        /**
         * Maps a raw `audio-device-list` node (the [MpvLib.readNode] plain
         * shape: a list of maps with `name`/`description` string entries) to
         * devices, `auto` first. Entries missing either field are skipped;
         * a `null`/malformed node yields just the auto entry.
         */
        fun parse(deviceListNode: List<*>?): List<MpvAudioDevice> = buildList {
            add(AUTO_ENTRY)
            if (deviceListNode == null) return@buildList
            for (entry in deviceListNode) {
                val map = entry as? Map<*, *> ?: continue
                val name = (map["name"] as? String)?.takeIf { it.isNotBlank() } ?: continue
                // mpv duplicates `auto` as the first real entry of the list —
                // drop the copy; our synthesized row already covers it.
                if (name == AUTO_ENTRY.name) continue
                add(
                    MpvAudioDevice(
                        name = name,
                        description = (map["description"] as? String).orEmpty(),
                    ),
                )
            }
        }
    }
}
