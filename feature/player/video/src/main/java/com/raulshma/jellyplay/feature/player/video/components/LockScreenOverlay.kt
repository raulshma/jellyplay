package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Lock
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
internal fun SlideToUnlockOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unlockThresholdPx = with(LocalDensity.current) { 120.dp.toPx() }
    val tapSlopPx = with(LocalDensity.current) { 10.dp.toPx() }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var uiVisible by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            delay(3000L)
            onDismiss()
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
                .pointerInput(unlockThresholdPx, tapSlopPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var revealed = false
                        var totalDy = 0f
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val dy = change.position.y - down.position.y
                            totalDy = dy

                            if (!revealed && abs(dy) < tapSlopPx) {
                                if (!uiVisible) {
                                    uiVisible = true
                                }
                            } else {
                                revealed = true
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
                        dragOffsetY = 0f
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val progress = (dragOffsetY / unlockThresholdPx).coerceIn(0f, 1f)
            val iconAlpha = 0.5f + progress * 0.5f

            AnimatedVisibility(
                visible = uiVisible || progress > 0f,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.offset(y = with(LocalDensity.current) { (-dragOffsetY).toDp() }),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f + progress * 0.2f))
                            .alpha(iconAlpha),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Tabler.Outline.Lock,
                            contentDescription = "Drag up to unlock",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    Text(
                        text = "Slide up to unlock",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
