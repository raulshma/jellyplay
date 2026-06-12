package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
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
    var uiVisible by remember { mutableStateOf(false) }

    val animatedOffset = remember { Animatable(0f) }

    val tvUnlockFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isDragging, dragOffsetY) {
        if (isDragging) {
            animatedOffset.snapTo(dragOffsetY)
        } else {
            animatedOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            delay(3000L)
            onDismiss()
        }
    }

    LaunchedEffect(visible, isTv) {
        if (visible && isTv) {
            tvUnlockFocusRequester.requestFocus()
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
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
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
                            .background(Color.White.copy(alpha = 0.15f + progress * 0.15f))
                            .alpha(1f - progress * 0.7f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Crossfade(
                            targetState = isUnlocked,
                            animationSpec = tween(200),
                            label = "lockIcon",
                        ) { unlocked ->
                            Icon(
                                imageVector = if (unlocked) Tabler.Outline.LockOpen else Tabler.Outline.Lock,
                                contentDescription = if (unlocked) "Unlocked" else if (isTv) "Press OK to unlock" else "Drag up to unlock",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    Text(
                        text = if (isTv) "Press OK to unlock" else "Slide up to unlock",
                        color = Color.White.copy(alpha = 0.6f * (1f - progress)),
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
