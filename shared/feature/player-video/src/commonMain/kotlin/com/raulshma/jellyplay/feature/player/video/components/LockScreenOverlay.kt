package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Lock
import com.composables.icons.tabler.outline.LockOpen
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
internal fun SlideToUnlockOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current
    val density = LocalDensity.current
    val unlockThresholdPx = with(density) { 120.dp.toPx() }
    val tapSlopPx = with(density) { 10.dp.toPx() }

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    // Show the unlock hint immediately when the lock engages so the user knows the
    // screen is locked and how to unlock. Auto-hides after a few seconds (below) to
    // avoid obstructing the video; a tap re-reveals it.
    var uiVisible by remember { mutableStateOf(true) }
    // A monotonic "the user just asked to see the hint again" counter. `uiVisible`
    // alone can't restart the auto-hide `LaunchedEffect` below: a tap while the
    // hint is already shown is a `true -> true` no-op, so Compose won't relaunch
    // the effect and the 3s timer keeps ticking toward the original deadline.
    // Bumping this nonce forces a relaunch and resets the deadline on every
    // re-show (tap, drag-up, or re-engage of the lock).
    var hintShowKey by remember { mutableIntStateOf(0) }

    val animatedOffset = remember { Animatable(0f) }
    val offsetSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    val tvUnlockFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isDragging, dragOffsetY) {
        if (isDragging) {
            animatedOffset.snapTo(dragOffsetY)
        } else {
            animatedOffset.animateTo(
                targetValue = 0f,
                animationSpec = offsetSpec,
            )
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            uiVisible = true
            hintShowKey++
        }
    }

    LaunchedEffect(visible, uiVisible, isDragging, hintShowKey) {
        if (visible && uiVisible && !isDragging) {
            // Auto-hide the unlock hint after a few seconds so it doesn't sit on the
            // video forever. Hides the hint while the screen stays locked; a tap re-reveals it.
            delay(3000L)
            uiVisible = false
        }
    }

    LaunchedEffect(visible, isTv) {
        if (visible && isTv) {
            tvUnlockFocusRequester.tryRequestFocus("tv_unlock")
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isTv) {
                        Modifier
                            .focusRequester(tvUnlockFocusRequester)
                            .focusable()
                            .onDpadKey(
                                onSelect = {
                                    onUnlock()
                                    true
                                },
                                onBack = {
                                    onDismiss()
                                    true
                                },
                            )
                    } else {
                        Modifier.pointerInput(unlockThresholdPx, tapSlopPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                var revealed = false
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) break
                                    val dy = change.position.y - down.position.y

                                    if (!revealed && abs(dy) < tapSlopPx) {
                                        if (!uiVisible) uiVisible = true
                                        hintShowKey++
                                    } else {
                                        revealed = true
                                        isDragging = true
                                        val upward = (-dy).coerceAtLeast(0f)
                                        dragOffsetY = upward
                                        if (upward >= unlockThresholdPx) {
                                            onUnlock()
                                            return@awaitEachGesture
                                        }
                                    }
                                    change.consume()
                                } while (true)

                                if (!revealed) {
                                    uiVisible = true
                                    hintShowKey++
                                }
                                isDragging = false
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            val offsetY = if (isTv) 0f else animatedOffset.value
            val progress = if (isTv) 0f else (offsetY / unlockThresholdPx).coerceIn(0f, 1f)
            val isUnlocked = progress > 0.7f

            val showUi = uiVisible || progress > 0f || isTv

            AnimatedVisibility(
                visible = showUi,
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.graphicsLayer {
                        translationY = -offsetY
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(playerOnScrim().copy(alpha = 0.15f + progress * 0.15f))
                            .graphicsLayer { alpha = 1f - progress * 0.7f },
                        contentAlignment = Alignment.Center,
                    ) {
                        Crossfade(
                            targetState = isUnlocked,
                            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                            label = "lockIcon",
                        ) { unlocked ->
                            Icon(
                                imageVector = if (unlocked) Tabler.Outline.LockOpen else Tabler.Outline.Lock,
                                contentDescription = if (unlocked) "Unlocked" else if (isTv) "Press OK to unlock" else "Drag up to unlock",
                                tint = playerOnScrim(),
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    Text(
                        text = if (isTv) "Press OK to unlock" else "Slide up to unlock",
                        color = playerOnScrim().copy(alpha = 0.6f * (1f - progress)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .graphicsLayer { alpha = 1f - progress },
                    )
                }
            }
        }
    }
}
