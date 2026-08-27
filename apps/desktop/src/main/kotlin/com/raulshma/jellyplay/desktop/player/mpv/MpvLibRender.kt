package com.raulshma.jellyplay.desktop.player.mpv

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference

/**
 * JNA binding for the subset of libmpv's `render.h` API that the software
 * renderer path uses (wave 12B). Every constant below was verified against
 * the AUTHORITATIVE dev header shipped with this checkout
 * (`tools/mpv/include/mpv/render.h`, mpv-dev package) — line-referenced in
 * docs/spikes/x-desktop-video-surface-story.md.
 *
 * What is deliberately NOT mapped (and why):
 *  - `mpv_render_context_set_update_callback` — the native callback fires on an
 *    mpv-owned thread and must not call back into libmpv; marshalling that into
 *    a JVM callback adds a thread-management hazard for zero benefit over our
 *    poll model. We POLL with a fixed cadence instead of subscribing (decision
 *    rationale in the spike doc).
 *  - `mpv_render_context_update` / MPV_RENDER_UPDATE_FRAME — only meaningful
 *    together with MPV_RENDER_PARAM_ADVANCED_CONTROL, which we do not set, and
 *    redundant under polling anyway ("This is optional if ADVANCED_CONTROL was
 *    not set" — render.h L642-643).
 *  - `mpv_render_context_report_swap` — informs libmpv's video-sync timing; it
 *    warns "if you use it inconsistently, expect bad playback" (render.h
 *    L715-716). A fixed-cadence poller cannot promise per-swap calls, so we
 *    never start using it at all.
 *  - `mpv_render_context_get_info` / NEXT_FRAME_INFO — no consumer yet.
 *
 * Loading mirrors [MpvLib] exactly: same candidate names, same MPV_LIBRARY
 * override. JNA resolves the second interface against the same dll the first
 * one loaded (OS loader refcounts the shared object), so both bindings stay in
 * sync across MpvLib's candidates without coupling the files.
 */
object MpvLibRender {

    // ── mpv_render_param_type (render.h L171-425) ──────────────────────────
    const val PARAM_INVALID = 0                 // L176: array terminator, always 0
    const val PARAM_API_TYPE = 1                // L192 (char*)
    const val PARAM_OPENGL_INIT_PARAMS = 2      // L198 (mpv_opengl_init_params*)
    const val PARAM_FLIP_Y = 4                  // L211 (int*)
    const val PARAM_DEPTH = 5                   // L219 (int*)
    const val PARAM_ICC_PROFILE = 6             // L226
    const val PARAM_AMBIENT_LIGHT = 7           // L233 (deprecated)
    const val PARAM_X11_DISPLAY = 8             // L240
    const val PARAM_WL_DISPLAY = 9              // L247
    const val PARAM_ADVANCED_CONTROL = 10       // L287 (int*)
    const val PARAM_NEXT_FRAME_INFO = 11        // L300
    const val PARAM_BLOCK_FOR_TARGET_TIME = 12  // L317 (int*)
    const val PARAM_SKIP_RENDERING = 13         // L333 (int*)
    const val PARAM_DRM_DISPLAY = 14            // L338 (deprecated)
    const val PARAM_DRM_DRAW_SURFACE_SIZE = 15  // L344
    const val PARAM_DRM_DISPLAY_V2 = 16         // L350
    const val PARAM_SW_SIZE = 17                // L360 (int[2] {w, h})
    const val PARAM_SW_FORMAT = 18              // L385 (char*)
    const val PARAM_SW_STRIDE = 19              // L406 (size_t*)
    const val PARAM_SW_POINTER = 20             // L424 (void*)

    /** Predefined API-type string for the software renderer (render.h L470). */
    const val API_TYPE_SW = "sw"

    /**
     * Target pixel format for the sw surface. "bgr0" is from render.h's
     * documented valid list (L367-374): 4 bytes/pixel, byte order increasing
     * by address B,G,R then one UNINITIALIZED byte. That memory layout is
     * exactly Skia's BGRA_8888 raster when the alpha type is OPAQUE (skia then
     * ignores the garbage 4th channel), which lets the compose surface consume
     * mpv output with no per-pixel conversion. Video is opaque anyway.
     */
    const val SW_FORMAT_BGRA = "bgr0"

    private val CANDIDATE_NAMES = listOf("libmpv-2", "mpv-2", "mpv")

    /** Loaded lazily; see [MpvLib.mpv] for the loading policy this mirrors. */
    val render: RenderC by lazy { load() }

