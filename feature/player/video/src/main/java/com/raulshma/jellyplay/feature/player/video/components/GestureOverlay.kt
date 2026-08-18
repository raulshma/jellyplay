package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.LocalReducedMotion
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.pow
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.GestureIndicatorSide

private fun brightnessIcon(value: Float) = when {
    value <= 0f -> Tabler.Outline.BrightnessDown
    value < 0.3f -> Tabler.Outline.BrightnessHalf
    else -> Tabler.Outline.BrightnessUp
}

private fun volumeIcon(value: Float) = when {
    value <= 0f -> Tabler.Outline.VolumeOff
    else -> Tabler.Outline.Volume
}

private fun applySensitivityCurve(rawDelta: Float): Float {
    val sign = if (rawDelta >= 0) 1f else -1f
    val magnitude = abs(rawDelta)
    val curved = (magnitude / 0.5f).pow(0.8f) * 0.5f
    return sign * curved.coerceIn(0f, 0.5f)
}

// a11y step magnitude for the brightness/volume custom actions. Brightness and
// volume are both normalized 0f..1f, and the drag handler feeds the curved delta
// straight to the gesture callbacks; a fixed ~10% step matches a moderate drag
// and gives TalkBack/Switch Access users a predictable increment per invocation.
private const val A11Y_STEP_DELTA = 0.1f

