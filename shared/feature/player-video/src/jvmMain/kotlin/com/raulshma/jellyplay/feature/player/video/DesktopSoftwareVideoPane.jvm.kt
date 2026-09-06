package com.raulshma.jellyplay.feature.player.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.sun.jna.Memory
import com.sun.jna.Pointer
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo

/**
 * Seam implemented by engines whose video output is a CPU buffer stream rather
 * than a native window handle (wave 12B): [MpvSoftwareRenderEngine] on
 * apps/desktop — mpv's render-API software backend pulling composited frames.
 *
 * Lives in jvmMain (not commonMain) because its contract is JVM-native
 * ([Pointer], the caller-owned direct buffer); the Android target never sees
 * it, so the commonMain [EngineVideoSurface] expect keeps its signature and
 * the Android actual stays untouched. [EngineVideoSurface]'s desktop actual
 * downcasts its engine parameter to this interface to pick between the two
 * hosts (SwingPanel/HWND vs this pane).
 *
 * Implementations must be single-flight-safe around native renders and return
 * false for skipped ticks ([MpvSoftwareRenderEngine.pullFrame] documents how).
 */
interface SoftwareFrameVideoSurface {

    /**
     * Pulls the newest decoded frame into the CALLER-OWNED native buffer
     * [target], laid out as width=[widthPx], height=[heightPx],
     * bytes-per-line=[strideBytes], pixel format BGRA ("bgr0", garbage 4th
     * byte — treat as opaque). Synchronous; cheap to poll. Returns false when
     * the tick was dropped (previous render still in flight / no renderer /
     * render error / bad geometry).
     */
    fun pullFrame(target: Pointer, widthPx: Int, heightPx: Int, strideBytes: Long): Boolean

    /** Wall-clock ms of the most recent successful [pullFrame]; 0 before any frame. */
    val lastFrameTimestampMs: Long
}

/**
 * Desktop software-render video surface (wave 12B, the plan R1 fallback path):
 * hosts the newest pulled mpv frame in a Compose [Canvas] — platform
 * independent, no GL context, no heavyweight child window.
 *
 * Redraw trigger model ([SwPaneTicker.plan] is the extracted, unit-tested form):
 *  - PLAYING: pull ticker at PULL_INTERVAL_MS (~30 fps cap). Inside each pull,
 *    `mpv_render_context_render` self-throttles toward the frame's display
 *    time (BLOCK_FOR_TARGET_TIME default on), so the effective rate is content
 *    fps ≤ 30.
 *  - LOADED, NOT PLAYING (paused / keep-open ENDED): a 250 ms watchdog tick
 *    still pulls — this is what makes seekTo-while-paused repaint (the decoded
 *    frame lands in the VO even with a frozen clock) instead of leaving a
 *    stale frozen poster under a moving progress bar. A full-payload compare
 *    dedups unchanged frames, so an untouched pause costs one memcmp per tick,
 *    not a raster rebuild.
 *  - UNLOADED and NOT PLAYING: no video work exists — zero polling; the loop
 *    suspends until the session loads or playback starts. (Last pulled frame
 *    stays as the poster through idle.)
 *
 * Both ticks do ALL heavy work on Dispatchers.Default — native render AND the
 * native→JVM copy — never on the compose dispatcher (see [publishFrame]).
 *
 * Pixel path (zero format conversion): the sink allocates one JNA [Memory]
 * with 64-byte-aligned stride (render.h recommends pointer/stride multiples of
 * 64 for the SIMD path); mpv writes bgr0 into it; each CHANGED payload is
 * wrapped with ColorType.BGRA_8888 + OPAQUE alpha (skia ignores mpv's
 * uninitialized 4th channel) + sRGB — the same deterministic non-N32 pattern
 * the BlurHash jvmShared actual established. Each changed frame is installed
 * into a reused pre-allocated Skia [Bitmap] via installPixels (round-robin
 * over a small pool, see [FrameSink]) instead of allocating a fresh ~8 MB
 * raster per frame at 1080p.
 *
 * Geometry: the sink is sized to the MEASURED PIXEL size of this pane
 * (onSizeChanged) and rebuilt on change; mpv itself scales + letterboxes the
 * video into whatever SW_SIZE it receives (render.h L352-359), matching the
 * aspect-ratio/panscan behavior of the HWND path. `effectiveZoom` scales the
 * layer exactly like the SwingPanel host does.
 *
 * Subtitles/OSD: mpv composites them INTO the rendered surface, identical to
 * the embedded-window path — no new subtitle handling here. Unlike the
 * heavyweight HWND window (which composites ABOVE all Compose content),
 * this Canvas sits INSIDE the compose tree, so controls/overlays correctly
 * stack above the video while it plays.
 */