    private fun load(): RenderC {
        val override = System.getenv("MPV_LIBRARY")
        if (!override.isNullOrBlank()) {
            return Native.load(override, RenderC::class.java)
        }
        var last: Throwable? = null
        for (name in CANDIDATE_NAMES) {
            try {
                return Native.load(name, RenderC::class.java)
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
    interface RenderC : Library {
        /**
         * render.h L578: `int mpv_render_context_create(mpv_render_context **res,
         * mpv_handle *mpv, mpv_render_param *params)` — [resOut] receives the
         * context on success; [params] is terminated by [PARAM_INVALID].
         */
        fun mpv_render_context_create(resOut: PointerByReference, mpv: Pointer, params: Array<MpvRenderParam>): Int

        /**
         * render.h L709: renders/pulls a frame into the target described by
         * [params]. Exercised per pull tick by [com
         .raulshma.jellyplay.desktop.player.MpvSoftwareRenderEngine.pullFrame].
         */
        fun mpv_render_context_render(ctx: Pointer, params: Array<MpvRenderParam>): Int

        /** render.h L591 (mapped for completeness/future use; currently unused). */
        fun mpv_render_context_set_parameter(ctx: Pointer, param: MpvRenderParam): Int

        /** render.h L661 — unmapped caller-side: we never set ADVANCED_CONTROL. */
        fun mpv_render_context_update(ctx: Pointer): Long

        /** render.h L733: NULL is allowed and does nothing. MUST run before the core is destroyed. */
        fun mpv_render_context_free(ctx: Pointer?)
    }

    /**
     * `mpv_render_param` (render.h L458-461): `{ int type; void* data; }` —
     * field order ABI-guaranteed. For params documented as `T*`, [data] points
     * at the value; for `char*` it points at the NUL-terminated string itself.
     */
    @Structure.FieldOrder("type", "data")
    class MpvRenderParam : Structure {
        constructor() : super()
        constructor(p: Pointer) : super(p)

        @JvmField var type: Int = 0
        @JvmField var data: Pointer? = null
    }

    // ── Param-array builders ────────────────────────────────────────────────
    //
    // All returned Memories are pinned by live references inside the returned
    // array for the duration of the synchronous native call; use immediately.

    /** `[{type=API_TYPE,data="sw"}, terminator]` for context creation. */
    fun apiTypeCreateParams(): Array<MpvRenderParam> {
        val params = newParamArray(2)
        params[0].type = PARAM_API_TYPE
        params[0].data = cString(API_TYPE_SW)
        return params
    }

    /**
     * SW render target params required by render.h L135-138: SIZE, FORMAT,
     * STRIDE, POINTER (+ terminator). Stride multiples of 64 are recommended
     * for the SIMD fast path (L395-398); callers own that sizing decision.
     *
     * INDIRECTION LEVELS — the load-bearing detail of this whole mapping
     * (verified against mpv's own sw backend, `video/out/libmpv_sw.c`, which
     * does `void *ptr = get_mpv_render_param(params, MPV_RENDER_PARAM_SW_POINTER,
     * NULL); wrap_img.planes[0] = ptr;`):
     *  - SW_SIZE/SW_STRIDE/BLOCK_FOR_TARGET_TIME: data = &value (int-array or size_t pointer)
     *  - SW_FORMAT/API_TYPE:                     data = the char* itself
     *  - SW_POINTER:                             data = the pixel buffer ITSELF
     *
     * Getting SW_POINTER wrong is catastrophic and SILENT-later: when `data`
     * holds the ADDRESS OF a pointer variable instead of the buffer, mpv
     * writes stride*height bytes into that 8-byte slot, smashing the native
     * heap — the JVM keeps running and then dies somewhere unrelated
     * (observed: EXCEPTION_ACCESS_VIOLATION inside jvm.dll during JUnit class
     * filtering / class defining, minutes-of-distance from the actual call).
     */
    fun swRenderParams(widthPx: Int, heightPx: Int, strideBytes: Long, target: Pointer): Array<MpvRenderParam> {
        val sizeMem = Memory(8).apply {
            setInt(0, widthPx)
            setInt(4, heightPx)
        }
        val strideMem = Memory(8).apply { setLong(0, strideBytes) }
        val params = newParamArray(5)
        params[0].type = PARAM_SW_SIZE
        params[0].data = sizeMem
        params[1].type = PARAM_SW_FORMAT
        params[1].data = cString(SW_FORMAT_BGRA)
        params[2].type = PARAM_SW_STRIDE
        params[2].data = strideMem
        // Single indirection: mpv reads param.data AS the pixel pointer.
        params[3].type = PARAM_SW_POINTER
        params[3].data = target
        // params[4]: {type=PARAM_INVALID(0), data=null} terminator.
        return params
    }

    fun errorString(code: Int): String = MpvLib.mpv.mpv_error_string(code)

    /**
     * Allocates [size] contiguous mpv_render_param elements. JNA demands the
     * array share ONE backing allocation ("Structure array elements must use
     * contiguous memory") — individually constructed structs would not.
     * Elements beyond those filled stay {0, null} (valid terminators).
     */
    private fun newParamArray(size: Int): Array<MpvRenderParam> =
        @Suppress("UNCHECKED_CAST")
        (MpvRenderParam().toArray(size) as Array<MpvRenderParam>)

    private fun cString(s: String): Memory {
        val bytes = s.toByteArray(Charsets.US_ASCII) + 0
        val mem = Memory(bytes.size.toLong())
        mem.write(0, bytes, 0, bytes.size)
        return mem
    }
}
