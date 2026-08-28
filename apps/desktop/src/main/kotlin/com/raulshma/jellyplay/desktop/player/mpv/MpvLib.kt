package com.raulshma.jellyplay.desktop.player.mpv

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure

/**
 * JNA binding for the subset of libmpv's `client.h` API that
 * [com.raulshma.jellyplay.desktop.player.MpvDesktopEngine] uses (Phase V2).
 * Constant values were verified against the mpv-dev `include/mpv/client.h`
 * shipped with the libmpv used for development (mpv v0.41 era, client API 2.x).
 *
 * Loading: JNA resolves the library from `jna.library.path`, the JVM library
 * path, or the OS default locations, trying [CANDIDATE_NAMES] in order — the
 * dev-package dll is `libmpv-2.dll`, distro packages install `mpv-2.dll`
 * (Windows) / `libmpv.so` (Linux) / `libmpv.dylib` (macOS). The
 * `MPV_LIBRARY` env var overrides with an absolute path.
 */
object MpvLib {

    // ── mpv_format ──────────────────────────────────────────────────────────
    const val FORMAT_NONE = 0
    const val FORMAT_STRING = 1
    const val FORMAT_FLAG = 3
    const val FORMAT_INT64 = 4
    const val FORMAT_DOUBLE = 5
    const val FORMAT_NODE = 6

    /**
     * MPV_FORMAT_NODE_ARRAY / MPV_FORMAT_NODE_MAP: mpv normalizes a top-level
     * NODE read to these when the value IS an array/map (e.g. `track-list`
     * comes back as NODE_ARRAY), and nested array/map values use them too —
     * all three formats share the same `u.list` union member.
     */
    const val FORMAT_NODE_ARRAY = 7
    const val FORMAT_NODE_MAP = 8

    // ── mpv_event_id ────────────────────────────────────────────────────────
    const val EVENT_NONE = 0
    const val EVENT_SHUTDOWN = 1
    const val EVENT_LOG_MESSAGE = 2
    const val EVENT_START_FILE = 6
    const val EVENT_END_FILE = 7
    const val EVENT_FILE_LOADED = 8
    const val EVENT_IDLE = 11
    const val EVENT_SEEK = 20
    const val EVENT_PLAYBACK_RESTART = 21
    const val EVENT_PROPERTY_CHANGE = 22

    // ── mpv_end_file_reason (first field of mpv_event_end_file) ─────────────
    const val END_FILE_REASON_EOF = 0
    const val END_FILE_REASON_STOP = 1
    const val END_FILE_REASON_QUIT = 2
    const val END_FILE_REASON_ERROR = 3
    const val END_FILE_REASON_REDIRECT = 4

    // ── mpv_error (subset the engine maps to the EngineError taxonomy) ──────
    const val ERROR_LOADING_FAILED = -13
    const val ERROR_AO_INIT_FAILED = -14
    const val ERROR_VO_INIT_FAILED = -15
    const val ERROR_NOTHING_TO_PLAY = -16
    const val ERROR_UNKNOWN_FORMAT = -17
    const val ERROR_UNSUPPORTED = -18
    const val ERROR_NOT_IMPLEMENTED = -19

    private val CANDIDATE_NAMES = listOf("libmpv-2", "mpv-2", "mpv")

    /** Loaded lazily so a machine without libmpv only fails when playback starts. */
    val mpv: MpvC by lazy { load() }

    private fun load(): MpvC {
        val override = System.getenv("MPV_LIBRARY")
        if (!override.isNullOrBlank()) {
            return Native.load(override, MpvC::class.java)
        }
        var last: Throwable? = null
        for (name in CANDIDATE_NAMES) {
            try {
                return Native.load(name, MpvC::class.java)
            } catch (e: Throwable) {
                last = e
            }
        }
        throw IllegalStateException(
            "libmpv not found — install mpv/libmpv or set MPV_LIBRARY to the library path",
            last,
        )
    }

    @Suppress("FunctionName", "PropertyName")
    interface MpvC : Library {
        fun mpv_create(): Pointer?
        fun mpv_initialize(ctx: Pointer): Int
        fun mpv_terminate_destroy(ctx: Pointer)

