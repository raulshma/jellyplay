package com.raulshma.jellyplay.desktop.player.mpv

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.Union

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

    /**
     * `mpv_node_u` — which member is valid is decided by the node's format.
     * `list` is a raw pointer (mpv_node is recursive — a typed field would make
     * JNA's eager field validation instantiate the cycle forever).
     */
    class MpvNodeU : Union {
        constructor() : super()

        @JvmField var string: Pointer? = null
        @JvmField var flag: Int = 0
        @JvmField var int64: Long = 0
        @JvmField var doubleValue: Double = 0.0
        @JvmField var list: Pointer? = null
        @JvmField var byteArray: Pointer? = null
    }

    /** `mpv_node` — tagged union read via [readNodeValue]. */
    @Structure.FieldOrder("u", "format")
    class MpvNode : Structure {
        constructor() : super()
        constructor(p: Pointer) : super(p)

        @JvmField var u: MpvNodeU = MpvNodeU()
        @JvmField var format: Int = 0

        companion object {
            /** sizeof(mpv_node) — union(8) + format(4) + tail padding(4). */
            val BYTE_SIZE: Long = MpvNode().size().toLong()
        }
    }

    /** `mpv_node_list` — array (keys == null) or map (keys != null); pointers only. */
    @Structure.FieldOrder("num", "values", "keys")
    class MpvNodeList : Structure {
        constructor() : super()
        constructor(p: Pointer) : super(p)

        @JvmField var num: Int = 0
        @JvmField var values: Pointer? = null
        @JvmField var keys: Pointer? = null
    }

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
        val node = MpvNode()
        val rc = mpv.mpv_get_property(ctx, name, FORMAT_NODE, node.getPointer())
        if (rc < 0) return null
        try {
            return readNodeValue(node)
        } finally {
            mpv.mpv_free_node_contents(node.getPointer())
        }
    }

    /**
     * Decodes an already-marshalled mpv_node. The union's active member must be
     * selected before the struct can be read, and the discriminator (`format`)
     * sits behind the union — so read the field alone first via [Structure.readField].
     */
    private fun readNodeValue(node: MpvNode): Any? {
        val format = (node.readField("format") as? Int) ?: FORMAT_NONE
        return when (format) {
            FORMAT_STRING -> node.u.readMember("string")?.let { (it as Pointer).getString(0) }
            FORMAT_FLAG -> node.u.readMember("flag") != 0
            FORMAT_INT64 -> node.u.readMember("int64") as Long
            FORMAT_DOUBLE -> node.u.readMember("doubleValue") as Double
            FORMAT_NODE -> {
                val listPtr = node.u.readMember("list") as? Pointer ?: return null
                val list = MpvNodeList(listPtr).also { it.read() }
                val valuesPtr = list.values
                if (valuesPtr == null || list.num <= 0) {
                    if (list.keys != null) emptyMap<String, Any?>() else emptyList<Any?>()
                } else {
                    val nodes = (0 until list.num).map { i ->
                        MpvNode(valuesPtr.share(i * MpvNode.BYTE_SIZE)).also { it.read() }
                    }
                    val keysPointer = list.keys
                    if (keysPointer == null) {
                        nodes.map { readNodeValue(it) }
                    } else {
                        val keys = (0 until list.num).map { i ->
                            keysPointer.getPointer((i * Native.POINTER_SIZE).toLong())?.getString(0)
                        }
                        keys.mapIndexedNotNull { i, key ->
                            key?.let { it to (nodes.getOrNull(i)?.let(::readNodeValue)) }
                        }.toMap()
                    }
                }
            }
            else -> null
        }
    }

    /** Selects [member] on the union, reads it, and returns its value. */
    private fun Union.readMember(member: String): Any? {
        setType(member)
        read()
        return javaClass.getField(member).get(this).let { unwrapped ->
            // JNA boxes primitives in the reflected field read; Pointer fields
            // come back as-is.
            unwrapped
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
