package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.feature.player.video.PlatformBitmap
import com.raulshma.jellyplay.feature.player.video.SeekBarBufferBands
import com.raulshma.jellyplay.feature.player.video.formatDuration
import com.raulshma.jellyplay.feature.player.video.state.rememberTvSeekController
import com.raulshma.jellyplay.feature.player.video.state.tvSeekStepFraction
import com.raulshma.jellyplay.core.ui.player.playerSeekbarDpSpec
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache

// ── Section split: the scrubber + trickplay preview ───────────────────────
// The TV-controllable seek bar (band shading, segment/chapter/A-B markers,
// D-pad + drag seek wiring, the trickplay thumbnail preview and the time
// labels). Moved verbatim from PlayerControls.kt as a composition-only
// section split (the VideoPlayerScreenOverlays.kt precedent): the
// declaration is unchanged except `private` → `internal` where the root file
// calls the symbol. See the section-host map on PlayerControls.kt.

@Composable
internal fun TvControllableSeekBar(
    // High-frequency streams collected at this leaf so only the seek bar
    // recomposes at 4 Hz, not the whole PlayerControls body. Mirrors the
    // VideoStatsOverlay pattern. The previous design collected these in
    // PlayerControls' root, which invalidated a ~670-line, 110-parameter
    // composable on every position tick and was a primary driver of the
    // MPV playback ANR (Davey! / skipped frames / input-dispatch timeout).
    currentPositionFlow: StateFlow<Long>,
    duration: Long,
    chapters: List<ChapterInfo>,
    segments: List<MediaSegment> = emptyList(),
    // each range shades its own band (multi-range buffered model);
    // collected at this leaf like currentPositionFlow so only the seek bar
    // recomposes when the engine republishes ranges. Empty = no shading.
    bufferedRangesFlow: StateFlow<List<LongRange>> = MutableStateFlow(emptyList()),
    trickplayBitmap: PlatformBitmap? = null,
    playbackSpeed: Float = 1.0f,
    showTimeRemaining: Boolean = false,
    // Plain values (not streams): A/B points change only on user action, never
    // per position tick, so passing them here keeps the leaf-collection rule
    // intact while letting the canvas visualize the window. Point presence
    // drives drawing — A alone shows its tick, both points show the region.
    // `setEnabled(false)` wipes points, so a disabled window never renders.
    abRepeatStartMs: Long? = null,
    abRepeatEndMs: Long? = null,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
    onSeekPositionChange: (Long) -> Unit = {},
    tvFocusRequester: FocusRequester = remember { FocusRequester() },
    tvUpFocusRequester: FocusRequester? = null,
    tvDownFocusRequester: FocusRequester? = null,
) {
    val currentPosition by currentPositionFlow.collectAsStateWithLifecycle()
    // the band math (range→fraction coercion + degenerate pruning) is
    // the pure [SeekBarBufferBands] ladder — the Canvas below only loops over
    // the result.
    val bufferedRanges by bufferedRangesFlow.collectAsStateWithLifecycle()
    val bufferedBands = remember(bufferedRanges, duration) {
        SeekBarBufferBands.bands(bufferedRanges, duration)
    }
    val isTv = LocalTvMode.current
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current

    // The seek state machine (focus seeding, D-pad accumulate-and-clamp,
    // drag-vs-tv-vs-live priority, the seek-callback choreography) lives in
    // TvSeekController; this composable keeps only drawing and event wiring.
    val seekController = rememberTvSeekController(
        onSeekStart = onSeekStart,
        onSeekPreview = onSeekPositionChange,
        onSeekEnd = onSeekEnd,
    )
    // DurationChanged: duration is a plain recomposition input — assigning it
    // in the body keeps every handler's duration exactly as fresh as the
    // former inline lambdas that captured the parameter per recomposition.
    seekController.durationMs = duration
    val isDragging by seekController.isDragging.collectAsStateWithLifecycle()
    val dragFraction by seekController.dragFraction.collectAsStateWithLifecycle()
    val isSeekBarFocused by seekController.isFocused.collectAsStateWithLifecycle()
    val tvSeekPosition by seekController.tvSeekPosition.collectAsStateWithLifecycle()

    val progress = seekController.progress(currentPosition)

    val activeColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val isActive = isPressed || isDragging || (isTv && isSeekBarFocused)
    val trackHeight by animateDpAsState(
        targetValue = if (isActive) 5.dp else 3.dp,
        animationSpec = playerSeekbarDpSpec(),
        label = "trackH",
    )
    val thumbRadiusDp by animateDpAsState(
        targetValue = if (isActive) 7.dp else 5.dp,
        animationSpec = playerSeekbarDpSpec(),
        label = "thumbR",
    )

    val chapterMarkerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    // A/B repeat window: translucent region on the track + primary edge ticks.
    val abRepeatRegionColor = activeColor.copy(alpha = 0.28f)
    val abRepeatMarkerColor = activeColor

    // Precompute the per-segment draw color once per segment list. The seek bar
    // Canvas redraws on every position/buffered tick (~4 Hz) and previously
    // allocated a fresh Color(segment.type.colorLong) per segment per frame;
    // the segment colors only change when the segment list itself changes.
    val segmentColors = remember(segments) {
        segments.associate { it to Color(it.type.colorLong) }
    }

    val tvFocusState = rememberTvFocusState(focusedScale = 1f)

    // The 30s-TV / 10s-touch seek-step divergence — the policy lives on
    // TvSeekController.tvSeekStepFraction (jvmTest-pinned); the controller's
    // D-pad handlers are clamped by this fraction per tick.
    val seekStep = tvSeekStepFraction(isTv, duration)

    // Position-derived labels memoized by second to cut formatDuration allocations
    // during the 4 Hz position tick (most recomposes only move the playhead).
    val currentPositionText = remember(currentPosition / 1000) { formatDuration(currentPosition) }
    val durationText = remember(duration / 1000) { formatDuration(duration) }
    val remainingText = remember(duration, currentPosition / 1000, showTimeRemaining) {
        if (duration > 0 && showTimeRemaining) {
            val remainingMs = (duration - currentPosition).coerceAtLeast(0)
            "-" + formatDuration(remainingMs)
        } else null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (isTv && isSeekBarFocused && trickplayBitmap != null) {
            val displayMs = (tvSeekPosition * duration).toLong()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                TrickplayOverlay(
                    bitmap = trickplayBitmap,
                    positionMs = displayMs,
                    durationMs = duration,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                // a11y: the Canvas-drawn seekbar previously carried no
                // semantics, so TalkBack ignored the primary scrub control
                // entirely. Expose it as a Role.Slider with a ProgressBarRangeInfo
                // (announces "% of duration") and a SetProgress action so
                // accessibility services can both read and move the position.
                .semantics {
                    // No explicit Role.Slider/Role.ProgressBar (neither value is
                    // present in this Compose BOM). The setProgress action and
                    // progressBarRangeInfo together make TalkBack announce the
                    // control as an adjustable progress element.
                    progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(
                        progress.coerceIn(0f, 1f),
                        0f..1f,
                    )
                    if (duration > 0) {
                        setProgress { target ->
                            val clamped = target.coerceIn(0f, 1f)
                            onSeekStart()
                            onSeekPositionChange((clamped * duration).toLong())
                            onSeekEnd()
                            true
                        }
                    }
                }
                .then(
                    if (isTv) {
                        Modifier
                            .focusRequester(tvFocusRequester)
                            .then(tvFocusState.focusModifier)
                            .tvFocusIndicator(tvFocusState, ShapeCache.smooth4)
                            .then(
                                if (tvUpFocusRequester != null || tvDownFocusRequester != null) {
                                    Modifier.focusProperties {
                                        tvUpFocusRequester?.let { up = it }
                                        tvDownFocusRequester?.let { down = it }
                                    }
                                } else Modifier
                            )
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    // Seed only on the gain transition — a
                                    // repeated focused callback must not
                                    // clobber an in-flight seek.
                                    if (!seekController.isFocused.value) {
                                        seekController.onFocusGained(currentPosition)
                                    }
                                } else {
                                    // Commits any unflushed D-pad seek.
                                    seekController.onFocusLost()
                                }
                            }
                            .focusable()
                            .onDpadKey(
                                onRight = { seekController.onDpadTick(direction = +1, step = seekStep) },
                                onLeft = { seekController.onDpadTick(direction = -1, step = seekStep) },
                                onSelect = {
                                    seekController.flush()
                                    true
                                },
                            )
                    } else {
                        Modifier.pointerInput(duration) {
                            if (duration <= 0) return@pointerInput
                            awaitPointerEventScope {
                                while (true) {
                                    val downEvent = awaitFirstDown()
                                    downEvent.consume()
                                    var fraction = (downEvent.position.x / size.width).coerceIn(0f, 1f)
                                    seekController.onDragStart(fraction)

                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == downEvent.id }
                                        if (change != null) {
                                            fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                            seekController.onDragTo(fraction)
                                            change.consume()
                                        }
                                    } while (change?.pressed == true)

                                    seekController.onDragEnd()
                                }
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
            ) {
                val trackY = (size.height / 2f) - (trackHeight.toPx() / 2f)
                val trackWidth = size.width

                drawRoundRect(
                    color = trackColor,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, trackY),
                    size = androidx.compose.ui.geometry.Size(trackWidth, trackHeight.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight.toPx() / 2f),
                )

                if (duration > 0) {
                    segments.forEach { segment ->
                        if (!segment.hasSegment) return@forEach
                        val startFrac = (segment.startTicks / 10_000f) / duration
                        val endFrac = (segment.endTicks / 10_000f) / duration
                        if (startFrac !in 0f..1f || endFrac <= startFrac) return@forEach
                        val segHeight = 6.dp.toPx()
                        val segY = (size.height / 2f) - (segHeight / 2f)
                        drawRoundRect(
                            color = (segmentColors[segment] ?: Color.Black).copy(alpha = 0.4f),
                            topLeft = androidx.compose.ui.geometry.Offset(startFrac * trackWidth, segY),
                            size = androidx.compose.ui.geometry.Size((endFrac - startFrac) * trackWidth, segHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(segHeight / 2f),
                        )
                    }
                }

                // EACH buffered range shades its own band — the
                // generalization of the former single 0..bufferedFraction
                // draw. Bands come from the pure SeekBarBufferBands ladder;
                // gaps between ranges stay unshaded (a forward seek drops the
                // old window while the back-buffer survives behind the
                // playhead).
                if (bufferedBands.isNotEmpty()) {
                    val bufferColor = activeColor.copy(alpha = 0.25f)
                    bufferedBands.forEach { band ->
                        drawRoundRect(
                            color = bufferColor,
                            topLeft = androidx.compose.ui.geometry.Offset(band.startFraction * trackWidth, trackY),
                            size = androidx.compose.ui.geometry.Size(
                                (band.endFraction - band.startFraction) * trackWidth,
                                trackHeight.toPx(),
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight.toPx() / 2f),
                        )
                    }
                }

                drawRoundRect(
                    color = activeColor,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, trackY),
                    size = androidx.compose.ui.geometry.Size(trackWidth * progress, trackHeight.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight.toPx() / 2f),
                )

                // A/B repeat window: the A tick appears as soon as A is
                // captured; completing the window (A < B) adds the shaded
                // region and the B tick. Toggling A/B repeat off wipes the
                // points, so the markers vanish with it.
                val abStart = abRepeatStartMs
                val abEnd = abRepeatEndMs
                if (duration > 0) {
                    val abMarkerHeight = 10.dp.toPx()
                    val abMarkerWidth = 2.dp.toPx()
                    if (abStart != null && abEnd != null && abStart < abEnd) {
                        val abStartFrac = (abStart.toFloat() / duration).coerceIn(0f, 1f)
                        val abEndFrac = (abEnd.toFloat() / duration).coerceIn(0f, 1f)
                        if (abEndFrac > abStartFrac) {
                            drawRoundRect(
                                color = abRepeatRegionColor,
                                topLeft = androidx.compose.ui.geometry.Offset(abStartFrac * trackWidth, trackY),
                                size = androidx.compose.ui.geometry.Size(
                                    (abEndFrac - abStartFrac) * trackWidth,
                                    trackHeight.toPx(),
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight.toPx() / 2f),
                            )
                            listOf(abStartFrac, abEndFrac).forEach { frac ->
                                drawRoundRect(
                                    color = abRepeatMarkerColor,
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        frac * trackWidth - abMarkerWidth / 2f,
                                        trackY + trackHeight.toPx() / 2f - abMarkerHeight / 2f,
                                    ),
                                    size = androidx.compose.ui.geometry.Size(abMarkerWidth, abMarkerHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
                                )
                            }
                        }
                    } else if (abStart != null) {
                        val abStartFrac = (abStart.toFloat() / duration).coerceIn(0f, 1f)
                        drawRoundRect(
                            color = abRepeatMarkerColor,
                            topLeft = androidx.compose.ui.geometry.Offset(
                                abStartFrac * trackWidth - abMarkerWidth / 2f,
                                trackY + trackHeight.toPx() / 2f - abMarkerHeight / 2f,
                            ),
                            size = androidx.compose.ui.geometry.Size(abMarkerWidth, abMarkerHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
                        )
                    }
                }

                if (chapters.isNotEmpty() && duration > 0) {
                    chapters.forEach { chapter ->
                        val chapterFraction = (chapter.startPositionTicks / 10_000f) / duration
                        if (chapterFraction in 0.01f..0.99f) {
                            val markerX = chapterFraction * trackWidth
                            val markerHeight = 7.dp.toPx()
                            val markerWidth = 2.dp.toPx()
                            drawRoundRect(
                                color = chapterMarkerColor,
                                topLeft = androidx.compose.ui.geometry.Offset(
                                    markerX - markerWidth / 2f,
                                    trackY + trackHeight.toPx() / 2f - markerHeight / 2f,
                                ),
                                size = androidx.compose.ui.geometry.Size(markerWidth, markerHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
                            )
                        }
                    }
                }

                val thumbRadius = thumbRadiusDp.toPx()
                val thumbCenterX = progress * trackWidth
                val thumbCenterY = size.height / 2f

                if (isActive) {
                    drawCircle(
                        color = activeColor.copy(alpha = 0.2f),
                        radius = thumbRadius * 2.2f,
                        center = androidx.compose.ui.geometry.Offset(thumbCenterX, thumbCenterY),
                    )
                }

                drawCircle(
                    color = activeColor,
                    radius = thumbRadius,
                    center = androidx.compose.ui.geometry.Offset(thumbCenterX, thumbCenterY),
                )
            }
        }

        if (isDragging || (isTv && isSeekBarFocused)) {
            val displayMs = if (isDragging) (dragFraction * duration).toLong() else (tvSeekPosition * duration).toLong()

            Row(
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatDuration(displayMs),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    currentPositionText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                val endLabel = if (duration > 0) {
                    remainingText ?: durationText
                } else {
                    "--:--"
                }
                Text(
                    endLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}
