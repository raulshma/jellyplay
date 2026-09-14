package com.raulshma.jellyplay.feature.book

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor

/**
 * The reader's TOC tick rail: a compact stack of side ticks on the start edge
 * showing the current chapter's TOC neighborhood — [TOC_RAIL_CONTEXT_TICKS]
 * previous, the current (widest, primary-tinted), [TOC_RAIL_CONTEXT_TICKS]
 * next. Interaction mirrors the library screen's alphabet jump rail:
 * - Tap a tick → jump to that entry (bubble flashes the title).
 * - Press-drag → scrub: the tick stack stays anchored, the title bubble
 *   tracks the finger, and dragging past either end of the stack keeps
 *   stepping one chapter per row through the whole TOC. The jump COMMITS ON
 *   RELEASE — an EPUB chapter jump rebuilds WebView sections, so live-jumping
 *   per boundary crossing (the alphabet rail's contract) would thrash it.
 * Drawn above the content, below the chrome; composed only when the reader
 * controls' opt-in "chapter tick rail" preference is ON, and never for a
 * book without a TOC (the caller simply does not compose the rail).
 */

/**
 * Compose-free geometry core for [ReaderTocRail] (the `AlphabetRailGeometry`
 * precedent): the finger-y → tick-row and full-TOC-index mappings with the
 * beyond-the-ends extrapolation, plus the gaussian fisheye lens. All plain
 * numbers, deterministically testable; the composable keeps dp/px conversion,
 * drawing, and pointer wiring.
 *
 * @param windowSize visible tick rows — the rail is exactly
 *   `windowSize * rowPx` tall and every row derives from that height.
 * @param rowPx fixed per-tick row height in px.
 */
internal class TocRailGeometry(
    private val windowSize: Int,
    private val rowPx: Float,
) {
    /**
     * Fractional row position of [yPx] in tick-row units, UNclamped — the
     * values past either end are what the drag extrapolation consumes.
     */
    fun rawRowAt(yPx: Float): Float =
        if (rowPx <= 0f) 0f else yPx / rowPx

    /** Visible tick row under [yPx], clamped into the rail. */
    fun windowRowAt(yPx: Float): Float =
        rawRowAt(yPx).coerceIn(0f, (windowSize - 1).coerceAtLeast(0).toFloat())

    /**
     * Full-TOC index the finger at [yPx] previews: a row inside the rail maps
     * to its window entry; past either end the index keeps stepping one
     * chapter per row (so a 5-tick stack can reach a 50-chapter TOC), clamped
     * to `[0, tocLastIndex]`. Empty window → -1 (caller treats as no preview).
     */
    fun previewIndexAt(yPx: Float, window: List<Int>, tocLastIndex: Int): Int {
        if (window.isEmpty()) return -1
        val raw = rawRowAt(yPx)
        return when {
            // Above the rail: keep stepping one chapter per row upward.
            raw < 0f -> window.first() - ceil(-raw).toInt()
            // Below the rail (raw ≥ windowSize — a finger inside the LAST
            // row is 4.x, still the in-rail branch): one chapter per row down.
            raw >= windowSize -> window.last() + (floor(raw) - (windowSize - 1)).toInt()
            else -> window[floor(raw).toInt().coerceIn(0, window.lastIndex)]
        }.coerceIn(0, tocLastIndex)
    }

    /**
     * Gaussian fisheye scale for the tick at [row] under the fractional
     * finger row [touchRow] (null = not dragging → no lens). Pure — safe to
     * call from a draw-phase `graphicsLayer` lambda so the lens glides with
     * the finger without invalidating composition.
     */
    fun fisheyeScaleAt(row: Int, touchRow: Float?): Float {
        if (touchRow == null) return 1f
        val d = row - touchRow
        val g = exp(-(d * d) / (2 * FISHEYE_SIGMA_SQ))
        return 1f + (FISHEYE_PEAK - 1f) * g
    }

    companion object {
        /** Peak scale of the tick directly under the finger (fisheye lens). */
        private const val FISHEYE_PEAK = 2.5f

        /** Gaussian sigma² for the fisheye falloff — smaller = tighter bell. */
        private const val FISHEYE_SIGMA_SQ = 1.6f
    }
}

/** Fixed per-tick row height — compact stack, tall enough to grab. */
private val TICK_ROW_HEIGHT = 20.dp
/** Rail shell width (the tick lines draw inside it, right-aligned). */
private val RAIL_WIDTH = 32.dp
/** Title bubble metrics. */
private val BUBBLE_HEIGHT = 32.dp
private val BUBBLE_GAP = 10.dp
private val BUBBLE_MAX_WIDTH = 240.dp
/** How long the tap-flash title bubble lingers (the alphabet rail's timing). */
private const val BUBBLE_FLASH_MS = 350L

/**
 * The tick stack + title bubble. [ticks] is the FULL flattened TOC (the drag
 * extrapolation reaches beyond the visible window), [currentIndex] the entry
 * the reader sits at (null/unknown → nothing renders), [onJump] the commit
 * callback (EPUB href jump / PDF page seek).
 */