@Composable
internal fun GestureOverlay(
    seekDirection: Int,
    seekOffsetMs: Long,
    brightnessValue: Float,
    volumeValue: Float,
    indicatorSide: GestureIndicatorSide = GestureIndicatorSide.OPPOSITE,
    gesturesEnabled: Boolean,
    swipeSeekMaxMs: Long,
    showControls: Boolean,
    onSeekGesture: (Long) -> Unit,
    onBrightnessGesture: (Float) -> Unit,
    onVolumeGesture: (Float) -> Unit,
    onClearOverlays: () -> Unit,
    onEdgeSwipe: () -> Unit,
    onHapticPulse: () -> Unit = {},
    overlayDismissDelayMs: Long = 800L,
    onStartGesture: () -> Unit = {},
    onCancelOverlays: () -> Unit = {},
) {
    val currentOnSeekGesture by rememberUpdatedState(onSeekGesture)
    val currentOnBrightnessGesture by rememberUpdatedState(onBrightnessGesture)
    val currentOnVolumeGesture by rememberUpdatedState(onVolumeGesture)
    val currentOnClearOverlays by rememberUpdatedState(onClearOverlays)
    val currentOnEdgeSwipe by rememberUpdatedState(onEdgeSwipe)
    val currentOnHapticPulse by rememberUpdatedState(onHapticPulse)
    val currentOnStartGesture by rememberUpdatedState(onStartGesture)
    val currentOnCancelOverlays by rememberUpdatedState(onCancelOverlays)

    // Bound-haptic checks below must see the live values, but adding them as
    // pointerInput keys would restart the handler mid-gesture — read through
    // remembered state like the callbacks, same pattern.
    val currentVolumeValue by rememberUpdatedState(volumeValue)
    val currentBrightnessValue by rememberUpdatedState(brightnessValue)

    val context = LocalContext.current

    val edgeThresholdPx = with(LocalDensity.current) { 40.dp.toPx() }
    val deadZonePx = with(LocalDensity.current) { 30.dp.toPx() }

    var lastBrightnessBoundHaptic by remember { mutableLongStateOf(0L) }
    var lastVolumeBoundHaptic by remember { mutableLongStateOf(0L) }
    val hapticMinInterval = 300L

    Box(
        modifier = Modifier
            .fillMaxSize()
            // a11y: the vertical-drag gesture surface previously carried no
            // semantics, so TalkBack/Switch Access users could not adjust
            // brightness (left half) or volume (right half). Two adjustable
            // values share one surface, so setProgress alone is ambiguous —
            // expose four custom actions (increase/decrease per value) that
            // drive the same gesture callbacks the drag uses. Each action
            // applies a fixed ~10% step (A11Y_STEP_DELTA). The pointerInput
            // gesture handling below is left intact; semantics are additive.
            .semantics {
                contentDescription = context.getString(
                    com.raulshma.jellyplay.feature.player.video.R.string.a11y_gesture_controls
                )
                customActions = listOf(
                    CustomAccessibilityAction(
                        label = context.getString(
                            com.raulshma.jellyplay.feature.player.video.R.string.a11y_brightness_increase
                        ),
                    ) {
                        currentOnBrightnessGesture(A11Y_STEP_DELTA); true
                    },
                    CustomAccessibilityAction(
                        label = context.getString(
                            com.raulshma.jellyplay.feature.player.video.R.string.a11y_brightness_decrease
                        ),
                    ) {
                        currentOnBrightnessGesture(-A11Y_STEP_DELTA); true
                    },
                    CustomAccessibilityAction(
                        label = context.getString(
                            com.raulshma.jellyplay.feature.player.video.R.string.a11y_volume_increase
                        ),
                    ) {
                        currentOnVolumeGesture(A11Y_STEP_DELTA); true
                    },
                    CustomAccessibilityAction(
                        label = context.getString(
                            com.raulshma.jellyplay.feature.player.video.R.string.a11y_volume_decrease
                        ),
                    ) {
                        currentOnVolumeGesture(-A11Y_STEP_DELTA); true
                    },
                )
            }
            .then(
                if (gesturesEnabled) Modifier.pointerInput(swipeSeekMaxMs, showControls, edgeThresholdPx, deadZonePx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y
                        var decided = false
                        var isHorizontal = false
                        var isEdgeSwipeGesture = false
                        var edgeSwipeConsumed = false
                        var prevBrightnessBoundHapticTime = 0L
                        var prevVolumeBoundHapticTime = 0L
                        var wasMultiTouchCancelled = false
                        currentOnStartGesture()
                        do {
                            val event = awaitPointerEvent()
                            // A second finger means the user is pinching to
                            // zoom/reframe the video surface. Abort this
                            // single-finger gesture (seek/brightness/volume)
                            // so it doesn't fight the pinch detector, and leave
                            // the changes unconsumed so the surface's pinch
                            // handler can take over.
                            if (event.changes.count { it.pressed } >= 2) {
                                wasMultiTouchCancelled = true
                                break
                            }
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val totalDx = change.position.x - startX
                            val totalDy = change.position.y - startY
                            if (!decided && (abs(totalDx) > deadZonePx || abs(totalDy) > deadZonePx)) {
                                decided = true
                                isHorizontal = abs(totalDx) > abs(totalDy)
                                isEdgeSwipeGesture = isHorizontal &&
                                    (startX < edgeThresholdPx || startX > size.width - edgeThresholdPx)
                            }
                            if (decided) {
                                if (isEdgeSwipeGesture) {
                                    if (!edgeSwipeConsumed) {
                                        edgeSwipeConsumed = true
                                        currentOnEdgeSwipe()
                                    }
                                } else if (isHorizontal) {
                                    val seekDeltaMs = ((totalDx / size.width) * swipeSeekMaxMs).toLong()
                                    currentOnSeekGesture(seekDeltaMs)
                                } else {
                                    val halfWidth = size.width / 2f
                                    val dy = change.position.y - change.previousPosition.y
                                    val rawDelta = -(dy / size.height) * 0.5f
                                    val delta = applySensitivityCurve(rawDelta)
                                    // Monotonic clock — avoids the wall-clock syscall of
                                    // System.currentTimeMillis() per move event and is immune
                                    // to wall-clock jumps. Gesture timing is duration-based
                                    // (hapticMinInterval), so this is strictly more correct.
                                    val now = SystemClock.elapsedRealtime()
                                    if (change.position.x > halfWidth) {
                                        currentOnVolumeGesture(delta)
                                        if ((currentVolumeValue <= 0f && delta < 0f) || (currentVolumeValue >= 1f && delta > 0f)) {
                                            if (now - prevVolumeBoundHapticTime > hapticMinInterval) {
                                                prevVolumeBoundHapticTime = now
                                                currentOnHapticPulse()
                                            }
                                        }
                                    } else {
                                        currentOnBrightnessGesture(delta)
                                        if ((currentBrightnessValue <= 0f && delta < 0f) || (currentBrightnessValue >= 1f && delta > 0f)) {
                                            if (now - prevBrightnessBoundHapticTime > hapticMinInterval) {
                                                prevBrightnessBoundHapticTime = now
                                                currentOnHapticPulse()
                                            }
                                        }
                                    }
                                }
                                change.consume()
                            }
                        } while (true)
                        if (wasMultiTouchCancelled) {
                            currentOnCancelOverlays()
                        } else {
                            currentOnClearOverlays()
                        }
                    }
                } else Modifier
            ),
    ) {
        if (seekDirection != 0 && seekOffsetMs > 0) {
            val isLeft = seekDirection < 0
            SeekCircleOverlay(
                isLeft = isLeft,
                seekOffsetMs = seekOffsetMs,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Gesture sides are fixed (left = brightness, right = volume); the
        // indicator bar can render on the opposite side (default) or the same
        // side as the gesture. OPPOSITE is the default so the bar doesn't sit
        // under the user's dragging thumb.
        val opposite = indicatorSide == GestureIndicatorSide.OPPOSITE
        val brightnessAlignment =
            if (opposite) Alignment.CenterEnd else Alignment.CenterStart
        val volumeAlignment =
            if (opposite) Alignment.CenterStart else Alignment.CenterEnd

        if (brightnessValue >= 0f) {
            EdgeBarOverlay(
                value = brightnessValue,
                icon = brightnessIcon(brightnessValue),
                label = "${(brightnessValue * 100).toInt()}%",
                isAtBound = brightnessValue <= 0.01f || brightnessValue >= 0.99f,
                modifier = Modifier
                    .align(brightnessAlignment)
                    .then(
                        if (opposite) Modifier.padding(end = 24.dp)
                        else Modifier.padding(start = 24.dp)
                    ),
            )
        }

        if (volumeValue >= 0f) {
            EdgeBarOverlay(
                value = volumeValue,
                icon = volumeIcon(volumeValue),
                label = "${(volumeValue * 100).toInt()}%",
                isAtBound = volumeValue <= 0.01f || volumeValue >= 0.99f,
                modifier = Modifier
                    .align(volumeAlignment)
                    .then(
                        if (opposite) Modifier.padding(start = 24.dp)
                        else Modifier.padding(end = 24.dp)
                    ),
            )
        }
    }
}

@Composable
private fun SeekCircleOverlay(
    isLeft: Boolean,
    seekOffsetMs: Long,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val reducedMotion = LocalReducedMotion.current

    // Seek-scrub ripple. Only visible mid-gesture, but still spins a redraw
    // coroutine. Freeze at a single ring in performance/reduced-motion mode.
    val rippleAnim = if (!reducedMotion) {
        rememberInfiniteTransition(label = "seek_ripple").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "seek_ripple_progress",
        ).value
    } else {
        0.5f
    }

    AnimatedVisibility(
        visible = true,
        enter = playerGestureFeedbackEnter(),
        exit = playerGestureFeedbackExit(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .align(if (isLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 48.dp)
                    .drawBehind {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxRadius = size.minDimension * 0.9f
                        for (i in 0..2) {
                            val phase = (rippleAnim + i * 0.33f) % 1f
                            val radius = maxRadius * phase
                            val alpha = (1f - phase) * 0.35f
                            if (radius > 0f && alpha > 0f) {
                                drawCircle(
                                    color = primaryColor.copy(alpha = alpha),
                                    radius = radius,
                                    center = center,
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (isLeft) {
                        Icon(
                            imageVector = Tabler.Outline.PlayerTrackPrev,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "-${seekOffsetMs / 1000}s",
                            color = playerOnScrim(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        Text(
                            text = "+${seekOffsetMs / 1000}s",
                            color = playerOnScrim(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Tabler.Outline.PlayerTrackNext,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EdgeBarOverlay(
    value: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isAtBound: Boolean,
    modifier: Modifier = Modifier,
) {
    val boundGlowAlpha = remember { Animatable(0f) }
    val glowSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    LaunchedEffect(isAtBound) {
        if (isAtBound) {
            boundGlowAlpha.animateTo(
                targetValue = 1f,
                animationSpec = glowSpec,
            )
        } else {
            boundGlowAlpha.snapTo(0f)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val glowColor = primaryColor.copy(alpha = boundGlowAlpha.value * 0.4f)

    AnimatedVisibility(
        visible = true,
        enter = playerEdgeBarEnter(),
        exit = playerEdgeBarExit(),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(playerOnScrim().copy(alpha = 0.1f))
                    .then(
                        if (boundGlowAlpha.value > 0.01f) Modifier.drawBehind {
                            drawCircle(
                                color = glowColor,
                                radius = size.minDimension * 0.8f,
                                center = Offset(size.width / 2f, size.height / 2f),
                            )
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isAtBound) primaryColor else playerOnScrim(),
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(160.dp)
                    .clip(ShapeCache.smoothPill)
                    .background(playerOnScrim().copy(alpha = 0.15f))
                    .then(
                        if (boundGlowAlpha.value > 0.01f) Modifier.drawBehind {
                            drawRoundRect(
                                color = glowColor,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension / 2f),
                            )
                        } else Modifier
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(value)
                        .clip(ShapeCache.smoothPill)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    primaryColor,
                                    primaryColor.copy(alpha = 0.7f),
                                )
                            )
                        )
                        .align(Alignment.BottomCenter),
                )
            }
            Text(
                label,
                color = if (isAtBound) primaryColor else playerOnScrim(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isAtBound) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                ),
            )
        }
    }
}
