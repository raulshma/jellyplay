package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.desktop.player.mpv.MpvLib
import com.raulshma.jellyplay.desktop.player.mpv.MpvLibRender
import com.raulshma.jellyplay.feature.player.video.SoftwareFrameVideoSurface
import com.raulshma.jellyplay.feature.player.video.engine.EngineError
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference

/**
 * Desktop playback backend for machines WITHOUT the embedded-child-window
 * path (non-Windows today): libmpv renders through the **render-API software
 * backend** ("sw") into CPU buffers the Compose layer blits into a compose
 * ImageBitmap (see DesktopSoftwareVideoPane).
 *
 * Everything else — event pump, property surface, error taxonomy, release
 * discipline — is the [MpvDesktopEngine] contract verbatim, which is why this
 * type SUBCLASSES rather than duplicates it (wave 12B hooks: [liveMpvHandle],
 * [hwdecFor], [onBeforeContextDestroy]).
 *
 * Video pipeline (render.h, header-verified):
 *  - options applied pre-initialize: `vo=libmpv` — tells mpv video output is
 *    driven by an mpv_render_context instead of a VO window/audio device path;
 *    without it the core would fall back to creating its own window ("Video
 *    initialization will fail if the render context was not initialized yet,
 *    or it will revert to a VO that creates its own window", render.h L113-115).
 *  - `mpv_render_context_create(API_TYPE="sw")` happens HERE, at construction,
 *    i.e. before any loadfile can start decoder/VO init (render.h L32-34
 *    requires the context before playback begins).
 *  - frames are PULLED by [pullFrame]: `mpv_render_context_render` with
 *    SW_SIZE/SW_FORMAT("bgr0")/SW_STRIDE/SW_POINTER writes the composited
 *    frame (video + subtitles + OSD — mpv renders them all into the surface)
 *    scaled+letterboxed by mpv itself into the caller's buffer. We do NOT pass
 *    BLOCK_FOR_TARGET_TIME=0, so render blocks up to `video-timing-offset`
 *    toward the true display time — that blocking IS the frame pacer and keeps
 *    the poller from redrawing identical frames faster than content fps
 *    (rationale in docs/spikes/x-desktop-video-surface-story.md).
 *  - no update callback / no ADVANCED_CONTROL / no report_swap — see the
 *    polling decision note on [MpvLibRender].
 *
 * Quirks observed empirically are recorded in the spike doc. Threading: only
 * one mpv_render_* call may be in flight (render.h L62-64); [pullFrame]
 * enforces that with a tryLock (a tick arriving while another render is still
 * running simply drops that tick — the next one catches up), and
 * [onBeforeContextDestroy] drains the same lock before freeing the context,
 * satisfying "free before the core is destroyed" (render.h L122-123) without
 * use-after-free either direction.
 */
class MpvSoftwareRenderEngine(
    /**
     * Raw mpv options applied before mpv_initialize, ON TOP of the sw-path
     * defaults (`vo=libmpv`). User entries win over defaults — passing
     * `vo=null` turns this into an audio-only engine, which the real-engine
     * tests avoid deliberately (they exercise the render path headlessly).
     */
    extraOptions: Map<String, String> = emptyMap(),
) : MpvDesktopEngine(
    extraOptions = buildMap {
        put("vo", "libmpv")
        putAll(extraOptions)
    },
    // No wid, ever: the whole point of this path is producing CPU frames
    // instead of embedding a child window.
    windowHandle = null,
),
    SoftwareFrameVideoSurface {

    // ── Render context ──────────────────────────────────────────────────────

    private val pullLock = java.util.concurrent.locks.ReentrantLock()

    /** Non-null between construction and [release]; created before any playback. Nulled in [onBeforeContextDestroy] so post-release pulls can never touch freed memory. */
    @Volatile private var swContext: Pointer? = createContextSafely()

    /** True when the sw context exists (and thus frames can actually be pulled). */
    val isSoftwareRendererActive: Boolean get() = swContext != null

    @Volatile private var lastFrameAtMs: Long = 0L
    override val lastFrameTimestampMs: Long get() = lastFrameAtMs

    /** Last negative return code from render, or null — diagnostics only. */
    @Volatile var lastPullFailureCode: Int? = null
        private set

    private fun createContextSafely(): Pointer? = try {
        val core = liveMpvHandle() ?: return null
        val out = PointerByReference()
        val rc = MpvLibRender.render.mpv_render_context_create(
            out,
            core,
            MpvLibRender.apiTypeCreateParams(),
        )
        if (rc >= 0 && out.value != null) {
            out.value
        } else {
            tryEmitError(
                EngineError.Render(
                    IllegalStateException(
                        "sw render context create failed (${MpvLibRender.errorString(rc)}, rc=$rc)",
                    ),
                ),
            )
            null
        }
    } catch (t: Throwable) {
        // libmpv lacks render symbols / not loadable: degrade like createMpv
        // does — surfaced through errorFlow at playback, boot never crashes.
        tryEmitError(EngineError.Render(t))
        null
    }

    // ── SoftwareFrameVideoSurface ───────────────────────────────────────────

    override fun pullFrame(target: Pointer, widthPx: Int, heightPx: Int, strideBytes: Long): Boolean {
        if (widthPx <= 0 || heightPx <= 0 || strideBytes < widthPx * 4L || strideBytes % 4L != 0L) {
            return false   // render.h L392-394: stride ≥ w*pixel size, multiple of pixel size
        }
        // Drop the tick when a previous render is still in flight: never queue
        // native work behind itself (single-flight rule), just catch up later.
        if (!pullLock.tryLock()) return false
        try {
            val ctx = swContext ?: return false
            val rc = MpvLibRender.render.mpv_render_context_render(
                ctx,
                MpvLibRender.swRenderParams(widthPx, heightPx, strideBytes, target),
            )
            return if (rc >= 0) {
                lastFrameAtMs = System.currentTimeMillis()
                lastPullFailureCode = null
                true
            } else {
                lastPullFailureCode = rc
                false
            }
        } finally {
            pullLock.unlock()
        }
    }

    // ── Variant deltas over the HWND engine ─────────────────────────────────

    /**
     * Hardware decode pinned off for this path: GPU decoders hand frames back
     * through GL/D3D interop that the sw renderer would have to copy back to
     * system memory anyway, and unsupported-interop combinations fail at VO
     * init instead of falling back cleanly. Revisit once real mac/linux
     * hardware numbers exist (spike doc "unverified" section).
     */
    override fun hwdecFor(mode: DecoderMode): String = "no"

    override fun onBeforeContextDestroy() {
        // Drain any in-flight render, then free against the still-live core.
        // Base guarantees this hook runs exactly once, before terminate_destroy
        // — and post-release pullFrame() calls find swContext == null.
        pullLock.lock()
        try {
            swContext?.let { ctx ->
                runCatching { MpvLibRender.render.mpv_render_context_free(ctx) }
            }
            swContext = null
        } finally {
            pullLock.unlock()
        }
    }
}
