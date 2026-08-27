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
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sun.jna.Memory
import com.sun.jna.Pointer
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
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
 * Redraw driver: a [LaunchedEffect] ticker pulls at PULL_INTERVAL_MS (~30 fps)
 * ONLY while the session reports playing (through [playingFlow]); on pause it
 * publishes one final frame (the exact stop frame becomes the frozen poster)
 * and then suspends until playback resumes — zero idle-tick burn. Inside a
 * pull, `mpv_render_context_render` self-throttles toward the frame's display
 * time (BLOCK_FOR_TARGET_TIME default on), so the effective rate is content fps
 * ≤ 30.
 *
 * Pixel path (zero format conversion): the sink allocates one JNA [Memory]
 * with 64-byte-aligned stride (render.h recommends pointer/stride multiples of
 * 64 for the SIMD path); mpv writes bgr0 into it; each successful pull copies
 * native→byte[] ONCE (JNA bulk read) and wraps it as a fresh Skia raster with
 * ColorType.BGRA_8888 + OPAQUE alpha (skia ignores mpv's uninitialized 4th
 * channel) + sRGB — the same deterministic non-N32 pattern the BlurHash
 * jvmShared actual established. Per-frame image allocation is deliberate v1
 * simplicity (~8 MB/frame at 1080p, native-backed); double-buffering via
 * Bitmap.installPixels is the identified future optimization.
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

    LaunchedEffect(surface, playingFlow, sink) {
        if (sink == null) return@LaunchedEffect
        var prevWasPlaying = false
        while (isActive) {
            if (playingFlow.value) {
                publishFrame(surface, sink) { frameBitmap = it }
                prevWasPlaying = true
                delay(PULL_INTERVAL_MS)
            } else {
                if (prevWasPlaying) {
                    // Final refresh on pause: freeze on the exact stop frame.
                    publishFrame(surface, sink) { frameBitmap = it }
                    prevWasPlaying = false
                }
                playingFlow.first { it }   // suspend until playback resumes
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

/** Publishes one pulled frame, or leaves [into] untouched when the pull dropped. */
private suspend fun publishFrame(
    surface: SoftwareFrameVideoSurface,
    sink: FrameSink,
    into: (ImageBitmap?) -> Unit,
) {
    val pulled = try {
        surface.pullFrame(sink.nativeMemory, sink.widthPx, sink.heightPx, sink.strideBytes)
    } catch (_: Throwable) {
        false   // released engine mid-flight: next tick finds another world
    }
    if (!pulled) return
    // Heavy part (native bulk read + raster allocation) stays off the UI thread;
    // the single state write hops back for the recomposition.
    val bitmap = withContext(Dispatchers.Default) {
        sink.nativeMemory.read(0, sink.stageBytes, 0, sink.stageBytes.size)
        Image.makeRaster(
            ImageInfo(
                width = sink.widthPx,
                height = sink.heightPx,
                colorType = ColorType.BGRA_8888,
                alphaType = ColorAlphaType.OPAQUE,
                colorSpace = ColorSpace.sRGB,
            ),
            sink.stageBytes,
            sink.strideBytes.toInt(),
        ).toComposeImageBitmap()
    }
    into(bitmap)
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

    companion object {
        private const val STRIDE_ALIGN = 64L
    }
}

/** Pull cadence cap; mpv paces real rendering below this (see class KDoc). */
private const val PULL_INTERVAL_MS = 33L