        /** [args] is a null-terminated `const char**`; see [command]. */
        fun mpv_command(ctx: Pointer, args: Pointer): Int
        fun mpv_command_string(ctx: Pointer, command: String): Int

        fun mpv_set_option_string(ctx: Pointer, name: String, value: String): Int
        fun mpv_set_property_string(ctx: Pointer, name: String, value: String): Int
        fun mpv_set_property(ctx: Pointer, name: String, format: Int, data: Pointer): Int

        /** Returned string is malloc'd — copy then [mpv_free]. */
        fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
        fun mpv_get_property(ctx: Pointer, name: String, format: Int, data: Pointer): Int

        fun mpv_free_node_contents(node: Pointer)
        fun mpv_observe_property(ctx: Pointer, replyUserData: Long, name: String, format: Int): Int

        /** Blocks until an event or [mpv_wakeup]; returns mpv's internal event. */
        fun mpv_wait_event(ctx: Pointer, timeout: Double): MpvEvent?
        fun mpv_wakeup(ctx: Pointer)
        fun mpv_free(data: Pointer)
        fun mpv_error_string(code: Int): String
        fun mpv_client_api_version(): Long
    }

    // ── Struct mappings ─────────────────────────────────────────────────────

    /**
     * `mpv_event` — returned by mpv_wait_event as a pointer to mpv's internal
     * event storage, valid until the next wait call. Fields are copied out on
     * the event thread immediately.
     */
    @Structure.FieldOrder("event_id", "error", "reply_userdata", "data")
    class MpvEvent : Structure {
        constructor() : super()
        constructor(p: Pointer) : super(p)

        @JvmField var event_id: Int = 0
        @JvmField var error: Int = 0
        @JvmField var reply_userdata: Long = 0
        @JvmField var data: Pointer? = null
    }

    /** `mpv_event_property` — [data] points at the raw format-specific value. */
    @Structure.FieldOrder("name", "format", "data")
    class MpvEventProperty : Structure {
        constructor() : super()
        constructor(p: Pointer) : super(p)

        @JvmField var name: Pointer? = null
        @JvmField var format: Int = 0
        @JvmField var data: Pointer? = null
    }

    /** `mpv_event_end_file` — only [reason]/[error] are consumed (stable prefix). */
    @Structure.FieldOrder("reason", "error", "playlist_entry_id")
    class MpvEventEndFile : Structure {
        constructor() : super()
        constructor(p: Pointer) : super(p)

        @JvmField var reason: Int = 0
        @JvmField var error: Int = 0
        @JvmField var playlist_entry_id: Long = 0
    }

    // ── mpv_node / mpv_node_list x64 C-layout offsets (readNode's raw reads) ─
    // mpv_node { union u(8); int format; pad } and
    // mpv_node_list { int num; pad; mpv_node* values; char** keys }.
    private const val NODE_BYTE_SIZE = 16L
    private const val NODE_FORMAT_OFFSET = 8L
    private const val LIST_VALUES_OFFSET = 8L
    private const val LIST_KEYS_OFFSET = 16L

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Builds a null-terminated `const char**` for mpv_command. The backing
     * string Memory blocks are pinned in the returned holder's referable scope;
     * use strictly synchronously (command returns before the array is freed).
     */
    fun command(ctx: Pointer, vararg args: String): Boolean {
        // One contiguous block: pointer array + each C string, so nothing can
        // be GC'd mid-call.
        val enc = args.map { it.toByteArray() }
        val ptrArrayOffset = 0L
        val stringsBase = ((args.size + 1) * Native.POINTER_SIZE).toLong()
        var cursor = stringsBase
        val offsets = enc.map { bytes ->
            val at = cursor
            cursor += bytes.size + 1
            at
        }
        val mem = Memory(cursor)
        enc.forEachIndexed { i, bytes ->
            mem.write(offsets[i], bytes, 0, bytes.size)
            mem.setByte(offsets[i] + bytes.size, 0)
            mem.setPointer(ptrArrayOffset + i * Native.POINTER_SIZE, mem.share(offsets[i]))
        }
        mem.setPointer(ptrArrayOffset + args.size * Native.POINTER_SIZE, Pointer.NULL)
        return mpv.mpv_command(ctx, mem) >= 0
    }