@Composable
internal fun DesktopSoftwareVideoPane(
    surface: SoftwareFrameVideoSurface,
    playingFlow: StateFlow<Boolean>,
    /** The session state machine; IDLE here means "nothing loaded" and stops all polling. */
    playbackStateFlow: StateFlow<EnginePlaybackState>,
    effectiveZoom: Float,
    onSurfaceCreated: (Any?) -> Unit,
    onSurfaceUpdate: () -> Unit,
) {
    var surfaceSizePx by remember { mutableStateOf(IntSize.Zero) }
    var frameBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Screen parity with the SwingPanel host: the factory/update callbacks run
    // exactly once when the surface attaches (the screen applies the subtitle
    // style snapshot inside onSurfaceCreated). The sw path has no platform
    // surface object to hand back — null degrades like the empty-view fallback.
    DisposableEffect(surface) {
        onSurfaceCreated(null)
        onSurfaceUpdate()
        onDispose {
            onSurfaceCreated(null)
            frameBitmap = null
        }
    }

    val sink = remember(surfaceSizePx) {
        if (surfaceSizePx.width <= 0 || surfaceSizePx.height <= 0) {
            null
        } else {
            FrameSink(surfaceSizePx.width, surfaceSizePx.height)
        }
    }

    LaunchedEffect(surface, playingFlow, playbackStateFlow, sink) {
        if (sink == null) return@LaunchedEffect
        while (isActive) {
            when (
                val plan = SwPaneTicker.plan(
                    isPlaying = playingFlow.value,
                    unloaded = playbackStateFlow.value == EnginePlaybackState.IDLE,
                )
            ) {
                is SwPaneTicker.Poll -> {
                    publishFrame(surface, sink) { frameBitmap = it }
                    delay(plan.intervalMs)
                }

                SwPaneTicker.Suspend -> {
                    // Fully idle AND paused-stopped: suspend until either the
                    // session loads something or playback starts again.
                    combine(playingFlow, playbackStateFlow) { playing, state ->
                        playing || state != EnginePlaybackState.IDLE
                    }.first { it }
                }
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { surfaceSizePx = it }
            .graphicsLayer {
                scaleX = effectiveZoom
                scaleY = effectiveZoom
            },
    ) {
        val bmp = frameBitmap ?: return@Canvas
        drawImage(
            bmp,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bmp.width, bmp.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(
                size.width.roundToInt().coerceAtLeast(1),
                size.height.roundToInt().coerceAtLeast(1),
            ),
        )
    }
}

/**
 * Pulls and, when the payload CHANGED, publishes one frame. Everything heavy
 * runs on Dispatchers.Default — never the compose dispatcher: the native
 * `mpv_render_context_render` performs the whole software scale/composite on
 * the calling thread (render.h L139 is blunt that the sw renderer is "very
 * slow") and self-throttles toward frame-display time while playing, so an
 * inline call would jank the UI at HD sizes. render.h L62-64 permits renders
 * from any thread as long as only one mpv_render_* call runs per context —
 * the engine's single-flight tryLock provides exactly that, which is what
 * makes this hoist safe.
 *
 * The [into] write targets compose snapshot state, which supports writers from
 * background threads; recomposition observes the apply off-thread.
 */
private suspend fun publishFrame(
    surface: SoftwareFrameVideoSurface,
    sink: FrameSink,
    into: (ImageBitmap?) -> Unit,
) {
    val pulled = withContext(Dispatchers.Default) {
        try {
            surface.pullFrame(sink.nativeMemory, sink.widthPx, sink.heightPx, sink.strideBytes)
        } catch (_: Throwable) {
            false   // released engine mid-flight: next tick finds another world
        }
    }
    if (!pulled) return
    withContext(Dispatchers.Default) {
        sink.nativeMemory.read(0, sink.stageBytes, 0, sink.stageBytes.size)
        // Dedup guard for the paused watchdog cadence: rebuild the Skia raster
        // only when the composited payload actually changed (seek step while
        // paused), not on every unchanged tick. Conservative by construction —
        // if it ever over-fires (the bgr0 4th channel is documented
        // uninitialized garbage), the cost is a redundant raster, NEVER a
        // missed update.
        if (!sink.stageBytes.contentEquals(sink.lastPublished)) {
            System.arraycopy(sink.stageBytes, 0, sink.lastPublished, 0, sink.stageBytes.size)
            into(sink.acquireImageBitmap())
        }
    }
}

/**
 * Trigger-model decision table for the redraw loop, extracted so the pause/
 * idle policy stays unit-testable without Compose (SwPaneTickerTest).
 */
internal object SwPaneTicker {

    /** Poll now and wait [intervalMs] before the next pull attempt. */
    data class Poll(val intervalMs: Long) : Plan

    /** No video work exists; suspend until load or play resumes. */
    data object Suspend : Plan

    sealed interface Plan

    fun plan(isPlaying: Boolean, unloaded: Boolean): Plan = when {
        isPlaying -> Poll(PULL_INTERVAL_MS)
        !unloaded -> Poll(PAUSED_POLL_INTERVAL_MS)
        else -> Suspend
    }

    /** Playing-pull cadence cap; mpv paces real rendering below this. */
    const val PULL_INTERVAL_MS = 33L

    /**
     * Paused-watchdog cadence: slow enough to be free (~4 deduped memcmps/sec
     * on an untouched pause), fast enough that a seek-while-paused repaints
     * within a quarter second.
     */
    const val PAUSED_POLL_INTERVAL_MS = 250L
}

/**
 * One native render target + its JVM staging copy. 64-byte stride/pointer
 * alignment per render.h L395-398 ("facilitate fast SIMD operation"); JNA
 * Memory allocations are at least 16-aligned, above the mandatory 4-byte
 * minimum — the fast path may still decline, worst case mpv copies internally.
 */
private class FrameSink(widthPx: Int, heightPx: Int) {
    val widthPx: Int = widthPx
    val heightPx: Int = heightPx
    val strideBytes: Long = ((widthPx * 4L + STRIDE_ALIGN - 1) / STRIDE_ALIGN) * STRIDE_ALIGN
    val nativeMemory: Memory = Memory(strideBytes * heightPx)

    /** Staging array consumed by Skia makeRaster (BGRA_8888, opaque). */
    val stageBytes: ByteArray = ByteArray((strideBytes * heightPx).toInt())

    /** Snapshot of the last RASTERIZED payload — the dedup baseline. */
    val lastPublished: ByteArray = ByteArray((strideBytes * heightPx).toInt())

    /**
     * Pixel layout the pooled [Bitmap]s present to Skia — identical to what
     * [Image.makeRaster] produced before the pool existed (BGRA_8888, opaque,
     * sRGB, the native sink's 64-byte rowbytes), so rendered output is
     * bit-identical.
     */
    val imageInfo = ImageInfo(
        width = widthPx,
        height = heightPx,
        colorType = ColorType.BGRA_8888,
        alphaType = ColorAlphaType.OPAQUE,
        colorSpace = ColorSpace.sRGB,
    )

    /**
     * Round-robin raster pool (wave 12B perf fix): publishes install
     * [stageBytes] into a pooled [Bitmap] instead of allocating a fresh Image +
     * ImageBitmap wrapper chain per frame. Pool size 3, not 2: a buffer is "in
     * flight" from its publish until Compose draws the NEXT-BUT-ONE publish
     * (double-buffering covers the normal case, but a compose-side frame stall
     * — window drag, GC pause — can hold a reference one frame longer), so the
     * third buffer guarantees the one being rewritten is never the one Compose
     * is reading. Resolution changes recreate the whole [FrameSink] (see the
     * `remember(surfaceSizePx)` in the pane), so the pool never needs a
     * mid-life reallocation.
     *
     * Note: skiko's `installPixels(ByteArray)` copies the array into freshly
     * allocated native pixel memory (Bitmap.cc allocates `new jbyte[]` and
     * installs it with a delete proc — it never wraps the Java buffer), so one
     * native pixel copy per publish is unavoidable through the public API; the
     * `allocPixels` pre-allocation below is superseded on first install. What
     * the pool removes is the per-frame Image/Bitmap/ImageBitmap object churn
     * the old `makeRaster(...).toComposeImageBitmap()` path produced.
     */
    private val rasterPool = Array(POOL_SIZE) {
        Bitmap().apply { allocPixels(imageInfo) }
    }
    private var poolCursor = 0

    /**
     * Installs the current [stageBytes] payload into the next pooled bitmap
     * and returns it as an [ImageBitmap]. The bytes are copied into the
     * bitmap's native pixels; the bitmap then stays unmutated for the next two
     * publishes (see [rasterPool]).
     */
    fun acquireImageBitmap(): ImageBitmap {
        val bitmap = rasterPool[poolCursor]
        poolCursor = (poolCursor + 1) % POOL_SIZE
        bitmap.installPixels(imageInfo, stageBytes, strideBytes.toInt())
        bitmap.notifyPixelsChanged()
        return bitmap.asComposeImageBitmap()
    }

    companion object {
        private const val STRIDE_ALIGN = 64L

        /** Round-robin depth — see [rasterPool] for why it is 3. */
        private const val POOL_SIZE = 3
    }
}