@Composable
internal fun ReaderTocRail(
    ticks: List<ReaderTocTick>,
    currentIndex: Int?,
    onJump: (ReaderTocTick) -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = remember(ticks.size, currentIndex) {
        tocTickWindow(ticks.size, currentIndex)
    }
    if (window.isEmpty()) return

    val density = LocalDensity.current
    val rowPx = with(density) { TICK_ROW_HEIGHT.toPx() }
    val geometry = remember(window.size, rowPx) { TocRailGeometry(window.size, rowPx) }

    // Fractional finger row (raw, unclamped) while pressed; null at rest.
    // Backing states are vals so draw-phase provider lambdas stay stable (the
    // alphabet rail's skipping contract).
    val touchRowState = remember { mutableStateOf<Float?>(null) }
    // Last touched row — bubble placement survives the drag-end null-out.
    val bubbleRowState = remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    // Tap flash: the title bubble lingers after a tap-jump.
    var flashTitle by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(flashTitle) {
        if (flashTitle != null) {
            kotlinx.coroutines.delay(BUBBLE_FLASH_MS)
            flashTitle = null
        }
    }

    // The gesture handlers read the LATEST inputs through these refs instead
    // of capturing them: the callers' onJump lambdas are fresh identities on
    // every recomposition (EPUB relocation events recompose the screen
    // constantly), and pointerInput keys on the geometry alone — a detector
    // restart mid-press would silently eat the in-flight tap.
    val currentTicks by rememberUpdatedState(ticks)
    val currentWindow by rememberUpdatedState(window)
    val currentOnJump by rememberUpdatedState(onJump)

    // Full-TOC preview tick under the finger — derived over the raw state so
    // composition only recomputes on chapter crossings, not per pointer move.
    val previewTick by remember(ticks, window, geometry) {
        derivedStateOf {
            if (!dragging) return@derivedStateOf null
            val row = touchRowState.value ?: return@derivedStateOf null
            ticks.getOrNull(geometry.previewIndexAt(row, window, ticks.lastIndex))
        }
    }
    val bubbleTitle by remember(ticks, window, geometry) {
        derivedStateOf { previewTick?.label ?: flashTitle }
    }
    var lastBubbleTitle by remember {
        mutableStateOf(ticks.getOrNull(window.first())?.label.orEmpty())
    }
    bubbleTitle?.let { lastBubbleTitle = it }
    val bubbleVisible = bubbleTitle != null

    fun commitPreview() {
        val tick = previewTick
        dragging = false
        touchRowState.value = null
        tick?.let(onJump)
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .width(RAIL_WIDTH)
                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                window.forEachIndexed { row, tickIndex ->
                    val tick = ticks.getOrNull(tickIndex) ?: return@forEachIndexed
                    val isCurrent = tickIndex == currentIndex
                    // Static emphasis by TOC distance (the screenshot look:
                    // current widest/brightest, neighbors taper). The
                    // draw-phase fisheye multiplies on top while pressed.
                    val distance = if (isCurrent) {
                        0
                    } else {
                        abs(tickIndex - (currentIndex ?: tickIndex))
                    }
                    val baseWidth = when (distance) {
                        0 -> 26.dp
                        1 -> 18.dp
                        else -> 12.dp
                    }
                    val baseAlpha = when (distance) {
                        0 -> 1f
                        1 -> 0.7f
                        else -> 0.45f
                    }
                    // Stable provider per row: captures only `row` (a
                    // constant) and the state ref, read live inside the
                    // graphicsLayer draw lambda — finger motion animates the
                    // lens with zero recomposition.
                    val scaleProvider = remember(row, geometry) {
                        { geometry.fisheyeScaleAt(row, touchRowState.value) }
                    }
                    Box(
                        modifier = Modifier
                            .height(TICK_ROW_HEIGHT)
                            .fillMaxWidth()
                            .graphicsLayer {
                                val s = scaleProvider()
                                scaleX = s
                                scaleY = s
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .width(baseWidth)
                                .height(3.dp)
                                .background(
                                    color = if (isCurrent) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.White.copy(alpha = baseAlpha)
                                    },
                                    shape = RoundedCornerShape(2.dp),
                                ),
                        )
                    }
                }
            }

            // Touch overlay — LAST child on top of the ticks (the alphabet
            // rail's wiring): rail-level gestures, so the user never has to
            // hit a ~3dp line. It consumes touches only inside this 32dp
            // strip; the book content's own gestures stay untouched elsewhere.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(geometry) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                bubbleRowState.value = geometry.rawRowAt(offset.y)
                                touchRowState.value = geometry.rawRowAt(offset.y)
                                dragging = true
                            },
                            onDrag = { change, _ ->
                                bubbleRowState.value = geometry.rawRowAt(change.position.y)
                                touchRowState.value = geometry.rawRowAt(change.position.y)
                            },
                            onDragEnd = { commitPreview() },
                            onDragCancel = {
                                dragging = false
                                touchRowState.value = null
                            },
                        )
                    }
                    .pointerInput(geometry) {
                        detectTapGestures { offset ->
                            val ticks = currentTicks
                            val window = currentWindow
                            val row = geometry.windowRowAt(offset.y).toInt()
                                .coerceIn(0, window.lastIndex)
                            val tick = ticks.getOrNull(window[row]) ?: return@detectTapGestures
                            bubbleRowState.value = geometry.windowRowAt(offset.y)
                            flashTitle = tick.label
                            currentOnJump(tick)
                        }
                    },
            )

            // Title bubble — the hovered/scrubbed tick's TITLE ONLY (no
            // expanded tooltip body), tracking the finger row just right of
            // the stack. Uses lastBubbleTitle during the exit fade when the
            // live source is already null (the alphabet rail's trick).
            AnimatedVisibility(
                visible = bubbleVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                val bubbleHeightPx = with(density) { BUBBLE_HEIGHT.toPx() }
                val xPx = with(density) { (RAIL_WIDTH + BUBBLE_GAP).toPx() }
                Box(
                    modifier = Modifier
                        .sizeIn(maxWidth = BUBBLE_MAX_WIDTH, minHeight = BUBBLE_HEIGHT)
                        .offset {
                            val y = bubbleRowState.value * rowPx - bubbleHeightPx / 2f
                            IntOffset(xPx.toInt(), y.toInt())
                        }
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = lastBubbleTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