    /** Reads a NODE property into a plain Kotlin value (String/Boolean/Long/Double/List/Map). */
    fun readNode(ctx: Pointer, name: String): Any? {
        // Wave 17B: raw memory + manual offsets, NOT the MpvNode Structure.
        // mpv writes the node tree in C layout and two things broke the old
        // Structure path: top-level arrays arrive as MPV_FORMAT_NODE_ARRAY
        // (format 7 — the old `when` matched only generic NODE), and JNA's
        // nested-union marshalling never successfully followed the
        // foreign-written memory anyway. Net effect: readNode returned null
        // for every list/map property and the engine's track listing was
        // empty since V2. Offsets are the x64 C layouts (constants below).
        val mem = Memory(NODE_BYTE_SIZE)
        val rc = mpv.mpv_get_property(ctx, name, FORMAT_NODE, mem)
        if (rc < 0) return null
        try {
            return readNodeAt(mem)
        } finally {
            mpv.mpv_free_node_contents(mem)
        }
    }

    /**
     * Decodes the `mpv_node` at [node] via raw reads: the `u` union at
     * offset 0, the `format` int at offset 8. mpv normalizes a top-level
     * list/map value to [FORMAT_NODE_ARRAY]/[FORMAT_NODE_MAP] (verified
     * live: `track-list` reads back format 7) — all three list-shaped
     * formats share the `u.list` union member.
     */
    private fun readNodeAt(node: Pointer): Any? {
        fun emptyFor(): Any = if (node.getInt(NODE_FORMAT_OFFSET) == FORMAT_NODE_MAP) {
            emptyMap<String, Any?>()
        } else {
            emptyList<Any?>()
        }
        val format = node.getInt(NODE_FORMAT_OFFSET)
        return when (format) {
            FORMAT_STRING -> node.getPointer(0)?.getString(0)
            FORMAT_FLAG -> node.getInt(0) != 0
            FORMAT_INT64 -> node.getLong(0)
            FORMAT_DOUBLE -> node.getDouble(0)
            FORMAT_NODE, FORMAT_NODE_ARRAY, FORMAT_NODE_MAP -> {
                val list = node.getPointer(0) ?: return emptyFor()
                val num = list.getInt(0)
                if (num <= 0) return emptyFor()
                val values = list.getPointer(LIST_VALUES_OFFSET) ?: return emptyFor()
                val keys = list.getPointer(LIST_KEYS_OFFSET)
                if (keys == null) {
                    (0 until num).map { readNodeAt(values.share(it * NODE_BYTE_SIZE)) }
                } else {
                    (0 until num).mapNotNull { i ->
                        val key = keys.getPointer((i * Native.POINTER_SIZE).toLong())?.getString(0)
                            ?: return@mapNotNull null
                        key to readNodeAt(values.share(i * NODE_BYTE_SIZE))
                    }.toMap()
                }
            }
            else -> null
        }
    }

    fun getPropertyString(ctx: Pointer, name: String): String? {
        val p = mpv.mpv_get_property_string(ctx, name) ?: return null
        try {
            return p.getString(0)
        } finally {
            mpv.mpv_free(p)
        }
    }

    fun setPropertyDouble(ctx: Pointer, name: String, value: Double): Boolean {
        val mem = Memory(8)
        mem.setDouble(0, value)
        return mpv.mpv_set_property(ctx, name, FORMAT_DOUBLE, mem) >= 0
    }

    fun setPropertyFlag(ctx: Pointer, name: String, value: Boolean): Boolean {
        val mem = Memory(4)
        mem.setInt(0, if (value) 1 else 0)
        return mpv.mpv_set_property(ctx, name, FORMAT_FLAG, mem) >= 0
    }

    fun setPropertyString(ctx: Pointer, name: String, value: String): Boolean =
        mpv.mpv_set_property_string(ctx, name, value) >= 0
}
